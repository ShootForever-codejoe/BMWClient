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

package net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.share

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PlayerUseMultiplier
import net.ccbluex.liquidbounce.event.handler
import net.minecraft.item.consume.UseAction

internal class GrimNoSlowShareHalf(
    override val parent: ChoiceConfigurable<*>,
    val useActions: Array<UseAction>
) : Choice("Half") {

    private var wasSlowdownActive = false
    private var pausePhase = false
    private var pauseTimer = 0

    override fun enable() {
        wasSlowdownActive = false
        pausePhase = false
        pauseTimer = 0
    }

    @Suppress("unused")
    private val playerUseMultiplierHandler = handler<PlayerUseMultiplier> { event ->
        if (player.activeItem.useAction !in useActions) return@handler

        val shouldActivate = player.itemUseTime % 3 != 0
        if (shouldActivate) {
            if (!wasSlowdownActive) {
                pausePhase = true
                pauseTimer = 0
                wasSlowdownActive = true
                return@handler
            }

            if (pausePhase) {
                pauseTimer--
                if (pauseTimer <= 0) {
                    pausePhase = false
                    event.forward = 1f
                    event.sideways = 1f
                    player.isSprinting = true
                }
                return@handler
            }

            event.forward = 1f
            event.sideways = 1f
            player.isSprinting = true
        } else {
            wasSlowdownActive = false
            pausePhase = false
            pauseTimer = 0
        }
    }

}
