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

import net.ccbluex.liquidbounce.bmw.notifyAsMessageAndNotification
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket

object ModuleStaffCheck : ClientModule("StaffCheck", Category.BMW) {

    private val autoExit by boolean("AutoExit", false)
    private val emptyThreshold by int("EmptyThreshold", 3, 0..10)
    private val exitDelay by int("ExitDelay", 5, 0..60, "ticks")

    private var pauseTicks = 0

    override fun onEnabled() {
        pauseTicks = 0
    }

    @Suppress("unused")
    private val packetEventHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        if (packet !is GameMessageS2CPacket) return@sequenceHandler

        var content = packet.content.string

        if (content.startsWith("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬地图评分▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬")) {
            pauseTicks = 40
        }

        if (content.startsWith("⚠ 该模式禁止跨队联合!")) {
            pauseTicks = 300
        }

        if (content.startsWith("战斗 现在开始!")) {
            pauseTicks = 40
        }

        if (pauseTicks > 0) return@sequenceHandler

        val visible = getVisibleContent(content)
        if (visible.length <= emptyThreshold) {
            notifyAsMessageAndNotification(ModuleStaffCheck, "Staff detected!")

            if (autoExit) {
                waitTicks(exitDelay)
                network.sendCommand("hub")
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (pauseTicks > 0) {
            pauseTicks--
        }
    }

    private fun getVisibleContent(content: String?): String {
        if (content == null) return ""

        var result = content.replace("§[0-9a-fk-or]".toRegex(), "")
        result = result.replace("[\\u200B\\u200C\\u200D\\uFEFF\\u00A0]".toRegex(), "")
        result = result.replace("[\\x00-\\x1F\\x7F]".toRegex(), "")

        return result.trim { it <= ' ' }
    }

}
