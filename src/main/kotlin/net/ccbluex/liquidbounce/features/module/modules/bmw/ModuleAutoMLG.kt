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
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raycast
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

object ModuleAutoMLG : ClientModule("AutoMLG", Category.BMW) {

    private val fallDistance by float("FallDistance", 5f, 3f..15f)

    private var rotation = false
    private var above: BlockPos? = null
    private var originalSlot = 0
    private var timeout = 0

    override fun onEnabled() {
        rotation = false
        above = null
        originalSlot = 0
        timeout = 0
    }

    private fun isOnGround(height: Double): Boolean {
        val collisions = world.getBlockCollisions(player, player.boundingBox.offset(0.0, height, 0.0))
        return collisions.iterator().hasNext()
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (rotation) {
            event.directionalInput = DirectionalInput.NONE
        }
    }

    @Suppress("unused", "DEPRECATION")
    private val playerNetworkMovementTickEventHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (event.state != EventState.PRE) return@handler

        if (player.fallDistance > fallDistance) {
            if (rotation && isOnGround(player.velocity.y)) {
                val hand = if (originalSlot == -1) {
                    Hand.OFF_HAND
                } else {
                    Hand.MAIN_HAND
                }

                if (above == null) {
                    network.sendPacket(
                        PlayerMoveC2SPacket.LookAndOnGround(
                            player.yaw,
                            90f,
                            player.isOnGround,
                            player.horizontalCollision
                        )
                    )
                    interaction.interactItem(player, hand)
                    above = raycast(rotation = Rotation(player.yaw, 90f)).blockPos?.up()
                } else if (player.isOnGround) {
                    val nowAbove = raycast(rotation = Rotation(player.yaw, 90f)).blockPos?.up()
                    if (nowAbove == above) {
                        network.sendPacket(
                            PlayerMoveC2SPacket.LookAndOnGround(
                                player.yaw,
                                90f,
                                player.isOnGround,
                                player.horizontalCollision
                            )
                        )
                        interaction.interactItem(player, hand)
                    } else {
                        notifyAsMessage(ModuleAutoMLG, "Failed to recycle water")
                    }

                    if (originalSlot != -1) {
                        player.inventory.selectedSlot = originalSlot
                    }

                    above = null
                    rotation = false
                }

            } else if (isOnGround(player.velocity.y * 2.0)) {
                val item = Slots.OffhandWithHotbar.findSlot(Items.WATER_BUCKET)
                if (item != null) {
                    if (item.useHand == Hand.MAIN_HAND) {
                        originalSlot = player.inventory.selectedSlot
                        player.inventory.selectedSlot = item.hotbarSlot
                    } else {
                        originalSlot = -1
                    }

                    rotation = true
                    timeout = 20
                    above = null
                }
            }
        }

        if (--timeout == 0 && rotation) {
            rotation = false
            notifyAsMessage(ModuleAutoMLG, "Failed to place water")
        }
    }

}
