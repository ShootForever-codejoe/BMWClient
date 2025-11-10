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

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.utils.item.isConsumable

internal class NoSlowSharedHeypixel(override val parent: ChoiceConfigurable<*>) : Choice("Heypixel") {
    private enum class Mode(override val choiceName: String) : NamedChoice {
        GRIM_JUMP("GrimJump"),
        GRIM_33("Grim33%")
    }

    private val mode by enumChoice("Mode", Mode.GRIM_33)

    private var onGroundTick = 0

    companion object {
        @JvmStatic
        var shouldNoSlow = false
            private set
    }

    @Suppress("unused")
    private val playerNetworkMovementTickEventHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (!player.isUsingItem || event.state != EventState.PRE) return@handler
        if (player.activeItem.isConsumable && player.itemUseTime > 30) return@handler

        when (mode) {
            Mode.GRIM_JUMP -> {
                if (onGroundTick == 1 && player.itemUseTime <= 30) {
                    shouldNoSlow = true
                    if (!player.isSprinting) player.isSprinting = true
                } else {
                    shouldNoSlow = false
                }
            }

            Mode.GRIM_33 -> {
                if (player.itemUseTime % 3 == 0) {
                    shouldNoSlow = true
                    if (!player.isSprinting) player.isSprinting = true
                } else {
                    shouldNoSlow = false
                }
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.isOnGround) {
            onGroundTick++
        } else {
            onGroundTick = 0
        }
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (player.isOnGround
            && player.isUsingItem
            && event.directionalInput.isMoving
            && mode == Mode.GRIM_JUMP
        ) {
            event.jump = true
        }
    }

    override fun enable() {
        onGroundTick = 0
    }

    override fun disable() {
        onGroundTick = 0
    }
}
