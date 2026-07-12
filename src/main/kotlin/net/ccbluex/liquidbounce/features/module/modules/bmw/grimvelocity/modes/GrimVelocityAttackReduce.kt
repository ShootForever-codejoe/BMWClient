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
import net.ccbluex.liquidbounce.bmw.PlacementManager
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.*
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFoodNoC0F
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.GrimVelocityMode
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.*
import net.ccbluex.liquidbounce.utils.aiming.utils.projectPointsOnBox
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.boxedDistanceTo
import net.ccbluex.liquidbounce.utils.entity.getBoundingBoxAt
import net.ccbluex.liquidbounce.utils.entity.getNearestPoint
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.render.WireframePlayer
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.TrackedPosition
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.play.*
import net.minecraft.util.Hand
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.math.sqrt

object GrimVelocityAttackReduce : GrimVelocityMode("AttackReduce") {

    private val attackCount by intRange("AttackCount", 5..5, 0..20)
    private val autoAttackCount by boolean("AutoAttackCount", true)

    private enum class AttackMode(override val choiceName: String) : NamedChoice {
        ONE_TIME("OneTime"),
        PER_TICK("PerTick")
    }

    private val attackMode by enumChoice("AttackMode", AttackMode.PER_TICK)
    private val attackTargetRange by floatRange("AttackTargetRange", 2f..3f, 0f..6f)
    private val alinkInAir by boolean("AlinkInAir", true)
    private val alinkTargetRange by float("AlinkTargetRange", 6f, 0f..20f)
    private val alinkMaxDelay by int("AlinkMaxDelay", 30, 0..200, "ticks")

    private val autoRotate = tree(object : ToggleableConfigurable(
        this, "AutoRotate", true
    ) {
        val rotationTime by int("RotationTime", 2, 0..20, "ticks")
        val angleSmooth = choices(GrimVelocityAttackReduce, "AngleSmooth", 0) {
            val linearAngleSmooth = LinearAngleSmooth(it)
            val interpolationAngleSmooth = InterpolationAngleSmooth(it)

            @Suppress("DEPRECATION")
            listOfNotNull(
                linearAngleSmooth,
                SigmoidAngleSmooth(it),
                interpolationAngleSmooth,
                AccelerationAngleSmooth(it),
                MinaraiAngleSmooth(it, interpolationAngleSmooth)
            ).toTypedArray()
        }
        val notDuringKillAura by boolean("NotDuringKillAura", false)

        val canRotate: Boolean
            get() = enabled
                && (!notDuringKillAura
                || !ModuleKillAura.running
                || ModuleKillAura.targetTracker.target == null)
    })

    private val requireKillAura by boolean("RequireKillAura", true)
    private val debug by boolean("Debug", false)

    private val renderTargetMode = choices(
        "RenderTargetMode", Wireframe, arrayOf(
            Box, Model, Wireframe, None
        )
    )

    private var target: Entity? = null
    private var renderTarget: Entity? = null
    private var renderTargetPos: TrackedPosition? = null
    var attackQueue = 0
        private set
    private var receiveDamage = false
    var alinkTicks = -1
        private set
    private var releaseReason: String? = null
    private var velocity = 0.0
    private val packets = Queues.newConcurrentLinkedQueue<Packet<*>>()

    override val shouldStopBacktrack: Boolean
        get() = alinkTicks >= 0 || attackQueue > 0

    private val isInAir: Boolean
        get() = !player.isOnGround && !player.isInFluid && !player.isInsideWall

    override fun disable() {
        reset()
        EventManager.callEvent(AlinkUpdateEvent(0, alinkMaxDelay, false))
    }

