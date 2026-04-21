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
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFood
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFoodNoC0F
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.entity.getBoundingBoxAt
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.entity.Entity
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

object ModuleAutoMLG : ClientModule("AutoMLG", Category.BMW) {

    private val fallDistance by float("FallDistance", 3f, 0f..15f)
    private val notDuringKillAura by boolean("NotDuringKillAura", false)
    private val simulationTicks by int("SimulationTicks", 100, 1..500, "ticks")

    private const val GRAVITY = 0.08
    private const val DRAG = 0.98

    private var rotated = false
    private var placeWater = false
    private var timeout = -1
    private var oldSlot = -1
    private var scaffold = false
    private var killAura = false

    override fun onEnabled() {
        clear()
    }

    private fun clear() {
        rotated = false
        placeWater = false
        timeout = -1
        oldSlot = -1
        scaffold = false
        killAura = false
    }

    private fun reset() {
        if (oldSlot != -1) {
            player.inventory.selectedSlot = oldSlot
        }
        if (scaffold) {
            ModuleScaffold.enabled = true
        }
        if (killAura) {
            ModuleKillAura.enabled = true
        }
        clear()
    }

    private fun predictLandingTicks(): Int {
        var position = player.pos
        var velocity = player.velocity

        val movementForward = player.input.movementForward.toDouble()
        val movementSideways = player.input.movementSideways.toDouble()

        repeat(simulationTicks) { tick ->
            val inputVelocity = Entity.movementInputToVelocity(
                Vec3d(movementSideways * DRAG, 0.0, movementForward * DRAG),
                0.02f,
                player.yaw
            )

            velocity = velocity.add(inputVelocity)
            position = position.add(velocity)

            if (world.getBlockCollisions(
                    player,
                    player.getBoundingBoxAt(position)
                ).iterator().hasNext()
            ) {
                return tick
            }

            velocity = velocity.multiply(DRAG, DRAG, DRAG).subtract(0.0, GRAVITY, 0.0)
        }

        return -1
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
    private val tickHandler = tickHandler {
        if (--timeout == 0) {
            reset()
            notifyAsMessage(ModuleAutoMLG, "Failed to place water (timeout)")
            return@tickHandler
        }

        if (placeWater) {
            placeWater = false

            var blockPos: BlockPos? = null
            if ((mc.crosshairTarget as? BlockHitResult)?.side == Direction.UP) {
                blockPos = (mc.crosshairTarget as BlockHitResult).blockPos
                interaction.sendSequencedPacket(world) { sequence ->
                    PlayerInteractItemC2SPacket(
                        Hand.MAIN_HAND,
                        sequence,
                        RotationManager.currentRotation?.yaw ?: player.yaw,
                        RotationManager.currentRotation?.pitch ?: player.pitch
                    )
                }

                var rotation = Rotation.lookingAt(
                    blockPos.up().toCenterPos(),
                    player.eyePos
                )
                rotation = Rotation(
                    rotation.yaw + (-0.002f..0.002f).random(),
                    rotation.pitch + (-0.002f..0.002f).random()
                ).normalize()
                RotationManager.setRotationTarget(
                    plan = RotationTarget(
                        rotation = rotation,
                        ticksUntilReset = 2,
                        resetThreshold = 2f,
                        considerInventory = false,
                        movementCorrection = MovementCorrection.SILENT
                    ),
                    priority = Priority.IMPORTANT_FOR_USER_SAFETY,
                    provider = ModuleAutoMLG
                )
            } else {
                reset()
                notifyAsMessage(ModuleAutoMLG, "Failed to place water (bad rotation)")
                return@tickHandler
            }

            waitTicks(1)

            interaction.sendSequencedPacket(world) { sequence ->
                PlayerInteractItemC2SPacket(
                    Hand.MAIN_HAND,
                    sequence,
                    RotationManager.currentRotation?.yaw ?: player.yaw,
                    RotationManager.currentRotation?.pitch ?: player.pitch
                )
            }

            reset()
            return@tickHandler
        }

        if (player.fallDistance < fallDistance || player.velocity.y >= -0.08) return@tickHandler

        val landingTicks = predictLandingTicks()

        if (landingTicks == 1 && rotated && getWaterBucketSlot() != -1) {
            placeWater = true

        } else if (!rotated
            && landingTicks == 2
            && getWaterBucketSlot() != -1
            && (!notDuringKillAura || !ModuleKillAura.running || ModuleKillAura.targetTracker.target == null)
        ) {
            scaffold = ModuleScaffold.enabled
            if (scaffold) ModuleScaffold.enabled = false

            killAura = ModuleKillAura.enabled
            if (killAura) ModuleKillAura.enabled = false

            if (GrimNoSlowFoodNoC0F.working) {
                (GrimNoSlowFood.modes.activeChoice as GrimNoSlowFoodNoC0F).release()
            }

            RotationManager.setRotationTarget(
                plan = RotationTarget(
                    rotation = Rotation(player.yaw, 90f - (0.002f..0.004f).random()),
                    ticksUntilReset = 2,
                    resetThreshold = 2f,
                    considerInventory = false,
                    movementCorrection = MovementCorrection.SILENT
                ),
                priority = Priority.IMPORTANT_FOR_USER_SAFETY,
                provider = ModuleAutoMLG
            )
            rotated = true
            oldSlot = player.inventory.selectedSlot
            player.inventory.selectedSlot = getWaterBucketSlot()

            timeout = 5
        }
    }

}
