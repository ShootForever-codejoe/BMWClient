/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
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

package net.ccbluex.liquidbounce.features.module.modules.movement.noslow.modes.shared

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PlayerUseMultiplier
import net.ccbluex.liquidbounce.event.handler

internal class NoSlowSharedHeypixel(override val parent: ChoiceConfigurable<*>) : Choice("Heypixel") {

    private var wasSlowdownActive = false

    companion object {
        @JvmStatic
        var shouldNoSlow = false
            private set
    }

    @Suppress("unused")
    private val slowdownEventHandler = handler<PlayerUseMultiplier> {
        val shouldActivate = player.itemUseTime % 3 != 0

        if (shouldActivate) {
            if (!wasSlowdownActive) {
                wasSlowdownActive = true
                shouldNoSlow = false
                return@handler
            }

            shouldNoSlow = true
            player.isSprinting = true
        } else {
            wasSlowdownActive = false
            shouldNoSlow = false
        }
    }

}
