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

package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket

internal object NoFallHeypixel : NoFallMode("Heypixel") {

    private val fallDistance by float("FallDistance", 3f, 3f..15f)

    @Suppress("unused")
    private val playerNetworkMovementTickEventHandler = handler<PlayerNetworkMovementTickEvent> { event ->
        if (event.state != EventState.PRE) {
            return@handler
        }

        if (player.fallDistance >= fallDistance && event.ground) {
            event.ground = false
            mc.send {
                sendPacketSilently(
                    PlayerMoveC2SPacket.OnGroundOnly(
                        true,
                        player.horizontalCollision
                    )
                )
            }
        }
    }

}
