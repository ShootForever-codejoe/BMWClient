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
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.GrimVelocityMode
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.NoFallGrim
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.math.copy
import net.ccbluex.liquidbounce.utils.render.WireframePlayer
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.entity.Entity
import net.minecraft.entity.TrackedPosition
import net.minecraft.network.packet.Packet
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
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

object GrimVelocityDelay : GrimVelocityMode("Delay") {

    private object DelayInAir : Choice("InAir") {
        override val parent: ChoiceConfigurable<*>
            get() = mode

        val jumpReset by boolean("JumpReset", true)
    }

    private object DelayByTicks : Choice("ByTicks") {
        override val parent: ChoiceConfigurable<*>
            get() = mode

        val delay by intRange("Delay", 5..10, 0..60, "ticks")
    }

    private val mode = choices(
        "Mode", DelayByTicks, arrayOf(
            DelayInAir,
            DelayByTicks
        )
    )

    private val renderTargetMode = choices(
        "RenderTargetMode", Wireframe, arrayOf(
            Box, Model, Wireframe, None
        )
    )

    private val requireKillAura by boolean("RequireKillAura", true)


    private var delaying = false
    private var damage = false
    private var delayTicks = 0
    private var jump = false
    private var target: Entity? = null
    private var targetPos: TrackedPosition? = null
    private val packets = Queues.newConcurrentLinkedQueue<Packet<*>>()

    override val shouldStopBacktrack: Boolean
        get() = delaying

    override fun disable() {
        delaying = false
        damage = false
        delayTicks = 0
        jump = false
        target = null
        targetPos = null
        packets.clear()
    }

    private fun handle() {
        packets.removeIf {
            handlePacket(it)
            true
        }
        delaying = false
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING) return@handler

        val packet = event.packet

        if (delaying) {
            when (packet) {
                is ChatMessageS2CPacket,
                is GameMessageS2CPacket -> {
                    return@handler
                }

                is DisconnectS2CPacket,
                is PlayerRespawnS2CPacket,
                is GameJoinS2CPacket,
                is PlayerPositionLookS2CPacket -> {
                    handle()
                    return@handler
                }

                is EntityS2CPacket if (targetPos != null && packet.getEntity(world) == target) -> {
                    targetPos!!.pos = targetPos!!.withDelta(
                        packet.deltaX.toLong(),
                        packet.deltaY.toLong(),
                        packet.deltaZ.toLong()
                    )
                }

                is EntityPositionS2CPacket if (targetPos != null && packet.entityId == target?.id) -> {
                    targetPos!!.pos = packet.change.position.copy()
                }

                is EntityPositionSyncS2CPacket if (targetPos != null && packet.id == target?.id) -> {
                    targetPos!!.pos = packet.values.position()
                }
            }

            event.cancelEvent()
            packets.add(packet)
            return@handler
        }

        if (pause) return@handler

        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            damage = true
        }

        if (damage && packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            if (!requireKillAura || (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null)) {
                delayTicks = when (mode.activeChoice) {
                    DelayInAir -> 30
                    DelayByTicks -> DelayByTicks.delay.random()
                    else -> 0
                }
                delaying = true
                if (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null) {
                    target = ModuleKillAura.targetTracker.target
                    targetPos = TrackedPosition().apply { pos = target!!.pos }
                }
                event.cancelEvent()
                packets.add(packet)
            }
            damage = false
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (delaying) {
            delayTicks--
        }
    }

    @Suppress("unused")
    private val tickPacketProcessEventHandler = handler<TickPacketProcessEvent> {
        if (delaying && when (mode.activeChoice) {
                DelayInAir -> player.isOnGround || delayTicks <= 0
                DelayByTicks -> delayTicks <= 0
                else -> true
            }
        ) {
            handle()
            if (mode.activeChoice == DelayInAir && DelayInAir.jumpReset) {
                jump = true
            }
            target = null
            targetPos = null
        }
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (jump) {
            if (!InventoryManager.isInventoryOpen
                && mc.currentScreen !is GenericContainerScreen
                && player.isOnGround
                && !(NoFallGrim.running && NoFallGrim.jumping)
            ) {
                event.jump = true
            }
            jump = false
        }
    }

    private sealed class RenderChoice(name: String) : Choice(name) {
        final override val parent: ChoiceConfigurable<*>
            get() = renderTargetMode

        protected fun getEntityPosition(): Pair<Entity, Vec3d>? {
            if (!delaying) return null
            val entity = target ?: return null
            val pos = targetPos?.pos ?: return null
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
