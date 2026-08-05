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

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.util.Hand

object ModuleAttackCrystal : ClientModule("AttackCrystal", Category.BMW) {

    private val range by float("Range", 4.5f, 1f..6f)
    private val maxAngle by float("MaxAngle", 4f, 0.1f..20f)

    private var waitCrystal: EndCrystalEntity? = null
    private var waitRotation: Rotation? = null
    private var targetCrystal: EndCrystalEntity? = null
    private var targetRotation: Rotation? = null

    private val rotations = tree(RotationsConfigurable(this))

    @Suppress("unused")
    private val entitySpawnHandler = handler<PacketEvent> { event ->
        val packet = event.packet as? EntitySpawnS2CPacket ?: return@handler
        if (packet.entityType != EntityType.END_CRYSTAL) {
            return@handler
        }

        val crystal = EntityType.END_CRYSTAL.create(world, SpawnReason.SPAWN_ITEM_USE)
            ?: return@handler
        crystal.onSpawnPacket(packet)

        val rotation = Rotation.lookingAt(crystal.pos, player.eyePos)
        val angleDifference = (RotationManager.currentRotation ?: rotation).angleTo(rotation)
        val distance = crystal.distanceTo(player)

        if (distance <= range.toDouble() && angleDifference <= maxAngle) {
            waitCrystal = crystal
            waitRotation = rotation
        }
    }

    @Suppress("unused")
    private val scanHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (event.state != EventState.PRE) {
            return@handler
        }

        targetCrystal = null
        targetRotation = null

        for (crystal in world.entities.filterIsInstance<EndCrystalEntity>()) {
            val rotation = Rotation.lookingAt(crystal.pos, player.eyePos)
            val angleDifference = (RotationManager.currentRotation ?: rotation).angleTo(rotation)
            val distance = crystal.distanceTo(player)

            if (distance <= range.toDouble() && angleDifference <= maxAngle) {
                targetCrystal = crystal
                targetRotation = rotation
                break
            }
        }

        val crystalToAttack = waitCrystal ?: targetCrystal ?: return@handler
        val rotationToUse = waitRotation ?: targetRotation ?: return@handler
        waitCrystal = null
        waitRotation = null

        RotationManager.setRotationTarget(
            rotationToUse,
            considerInventory = false,
            configurable = rotations,
            Priority.IMPORTANT_FOR_ATTACK_CRYSTAL,
            this@ModuleAttackCrystal

        )

        performAttack(rotationToUse)
    }

    private fun performAttack(rotation: Rotation) {
        val originalYaw = player.yaw
        val originalPitch = player.pitch
        val attackRotation = RotationManager.currentRotation ?: rotation

        player.yaw = attackRotation.yaw
        player.pitch = attackRotation.pitch

        network.sendPacket(PlayerInteractEntityC2SPacket.attack(targetCrystal, player.isSneaking))
        player.swingHand(Hand.MAIN_HAND)

        player.yaw = originalYaw
        player.pitch = originalPitch
    }

}
