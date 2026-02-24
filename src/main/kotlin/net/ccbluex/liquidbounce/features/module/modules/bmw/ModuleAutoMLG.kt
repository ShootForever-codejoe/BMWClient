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
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object ModuleAutoMLG : ClientModule("AutoMLG", Category.BMW) {

    private val fallDistance by float("FallDistance", 4f, 3f..15f)

    private var placeWater = false
    private var timeout = -1
    private var oldRotation: Rotation? = null
    private var oldSlot = -1
    private var scaffold = false

    override fun onEnabled() {
        clear()
    }

    private fun clear() {
        placeWater = false
        timeout = -1
        oldRotation = null
        oldSlot = -1
        scaffold = false
    }

    private fun reset() {
        if (oldRotation != null) {
            player.yaw = oldRotation!!.yaw
            player.pitch = oldRotation!!.pitch
        }
        if (oldSlot != -1) {
            player.inventory.selectedSlot = oldSlot
        }
        if (scaffold) {
            ModuleScaffold.enabled = true
        }
        clear()
    }

    private fun willBeOnGround(height: Double): Boolean {
        return world.getBlockCollisions(
            player,
            player.boundingBox.offset(0.0, height, 0.0)
        ).iterator().hasNext()
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

        if (player.fallDistance >= fallDistance) {
            if (oldRotation != null && willBeOnGround(player.velocity.y)) {
                placeWater = true
            } else if (shouldPlaceWater()) {
                scaffold = ModuleScaffold.enabled
                if (scaffold) {
                    ModuleScaffold.enabled = false
                }
                oldRotation = RotationManager.currentRotation ?: player.rotation
                player.pitch = 90f - (0.002f..0.004f).random()
                oldSlot = player.inventory.selectedSlot
                player.inventory.selectedSlot = getWaterBucketSlot()
                timeout = 5
            }
        }

        if (--timeout == 0) {
            reset()
            notifyAsMessage(ModuleAutoMLG, "Failed to place water (timeout)")
        }
    }

    private fun shouldPlaceWater(): Boolean {
        if (oldRotation != null) return false
        if (!willBeOnGround(player.velocity.y * 3.0)) return false
        if (getWaterBucketSlot() == -1) return false

        val isTargetPresent = ModuleKillAura.running && ModuleKillAura.targetTracker.target != null
        return !isTargetPresent
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
                player.yaw = normalizeYaw(rotation.yaw + (-0.002f..0.002f).random())
                player.pitch = clampPitchTo90(rotation.pitch + (-0.002f..0.002f).random())
            } else {
                reset()
                notifyAsMessage(ModuleAutoMLG, "Failed to place water (bad rotation)")
                return@tickHandler
            }

            waitTicks(1)

            interaction.interactItem(player, Hand.MAIN_HAND)

            reset()
        }
    }

}
