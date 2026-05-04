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
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.utils.client.PacketSnapshot
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.minecraft.item.consume.UseAction
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/* 没写完，暂时不能使用 */
@Suppress("unused")
internal class GrimNoSlowFoodHeypixel(
    override val parent: ChoiceConfigurable<*>,
    val useActions: Array<UseAction>
) : Choice("Heypixel") {

    private var eatTicks = -1
    private var interactPacket: PlayerInteractItemC2SPacket? = null
    private var packets = Queues.newConcurrentLinkedQueue<PacketSnapshot>()

    override fun disable() {
        release(false)
    }

    fun release(use: Boolean = true) {
        if (use) {
            network.sendPacket(
                PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN,
                    Direction.DOWN
                )
            )
            mc.send {
                network.sendPacket(
                    PlayerInteractItemC2SPacket(
                        if (interactPacket!!.hand == Hand.MAIN_HAND) {
                            Hand.OFF_HAND
                        } else {
                            Hand.MAIN_HAND
                        },
                        interactPacket!!.sequence,
                        interactPacket!!.yaw,
                        interactPacket!!.pitch
                    )
                )
                packets.removeIf {
                    if (it.origin == TransferOrigin.OUTGOING) {
                        sendPacketSilently(it.packet)
                    } else {
                        handlePacket(it.packet)
                    }
                    true
                }
                eatTicks = -1
                interactPacket = null
                mc.send {
                    network.sendPacket(
                        PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            BlockPos.ORIGIN,
                            Direction.DOWN
                        )
                    )
                }
            }
        } else {
            packets.removeIf {
                if (it.origin == TransferOrigin.OUTGOING) {
                    sendPacketSilently(it.packet)
                } else {
                    handlePacket(it.packet)
                }
                true
            }
            eatTicks = -1
            interactPacket = null
        }
    }

    private fun isUsable(useAction: UseAction) = useAction in arrayOf(
        UseAction.EAT,
        UseAction.DRINK,
        UseAction.BOW,
        UseAction.SPEAR,
        UseAction.CROSSBOW
    )

    @Suppress("unused")
    private val playerUseMultiplierHandler = handler<PlayerUseMultiplier> { event ->
        if (!player.isUsingItem || player.activeItem.useAction !in useActions || player.itemUseTimeLeft <= 0) {
            return@handler
        }

        if (eatTicks > 0) {
            player.isSprinting = true
            event.forward = 1f
            event.sideways = 1f
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (eatTicks > 0) {
            eatTicks--
            if (eatTicks == 0) {
                //waitTicks(1)
                release()
            }
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (eatTicks >= 0) {
            if (packet is CommonPongC2SPacket ||
                (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id)
            ) {
                event.cancelEvent()
                packets.add(PacketSnapshot(packet, event.origin, System.currentTimeMillis()))
            }

            if (packet is PlayerActionC2SPacket
                && packet.action == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM
            ) {
                release(false)
            }

            return@handler
        }

        if (packet is PlayerInteractItemC2SPacket) {
            val stack = player.getStackInHand(packet.hand)
            if (stack.useAction in useActions) {
                interactPacket = packet
                eatTicks = stack.getMaxUseTime(player)
                event.cancelEvent()
            }
        }
    }

}