    private fun reset() {
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

    private fun rotate(target: Entity, targetPos: Vec3d) {
        val box = target.getBoundingBoxAt(targetPos)
        val points = mutableListOf<Vec3d>().apply {
            projectPointsOnBox(player.eyePos, box) { point ->
                add(point)
            }
        }
        val bestPoint = points.minByOrNull { it.squaredDistanceTo(player.eyePos) }
            ?: getNearestPoint(player.eyePos, box)
        val rotation = Rotation.lookingAt(bestPoint, player.eyePos)

        RotationManager.setRotationTarget(
            plan = RotationTarget(
                rotation = rotation,
                processors = listOf(autoRotate.angleSmooth.activeChoice),
                ticksUntilReset = autoRotate.rotationTime,
                resetThreshold = 2f,
                considerInventory = false,
                movementCorrection = MovementCorrection.STRICT
            ),
            priority = Priority.IMPORTANT_FOR_USER_SAFETY,
            provider = ModuleGrimVelocity
        )
    }

    private fun findTarget() {
        target = (mc.crosshairTarget as? EntityHitResult)?.entity
            ?.takeIf {
                !it.isRemoved
                    && it.shouldBeAttacked()
                    && it.boxedDistanceTo(player) <= attackTargetRange.start
            }

        if (alinkTicks == -1) renderTarget = target

        if (target != null) return

        var targetAround = world.entities.filter { entity ->
            entity is LivingEntity
                && entity != player
                && !entity.isRemoved
                && entity.shouldBeAttacked()
                && entity.boxedDistanceTo(player) <= alinkTargetRange
                && entity.id != renderTarget?.id
        }.minByOrNull { entity -> entity.distanceTo(player) }

        var targetPos = targetAround?.pos

        if (renderTarget != null
            && renderTargetPos != null
            && (targetPos == null
                || renderTarget!!.getBoundingBoxAt(renderTargetPos!!.pos).squaredBoxedDistanceTo(player.eyePos)
                <= targetAround!!.getBoundingBoxAt(targetPos).squaredBoxedDistanceTo(player.eyePos))
        ) {
            targetPos = renderTargetPos!!.pos
            targetAround = renderTarget
        }

        if (targetAround == null || targetPos == null) return

        if (targetAround.getBoundingBoxAt(targetPos)
                .squaredBoxedDistanceTo(player.eyePos) <= attackTargetRange.start.sq()
            && autoRotate.canRotate
        ) {
            rotate(targetAround, targetPos)
            if (alinkTicks >= 0) {
                target = targetAround
            }
        }

        if (alinkTicks == -1) renderTarget = targetAround
    }

    private fun handlePackets() {
        packets.removeIf {
            handlePacket(it)
            true
        }
    }

    private fun getCurrentAttackCount(): Int {
        if (!autoAttackCount) return attackCount.random()

        if (attackMode == AttackMode.PER_TICK) {
            return if (velocity < 1000.0) 0 else 5
        }

        return if (velocity < 1000.0) {
            0
        } else if (velocity in 1000.0..<2000.0) {
            3
        } else if (velocity in 2000.0..<10000.0) {
            4
        } else if (velocity >= 10000.0) {
            5
        } else {
            0
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (alinkTicks >= 0) {
            when (packet) {
                is DisconnectS2CPacket,
                is PlayerRespawnS2CPacket,
                is GameJoinS2CPacket -> {
                    handlePackets()
                }

                is PlayerPositionLookS2CPacket -> {
                    releaseReason = "flag"
                }

                is EntityS2CPacket -> {
                    event.cancelEvent()
                    packets.add(packet)
                    if (renderTargetPos != null && packet.getEntity(world) == renderTarget) {
                        renderTargetPos!!.pos = renderTargetPos!!.withDelta(
                            packet.deltaX.toLong(),
                            packet.deltaY.toLong(),
                            packet.deltaZ.toLong()
                        )
                    }
                }

                is EntityPositionS2CPacket -> {
                    event.cancelEvent()
                    packets.add(packet)
                    if (renderTargetPos != null && packet.entityId == renderTarget?.id) {
                        renderTargetPos!!.pos = packet.change.position.copy()
                    }
                }

                is EntityPositionSyncS2CPacket -> {
                    event.cancelEvent()
                    packets.add(packet)
                    if (renderTargetPos != null && packet.id == renderTarget?.id) {
                        renderTargetPos!!.pos = packet.values.position()
                    }
                }

                is EntityVelocityUpdateS2CPacket,
                is CommonPingS2CPacket -> {
                    event.cancelEvent()
                    packets.add(packet)
                }
            }

            return@handler
        }

        if (pause) return@handler

        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            receiveDamage = true
        }

        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id && receiveDamage) {
            receiveDamage = false

            if (ModuleFreeze.running
                || !(!requireKillAura || ModuleKillAura.running)
                || GrimNoSlowFoodNoC0F.working
                || PlacementManager.working
            ) return@handler

            if (attackQueue > 0) {
                reset()
            }

            velocity = sqrt((packet.velocityX.sq() + packet.velocityY.sq()).toDouble())

            val currentAttackCount = getCurrentAttackCount()
            if (currentAttackCount == 0) return@handler

            findTarget()
            if (renderTarget == null && !(alinkInAir && isInAir)) return@handler

            if (target == null || !player.isSprinting || (alinkInAir && isInAir)) {
                if (debug) {
                    if (!player.isSprinting) {
                        notifyAsMessage(ModuleGrimVelocity, "Alink... (not sprinting)")
                    } else {
                        notifyAsMessage(ModuleGrimVelocity, "Alink...")
                    }
                }
                if (renderTarget != null) {
                    renderTargetPos = TrackedPosition().apply { pos = renderTarget!!.pos }
                }
                alinkTicks = alinkMaxDelay
                event.cancelEvent()
                packets.add(packet)
            } else if (target != null) {
                attackQueue = currentAttackCount
                if (debug) notifyAsMessage(ModuleGrimVelocity, "Attack count: $attackQueue")
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        EventManager.callEvent(
            AlinkUpdateEvent(
                if (alinkTicks == -1) {
                    0
                } else {
                    alinkMaxDelay - alinkTicks
                },
                alinkMaxDelay,
                true
            )
        )

        if (attackQueue > 0) {
            if (target == null || target !in world.entities) {
                reset()
                return@tickHandler
            }

            when (attackMode) {
                AttackMode.ONE_TIME -> {
                    for (i in 1..attackQueue) {
                        if (target !in world.entities) break

                        val sprinting = player.isSprinting
                        if (sprinting) player.isSprinting = false
                        interaction.attackEntity(player, target!!)
                        player.swingHand(Hand.MAIN_HAND)
                        if (sprinting) player.setVelocity(
                            player.velocity.x * 0.6,
                            player.velocity.y,
                            player.velocity.z * 0.6
                        )
                    }

                    reset()
                }

                AttackMode.PER_TICK -> {
                    if (target!!.boxedDistanceTo(player) > attackTargetRange.endInclusive) {
                        if (debug) {
                            notifyAsMessage(ModuleGrimVelocity, "Unable to attack")
                        }
                        attackQueue--
                        if (attackQueue == 0) {
                            reset()
                        }
                        return@tickHandler
                    }

                    if (autoRotate.canRotate) {
                        rotate(target!!, target!!.pos)
                    }

                    val sprinting = player.isSprinting
                    if (sprinting) player.isSprinting = false
                    interaction.attackEntity(player, target!!)
                    player.swingHand(Hand.MAIN_HAND)
                    if (sprinting) player.setVelocity(
                        player.velocity.x * 0.6,
                        player.velocity.y,
                        player.velocity.z * 0.6
                    )

                    attackQueue--
                    if (attackQueue == 0) {
                        reset()
                    }
                }
            }
        }
    }

    @Suppress("unused")
    private val tickPacketProcessEventHandler = handler<TickPacketProcessEvent> {
        if (releaseReason != null) {
            handlePackets()
            alinkTicks = -1
            renderTarget = null
            renderTargetPos = null
            if (debug) {
                if (releaseReason!!.isEmpty()) {
                    notifyAsMessage(ModuleGrimVelocity, "Finish alink")
                    notifyAsMessage(ModuleGrimVelocity, "Attack count: $attackQueue")
                } else {
                    notifyAsMessage(ModuleGrimVelocity, "Finish alink ($releaseReason)")
                    reset()
                }
            }
            releaseReason = null
        }
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (attackQueue > 0) {
            event.directionalInput = DirectionalInput(
                forwards = true,
                backwards = false,
                left = false,
                right = false
            )
        }

        if (alinkTicks >= 0 && releaseReason == null) {
            if (alinkTicks > 0) alinkTicks--
            findTarget()

            if (alinkTicks == 0) {
                releaseReason = "max delay"
            } else if (player.abilities.flying) {
                releaseReason = "spectator"
            } else if (renderTarget !in world.entities && !isInAir) {
                releaseReason = "no target"
            } else if (player.pos.distanceTo(renderTargetPos!!.pos) > alinkTargetRange && !isInAir) {
                releaseReason = "out of range"
            } else if (target != null && (!alinkInAir || !isInAir)) {
                attackQueue = getCurrentAttackCount()
                releaseReason = ""
                event.directionalInput = DirectionalInput(
                    forwards = true,
                    backwards = false,
                    left = false,
                    right = false
                )
            }

            if (releaseReason != null && releaseReason!!.isNotEmpty()) {
                if (player.isOnGround && player.isSprinting) {
                    player.isSprinting = false
                }
            }
        }
    }

    private sealed class RenderChoice(name: String) : Choice(name) {
        final override val parent: ChoiceConfigurable<*>
            get() = renderTargetMode

        protected fun getEntityPosition(): Pair<Entity, Vec3d>? {
            if (alinkTicks == -1) return null
            val entity = renderTarget ?: return null
            val pos = renderTargetPos?.pos ?: return null
            return entity to pos
        }
    }

    private object Box : RenderChoice("Box") {
        private val color by color("Color", Color4b(36, 32, 147, 87))

        @Suppress("unused")
        private val renderHandler = handler<WorldRenderEvent> { event ->
            val (entity, pos) = getEntityPosition() ?: return@handler

            val dimensions = entity.getDimensions(entity.pose)
            val d = dimensions.width.toDouble() / 2.0

            val box = Box(-d, 0.0, -d, d, dimensions.height.toDouble(), d).expand(0.05)

            renderEnvironmentForWorld(event.matrixStack) {
                withPositionRelativeToCamera(pos) {
                    drawBox(box, color)
                }
            }
        }
    }

    private object Model : RenderChoice("Model") {
        private val lightAmount by float("LightAmount", 0.3f, 0.01f..1f)

        @Suppress("unused")
        private val renderHandler = handler<WorldRenderEvent> { event ->
            val (entity, pos) = getEntityPosition() ?: return@handler

            val light = world.getLightLevel(BlockPos.ORIGIN)
            val reducedLight = (light * lightAmount.toDouble()).toInt()

            renderEnvironmentForWorld(event.matrixStack) {
                withPositionRelativeToCamera(pos) {
                    mc.entityRenderDispatcher.render(
                        entity,
                        0.0,
                        0.0,
                        0.0,
                        1f,
                        event.matrixStack,
                        mc.bufferBuilders.entityVertexConsumers,
                        reducedLight
                    )
                }
            }
        }
    }

    private object Wireframe : RenderChoice("Wireframe") {
        private val color by color("Color", Color4b(255, 255, 255, 100))
        private val outlineColor by color("OutlineColor", Color4b(255, 255, 255, 255))

        @Suppress("unused")
        private val renderHandler = handler<WorldRenderEvent> {
            val (entity, pos) = getEntityPosition() ?: return@handler

            val wireframePlayer = WireframePlayer(pos, entity.yaw, entity.pitch)
            wireframePlayer.render(it, color, outlineColor)
        }
    }

    private object None : RenderChoice("None")

}
