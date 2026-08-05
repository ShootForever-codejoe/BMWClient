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

package net.ccbluex.liquidbounce.bmw

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFood
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFoodNoC0F
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.utils.raycast
import net.ccbluex.liquidbounce.utils.client.interactItem
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.math.minus
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

object PlacementManager : EventListener, MinecraftShortcuts {

    var working = false
        private set
    var requester: ClientModule? = null
        private set
    var request: PlacementRequest? = null
        private set
    private var oldSlot = -1
    private var scaffold = false
    private var killAura = false

    private fun reset() {
        if (oldSlot != -1) {
            player.inventory.selectedSlot = oldSlot
            oldSlot = -1
        }
        if (scaffold) {
            ModuleScaffold.enabled = true
            scaffold = false
        }
        if (killAura) {
            ModuleKillAura.enabled = true
            killAura = false
        }
        requester = null
        request = null
        oldSlot = -1
        working = false
    }

    abstract class PlacementRequest(
        val pos: Vec3d? = null, /* pitch = 90 when null */
        val priority: Priority = Priority.IMPORTANT_FOR_PLAYER_LIFE
    )

    class PlaceWaterRequest(
        pos: Vec3d? = null,
        priority: Priority = Priority.IMPORTANT_FOR_PLAYER_LIFE,
        val debug: PlaceWaterDebug = PlaceWaterDebug.NONE
    ) : PlacementRequest(pos, priority)

    data class PlaceWaterDebug(
        val noBucket: String? = null,
        val failToPlace: String? = null,
        val failToRecycle: String? = null
    ) {
        companion object {
            val NONE = PlaceWaterDebug()
            val DEFAULT = PlaceWaterDebug(
                noBucket = "No water bucket found",
                failToPlace = "Failed to place water",
                failToRecycle = "Failed to recycle water"
            )
        }
    }

    class PlaceBlockRequest(
        pos: Vec3d? = null,
        priority: Priority = Priority.IMPORTANT_FOR_PLAYER_LIFE,
        val slot: Int, /* 9: Offhand */
        val debug: String? = null
    ) : PlacementRequest(pos, priority)

    fun place(requester: ClientModule, content: PlacementRequest): Boolean {
        if (this.requester != null || working) {
            return false
        }

        this.requester = requester
        this.request = content
        return true
    }

    @Suppress("unused")
    private val tickHandler = tickHandler(priority = EventPriorityConvention.MODEL_STATE) {
        if (requester == null || working) return@tickHandler

        if (request == null) {
            reset()
            return@tickHandler
        }

        working = true

        scaffold = ModuleScaffold.enabled
        if (scaffold) ModuleScaffold.enabled = false

        killAura = ModuleKillAura.enabled
        if (killAura) ModuleKillAura.enabled = false

        if (GrimNoSlowFoodNoC0F.Companion.working) {
            (GrimNoSlowFood.modes.activeChoice as GrimNoSlowFoodNoC0F).release()
        }

        val position = simulatePlayerMovement(2).position
        var rotation: Rotation
        if (request!!.pos == null) {
            rotation = Rotation(
                RotationManager.currentRotation?.yaw ?: player.yaw,
                90f - (0.002f..0.004f).random()
            )
        } else {
            rotation = Rotation.lookingAt(
                request!!.pos!!,
                player.eyePos.add(position.minus(player.pos)),
            )
            rotation = Rotation(
                rotation.yaw + (-0.002f..0.002f).random(),
                rotation.pitch + (-0.002f..0.002f).random()
            ).normalize()
        }

        when (request) {
            is PlaceWaterRequest -> {
                val request = request as PlaceWaterRequest

                val waterBucketSlot = getWaterBucketSlot()
                if (waterBucketSlot == -1) {
                    if (request.debug.noBucket != null) {
                        notifyAsMessage(requester!!, request.debug.noBucket)
                    }
                    reset()
                    return@tickHandler
                }
                val hand = if (waterBucketSlot == 9) Hand.OFF_HAND else Hand.MAIN_HAND

                if (waterBucketSlot != 9) {
                    oldSlot = player.inventory.selectedSlot
                    player.inventory.selectedSlot = waterBucketSlot
                }

                RotationManager.setRotationTarget(
                    plan = RotationTarget(
                        rotation = rotation,
                        ticksUntilReset = 3,
                        resetThreshold = 1f,
                        considerInventory = false,
                        movementCorrection = MovementCorrection.SILENT
                    ),
                    priority = request.priority,
                    provider = requester!!
                )

                waitTicks(2)

                val hitResult1 = raycast(RotationManager.serverRotation, player.blockInteractionRange)
                if (hitResult1.type == HitResult.Type.BLOCK && hitResult1.side == Direction.UP) {
                    interaction.interactBlock(player, hand, hitResult1)
                    interaction.interactItem(
                        player,
                        hand,
                        RotationManager.serverRotation.yaw,
                        RotationManager.serverRotation.pitch
                    )
                } else {
                    if (request.debug.failToPlace != null) {
                        notifyAsMessage(requester!!, request.debug.failToPlace)
                    }
                    reset()
                    return@tickHandler
                }

                waitTicks(1)

                val hitResult2 = raycast(RotationManager.serverRotation, player.blockInteractionRange)
                if (hitResult2.type == HitResult.Type.BLOCK && hitResult2.side == Direction.UP) {
                    val blockHitResult = mc.crosshairTarget as BlockHitResult
                    interaction.interactBlock(player, hand, blockHitResult)
                    interaction.interactItem(
                        player,
                        hand,
                        RotationManager.serverRotation.yaw,
                        RotationManager.serverRotation.pitch
                    )
                } else {
                    if (request.debug.failToRecycle != null) {
                        notifyAsMessage(requester!!, request.debug.failToRecycle)
                    }
                    reset()
                    return@tickHandler
                }
            }

            is PlaceBlockRequest -> {
                val request = request as PlaceBlockRequest

                val hand = if (request.slot == 9) Hand.OFF_HAND else Hand.MAIN_HAND

                if (request.slot in 0..8) {
                    oldSlot = player.inventory.selectedSlot
                    player.inventory.selectedSlot = request.slot
                }

                RotationManager.setRotationTarget(
                    plan = RotationTarget(
                        rotation = rotation,
                        ticksUntilReset = 2,
                        resetThreshold = 1f,
                        considerInventory = false,
                        movementCorrection = MovementCorrection.SILENT
                    ),
                    priority = request.priority,
                    provider = requester!!
                )

                waitTicks(2)

                val hitResult = raycast(RotationManager.serverRotation, player.blockInteractionRange)
                if (hitResult.type == HitResult.Type.BLOCK && hitResult.side == Direction.UP) {
                    val blockHitResult = mc.crosshairTarget as BlockHitResult
                    interaction.interactBlock(player, hand, blockHitResult)
                } else {
                    if (request.debug != null) {
                        notifyAsMessage(requester!!, request.debug)
                    }
                    reset()
                    return@tickHandler
                }
            }
        }

        waitTicks(1)
        reset()
    }

}
