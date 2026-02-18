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

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.bmw.sendPacketNoEvent
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import net.ccbluex.liquidbounce.event.events.MouseScrollEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.once
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.client.option.KeyBinding
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import org.lwjgl.glfw.GLFW

@Suppress("unused")
object ModuleEnderChest : ClientModule("EnderChest", Category.BMW) {

    private val autoSaveResources by boolean("AutoSaveResources", true)
    private val saveSelectedKey by key("SaveSelected")
    private val saveAllResourcesKey by key("SaveAllResources")
    private val intervalForEachSlot by int("intervalForEachSlot", 2, 0..20, "ticks")
    private val onlyResources by boolean("OnlyResources", true)

    val shouldHide: Boolean
        get() = running && isEnderChestScreen()

    private val resources = arrayOf(
        Items.IRON_INGOT,
        Items.GOLD_INGOT,
        Items.DIAMOND,
        Items.EMERALD
    )

    private var chestSyncId = -1
    private var saving = false

    override fun onEnabled() {
        chestSyncId = -1
        saving = false
    }

    private fun inventorySlotToScreenSlot(slotId: Int): Int {
        return if (slotId in 0..8) {
            slotId + 54
        } else {
            slotId + 27
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is OpenScreenS2CPacket) {
            if (packet.screenHandlerType == ScreenHandlerType.GENERIC_9X3
                && (packet.name.string.lowercase().contains("ender")
                    || packet.name.string.contains("末影箱"))
            ) {
                if (chestSyncId != packet.syncId) {
                    chestSyncId = packet.syncId
                    notifyAsMessage(ModuleEnderChest, "Chest opened")
                }
            } else {
                if (isEnderChestScreen()) {
                    once<MovementInputEvent>(READ_FINAL_STATE) {
                        it.sneak = false
                        it.jump = false
                        it.directionalInput = DirectionalInput.NONE
                        mc.send { sendPacketNoEvent(ModuleEnderChest, CloseHandledScreenC2SPacket(chestSyncId)) }
                    }
                    notifyAsMessage(ModuleEnderChest, "Chest closed")
                }
            }
        }

        if (packet is CloseHandledScreenC2SPacket) {
            if (isEnderChestScreen()) {
                event.cancelEvent()
                chestSyncId = -1
                once<MovementInputEvent>(READ_FINAL_STATE) {
                    it.sneak = false
                    it.jump = false
                    it.directionalInput = DirectionalInput.NONE
                    mc.send { sendPacketNoEvent(ModuleEnderChest, CloseHandledScreenC2SPacket(chestSyncId)) }
                }
                notifyAsMessage(ModuleEnderChest, "Chest closed")
            }
        }

        if (packet is CloseScreenS2CPacket) {
            if (isEnderChestScreen()) {
                event.cancelEvent()
                notifyAsMessage(ModuleEnderChest, "Cancelled CloseScreenS2CPacket")
            }
        }
    }

