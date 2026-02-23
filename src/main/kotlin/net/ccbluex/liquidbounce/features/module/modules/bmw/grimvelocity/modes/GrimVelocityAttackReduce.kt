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

@file:Suppress("DEPRECATION")

package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import com.google.common.collect.Queues
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.AlinkUpdateEvent
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
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.AccelerationAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.InterpolationAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.MinaraiAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.SigmoidAngleSmooth
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
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
import net.minecraft.util.hit.EntityHitResult
import kotlin.math.sqrt

object GrimVelocityAttackReduce : GrimVelocityMode("AttackReduce") {

    private val attackCount by intRange("AttackCount", 4..4, 0..20)
    private val autoAttackCount by boolean("AutoAttackCount", true)
    private val alinkTargetRange by float("AlinkTargetRange", 10f, 0f..20f)
    private val alinkMaxDelay by int("AlinkMaxDelay", 60, 0..200, "ticks")

    private object AutoRotate : ToggleableConfigurable(this, "AutoRotate", true) {
        val rotationTime by int("RotationTime", 3, 0..20, "ticks")
        val angleSmooth = choices(GrimVelocityAttackReduce, "AngleSmooth", 0) {
            val linearAngleSmooth = LinearAngleSmooth(it)
            val interpolationAngleSmooth = InterpolationAngleSmooth(it)

            listOfNotNull(
                linearAngleSmooth,
                SigmoidAngleSmooth(it),
                interpolationAngleSmooth,
                AccelerationAngleSmooth(it),
                MinaraiAngleSmooth(it, interpolationAngleSmooth)
            ).toTypedArray()
        }
        val notDuringKillAura by boolean("NotDuringKillAura", true)
        val canRotate: Boolean
            get() = enabled
                && (!notDuringKillAura
                || !ModuleKillAura.running
                || ModuleKillAura.targetTracker.target == null)
    }

    private val requireKillAura by boolean("RequireKillAura", false)
    private val debug by boolean("Debug", false)

    init {
        tree(AutoRotate)
    }

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
        EventManager.callEvent(AlinkUpdateEvent(0, alinkMaxDelay, false))
    }

    private fun findTarget() {
        target = (mc.crosshairTarget as? EntityHitResult)?.entity
            ?.takeIf { !it.isRemoved && it.shouldBeAttacked() }

        if (alinkTicks == -1) renderTarget = target

        if (target != null) return

        var targetAround = world.entities.filter { entity ->
            entity is LivingEntity
                && entity != player
                && !entity.isRemoved
                && entity.shouldBeAttacked()
                && entity.distanceTo(player) <= alinkTargetRange
                && entity.id != renderTarget?.id
        }.minByOrNull { entity -> entity.distanceTo(player) }

        var targetPos = targetAround?.pos

        if (renderTarget != null
            && renderTargetPos != null
            && (targetPos == null
                || renderTargetPos!!.pos.distanceTo(player.pos)
                <= targetPos.distanceTo(player.pos))
        ) {
            targetPos = renderTargetPos!!.pos
            targetAround = renderTarget
        }

        if (targetAround == null || targetPos == null) return

        if (targetPos.distanceTo(player.pos) <= 3.0 && AutoRotate.canRotate) {
            RotationManager.setRotationTarget(
                plan = RotationTarget(
                    rotation = Rotation.lookingAt(targetPos.add(0.0, (0.5..1.0).random(), 0.0), player.eyePos),
                    processors = listOf(AutoRotate.angleSmooth.activeChoice),
                    ticksUntilReset = AutoRotate.rotationTime,
                    resetThreshold = 2f,
                    considerInventory = false,
                    movementCorrection = MovementCorrection.STRICT
                ),
                priority = Priority.IMPORTANT_FOR_PLAYER_LIFE,
                provider = ModuleGrimVelocity
            )
            if (alinkTicks >= 0) target = targetAround
        }

        if (alinkTicks == -1) renderTarget = targetAround
    }

    private fun handle() {
        packets.removeIf {
            handlePacket(it)
            true
        }
    }

    private fun getCurrentAttackCount(): Int {
        if (!autoAttackCount) return attackCount.random()

        if (velocity < 1000.0) {
            return 0
        } else if (velocity in 1000.0..<3000.0) {
            return 3
        } else if (velocity in 3000.0..<10000.0) {
            return 4
        } else if (velocity >= 10000.0) {
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

                is EntityS2CPacket if (renderTargetPos != null && packet.getEntity(world) == renderTarget) -> {
                    renderTargetPos!!.pos = renderTargetPos!!.withDelta(
                        packet.deltaX.toLong(),
                        packet.deltaY.toLong(),
                        packet.deltaZ.toLong()
                    )
                }

                is EntityPositionS2CPacket if (renderTargetPos != null && packet.entityId == renderTarget?.id) -> {
                    renderTargetPos!!.pos = packet.change.position.copy()
                }

                is EntityPositionSyncS2CPacket if (renderTargetPos != null && packet.id == renderTarget?.id) -> {
                    renderTargetPos!!.pos = packet.values.position()
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
            if (player.isUsingItem
                || ModuleFreeze.running
                || !(!requireKillAura || ModuleKillAura.running)
            ) return@handler

            findTarget()
            if (renderTarget == null) return@handler

            velocity = sqrt((packet.velocityX.sq() + packet.velocityY.sq()).toDouble())

            val currentAttackCount = getCurrentAttackCount()
            if (currentAttackCount == 0) return@handler

            if (target == null || !player.isSprinting) {
                if (debug) {
                    if (!player.isSprinting) {
                        notifyAsMessage(ModuleGrimVelocity, "Alink... (not sprinting)")
                    } else {
                        notifyAsMessage(ModuleGrimVelocity, "Alink...")
                    }
                }
                renderTargetPos = TrackedPosition().apply { pos = renderTarget!!.pos }
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
        EventManager.callEvent(AlinkUpdateEvent(
            if (alinkTicks == -1) {
                0
            } else {
                alinkMaxDelay - alinkTicks
            },
            alinkMaxDelay,
            true
        ))

        if (attackQueue > 0) {
            if (target == null) {
                attackQueue = 0
                return@tickHandler
            }

            if (debug) notifyAsMessage(ModuleGrimVelocity, "Attack count: $attackQueue")

            for (i in 1..attackQueue) {
                if (target !in world.entities) break

                player.isSprinting = false
                sendPacketSilently(
                    PlayerInteractEntityC2SPacket.attack(
                        target,
                        player.isSneaking
                    )
                )
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
            if (releaseReason!!.isEmpty() && !player.isSprinting) {
                releaseReason = null
                return@handler
            }

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
        if (alinkTicks >= 0 && releaseReason == null) {
            if (alinkTicks > 0) alinkTicks--
            findTarget()

            if (alinkTicks == 0) {
                releaseReason = "max delay"
            } else if (player.abilities.flying) {
                releaseReason = "spectator"
            } else if (player.pos.distanceTo(renderTargetPos!!.pos) > alinkTargetRange) {
                releaseReason = "out of range"
            } else if (target != null) {
                event.directionalInput = DirectionalInput(
                    forwards = true,
                    backwards = false,
                    left = false,
                    right = false
                )
                releaseReason = ""
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
            Color4b(255, 255, 255, 100),
            Color4b(255, 255, 255, 255)
        )
    }

}
