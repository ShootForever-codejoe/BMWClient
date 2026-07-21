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

import net.ccbluex.liquidbounce.bmw.isOnGround
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.bmw.simulatePlayerMovement
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFood
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFoodNoC0F
import net.ccbluex.liquidbounce.features.module.modules.bmw.newscaffold.ModuleNewScaffold
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection
import net.ccbluex.liquidbounce.utils.block.canPlayerReachBlock
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext

object ModuleClutch : ClientModule("Clutch", Category.BMW) {

    private val stuckWhenRescue by boolean("StuckWhenRescue", true)
    private val maxRescueTime by float("MaxRescueTime", 0.5f, 0f..5f, "seconds")
    private val maxTryCount by int("MaxTryCount", 5, 1..10)
    private val simulationTicks by int("SimulationTicks", 100, 1..500, "ticks")
    private val notDuringCombat by boolean("NotDuringCombat", false)
    private val onlyFalling by boolean("OnlyFalling", false)
    private val debug by boolean("Debug", false)

    private const val REST_TICKS = 3

    private var isRescuing = false
    private var scaffold = false
    private var rescueTriesLast = 0
    private var rescueStartTime = 0L
    private var restTicks = 0
    private var hasGivenUp = false

    // 统一使用新的搭路模块
    private val rescueScaffold
        get() = ModuleNewScaffold

    override fun onDisabled() {
        reset()
        hasGivenUp = false
    }

    private fun reset(clear: Boolean = true) {
        if (scaffold && rescueScaffold.enabled) {
            rescueScaffold.enabled = false
        }
        isRescuing = false
        scaffold = false
        rescueStartTime = 0L
        restTicks = 0
        if (clear) {
            rescueTriesLast = 0
        }
    }

    private fun willLandInVoid(): Boolean {
        val result = simulatePlayerMovement(simulationTicks) { position, velocity, tick ->
            isOnGround(position)
        }
        return !result.stop
    }

    private fun isOverVoid(): Boolean {
        val top = player.pos.add(0.0, 0.1, 0.0)
        val bottom = Vec3d(top.x, world.bottomY - 1.0, top.z)
        val result = world.raycast(
            RaycastContext(
                top,
                bottom,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
            )
        )
        return result.type == HitResult.Type.MISS
    }

    private fun reachable(): Boolean {
        val searchRadius = player.blockInteractionRange.toInt() + 1

        for (dx in -searchRadius..searchRadius) {
            for (dy in -searchRadius..0) {
                if (player.eyeY.toInt() + dy + 1 > player.y) {
                    break
                }
                for (dz in -searchRadius..searchRadius) {
                    val blockPos = BlockPos(
                        player.x.toInt() + dx,
                        player.eyeY.toInt() + dy,
                        player.z.toInt() + dz
                    )
                    val blockState = world.getBlockState(blockPos)
                    if (!blockState.isAir && !blockState.isReplaceable && canPlayerReachBlock(blockPos)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun haveBlock(): Boolean {
        Slots.OffhandWithHotbar.forEach {
            if (ScaffoldBlockItemSelection.isValidBlock(it.itemStack)) {
                return true
            }
        }
        return false
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (scaffold && !rescueScaffold.enabled) {
            reset()
            rescueScaffold.enabled = true
            return@tickHandler
        }

        if (hasGivenUp) {
            if (player.isOnGround) hasGivenUp = false
            return@tickHandler
        }

        if (restTicks > 0) {
            restTicks--
            return@tickHandler
        }

        if (isRescuing) {
            if (!isOverVoid()) {
                if (debug) notifyAsMessage(ModuleClutch, "Rescued successfully")
                reset()
                return@tickHandler
            }
            val elapsedTime = System.currentTimeMillis() - rescueStartTime
            val timeoutMillis = (maxRescueTime * 1000).toLong()
            if (elapsedTime > timeoutMillis) {
                rescueTriesLast--
                if (rescueTriesLast <= 0) {
                    if (debug) notifyAsMessage(ModuleClutch, "Failed to rescue")
                    reset()
                    hasGivenUp = true
                    return@tickHandler
                } else {
                    reset(false)
                    restTicks = REST_TICKS
                }
            }
        } else {
            if (player.isOnGround
                || !(isOverVoid() && willLandInVoid())
            ) {
                if (rescueTriesLast > 0) {
                    if (debug) notifyAsMessage(ModuleClutch, "Rescued successfully")
                    reset()
                }
                return@tickHandler
            }

            val combatBlocked = notDuringCombat &&
                ModuleKillAura.running && ModuleKillAura.targetTracker.target != null
            val fallingBlocked = onlyFalling && player.velocity.y >= -0.08
            val noRescuePath = !reachable() || !haveBlock()
            val rescueBlocked = rescueScaffold.enabled || player.isInFluid || fallingBlocked || noRescuePath
            if (combatBlocked || rescueBlocked) {
                if (rescueTriesLast > 0) {
                    if (debug) notifyAsMessage(ModuleClutch, "Failed to rescue")
                    reset()
                }
                return@tickHandler
            }

            if (GrimNoSlowFoodNoC0F.working) {
                (GrimNoSlowFood.modes.activeChoice as GrimNoSlowFoodNoC0F).release()
            }

            isRescuing = true
            rescueStartTime = System.currentTimeMillis()
            if (rescueTriesLast == 0) {
                if (debug) notifyAsMessage(ModuleClutch, "Rescuing...")
                rescueTriesLast = maxTryCount
            }
            scaffold = true
            rescueScaffold.enabled = true
        }
    }

    // 只清空方向输入 不取消玩家更新
    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
        if (isRescuing && stuckWhenRescue) {
            event.directionalInput = DirectionalInput.NONE
        }
    }

}
