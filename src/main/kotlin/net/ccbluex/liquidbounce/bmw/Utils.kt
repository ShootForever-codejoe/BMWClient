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
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.minecraft.network.packet.Packet

fun sendPacketNoEvent(parent: EventListener, packet: Packet<*>) {
    sendPacketSilently(packet)
    val event = PacketEvent(TransferOrigin.OUTGOING, packet)
    EventManager.callEventExcept(event, arrayOf(parent))
}

fun normalizeYaw(yaw: Float): Float {
    var yaw = yaw
    while (yaw > 180.0f) {
        yaw -= 360.0f
    }

    while (yaw < -180.0f) {
        yaw += 360.0f
    }

    return yaw
}

fun clampPitchTo90(pitch: Float): Float {
    return if (pitch > 90.0f) {
        90.0f
    } else {
        if (pitch < -90.0f) -90.0f else pitch
    }
}
