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

package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import com.google.common.collect.Queues
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.GrimVelocityMode
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceEntity
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.boxedDistanceTo
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.render.WireframePlayer
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.TrackedPosition
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket
import net.minecraft.network.packet.s2c.play.EntityS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
import net.minecraft.util.Hand
import kotlin.math.sqrt

object GrimVelocityAttackReduce : GrimVelocityMode("AttackReduce") {

    private val attackCount by intRange("AttackCount", 3..3, 0..20)
    private val autoAttackCount by boolean("AutoAttackCount", true)
    private val alinkTargetRange by floatRange("AlinkTargetRange", 2.5f..6f, 0f..20f)
    private val alinkMaxDelay by int("AlinkMaxDelay", 20, 0..100, "ticks")
    private val alinkRequireKillAura by boolean("AlinkRequireKillAura", true)
    private val debug by boolean("Debug", false)

    private val canAlink: Boolean
        get() = !alinkRequireKillAura || ModuleKillAura.running

    private var target: Entity? = null
    private var renderTarget: Entity? = null
    private var renderTargetPos: TrackedPosition? = null
    private var attackQueue = 0
    private var receiveDamage = false
    private var alinkTicks = -1
    private var releaseReason: String? = null
    private var velocity = 0.0
    private val packets = Queues.newConcurrentLinkedQueue<Packet<*>>()

    override val shouldStopBacktrack: Boolean
        get() = alinkTicks >= 0 || attackQueue > 0

    override fun disable() {
        target = null
        renderTarget = null
        renderTargetPos = null
        attackQueue = 0
        receiveDamage = false
        alinkTicks = -1
        releaseReason = null
        velocity = 0.0
        packets.clear()
    }

    private fun findTarget() {
        if (!canAlink && alinkTicks >= 0) {
            target = renderTarget
            return
        }

        target = raytraceEntity(
            (if (canAlink) {
                alinkTargetRange.start.toDouble()
            } else {
                ModuleKillAura.range.toDouble()
            }),
            RotationManager.currentRotation ?: player.rotation
        ) { !it.isRemoved && it.shouldBeAttacked() }?.entity

        if (alinkTicks == -1) {
            renderTarget = target
        }

        if (target != null) return

        if (alinkTicks >= 0) return

        val farTarget = world.entities.filter { entity ->
            entity is LivingEntity
                && entity != player
                && !entity.isRemoved
                && entity.shouldBeAttacked()
                && entity.boxedDistanceTo(player) <= alinkTargetRange.endInclusive
        }.minByOrNull { entity -> entity.boxedDistanceTo(player) }

        renderTarget = farTarget
    }

    private fun handle() {
        packets.removeIf {
            handlePacket(it)
            true
        }
    }

    private fun getCurrentAttackCount(): Int {
        if (!autoAttackCount) return attackCount.random()

        if (velocity < 1000) {
            return 0
        } else if (velocity in 1000.0..<3000.0) {
            return 3
        } else if (velocity in 3000.0..<15000.0) {
            return 4
        } else if (velocity >= 15000) {
            return 5
        }

        return 0
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING) return@handler

        val packet = event.packet

        if (alinkTicks >= 0) {
            when (packet) {
                is ChatMessageS2CPacket,
                is GameMessageS2CPacket -> {
                    return@handler
                }

                is DisconnectS2CPacket,
                is PlayerRespawnS2CPacket,
                is GameJoinS2CPacket -> {
                    handle()
                    return@handler
                }

                is PlayerPositionLookS2CPacket -> {
                    releaseReason = "flag"
                    return@handler
                }

                is EntityS2CPacket -> {
                    if (renderTargetPos != null && packet.getEntity(world) == renderTarget) {
                        renderTargetPos!!.pos = renderTargetPos!!.withDelta(
                            packet.deltaX.toLong(),
                            packet.deltaY.toLong(),
                            packet.deltaZ.toLong()
                        )
                    }
                }

                is EntityPositionS2CPacket -> {
                    if (renderTargetPos != null && packet.entityId == renderTarget!!.id) {
                        renderTargetPos!!.pos = packet.change.position.copy()
                    }
                }

                is EntityPositionSyncS2CPacket -> {
                    if (renderTargetPos != null && packet.id == renderTarget!!.id) {
                        renderTargetPos!!.pos = packet.values.position()
                    }
                }
            }

            event.cancelEvent()
            packets.add(packet)
            return@handler
        }

        if (pause) return@handler

        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            receiveDamage = true
        }

        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id && receiveDamage) {
            receiveDamage = false
            if (player.isUsingItem || ModuleScaffold.running || ModuleFreeze.running) return@handler

            findTarget()
            if (renderTarget == null) return@handler

            velocity = sqrt((packet.velocityX.sq() + packet.velocityY.sq()).toDouble())

            val currentAttackCount = getCurrentAttackCount()
            if (currentAttackCount == 0) return@handler

            if ((target == null && canAlink) || (target != null && !player.isSprinting)) {
                if (debug) {
                    if (target != null) {
                        notifyAsMessage(ModuleGrimVelocity, "Alink... (not sprinting)")
                    } else {
                        notifyAsMessage(ModuleGrimVelocity, "Alink...")
                    }
                }
                if (target == null) {
                    renderTargetPos = TrackedPosition().apply { this.pos = renderTarget!!.pos }
                }
                alinkTicks = alinkMaxDelay
                event.cancelEvent()
                packets.add(packet)
            } else if (target != null) {
                attackQueue = currentAttackCount
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (attackQueue > 0) {
            if (target == null) {
                attackQueue = 0
                return@tickHandler
            }

            if (debug) notifyAsMessage(ModuleGrimVelocity, "Attack count: $attackQueue")

            repeat(attackQueue) {
                if (player.isSprinting) player.isSprinting = false
                network.sendPacket(PlayerInteractEntityC2SPacket.attack(target, false))
                player.swingHand(Hand.MAIN_HAND)
                player.setVelocity(
                    player.velocity.x * 0.6,
                    player.velocity.y,
                    player.velocity.z * 0.6
                )
            }
            attackQueue = 0
            target = null
        }
    }

    @Suppress("unused")
    private val tickPacketProcessEventHandler = handler<TickPacketProcessEvent> {
        if (releaseReason != null) {
            handle()
            alinkTicks = -1
            renderTarget = null
            renderTargetPos = null
            if (releaseReason!!.isEmpty()) {
                if (debug) notifyAsMessage(ModuleGrimVelocity, "Finish alink")
                attackQueue = getCurrentAttackCount()
            } else {
                if (debug) notifyAsMessage(ModuleGrimVelocity, "Finish alink ($releaseReason)")
            }
            releaseReason = null
        }
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (alinkTicks > 0 && releaseReason == null) {
            alinkTicks--
            findTarget()

            if (player.abilities.flying) {
                releaseReason = "spectator"
            } else if (target != null) {
                event.directionalInput = DirectionalInput(
                    forwards = true,
                    backwards = false,
                    left = false,
                    right = false
                )
                releaseReason = ""
            } else if (player.squaredDistanceTo(renderTargetPos!!.pos) > alinkTargetRange.endInclusive.sq()) {
                releaseReason = "out of range"
            } else if (alinkTicks == 0) {
                releaseReason = "max delay"
            }
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> {
        if (alinkTicks == -1 || renderTarget == null || renderTargetPos == null) return@handler

        WireframePlayer(
            renderTargetPos!!.pos,
            renderTarget!!.yaw,
            renderTarget!!.pitch
        ).render(
            it,
            Color4b(255, 255, 255, 87),
            Color4b(255, 255, 255, 255)
        )
    }

}
