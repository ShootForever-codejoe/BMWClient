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

package net.ccbluex.liquidbounce.features.module.modules.bmw.helper

import net.ccbluex.liquidbounce.bmw.PlacementManager
import net.ccbluex.liquidbounce.bmw.getStandingBlock
import net.ccbluex.liquidbounce.bmw.getWaterBucketSlot
import net.ccbluex.liquidbounce.bmw.topCenter
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold

object HelperPutOutFire : ToggleableConfigurable(ModuleHelper, "PutOutFire", true) {

    private val interval by int("Interval", 10, 0..100, "ticks")
    private val maxTryCount by int("MaxTryCount", 5, 1..20)

    private var lastTryTime = 0L
    private var tryCount = 0
    private var paused: Boolean = false

    override fun onEnabled() {
        lastTryTime = 0
        tryCount = 0
        paused = false
    }

    fun handle() {
        if (!player.isOnFire) {
            if (paused) {
                paused = false
                tryCount = 0
                lastTryTime = 0
            }
            return
        }

        if (paused) {
            return
        }

        if (player.isOnGround
            && PlacementManager.requester != ModuleHelper
            && getWaterBucketSlot() != -1
            && !ModuleScaffold.running
        ) {
            if (System.currentTimeMillis() - lastTryTime < interval * 50L) {
                return
            }

            PlacementManager.place(
                ModuleHelper,
                PlacementManager.PlaceWaterRequest(
                    getStandingBlock()?.topCenter,
                    debug = PlacementManager.PlaceWaterDebug(
                        "No water bucket to put out fire",
                        "Failed to put out fire",
                        "Failed to recycle water"
                    )
                )
            )

            tryCount++
            lastTryTime = System.currentTimeMillis()
            if (tryCount >= maxTryCount) {
                paused = true
            }
        }
    }

}
