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

package net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food

import com.google.common.collect.Queues
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerUseMultiplier
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.minecraft.item.consume.UseAction
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

internal class GrimNoSlowFoodNoC0F(
    override val parent: ChoiceConfigurable<*>,
    val useActions: Array<UseAction>
) : Choice("NoC0F") {

    private var step = Step.NONE
    private var noUsingItemTicks = 0
    private var packets = Queues.newConcurrentLinkedQueue<Packet<*>>()

    companion object {
        val working: Boolean
            get() = GrimNoSlowFood.running
                && GrimNoSlowFood.modes.activeChoice is GrimNoSlowFoodNoC0F
                && (GrimNoSlowFood.modes.activeChoice as GrimNoSlowFoodNoC0F).step != Step.NONE
    }

    override fun disable() {
        step = Step.NONE
        noUsingItemTicks = 0
        packets.clear()
    }

    enum class Step {
        NONE,
        CANCEL_C0F,
        SWAP_HANDS,
        EATING
    }

    fun release() {
        step = Step.NONE
        packets.removeIf {
            handlePacket(it)
            true
        }
        network.sendPacket(PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ))
    }

    private fun isUsable(useAction: UseAction) = useAction in arrayOf(
        UseAction.EAT,
        UseAction.DRINK,
        UseAction.BOW,
        UseAction.SPEAR,
        UseAction.CROSSBOW
    )

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (step != Step.EATING) {
            noUsingItemTicks = 0
            return@tickHandler
        }

        if (player.isUsingItem) {
            noUsingItemTicks = 0
        } else {
            noUsingItemTicks++
            if (noUsingItemTicks >= 5) {
                release()
                return@tickHandler
            }
        }
    }

    @Suppress("unused")
    private val playerUseMultiplierHandler = handler<PlayerUseMultiplier> { event ->
        if (player.activeItem.useAction !in useActions || player.itemUseTimeLeft <= 0) {
            return@handler
        }

        val oppositeHand = if (player.activeHand == Hand.MAIN_HAND) {
            Hand.OFF_HAND
        } else {
            Hand.MAIN_HAND
        }

        if (isUsable(player.getStackInHand(oppositeHand).useAction)) {
            return@handler
        }

        if (step != Step.EATING) {
            mc.options.useKey.isPressed = false
        }

        if (step == Step.NONE) {
            step = Step.CANCEL_C0F
            if (InventoryManager.isInventoryOpenServerSide) {
                network.sendPacket(CloseHandledScreenC2SPacket(player.currentScreenHandler.syncId))
            }
        } else if (step == Step.EATING) {
            player.isSprinting = true
            event.forward = 1f
            event.sideways = 1f
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is CommonPingS2CPacket && step != Step.NONE) {
            event.cancelEvent()
            packets.add(packet)
            if (step == Step.CANCEL_C0F) {
                step = Step.SWAP_HANDS
                mc.send {
                    network.sendPacket(PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ORIGIN,
                        Direction.DOWN
                    ))
                }
            }
        }

        if (packet is ScreenHandlerSlotUpdateS2CPacket && step == Step.SWAP_HANDS) {
            mc.options.useKey.isPressed = true
            step = Step.EATING
        }

        if (packet is PlayerActionC2SPacket
            && packet.action == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM
            && step == Step.EATING
        ) {
            release()
        }

        if (packet is EntityVelocityUpdateS2CPacket
            && packet.entityId == player.id
            && step != Step.NONE
        ) {
            packets.add(packet)
            event.cancelEvent()
        }
    }

}
