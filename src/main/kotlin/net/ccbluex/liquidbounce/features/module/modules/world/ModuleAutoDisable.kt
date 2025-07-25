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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.bmw.HEYPIXEL_END_MESSAGE
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.ChatReceiveEvent
import net.ccbluex.liquidbounce.event.events.DeathEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.command.commands.module.CommandAutoDisable
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.ModuleDelayBlink
import net.ccbluex.liquidbounce.features.module.modules.bmw.ModuleStuck
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleNoClip
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.client.notification
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import java.util.EnumSet

/**
 * AutoDisable module
 *
 * Automatically disables modules, when special event happens.
 *
 * Command: [CommandAutoDisable]
 */
object ModuleAutoDisable : ClientModule("AutoDisable", Category.WORLD) {

    val listOfModules = arrayListOf(ModuleFly, ModuleSpeed, ModuleNoClip, ModuleKillAura)
    private val disableOn by multiEnumChoice<DisableOn>(
        "On",
        EnumSet.of(
            DisableOn.SPECTATOR,
            DisableOn.CHANGE_WORLD,
            DisableOn.HEYPIXEL_END_MESSAGE
        )
    )

    @Suppress("unused")
    val flagHandler = handler<PacketEvent> {
        if (it.packet is PlayerPositionLookS2CPacket && DisableOn.FLAG in disableOn) {
            disableAndNotify("flag")
        }
    }

    @Suppress("unused")
    val deathHandler = handler<DeathEvent> {
        if (DisableOn.DEATH in disableOn) disableAndNotify("your death")
    }

    @Suppress("unused")
    private val worldChangeEventHandler = handler<WorldChangeEvent> {
        if (DisableOn.CHANGE_WORLD in disableOn) disableModules()
    }

    @Suppress("unused")
    private val chatReceiveEventHandler = handler<ChatReceiveEvent> { event ->
        if (DisableOn.HEYPIXEL_END_MESSAGE in disableOn) {
            val message = event.message

            if (event.type != ChatReceiveEvent.ChatType.GAME_MESSAGE) {
                return@handler
            }

            if (event.message.contains(HEYPIXEL_END_MESSAGE)) {
                disableModules()
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (DisableOn.SPECTATOR in disableOn) {
            waitUntil { player.isSpectator || player.abilities.flying }
            disableModules()
            waitUntil { !player.isSpectator && !player.abilities.flying }
        }
    }

    private fun disableAndNotify(reason: String) {
        val modules = listOfModules.filter {
            module -> module.running
        }

        if (modules.isNotEmpty()) {
            for (module in modules) {
                module.enabled = false
            }
            notification("Notifier", "Disabled modules due to $reason", NotificationEvent.Severity.INFO)
        }
    }

    private fun disableModules() {
        val modules = arrayOf(
            ModuleKillAura,
            ModuleScaffold,
            ModuleDelayBlink,
            ModuleBlink,
            ModuleStuck
        )

        for (module in modules) {
            module.enabled = false
        }
    }

    private enum class DisableOn(override val choiceName: String) : NamedChoice {
        FLAG("Flag"),
        DEATH("Death"),
        SPECTATOR("Spectator"),
        CHANGE_WORLD("ChangeWorld"),
        HEYPIXEL_END_MESSAGE("HeypixelEndMessage")
    }
}
