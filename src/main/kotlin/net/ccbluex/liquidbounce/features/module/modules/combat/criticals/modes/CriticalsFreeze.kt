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
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.ccbluex.liquidbounce.utils.entity.boxedDistanceTo
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import kotlin.math.abs
import kotlin.random.Random

object CriticalsFreeze : Choice("Freeze") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleCriticals.modes

    private val targetRange by float("TargetRange", 2f, 0f..4f)
    private val freezeTime by int("FreezeTime", 2, 0..20, "ticks")
    private val pauseTime by int("PauseTime", 2, 0..20, "ticks")

    private var freezeTicks = 0
    private var pauseTicks = 0

    override fun enable() {
        freezeTicks = 0
        pauseTicks = 0
    }

    @Suppress("unused")
    private val playerTickEventHandler = handler<PlayerTickEvent> { event ->
        if (pauseTicks > 0) {
            pauseTicks--
            return@handler
        }

        if (freezeTicks > 0) {
            freezeTicks--
            event.cancelEvent()
            if (freezeTicks == 0) {
                pauseTicks = pauseTime
            }
        }
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
        if (freezeTicks > 0) {
            event.directionalInput = DirectionalInput.NONE
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val target = ModuleKillAura.targetTracker.target

        if (!ModuleKillAura.running
            || target == null
            || freezeTicks > 0
            || pauseTicks > 0
            || player.velocity.y >= -0.1
            || player.boxedDistanceTo(target) > targetRange
        ) return@tickHandler

        freezeTicks = freezeTime
    }

    private val yawOffset = FloatOffsetGenerator()
    private val pitchOffset = FloatOffsetGenerator()

    private class FloatOffsetGenerator : FloatIterator() {
        private var prev = 0f
        override fun hasNext() = true
        override fun nextFloat(): Float {
            var offset: Float
            do {
                offset = Random.nextDouble(0.002, 0.01).toFloat()
            } while (abs(offset - prev) < 1.0E-6F)
            return offset.also { prev = it }
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (freezeTicks == 0) return@handler

        val packet = event.packet
        val yaw = RotationManager.currentRotation?.yaw ?: player.yaw
        val pitch = RotationManager.currentRotation?.pitch ?: player.pitch
        val yawOffset = yawOffset.nextFloat()
        val pitchOffset = pitchOffset.nextFloat()

        when (packet) {
            is PlayerInteractItemC2SPacket -> {
                event.cancelEvent()
                sendPacketSilently(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        yaw + yawOffset,
                        pitch + pitchOffset,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
                sendPacketSilently(
                    PlayerInteractItemC2SPacket(
                        packet.hand,
                        packet.sequence,
                        yaw + yawOffset,
                        pitch + pitchOffset,
                    )
                )
            }

            is PlayerInteractEntityC2SPacket -> {
                event.cancelEvent()
                sendPacketSilently(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        yaw + yawOffset,
                        pitch + pitchOffset,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
                sendPacketSilently(packet)
            }

            is PlayerInteractBlockC2SPacket -> {
                event.cancelEvent()
                sendPacketSilently(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        yaw + yawOffset,
                        pitch + pitchOffset,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
                sendPacketSilently(packet)
            }
        }
    }

}
