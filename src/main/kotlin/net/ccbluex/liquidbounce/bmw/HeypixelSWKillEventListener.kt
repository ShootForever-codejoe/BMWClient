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

package net.ccbluex.liquidbounce.bmw

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.HeypixelSWKillEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket

object HeypixelSWKillEventListener : EventListener {

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet !is GameMessageS2CPacket) return@handler

        val message = packet.content.string

        val patterns = listOf(
            Regex("(.+?) 被 (.+?) 击败(.*)"),
            Regex("(.+?) 被炸成了粉尘, 最终还是被 (.+?) 击败(.*)"),
            Regex("(.+?) 消逝了, 最终还是被 (.+?) 击败(.+?)"),
            Regex("(.+?) 被架在了烧烤架上, 熟透了, 最终还是被 (.+?) 击败(.*)"),
            Regex("(.+?) 跑得很快, 但是他还是摔了一跤, 最终被 (.+?) 击败(.*)"),
            Regex("(.+?) 被 (.+?) 用弓箭射穿了(.*?)"),
            Regex("(.+?) 被重压地无法呼吸, 最终还是被 (.+?) 击败(.*)")
        )

        for (pattern in patterns) {
            val match = pattern.find(message) ?: continue
            val victim = match.groupValues[1].trim()
            val killer = match.groupValues[2].trim()
            EventManager.callEvent(HeypixelSWKillEvent(victim, killer))
            return@handler
        }
    }

}
