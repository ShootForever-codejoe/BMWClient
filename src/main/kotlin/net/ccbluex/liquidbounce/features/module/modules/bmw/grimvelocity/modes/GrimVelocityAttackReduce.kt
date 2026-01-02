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
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.GrimVelocityMode
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceEntity
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.boxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.render.WireframePlayer
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.TrackedPosition
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket
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

object GrimVelocityAttackReduce : GrimVelocityMode("AttackReduce") {

    private val attackCount by intRange("AttackCount", 3..3, 0..20)

    private val alink = tree(object : ToggleableConfigurable(this, "Alink", true) {
        val targetRange by floatRange("TargetRange", 2.5f..5f, 0f..10f)
        val maxDelay by int("MaxDelay", 20, 0..100, "ticks")
        val requireKillAura by boolean("RequireKillAura", true)

        val canWork: Boolean
            get() = enabled && (!requireKillAura || ModuleKillAura.running)
    })

    private val debug by boolean("Debug", false)

    private var target: Entity? = null
    private var displayTarget: Entity? = null
    private var displayTargetPos: TrackedPosition? = null
    private var attackQueue = 0
    private var receiveDamage = false
    private var delayTicks = -1
    private val packets = Queues.newConcurrentLinkedQueue<Packet<*>>()
    private var failReason: String? = null

    override val shouldStopBacktrack: Boolean
        get() = delayTicks >= 0 || attackQueue > 0

    override fun disable() {
        target = null
        displayTarget = null
        displayTargetPos = null
        attackQueue = 0
        receiveDamage = false
        delayTicks = -1
        packets.clear()
        failReason = null
    }

    private fun findTarget(): Boolean { // 返回周围是否有玩家（即可不可以alink），而不是能否打到玩家，如果不能打到则target为null
        displayTarget = null
        displayTargetPos = null

        if (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null) {
            if (!alink.canWork || ModuleKillAura.targetTracker.target!!.boxedDistanceTo(player) <= alink.targetRange.start) {
                target = ModuleKillAura.targetTracker.target
            } else {
                displayTarget = ModuleKillAura.targetTracker.target
            }
            return true
        }

        target = raytraceEntity(
            (if (alink.canWork) {
                alink.targetRange.start.toDouble()
            } else {
                ModuleKillAura.range.toDouble()
            }),
            RotationManager.serverRotation
        ) { !it.isRemoved && it.shouldBeAttacked() }?.entity

        if (target != null) return true

        if (!alink.canWork) return false

        val farTarget = world.entities.filter { entity ->
            entity is LivingEntity
                && entity != player
                && !entity.isRemoved
                && entity.shouldBeAttacked()
                && entity.boxedDistanceTo(player) <= alink.targetRange.endInclusive
        }.minByOrNull { entity -> entity.boxedDistanceTo(player) }

        displayTarget = farTarget ?: return false
        return true
    }

    private fun handle() {
        packets.removeIf {
            handlePacket(it)
            true
        }
        delayTicks = -1
        displayTarget = null
        displayTargetPos = null
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING) return@handler

        val packet = event.packet

        if (delayTicks >= 0) {
            when (packet) {
                is ChatMessageS2CPacket,
                is GameMessageS2CPacket,
                is KeepAliveS2CPacket -> {
                    return@handler
                }

                is DisconnectS2CPacket,
                is PlayerRespawnS2CPacket,
                is GameJoinS2CPacket -> {
                    handle()
                    return@handler
                }

                is PlayerPositionLookS2CPacket -> {
                    failReason = "flag"
                    delayTicks = 0
                    return@handler
                }

                is EntityS2CPacket -> {
                    if (packet.getEntity(world) == displayTarget) {
                        displayTargetPos!!.pos = displayTargetPos!!.withDelta(
                            packet.deltaX.toLong(),
                            packet.deltaY.toLong(),
                            packet.deltaZ.toLong()
                        )
                    }
                }

                is EntityPositionS2CPacket -> {
                    if (packet.entityId == displayTarget!!.id) {
                        displayTargetPos!!.pos = packet.change.position.copy()
                    }
                }

                is EntityPositionSyncS2CPacket -> {
                    if (packet.id == displayTarget!!.id) {
                        displayTargetPos!!.pos = packet.values.position()
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
            if (player.isUsingItem) return@handler
            if (!findTarget()) return@handler
            if (alink.canWork && target == null) {
                if (debug) notifyAsMessage(ModuleGrimVelocity, "Alink...")
                displayTargetPos = TrackedPosition().apply { this.pos = displayTarget!!.pos }
                delayTicks = alink.maxDelay
                event.cancelEvent()
                packets.add(packet)
                return@handler
            }
            attackQueue = attackCount.random()
        }
    }

    @Suppress("unused")
    private val tickPacketProcessEventHandler = handler<TickPacketProcessEvent> {
        if (delayTicks == 0) {
            handle()
            if (failReason != null) {
                if (debug) notifyAsMessage(ModuleGrimVelocity, "Finish alink ($failReason)")
            } else {
                if (debug) notifyAsMessage(ModuleGrimVelocity, "Finish alink")
                attackQueue = attackCount.random()
            }
            failReason = null
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (delayTicks > 0) {
            failReason = "max delay"

            if (player.abilities.flying) {
                failReason = "spectator"
                delayTicks = 0
                return@tickHandler
            }

            if (!findTarget()) {
                failReason = "not in range"
                delayTicks = 0
                return@tickHandler
            }

            if (target != null) {
                failReason = null
                delayTicks = 0
            } else {
                delayTicks--
            }

            return@tickHandler
        }

        if (attackQueue > 0 && delayTicks == -1) {
            if (target == null) {
                attackQueue = 0
                return@tickHandler
            }

            while (attackQueue > 0) {
                network.sendPacket(PlayerInteractEntityC2SPacket.attack(target, false))
                player.swingHand(Hand.MAIN_HAND)
                player.setVelocity(
                    player.velocity.x * 0.6,
                    player.velocity.y,
                    player.velocity.z * 0.6
                )
                player.isSprinting = false
                attackQueue--
            }

            target = null
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> {
        if (delayTicks == -1 || displayTarget == null || displayTargetPos == null) return@handler

        val wireframePlayer = WireframePlayer(
            displayTargetPos!!.pos,
            displayTarget!!.yaw,
            displayTarget!!.pitch
        )
        wireframePlayer.render(
            it,
            Color4b(255, 255, 255, 87),
            Color4b(255, 255, 255, 255)
        )
    }

}
