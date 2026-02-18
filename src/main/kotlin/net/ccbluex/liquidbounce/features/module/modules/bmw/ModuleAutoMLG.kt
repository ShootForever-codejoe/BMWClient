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
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import kotlin.math.min

object ModuleAutoMLG : ClientModule("AutoMLG", Category.BMW) {

    private val fallDistance by float("FallDistance", 7f, 3f..15f)

    private var placeWater = false
    private var timeout = -1
    private var oldRotation: Rotation? = null
    private var oldSlot = -1

    private fun reset() {
        placeWater = false
        timeout = -1
        oldRotation = null
        oldSlot = -1
    }

    override fun onEnabled() {
        reset()
    }

    private fun isOnGround(height: Double): Boolean {
        val collisions: Iterable<VoxelShape?> =
            world.getBlockCollisions(player, player.boundingBox.offset(0.0, height, 0.0))
        return collisions.iterator().hasNext()
    }

    private fun getWaterBucketSlot(): Int {
        for (i in 0..8) {
            if (player.inventory.getStack(i).item == Items.WATER_BUCKET) {
                return i
            }
        }

        return -1
    }

    @Suppress("unused")
    private val preTickHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (event.state != EventState.PRE) return@handler

        if (player.fallDistance > fallDistance) {
            if (oldRotation != null
                && oldSlot != -1
                && isOnGround(player.velocity.y)
                && getWaterBucketSlot() != -1
            ) {
                placeWater = true
                timeout = 10
            } else if (isOnGround(min(player.velocity.y, -1.0) * 2.0)
                && getWaterBucketSlot() != -1
                && (!ModuleKillAura.running || ModuleKillAura.targetTracker.target == null)
            ) {
                oldRotation = RotationManager.currentRotation ?: player.rotation
                player.pitch = 90f - (0.005f..0.01f).random()
                oldSlot = player.inventory.selectedSlot
                player.inventory.selectedSlot = getWaterBucketSlot()
            }
        }

        if (--timeout == 0) {
            reset()
            notifyAsMessage(ModuleAutoMLG, "Failed to place water")
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (placeWater) {
            placeWater = false

            var blockPos: BlockPos? = null
            if ((mc.crosshairTarget as? BlockHitResult)?.side == Direction.UP) {
                blockPos = (mc.crosshairTarget as BlockHitResult).blockPos
                interaction.interactItem(player, Hand.MAIN_HAND)

                val rotation = Rotation.lookingAt(blockPos.up().toCenterPos(), player.eyePos)
                player.yaw = normalizeYaw(rotation.yaw + (-0.005f..0.005f).random())
                player.pitch = clampPitchTo90(rotation.pitch + (-0.005f..0.005f).random())
            } else {
                player.pitch = oldRotation!!.pitch
                player.inventory.selectedSlot = oldSlot
                reset()
                notifyAsMessage(ModuleAutoMLG, "Failed to place water")
                return@tickHandler
            }

            waitTicks(1)

            interaction.interactItem(player, Hand.MAIN_HAND)
            player.yaw = oldRotation!!.yaw
            player.pitch = oldRotation!!.pitch
            player.inventory.selectedSlot = oldSlot
            reset()
        }
    }

}
