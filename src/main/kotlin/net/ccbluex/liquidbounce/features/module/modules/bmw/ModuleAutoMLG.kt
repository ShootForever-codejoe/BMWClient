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

import net.ccbluex.liquidbounce.bmw.PlacementManager
import net.ccbluex.liquidbounce.bmw.getStandingBlock
import net.ccbluex.liquidbounce.bmw.getWaterBucketSlot
import net.ccbluex.liquidbounce.bmw.isOnGround
import net.ccbluex.liquidbounce.bmw.simulatePlayerMovement
import net.ccbluex.liquidbounce.bmw.topCenter
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura

object ModuleAutoMLG : ClientModule("AutoMLG", Category.BMW) {

    private val fallDistance by float("FallDistance", 3f, 0f..15f)
    private val notDuringKillAura by boolean("NotDuringKillAura", false)

    private fun predictLandingTicks(): Int {
        val result = simulatePlayerMovement(5) { position, velocity, tick ->
            isOnGround(position)
        }
        return if (result.stop) {
            result.tick
        } else {
            -1
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.fallDistance < fallDistance || player.velocity.y >= -0.08) return@tickHandler

        if ((!notDuringKillAura || !ModuleKillAura.running || ModuleKillAura.targetTracker.target == null)
            && PlacementManager.requester != ModuleAutoMLG
            && getWaterBucketSlot() != -1
            && predictLandingTicks() == 3
        ) {
            val position = simulatePlayerMovement(3).position
            val standingBlock = getStandingBlock(position)
            PlacementManager.place(
                ModuleAutoMLG,
                PlacementManager.PlaceWaterRequest(
                    standingBlock?.topCenter,
                    PlacementManager.PlaceWaterDebug.DEFAULT
                )
            )
        }
    }

}
