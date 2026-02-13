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

import net.ccbluex.liquidbounce.bmw.clampPitchTo90
import net.ccbluex.liquidbounce.bmw.normalizeYaw
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape

object ModuleAutoMLG : ClientModule("AutoMLG", Category.BMW) {

    private val fallDistance by float("FallDistance", 5f, 3f..15f)

    private var pitch = -1f
    private var placeWater = false
    private var timeout = 0

    fun isOnGround(height: Double): Boolean {
        val collisions: Iterable<VoxelShape?> =
            world.getBlockCollisions(player, player.boundingBox.offset(0.0, height, 0.0))
        return collisions.iterator().hasNext()
    }

    @Suppress("unused")
    private val preTickHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (event.state != EventState.PRE) return@handler

        if (player.fallDistance > fallDistance) {
            if (pitch >= 0 && isOnGround(player.velocity.y)) {
                placeWater = true
            } else if (isOnGround(player.velocity.y * 2.0)) {
                for (i in 0..8) {
                    val item: ItemStack = player.inventory.getStack(i)
                    if (!item.isEmpty && item.item == Items.WATER_BUCKET) {
                        SilentHotbar.selectSlotSilently(ModuleAutoMLG, i, 3)
                        pitch = player.pitch
                        player.pitch = 90f - (0.005f..0.01f).random()
                        timeout = 10
                    }
                }
            }
        }

        if (--timeout == 0 && pitch >= 0) {
            pitch = -1f
            notifyAsMessage(ModuleAutoMLG, "Failed to place water")
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (placeWater) {
            placeWater = false

            var blockPos: BlockPos? = null
            if (mc.crosshairTarget is BlockHitResult && (mc.crosshairTarget as BlockHitResult).side == Direction.UP) {
                blockPos = (mc.crosshairTarget as BlockHitResult).blockPos
                interaction.interactItem(player, Hand.MAIN_HAND)
            } else {
                notifyAsMessage(ModuleAutoMLG, "Failed to place water")
                pitch = -1f
                return@tickHandler
            }

            waitTicks(1)

            val yaw = player.yaw
            val rotation = Rotation.lookingAt(blockPos.up().toCenterPos(), player.eyePos)
            player.yaw = normalizeYaw(rotation.yaw + (-0.005f..0.005f).random())
            player.pitch = clampPitchTo90(rotation.pitch + (-0.005f..0.005f).random())

            interaction.interactItem(player, Hand.MAIN_HAND)

            waitTicks(1)

            player.yaw = yaw
            player.pitch = pitch
            pitch = -1f
        }
    }

}