    @Suppress("unused")
    private val keyboardKeyHandler = sequenceHandler<KeyboardKeyEvent> { event ->
        if (!isEnderChestScreen()) return@sequenceHandler

        when (event.action) {
            GLFW.GLFW_PRESS -> {
                ModuleManager.getModules().filter { m -> m.bind.matchesKey(event.keyCode, event.scanCode) }
                    .forEach { m ->
                        m.enabled = !m.enabled || m.bind.action == InputBind.BindAction.HOLD
                    }
            }

            GLFW.GLFW_RELEASE -> {
                ModuleManager.getModules().filter { m ->
                    m.bind.matchesKey(event.keyCode, event.scanCode) &&
                        m.bind.action == InputBind.BindAction.HOLD
                }.forEach { m ->
                    m.enabled = false
                }
            }
        }

        if (event.action != GLFW.GLFW_PRESS || chestSyncId == -1) return@sequenceHandler

        when (event.key) {
            saveSelectedKey -> {
                val slotId = player.inventory.selectedSlot
                val itemStack = player.inventory.getStack(slotId)
                if (!itemStack.isEmpty && (!onlyResources || itemStack.item in resources)) {
                    once<MovementInputEvent>(READ_FINAL_STATE) {
                        it.sneak = false
                        it.jump = false
                        it.directionalInput = DirectionalInput.NONE
                        mc.send {
                            interaction.clickSlot(
                                chestSyncId,
                                inventorySlotToScreenSlot(slotId),
                                0,
                                SlotActionType.QUICK_MOVE,
                                player
                            )
                        }
                    }
                    notifyAsMessage(ModuleEnderChest, "Saved")
                } else {
                    notifyAsMessage(ModuleEnderChest, "Nothing to save")
                }
            }

            saveAllResourcesKey -> {
                val shouldSaveSlot = mutableListOf<Int>()

                for (slotId in 0..35) {
                    val itemStack = player.inventory.getStack(slotId)
                    if (!itemStack.isEmpty && itemStack.item in resources) {
                        shouldSaveSlot.add(slotId)
                    }
                }

                if (!shouldSaveSlot.isEmpty()) {
                    for (slotId in shouldSaveSlot) {
                        once<MovementInputEvent>(READ_FINAL_STATE) {
                            it.sneak = false
                            it.jump = false
                            it.directionalInput = DirectionalInput.NONE
                            mc.send {
                                interaction.clickSlot(
                                    chestSyncId,
                                    inventorySlotToScreenSlot(slotId),
                                    0,
                                    SlotActionType.QUICK_MOVE,
                                    player
                                )
                            }
                        }
                        waitTicks(intervalForEachSlot)
                    }
                    notifyAsMessage(ModuleEnderChest, "Saved")
                } else {
                    notifyAsMessage(ModuleEnderChest, "Nothing to save")
                }
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!isEnderChestScreen()) return@tickHandler

        if (player.abilities.flying) {
            mc.currentScreen!!.close()
            chestSyncId = -1
            once<MovementInputEvent>(READ_FINAL_STATE) {
                it.sneak = false
                it.jump = false
                it.directionalInput = DirectionalInput.NONE
                mc.send { sendPacketNoEvent(ModuleEnderChest, CloseHandledScreenC2SPacket(chestSyncId)) }
            }
            notifyAsMessage(ModuleEnderChest, "Chest closed")
            return@tickHandler
        }

        handleHotbarKeys()
        for (keyBinding in mc.options.allKeys) {
            handleInput(keyBinding)
        }

        val attackCode = mc.options.attackKey.boundKey.code
        if (attackCode <= 7) {
            if (isMouseButtonDown(mc.options.attackKey.boundKey.code)) doAttackStuff()
        } else {
            if (isKeyDown(mc.options.attackKey.boundKey.code)) doAttackStuff()
        }

        val useCode = mc.options.useKey.boundKey.code
        if (useCode <= 7) {
            if (isMouseButtonDown(mc.options.useKey.boundKey.code)) doItemUseStuff()
        } else {
            if (isKeyDown(mc.options.useKey.boundKey.code)) doItemUseStuff()
        }

        if (autoSaveResources && chestSyncId != -1 && !saving) {
            val shouldSaveSlot = mutableListOf<Int>()

            for (slotId in 0..35) {
                val itemStack = player.inventory.getStack(slotId)
                if (!itemStack.isEmpty && itemStack.item in resources) {
                    shouldSaveSlot.add(slotId)
                }
            }

            if (!shouldSaveSlot.isEmpty() && !saving) {
                saving = true
                for (slotId in shouldSaveSlot) {
                    once<MovementInputEvent>(READ_FINAL_STATE) {
                        it.sneak = false
                        it.jump = false
                        it.directionalInput = DirectionalInput.NONE
                        mc.send {
                            interaction.clickSlot(
                                chestSyncId,
                                inventorySlotToScreenSlot(slotId),
                                0,
                                SlotActionType.QUICK_MOVE,
                                player
                            )
                        }
                    }
                    waitTicks(intervalForEachSlot)
                }
                saving = false
                notifyAsMessage(ModuleEnderChest, "Saved")
            }
        }
    }

    @Suppress("unused")
    private val mouseScrollEventHandler = handler<MouseScrollEvent> { event ->
        if (isEnderChestScreen()) {
            val scrollY = event.vertical
            if (scrollY != 0.0) {
                val current = player.getInventory().selectedSlot
                val direction = if (scrollY > 0) -1 else 1
                val newSlot = ((current + direction + 9) % 9)
                player.getInventory().selectedSlot = newSlot
            }
        }
    }

    private fun isEnderChestScreen(): Boolean {
        val screen = mc.currentScreen
        return screen is GenericContainerScreen
            && screen.screenHandler.type == ScreenHandlerType.GENERIC_9X3
            && (screen.getTitle().string.lowercase().contains("ender")
            || screen.getTitle().string.contains("末影箱"))
    }

    private fun isKeyDown(key: Int): Boolean {
        return GLFW.glfwGetKey(mc.window.handle, key) == GLFW.GLFW_PRESS
    }

    private fun isMouseButtonDown(button: Int): Boolean {
        return GLFW.glfwGetMouseButton(mc.window.handle, button) == GLFW.GLFW_PRESS
    }

    private fun doAttackStuff() {
        val hr = mc.crosshairTarget
        var bhr: BlockHitResult? = null
        var ehr: EntityHitResult? = null

        if (hr is BlockHitResult) {
            bhr = hr
        } else if (hr is EntityHitResult) {
            ehr = hr
        }

        if (bhr != null && !world.getBlockState(bhr.blockPos).isAir) {
            interaction.updateBlockBreakingProgress(bhr.blockPos, bhr.side)
        }

        if (ehr != null && ehr.entity != null) {
            interaction.attackEntity(player, ehr.entity)
        }

        player.swingHand(Hand.MAIN_HAND)
    }

    private fun doItemUseStuff() {
        val hr = mc.crosshairTarget
        val bhr = hr as? BlockHitResult
        val ehr = hr as? EntityHitResult

        val hand = if (player.getInventory().getStack(40).isEmpty) Hand.MAIN_HAND else Hand.OFF_HAND

        interaction.interactItem(player, hand)

        if (bhr != null && !world.getBlockState(bhr.blockPos).isAir) {
            interaction.interactBlock(player, hand, bhr)
            player.swingHand(Hand.MAIN_HAND)
        }

        if (ehr != null && ehr.entity != null) {
            interaction.interactEntity(player, ehr.entity, hand)
            player.swingHand(Hand.MAIN_HAND)
        }
    }

    private fun handleInput(key: KeyBinding) {
        key.isPressed = isKeyDown(key.boundKey.code)
    }

    private fun handleHotbarKeys() {
        val hotbarKeys = intArrayOf(
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5,
            GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9
        )

        for (i in hotbarKeys.indices) {
            if (isKeyDown(hotbarKeys[i])) {
                player.getInventory().selectedSlot = i
            }
        }
    }

}
