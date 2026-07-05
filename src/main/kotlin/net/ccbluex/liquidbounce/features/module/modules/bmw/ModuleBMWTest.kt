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

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.bmw.simulatePlayerMovement
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.render.WireframePlayer
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket

object ModuleBMWTest : ClientModule("BMWTest", Category.BMW) {

    private val simulate by boolean("Simulate", false)
    private val simulationTime by int("SimulationTime", 20, 1..500, "ticks")
    private val interactPacket by boolean("InteractPacket", false)
    private val sprintPacket by boolean("SprintPacket", false)

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> {
        if (!simulate) {
            return@handler
        }

        val result = simulatePlayerMovement(simulationTime)

        val wireframePlayer = WireframePlayer(result.position, player.yaw, player.pitch)
        wireframePlayer.render(
            it,
            Color4b(255, 255, 255, 100),
            Color4b(255, 255, 255, 255)
        )
        notifyAsMessage("x: ${result.position.x}, y: ${result.position.y}, z: ${result.position.z}")
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (interactPacket && packet is PlayerInteractItemC2SPacket) {
            notifyAsMessage("interact item")
        }

        if (interactPacket && packet is PlayerInteractBlockC2SPacket) {
            notifyAsMessage("interact block")
        }

        if (sprintPacket && packet is ClientCommandC2SPacket) {
            if (packet.mode == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                notifyAsMessage("start sprint")
            } else if (packet.mode == ClientCommandC2SPacket.Mode.STOP_SPRINTING) {
                notifyAsMessage("stop sprint")
            }
        }
    }

}
