/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.PlayerUseMultiplier
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.renderEnvironmentForGUI
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.event.EventState
import net.minecraft.item.consume.UseAction
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.util.Hand
import org.lwjgl.glfw.GLFW
import java.util.ArrayDeque

/**
 * Reimplementation of the standalone Southside `NoSlowTest` module.
 *
 * The recovered UI exposes one optional `BowNoSlow` switch.  Food and drink
 * use always keep the normal movement multiplier; the switch additionally
 * enables the same behavior for bows and crossbows.  This distinction is
 * important because the switch is specifically named `BowNoSlow`, while the
 * base test module is also expected to cover the ordinary eating slowdown.
 *
 * Full client-side movement is implemented through the existing multiplier
 * event. No synthetic item-use packet is sent by this test module: the
 * diagnostic run showed that repeated PlayerInteractItem packets made
 * itemUseTimeLeft negative and prevented the food action from completing.
 */
object ModuleNoSlowTest : ClientModule(
    "NoSlowTest",
    Category.MOVEMENT,
    bind = GLFW.GLFW_KEY_K
) {

    /** Enables the bow/crossbow branch shown as `BowNoSlow` in the UI. */
    private val bowNoSlow by boolean("BowNoSlow", false)

    /** Enables the diagnostic log and on-screen state panel. */
    private val debug by boolean("Debug", false)

    /**
     * Uses a conservative one-full-tick/two-vanilla-tick cadence. This only
     * changes the movement multiplier; it never changes the server's item-use
     * state. Disable it for unrestricted client-side full speed.
     */
    private val grimSafe by boolean("GrimSafe", true)

    private val debugLines = ArrayDeque<String>()
    private var debugLastUsingItem = false
    private var debugLastAction: UseAction? = null
    private var debugLastNoSlow = false
    private var debugLastLoggedUseTime = Int.MIN_VALUE
    private var debugLastForward = 0.2f
    private var debugLastSideways = 0.2f
    /** True for the current tick when the conservative cadence uses vanilla slowdown. */
    private var grimSafePreventNoSlow = true
    private var debugGameTick = 0L
    private var debugNetworkPre = 0L
    private var debugNetworkPost = 0L
    private var debugLastSnapshotUseTime = Int.MIN_VALUE

    /**
     * The result is cached by item-use time because the same decision can be
     * read more than once in one client tick: once by the multiplier event and
     * once by the sprint hook. The cached value is always true for supported
     * actions, which is the actual NoSlowTest behavior.
     */
    private var cachedUseTime = Int.MIN_VALUE
    private var cachedAction: UseAction? = null
    private var cachedNoSlow = false

    private fun clearCachedDecision() {
        cachedUseTime = Int.MIN_VALUE
        cachedAction = null
        cachedNoSlow = false
        grimSafePreventNoSlow = false
    }

    private fun debugLog(message: String) {
        if (!debug) {
            return
        }

        val line = "[NoSlowTestDebug] $message"
        LiquidBounce.logger.info(line)
        synchronized(debugLines) {
            debugLines.addLast(line)
            while (debugLines.size > 8) {
                debugLines.removeFirst()
            }
        }
    }

    /**
     * A per-game-tick snapshot is intentionally separate from the multiplier
     * callback. It tells us whether the use state was already lost before the
     * movement hook runs, which distinguishes a key/input problem from a
     * packet-side interruption.
     */
    @Suppress("unused")
    private val debugGameTickHandler = handler<GameTickEvent> {
        if (!debug) {
            return@handler
        }

        debugGameTick++
        if (!running || !player.isUsingItem) {
            return@handler
        }

        val useTime = player.itemUseTime
        if (useTime != debugLastSnapshotUseTime) {
            debugLog(
                "snapshot tick=$debugGameTick using=true action=${player.activeItem.useAction.name} " +
                    "useTime=$useTime left=${player.itemUseTimeLeft} " +
                    "hand=${player.activeHand?.name ?: "NONE"} " +
                    "useKey=${mc.options.useKey.isPressed} " +
                    "forward=${"%.3f".format(player.input.movementForward)} " +
                    "sideways=${"%.3f".format(player.input.movementSideways)}"
            )
            debugLastSnapshotUseTime = useTime
        }
    }

    private fun debugObserve(
        action: UseAction?,
        useTime: Int,
        supported: Boolean,
        noSlow: Boolean,
        event: PlayerUseMultiplier
    ) {
        if (!debug) {
            return
        }

        val usingItem = action != null
        if (usingItem != debugLastUsingItem || action != debugLastAction) {
            debugLog(
                "state using=$usingItem action=${action?.name ?: "NONE"} " +
                    "supported=$supported useTime=$useTime"
            )
        }

        if (usingItem && noSlow != debugLastNoSlow) {
            debugLog("window useTime=$useTime noSlow=$noSlow")
        }

        if (usingItem && useTime != debugLastLoggedUseTime && useTime % 5 == 0) {
            debugLog(
                "sample useTime=$useTime left=${player.itemUseTimeLeft} " +
                    "event=${"%.2f".format(event.forward)}/${"%.2f".format(event.sideways)} " +
                    "input=${"%.3f".format(player.input.movementForward)}/" +
                    "${"%.3f".format(player.input.movementSideways)}"
            )
            debugLastLoggedUseTime = useTime
        }

        // Log every observed use-time transition, including values that are
        // not multiples of five. This is the key signal for detecting a
        // RELEASE_USE_ITEM or a server-side reset between two callbacks.
        if (usingItem && useTime != debugLastSnapshotUseTime) {
            debugLog(
                "multiplier tick=$debugGameTick useTime=$useTime left=${player.itemUseTimeLeft} " +
                    "action=${action.name} hand=${player.activeHand?.name ?: "NONE"} " +
                    "useKey=${mc.options.useKey.isPressed} " +
                    "event=${"%.2f".format(event.forward)}/${"%.2f".format(event.sideways)} " +
                    "inputBeforeVanilla=${"%.3f".format(player.input.movementForward)}/" +
                    "${"%.3f".format(player.input.movementSideways)} " +
                    "sprint=${player.isSprinting} ground=${player.isOnGround} " +
                    "phase=${useTime % 3}"
            )
        }

        debugLastUsingItem = usingItem
        debugLastAction = action
        debugLastNoSlow = noSlow
        debugLastForward = event.forward
        debugLastSideways = event.sideways
        if (!usingItem) {
            debugLastLoggedUseTime = Int.MIN_VALUE
            debugLastSnapshotUseTime = Int.MIN_VALUE
        }
    }

    private fun noSlowForCurrentUse(): Boolean {
        if (!running || !player.isUsingItem) {
            clearCachedDecision()
            return false
        }

        val action = player.activeItem.useAction
        if (!supportsNoSlow(action)) {
            clearCachedDecision()
            return false
        }

        val useTime = player.itemUseTime
        if (cachedUseTime == useTime && cachedAction == action) {
            return cachedNoSlow
        }

        cachedUseTime = useTime
        cachedAction = action
        // Do not send a second interaction packet here. The previous packet
        // strategy kept the client in an artificial use state (the log showed
        // itemUseTimeLeft=-68), so the food could never finish. The cadence is
        // now purely local and leaves the normal use/release packets intact.
        // The previous two-full-ticks cadence produced a Grim correction on
        // every full-speed window. Keep only the one-third window that is
        // used by the existing Grim33% implementation in this repository.
        cachedNoSlow = !grimSafe || useTime % 3 == 0
        grimSafePreventNoSlow = !cachedNoSlow
        return cachedNoSlow
    }

    /**
     * Shared predicate used by the sprint hook and the multiplier handler.
     * The vanilla `tickMovement` 0.2 branch must remain enabled: the existing
     * `PlayerUseMultiplier` hook first divides input by 0.2 and then applies
     * the selected multiplier. Removing that vanilla branch would leave the
     * input at five times its intended value and trigger Grim Simulation.
     */
    @JvmStatic
    fun shouldBypassItemSlowdown(): Boolean {
        return noSlowForCurrentUse()
    }

    /** Kept as a source-compatible alias for external calls. */
    @JvmStatic
    fun shouldBypassBowSlowdown(): Boolean = shouldBypassItemSlowdown()

    private fun shouldBypassUseAction(action: UseAction): Boolean = when (action) {
        UseAction.EAT, UseAction.DRINK -> true
        UseAction.BOW, UseAction.CROSSBOW -> bowNoSlow
        else -> false
    }

    private fun supportsNoSlow(action: UseAction): Boolean = shouldBypassUseAction(action)

    /**
     * Run after normal multiplier handlers so this test module remains
     * independent from the regular `NoSlow` module and its mode choices.
     */
    @Suppress("unused")
    private val playerUseMultiplierHandler = handler<PlayerUseMultiplier>(READ_FINAL_STATE) { event ->
        val action = if (player.isUsingItem) player.activeItem.useAction else null
        if (action == null) {
            debugObserve(null, 0, false, false, event)
            return@handler
        }

        val supported = supportsNoSlow(action)
        if (supported) {
            val noSlow = shouldBypassItemSlowdown()
            val multiplier = if (noSlow) 1f else 0.2f
            event.forward = multiplier
            event.sideways = multiplier

            if (noSlow) {
                // Keep the sprint state consistent with full-speed item use.
                // No additional interaction packet is sent.
                player.isSprinting = true
            }

            debugObserve(action, player.itemUseTime, true, noSlow, event)
        } else {
            debugObserve(action, player.itemUseTime, false, false, event)
        }
    }

    /**
     * Network trace only. This handler must remain side-effect free so the
     * normal START/RELEASE item-use packet sequence is not disturbed.
     */
    @Suppress("unused")
    private val grimSafeNetworkHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (debug && running && player.isUsingItem) {
            if (event.state == EventState.PRE) {
                debugNetworkPre++
            } else {
                debugNetworkPost++
            }
            debugLog(
                "network state=${event.state.name} tick=$debugGameTick " +
                    "pre=$debugNetworkPre post=$debugNetworkPost " +
                    "useTime=${player.itemUseTime} left=${player.itemUseTimeLeft} " +
                    "input=${"%.3f".format(player.input.movementForward)}/" +
                    "${"%.3f".format(player.input.movementSideways)} " +
                    "pos=${"%.3f".format(event.x)},${"%.3f".format(event.y)}," +
                    "${"%.3f".format(event.z)} ground=${event.ground}"
            )
        }

        if (!grimSafe || !running || !player.isUsingItem) {
            return@handler
        }

        debugLog(
            "movement-window state=${event.state.name} " +
                "useTime=${player.itemUseTime} prevent=$grimSafePreventNoSlow"
        )
    }

    /**
     * Packet trace for the exact packets that can end or restart item use.
     * It is read-only and never cancels or changes a packet.
     */
    @Suppress("unused")
    private val debugPacketHandler = handler<PacketEvent> { event ->
        if (!debug || !running) {
            return@handler
        }

        val packet = event.packet
        val packetType: String
        val detail = when (packet) {
            is PlayerActionC2SPacket -> {
                packetType = "PlayerActionC2SPacket"
                "action=${packet.action}"
            }
            is PlayerInteractItemC2SPacket -> {
                packetType = "PlayerInteractItemC2SPacket"
                "hand=${packet.hand.name} sequence=${packet.sequence}"
            }
            is UpdateSelectedSlotC2SPacket -> {
                packetType = "UpdateSelectedSlotC2SPacket"
                "slot=${packet.selectedSlot}"
            }
            is PlayerPositionLookS2CPacket -> {
                packetType = "PlayerPositionLookS2CPacket"
                "setback=${packet.toString().take(180)}"
            }
            is ScreenHandlerSlotUpdateS2CPacket -> {
                packetType = "ScreenHandlerSlotUpdateS2CPacket"
                "slotUpdate=${packet.toString().take(180)}"
            }
            else -> return@handler
        }

        debugLog(
            "packet origin=${event.origin.name} original=${event.original} " +
                "cancelled=${event.isCancelled} using=${mc.player?.isUsingItem == true} " +
                "type=$packetType $detail"
        )
    }

    /**
     * Draws the current use-action decision and the last few diagnostic lines
     * in the top-left corner while `Debug` is enabled.
     */
    @Suppress("unused")
    private val debugOverlayHandler = handler<OverlayRenderEvent> { event ->
        if (!debug || !running || mc.player == null || mc.world == null) {
            return@handler
        }

        val currentPlayer = mc.player ?: return@handler
        val action = if (currentPlayer.isUsingItem) currentPlayer.activeItem.useAction else null
        val supported = action?.let(::supportsNoSlow) == true
        val noSlow = supported && shouldBypassItemSlowdown()
        val lines = buildList {
            add("NoSlowTest DEBUG")
            add("using=${action != null} action=${action?.name ?: "NONE"} supported=$supported")
            add(
                "useTime=${if (action != null) currentPlayer.itemUseTime else 0} " +
                    "left=${if (action != null) currentPlayer.itemUseTimeLeft else 0} " +
                    "window=$noSlow"
            )
            add(
                "hand=${currentPlayer.activeHand?.name ?: "NONE"} " +
                    "sprint=${currentPlayer.isSprinting} bowNoSlow=$bowNoSlow " +
                    "prevent=$grimSafePreventNoSlow"
            )
            add(
                "event=${"%.2f".format(debugLastForward)}/" +
                    "${"%.2f".format(debugLastSideways)} " +
                    "input=${"%.3f".format(currentPlayer.input.movementForward)}/" +
                    "${"%.3f".format(currentPlayer.input.movementSideways)}"
            )
            add("cached=$cachedUseTime/$cachedAction/$cachedNoSlow")
            add("recent:")
            val recent = synchronized(debugLines) { debugLines.toList().takeLast(4) }
            addAll(recent)
        }

        renderEnvironmentForGUI(event) {
            val x = 4
            val y = 4
            val lineHeight = mc.textRenderer.fontHeight + 1
            val width = lines.maxOf { mc.textRenderer.getWidth(it) } + 8
            val height = lines.size * lineHeight + 6

            event.context.fill(x - 2, y - 2, x + width, y + height, 0xAA101018.toInt())
            lines.forEachIndexed { index, line ->
                val color = if (index == 0) 0xFFFFAA00.toInt() else 0xFFFFFFFF.toInt()
                event.context.drawTextWithShadow(mc.textRenderer, line, x, y + index * lineHeight, color)
            }
        }
    }
}
