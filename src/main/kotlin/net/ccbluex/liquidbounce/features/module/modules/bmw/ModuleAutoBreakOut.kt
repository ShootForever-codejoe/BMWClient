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

package net.ccbluex.liquidbounce.features.module.modules.bmw

import com.google.common.collect.Queues
import net.ccbluex.liquidbounce.bmw.notifyAsMessageAndNotification
import net.ccbluex.liquidbounce.bmw.sendPacketNoEvent
import net.ccbluex.liquidbounce.event.events.ChatReceiveEvent
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.block.canStandOn
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Direction

object ModuleAutoBreakOut : ClientModule("AutoBreakOut", Category.BMW) {

    private var movementLock = false
    private var isGameStarting = false
    var blinking = false
    private var clear = false
    private val packets = Queues.newConcurrentLinkedQueue<Packet<*>>()

    private fun reset() {
        movementLock = false
        isGameStarting = false
        clear = true
    }

    override fun disable() {
        reset()
    }

    @Suppress("unused")
    private val chatReceiveEventHandler = sequenceHandler<ChatReceiveEvent> { event ->
        val message = event.message
        var playerName = player.name.string

        if (event.type != ChatReceiveEvent.ChatType.GAME_MESSAGE) {
            return@sequenceHandler
        }

        if (event.message.contains("$playerName 加入了游戏 (") && event.message.contains(")")) {
            waitTicks(10)
            notifyAsMessageAndNotification(ModuleAutoBreakOut, "你暂时无法移动，直到游戏开始前2秒")
            breakOut()
            movementLock = true
        }

        if (event.message.contains("游戏将在 2 秒 后开始") && movementLock) {
            notifyAsMessageAndNotification(ModuleAutoBreakOut, "现在你可以移动了")
            movementLock = false
            isGameStarting = true
        }
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (movementLock) {
            event.directionalInput = DirectionalInput.NONE
        }
    }

    @Suppress("unused")
    private val tickPacketProcessEventHandler = handler<TickPacketProcessEvent> {
        if (clear) {
            if (blinking) {
                packets.removeIf {
                    sendPacketNoEvent(ModuleAutoBreakOut, it)
                    true
                }
            }
            clear = false
            blinking = false
            packets.clear()
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (!blinking) return@handler

        val packet = event.packet

        when (packet) {
            is ChatMessageS2CPacket,
            is GameMessageS2CPacket,
            is ChatMessageC2SPacket,
            is CommandExecutionC2SPacket,
            is KeepAliveS2CPacket,
            is KeepAliveC2SPacket -> {
                return@handler
            }

            is DisconnectS2CPacket,
            is PlayerRespawnS2CPacket,
            is GameJoinS2CPacket -> {
                clear = true
                return@handler
            }

            is PlaySoundS2CPacket -> {
                if (packet.sound.value() == SoundEvents.ENTITY_PLAYER_HURT) {
                    return@handler
                }
            }

            is HealthUpdateS2CPacket -> {
                if (packet.health <= 0) {
                    reset()
                    return@handler
                }
            }
        }

        if (event.origin == TransferOrigin.OUTGOING) {
            event.cancelEvent()
            packets.add(packet)
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        waitUntil { isGameStarting }
        waitTicks(20)
        clear = false
        blinking = true
        isGameStarting = false
        waitTicks(20)
        reset()
    }

    @Suppress("unused")
    private val worldChangeEventHandler = handler<WorldChangeEvent> {
        reset()
    }

    @Suppress("unused")
    private val disconnectEventHandler = handler<DisconnectEvent> {
        reset()
    }

    private fun breakOut() {
        val startPos = player.blockPos
        for (i in 1..5) {
            val targetPos = startPos.offset(Direction.UP, i)
            val isLocationSafe = targetPos.down().canStandOn() &&
                targetPos.getState()?.isAir == true &&
                targetPos.up().getState()?.isAir == true
            if (isLocationSafe) {
                player.updatePosition(targetPos.x + 0.5, targetPos.y.toDouble(), targetPos.z + 0.5)
                return
            }
        }
    }

}
