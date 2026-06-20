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

package net.ccbluex.liquidbounce.features.module.modules.combat.criticals.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket

object CriticalsGrim : Choice("Grim") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleCriticals.modes

    private var prevFallDistance = 0f
    private var isFalling = false
    private var freezeCloseTick = 0
    private var sprintRestoreTick = 0
    private var sprintShouldRestore = false
    private var timerCloseTick = 0
    private var hasTriggered = false

    override fun enable() {
        prevFallDistance = player.fallDistance
        isFalling = false
        freezeCloseTick = 0
        sprintRestoreTick = 0
        sprintShouldRestore = false
        timerCloseTick = 0
        hasTriggered = false
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val currentFallDistance = player.fallDistance

        if (currentFallDistance > prevFallDistance && currentFallDistance > 0) {
            isFalling = true
        }

        if (currentFallDistance <= 0) {
            isFalling = false
            hasTriggered = false
        }

        prevFallDistance = currentFallDistance

        if (freezeCloseTick > 0) {
            freezeCloseTick--
            if (freezeCloseTick == 0) {
                ModuleFreeze.interact()
            }
        }

        if (sprintRestoreTick > 0) {
            sprintRestoreTick--
            if (sprintRestoreTick == 0 && sprintShouldRestore) {
                sendSprintPacket(true)
                sprintShouldRestore = false
            }
        }

        if (timerCloseTick > 0) {
            Timer.requestTimerSpeed(0.8f, Priority.IMPORTANT_FOR_USAGE_2, ModuleCriticals)
            timerCloseTick--
            if (timerCloseTick == 0) {
                Timer.requestTimerSpeed(1f, Priority.IMPORTANT_FOR_USAGE_2, ModuleCriticals)
            }
        }
    }

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> {
        if (player.isSprinting && isFalling && !hasTriggered) {
            hasTriggered = true
            sendSprintPacket(false)
            sprintShouldRestore = true
            sprintRestoreTick = 1
            freezeCloseTick = 2
            timerCloseTick = 9
        }
    }

    @Suppress("unused")
    private val playerTickEventHandler = handler<PlayerTickEvent> { event ->
        if (freezeCloseTick > 0) {
            event.cancelEvent()
        }
    }

    private fun sendSprintPacket(start: Boolean) {
        val mode = if (start) {
            ClientCommandC2SPacket.Mode.START_SPRINTING
        } else {
            ClientCommandC2SPacket.Mode.STOP_SPRINTING
        }
        network.sendPacket(ClientCommandC2SPacket(player, mode))
    }

}
