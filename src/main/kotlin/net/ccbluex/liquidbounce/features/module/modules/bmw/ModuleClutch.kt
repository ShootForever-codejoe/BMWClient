/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.bmw.isOnGround
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.bmw.simulatePlayerMovement
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFood
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFoodNoC0F
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.block.canPlayerReachBlock
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import kotlin.math.abs
import kotlin.random.Random

object ModuleClutch : ClientModule("Clutch", Category.BMW) {

    private val stuckWhenRescue by boolean("StuckWhenRescue", true)
    private val maxRescueTime by float("MaxRescueTime", 0.5f, 0f..5f, "seconds")
    private val maxTryCount by int("MaxTryCount", 5, 1..10)
    private val notDuringCombat by boolean("NotDuringCombat", false)
    private val simulationTicks by int("SimulationTicks", 100, 1..500, "ticks")
    private val onlyFalling by boolean("OnlyFalling", true)
    private val debug by boolean("Debug", false)

    private const val REST_TICKS = 3

    private var isRescuing = false
    private var scaffold = false
    private var interacted = false
    private var rescueTriesLast = 0
    private var rescueStartTime = 0L
    private var restTicks = 0
    private var hasGivenUp = false

    private val yawOffset = FloatOffsetGenerator()
    private val pitchOffset = FloatOffsetGenerator()

    private class FloatOffsetGenerator : FloatIterator() {
        private var prev = 0f
        override fun hasNext() = true
        override fun nextFloat(): Float {
            var offset: Float
            do {
                offset = Random.nextDouble(0.002, 0.01).toFloat()
            } while (abs(offset - prev) < 1.0E-6F)
            return offset.also { prev = it }
        }
    }

    override fun onDisabled() {
        reset()
        hasGivenUp = false
    }

    private fun reset(clear: Boolean = true) {
        if (scaffold && ModuleScaffold.enabled) {
            ModuleScaffold.enabled = false
        }
        if (isRescuing && !interacted) {
            ModuleFreeze.interact()
        }
        isRescuing = false
        scaffold = false
        interacted = false
        rescueStartTime = 0L
        restTicks = 0
        if (clear) {
            rescueTriesLast = 0
        }
    }

    private fun willLandInVoid(): Boolean {
        val result = simulatePlayerMovement(simulationTicks) { position, velocity, tick ->
            isOnGround(position)
        }
        return !result.stop
    }

    private fun isOverVoid(): Boolean {
        val top = player.pos.add(0.0, 0.1, 0.0)
        val bottom = Vec3d(top.x, world.bottomY - 1.0, top.z)
        val result = world.raycast(
            RaycastContext(
                top,
                bottom,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
            )
        )
        return result.type == HitResult.Type.MISS
    }

    private fun reachable(): Boolean {
        val searchRadius = player.blockInteractionRange.toInt() + 1

        for (dx in -searchRadius..searchRadius) {
            for (dy in -searchRadius..0) {
                if (player.eyeY.toInt() + dy + 1 > player.y) {
                    break
                }
                for (dz in -searchRadius..searchRadius) {
                    val blockPos = BlockPos(
                        player.x.toInt() + dx,
                        player.eyeY.toInt() + dy,
                        player.z.toInt() + dz
                    )
                    val blockState = world.getBlockState(blockPos)
                    if (!blockState.isAir && !blockState.isReplaceable && canPlayerReachBlock(blockPos)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun haveBlock(): Boolean {
        Slots.OffhandWithHotbar.forEach {
            if (ScaffoldBlockItemSelection.isValidBlock(it.itemStack)) {
                return true
            }
        }
        return false
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (scaffold && !ModuleScaffold.enabled) {
            reset()
            ModuleScaffold.enabled = true
            return@tickHandler
        }

        if (hasGivenUp) {
            if (player.isOnGround) hasGivenUp = false
            return@tickHandler
        }

        if (restTicks > 0) {
            restTicks--
            return@tickHandler
        }

        if (isRescuing) {
            if (!isOverVoid()) {
                if (debug) notifyAsMessage(ModuleClutch, "Rescued successfully")
                reset()
                return@tickHandler
            }
            val elapsedTime = System.currentTimeMillis() - rescueStartTime
            val timeoutMillis = (maxRescueTime * 1000).toLong()
            if (elapsedTime > timeoutMillis) {
                rescueTriesLast--
                if (rescueTriesLast <= 0) {
                    if (debug) notifyAsMessage(ModuleClutch, "Failed to rescue")
                    reset()
                    hasGivenUp = true
                    return@tickHandler
                } else {
                    reset(false)
                    restTicks = REST_TICKS
                }
            }
        } else {
            if (player.isOnGround
                || !(isOverVoid() && willLandInVoid())
            ) {
                if (rescueTriesLast > 0) {
                    if (debug) notifyAsMessage(ModuleClutch, "Rescued successfully")
                    reset()
                }
                return@tickHandler
            }

            if (
                (notDuringCombat && ModuleKillAura.running && ModuleKillAura.targetTracker.target != null)
                || ModuleScaffold.enabled
                || player.isInFluid
                || (onlyFalling && player.velocity.y >= -0.08)
                || !(reachable() && haveBlock())
            ) {
                if (rescueTriesLast > 0) {
                    if (debug) notifyAsMessage(ModuleClutch, "Failed to rescue")
                    reset()
                }
                return@tickHandler
            }

            if (GrimNoSlowFoodNoC0F.working) {
                (GrimNoSlowFood.modes.activeChoice as GrimNoSlowFoodNoC0F).release()
            }

            isRescuing = true
            rescueStartTime = System.currentTimeMillis()
            if (rescueTriesLast == 0) {
                if (debug) notifyAsMessage(ModuleClutch, "Rescuing...")
                rescueTriesLast = maxTryCount
            }
            waitTicks(1)
            scaffold = true
            ModuleScaffold.enabled = true
        }
    }

    @Suppress("unused")
    private val playerTickHandler = handler<PlayerTickEvent> { event ->
        if (isRescuing && stuckWhenRescue) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (!isRescuing || !stuckWhenRescue) return@handler

        val yaw = RotationManager.currentRotation?.yaw ?: player.yaw
        val pitch = RotationManager.currentRotation?.pitch ?: player.pitch
        val yawOffset = yawOffset.nextFloat()
        val pitchOffset = pitchOffset.nextFloat()

        when (packet) {
            is PlayerMoveC2SPacket -> {
                event.cancelEvent()
            }

            is PlayerInteractItemC2SPacket -> {
                event.cancelEvent()
                sendPacketSilently(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        yaw + yawOffset,
                        pitch + pitchOffset,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
                sendPacketSilently(
                    PlayerInteractItemC2SPacket(
                        packet.hand,
                        packet.sequence,
                        yaw + yawOffset,
                        pitch + pitchOffset,
                    )
                )
                interacted = true
            }

            is PlayerInteractEntityC2SPacket -> {
                event.cancelEvent()
                sendPacketSilently(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        yaw + yawOffset,
                        pitch + pitchOffset,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
                sendPacketSilently(packet)
            }

            is PlayerInteractBlockC2SPacket -> {
                event.cancelEvent()
                sendPacketSilently(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        yaw + yawOffset,
                        pitch + pitchOffset,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
                sendPacketSilently(packet)
                interacted = true
            }
        }
    }

}
