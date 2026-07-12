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

package net.ccbluex.liquidbounce.features.module.modules.combat.aimbot.autobow

import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.KeybindIsPressedEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.aimbot.ModuleAutoBow
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.PlayerSimulationCache
import net.ccbluex.liquidbounce.utils.entity.SimulatedArrow
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayerCache
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.ccbluex.liquidbounce.utils.math.scale
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryData
import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.item.BowItem
import net.minecraft.item.CrossbowItem
import net.minecraft.item.ItemStack
import net.minecraft.item.TridentItem
import net.minecraft.util.Hand
import net.minecraft.util.math.Vec3d

object AutoBowAutoShootFeature : ToggleableConfigurable(ModuleAutoBow, "AutoShoot", true) {

    private val charged by int("Charged", 15, 3..20, suffix = "ticks")

    private val chargedRandom by floatRange(
        "ChargedRandom",
        0.0F..0.0F,
        -10.0F..10.0F,
        suffix = "ticks"
    )
    private val delayBetweenShots by float("DelayBetweenShots", 0.0F, 0.0F..5.0F, suffix = "s")
    private val aimThreshold by float("AimThreshold", 1.5F, 1.0F..4.0F, suffix = "°")
    private val requiresHypotheticalHit by boolean("RequiresHypotheticalHit", false)
    private val usePrechargedCrossbow by boolean("UsePrechargedCrossbow", false)

    private var currentChargeRandom: Int? = null

    private fun updateChargeRandom() {
        val lenHalf = (chargedRandom.endInclusive - chargedRandom.start) / 2.0F
        val mid = chargedRandom.start + lenHalf

        currentChargeRandom =
            (mid + ModuleAutoBow.random.nextGaussian() * lenHalf).toInt()
                .coerceIn(chargedRandom.start.toInt()..chargedRandom.endInclusive.toInt())
    }

    private fun getChargedRandom(): Int {
        if (currentChargeRandom == null) {
            updateChargeRandom()
        }

        return currentChargeRandom!!
    }

    private var forceUncharged = false

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        forceUncharged = false

        val usingItemHand = player.activeHand
            ?: if (usePrechargedCrossbow) {
                Hand.entries.find { player.getStackInHand(it).isChargedCrossbow } ?: return@handler
            } else {
                return@handler
            }

        val usingItemStack = if (player.isUsingItem) player.activeItem else player.getStackInHand(usingItemHand)

        when (usingItemStack.item) {
            is CrossbowItem -> {
                val pullTime = CrossbowItem.getPullTime(usingItemStack, player)
                val isChargedNow = usingItemStack.isChargedCrossbow
                if (!isChargedNow && player.itemUseTime < pullTime) {
                    return@handler
                }
            }

            is BowItem -> {
                if (player.itemUseTime < charged + getChargedRandom()) {
                    return@handler
                }
            }

            is TridentItem -> {
                if (player.itemUseTime <= TridentItem.MIN_DRAW_DURATION) {
                    return@handler
                }
            }

            else -> return@handler
        }

        if (!ModuleAutoBow.lastShotTimer.hasElapsed((delayBetweenShots * 1000.0F).toLong())) {
            return@handler
        }

        if (requiresHypotheticalHit) {
            val hypotheticalHit = getHypotheticalHit()

            if (hypotheticalHit == null || !hypotheticalHit.shouldBeAttacked()) {
                return@handler
            }
        } else if (AutoBowAimbotFeature.enabled) {
            if (AutoBowAimbotFeature.targetTracker.target == null) {
                return@handler
            }

            val targetRotation = RotationManager.activeRotationTarget ?: return@handler

            val aimDifference = RotationManager.serverRotation.angleTo(targetRotation.rotation)

            if (aimDifference > aimThreshold) {
                return@handler
            }
        }

        if (usingItemStack.item is CrossbowItem) {
            val isChargedNow = usingItemStack.isChargedCrossbow
            if (isChargedNow) {
                interaction.interactItem(player, usingItemHand)
                ModuleAutoBow.lastShotTimer.reset()
            } else {
                forceUncharged = true
            }
        } else {
            forceUncharged = true
            if (usingItemStack.item is BowItem) {
                updateChargeRandom()
            }
        }
    }

    @Suppress("unused")
    private val keybindHandler = handler<KeybindIsPressedEvent> { event ->
        if (event.keyBinding == mc.options.useKey && forceUncharged) {
            event.isPressed = false
        }
    }

    private fun getHypotheticalHit(): Entity? {
        player.activeHand ?: return null
        val rotation = RotationManager.serverRotation
        val trajectoryInfo =
            TrajectoryData.getRenderedTrajectoryInfo(player, player.activeItem.item, false) ?: return null

        val initialVelocity = rotation.directionVector
            .multiply(trajectoryInfo.initialVelocity)
            .add(if (trajectoryInfo.copiesPlayerVelocity) player.velocity else Vec3d.ZERO)

        val arrow = SimulatedArrow(
            world,
            player.eyePos,
            initialVelocity,
            collideEntities = false
        )

        val entities = findAndBuildSimulatedEntities(rotation.directionVector)

        for (i in 0 until 40) {
            val lastPos = arrow.pos

            arrow.tick()

            entities.forEach { (entity, simulatedPos) ->
                val predictedPos = if (entity is AbstractClientPlayerEntity && simulatedPos != null) {
                    simulatedPos.getSnapshotAt(i).pos
                } else {
                    entity.pos.add(entity.velocity.scale(i.toDouble()))
                }

                val entityBox = entity.boundingBox
                    .expand(0.3)
                    .offset(predictedPos.subtract(entity.pos))

                if (entityBox.raycast(lastPos, arrow.pos).isPresent) {
                    return entity
                }
            }

            if (arrow.inGround) {
                return null
            }
        }

        return null
    }

    private fun findAndBuildSimulatedEntities(direction: Vec3d): List<Pair<Entity, SimulatedPlayerCache?>> {
        return world.entities.filter { entity ->
            entity != player &&
                entity.shouldBeAttacked() &&
                Line(player.eyePos, direction)
                    .squaredDistanceTo(entity.pos) < 10.0 * 10.0
        }.map { entity ->
            val simulation = if (entity is AbstractClientPlayerEntity) {
                PlayerSimulationCache.getSimulationForOtherPlayers(entity)
            } else {
                null
            }
            Pair(entity, simulation)
        }
    }

    private inline val ItemStack.isChargedCrossbow
        get() = CrossbowItem.isCharged(this)

    override fun onDisabled() {
        forceUncharged = false
        super.onDisabled()
    }

}
