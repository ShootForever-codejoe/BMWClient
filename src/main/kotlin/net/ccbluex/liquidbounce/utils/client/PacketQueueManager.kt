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
package net.ccbluex.liquidbounce.utils.client

import com.google.common.collect.Queues
import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.*
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.render.drawLineStrip
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Vec3
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FINAL_DECISION
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.ccbluex.liquidbounce.utils.render.WireframePlayer
import net.minecraft.client.option.Perspective
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket
import net.minecraft.network.packet.c2s.query.QueryRequestC2SPacket
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Allows to queue packets and flush them later on demand.
 *
 * Fires [QueuePacketEvent] to determine whether a packet should be queued or not. They can be
 * from origin [TransferOrigin.INCOMING] or [TransferOrigin.OUTGOING], but will be handled separately.
 */
@Suppress("TooManyFunctions")
object PacketQueueManager : EventListener {

    @Volatile
    private var rescueActiveProvider: (() -> Boolean)? = null

    private var rescueDrainTick = 0L
    private var rescueDropTotal = 0L

    @Volatile
    var lastRescueDrainReport: RescueDrainReport = RescueDrainReport.INACTIVE
        private set

    val packetQueue: ConcurrentLinkedQueue<PacketSnapshot> = Queues.newConcurrentLinkedQueue()
    val positions
        get() = packetQueue
            .map { snapshot -> snapshot.packet }
            .filterIsInstance<PlayerMoveC2SPacket>()
            .filter { playerMoveC2SPacket -> playerMoveC2SPacket.changePosition }
            .map { playerMoveC2SPacket -> Vec3d(playerMoveC2SPacket.x, playerMoveC2SPacket.y, playerMoveC2SPacket.z) }

    val isLagging
        get() = packetQueue.isNotEmpty()

    // 注册自救状态 避免队列反向依赖模块
    fun registerRescueActiveProvider(provider: (() -> Boolean)?) {
        rescueActiveProvider = provider
    }

    // 自救开始后丢弃旧移动包 保留控制包顺序
    fun discardRescueMovementBacklog(): RescueDrainReport {
        if (!isRescueDrainActive()) {
            lastRescueDrainReport = RescueDrainReport.INACTIVE
            return RescueDrainReport.INACTIVE
        }

        val queueSizeBefore = packetQueue.size
        val movements = queuedOutgoingMovements()
        val dropped = movements.count(packetQueue::remove)
        rescueDropTotal += dropped

        val report = RescueDrainReport(
            active = true,
            tick = rescueDrainTick,
            dropped = dropped,
            queuedMovementsBefore = movements.size,
            queuedMovementsAfter = 0,
            queueSizeBefore = queueSizeBefore,
            queueSizeAfter = packetQueue.size,
            totalDropped = rescueDropTotal,
        )
        lastRescueDrainReport = report
        logRescueDrain(report)
        return report
    }

    // 清空自救队列统计
    fun resetRescueDrain() {
        rescueDrainTick = 0L
        rescueDropTotal = 0
        lastRescueDrainReport = RescueDrainReport.INACTIVE
    }

    // 按原顺序发送自救前的控制包
    fun flushOutgoingRescueControlBacklog(): Int {
        val snapshots = packetQueue.filter {
            it.origin == TransferOrigin.OUTGOING && it.packet !is PlayerMoveC2SPacket
        }
        var flushed = 0
        snapshots.forEach { snapshot ->
            if (packetQueue.remove(snapshot)) {
                flushSnapshot(snapshot)
                flushed++
            }
        }
        if (flushed > 0) {
            val types = snapshots
                .groupingBy { it.packet::class.simpleName ?: "unknown" }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .joinToString(limit = 8) { "${it.key}:${it.value}" }
            LiquidBounce.logger.info(
                "[PacketQueue][Rescue] flushed ordered non-movement backlog " +
                    "count=$flushed types=[$types] queueAfter=${packetQueue.size}"
            )
        }
        return flushed
    }

    // 只发送最新基线 其余旧移动包直接丢弃
    fun flushLatestOutgoingRescueMovementBaseline(): RescueMovementBaselineResult {
        val movements = packetQueue.filter {
            it.origin == TransferOrigin.OUTGOING && it.packet is PlayerMoveC2SPacket
        }
        val latest = movements.lastOrNull()
        var dropped = 0
        var flushed = 0
        movements.forEach { snapshot ->
            if (!packetQueue.remove(snapshot)) return@forEach
            if (snapshot === latest) {
                flushSnapshot(snapshot)
                flushed++
            } else {
                dropped++
            }
        }
        rescueDropTotal += dropped
        if (movements.isNotEmpty()) {
            LiquidBounce.logger.info(
                "[PacketQueue][Rescue] movement baseline flushed=$flushed " +
                    "olderDropped=$dropped queueAfter=${packetQueue.size}"
            )
        }
        return RescueMovementBaselineResult(flushed, dropped)
    }

    @Suppress("unused")
    private val flushHandler = handler<GameRenderTaskQueueEvent> {
        if (mc.networkHandler?.connection?.isOpen != true) {
            packetQueue.clear()
            return@handler
        }

        if (fireEvent(null, TransferOrigin.OUTGOING) == Action.FLUSH) {
            flush(TransferOrigin.OUTGOING)
        }
    }

    // 每刻清理一次残留移动包
    @Suppress("unused")
    private val rescueDrainTickHandler = handler<GameTickEvent>(priority = FIRST_PRIORITY) {
        rescueDrainTick++
        if (isRescueDrainActive()) {
            discardRescueMovementBacklog()
        }
    }

    @Suppress("unused")
    private val flushReceiveHandler = handler<TickPacketProcessEvent> {
        if (mc.networkHandler?.connection?.isOpen != true) {
            packetQueue.clear()
            return@handler
        }

        if (fireEvent(null, TransferOrigin.INCOMING) == Action.FLUSH) {
            flush(TransferOrigin.INCOMING)
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(priority = FINAL_DECISION) { event ->
        // Ignore packets that are already cancelled, as they are already handled
        if (event.isCancelled) {
            return@handler
        }

        val packet = event.packet
        val origin = event.origin

        // 自救移动包始终直发
        if (origin == TransferOrigin.OUTGOING && packet is PlayerMoveC2SPacket && isRescueDrainActive()) {
            discardRescueMovementBacklog()
            return@handler
        }

        // If we shouldn't lag, don't do anything
        val lagResult = fireEvent(packet, origin)
        if (lagResult == Action.FLUSH) {
            flush(origin)
            return@handler
        }

        if (lagResult == Action.PASS) {
            return@handler
        }

        when (packet) {

            is HandshakeC2SPacket, is QueryRequestC2SPacket, is QueryPingC2SPacket -> {
                return@handler
            }

            // Ignore message-related packets
            is ChatMessageC2SPacket, is GameMessageS2CPacket, is CommandExecutionC2SPacket -> {
                return@handler
            }

            // Flush on teleport or disconnect
            is PlayerPositionLookS2CPacket, is DisconnectS2CPacket -> {
                flush(origin)
                return@handler
            }

            // Ignore own hurt sounds
            is PlaySoundS2CPacket if packet.sound.value() == SoundEvents.ENTITY_PLAYER_HURT -> {
                return@handler
            }

            // Flush on own death
            is HealthUpdateS2CPacket if packet.health <= 0 -> {
                flush(origin)
                return@handler
            }

        }

        event.cancelEvent()
        packetQueue.add(
            PacketSnapshot(
                packet,
                origin,
                System.currentTimeMillis()
            )
        )
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> { event ->
        // Clear packets on disconnect
        if (event.world == null) {
            packetQueue.clear()
            resetRescueDrain()
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val matrixStack = event.matrixStack

        renderEnvironmentForWorld(matrixStack) {
            // Use LiquidBounce accent color
            drawLineStrip(
                argb = Color4b.LIQUID_BOUNCE.toARGB(),
                positions = positions.mapToArray { vec3d -> Vec3(relativeToCamera(vec3d)) },
            )
        }

        val perspectiveEvent = EventManager.callEvent(PerspectiveEvent(mc.options.perspective))
        if (perspectiveEvent.perspective != Perspective.FIRST_PERSON) {
            val pos = positions.firstOrNull() ?: return@handler
            val rotation = RotationManager.actualServerRotation

            val wireframePlayer = WireframePlayer(pos, rotation.yaw, rotation.pitch)
            wireframePlayer.render(event, Color4b(36, 32, 147, 87), Color4b(36, 32, 147, 255))
        }
    }

    fun flush(flushWhen: (PacketSnapshot) -> Boolean) {
        if (isRescueDrainActive()) {
            discardRescueMovementBacklog()
        }
        packetQueue.removeIf { snapshot ->
            if (flushWhen(snapshot)) {
                flushSnapshot(snapshot)
                true
            } else {
                false
            }
        }
    }

    fun flush(origin: TransferOrigin) {
        if (origin == TransferOrigin.OUTGOING && isRescueDrainActive()) {
            discardRescueMovementBacklog()
            return
        }
        flush { it.origin == origin }
    }

    fun flush(count: Int) {
        if (isRescueDrainActive()) {
            discardRescueMovementBacklog()
            return
        }
        // Take all packets until the counter of move packets reaches count and send them
        var counter = 0

        with(packetQueue.iterator()) {
            while (hasNext()) {
                val snapshot = next()
                val packet = snapshot.packet

                if (packet is PlayerMoveC2SPacket && packet.changePosition) {
                    counter += 1
                }

                flushSnapshot(snapshot)
                remove()

                if (counter >= count) {
                    break
                }
            }
        }
    }

    fun cancel() {
        positions.firstOrNull()?.let { pos ->
            player.setPosition(pos)
        }

        for (snapshot in packetQueue) {
            when (snapshot.packet) {
                is PlayerMoveC2SPacket -> continue
                else -> flushSnapshot(snapshot)
            }
        }
        packetQueue.clear()
    }

    fun isAboveTime(delay: Long): Boolean {
        val entryPacketTime = (packetQueue.firstOrNull()?.timestamp ?: return false)
        return System.currentTimeMillis() - entryPacketTime >= delay
    }

    inline fun <reified T> rewrite(action: (T) -> Unit) {
        packetQueue
            .filterIsInstance<T>()
            .forEach(action)
    }

    private fun flushSnapshot(snapshot: PacketSnapshot) {
        when (snapshot.origin) {
            TransferOrigin.OUTGOING -> sendPacketSilently(snapshot.packet)
            TransferOrigin.INCOMING -> handlePacket(snapshot.packet)
        }
    }

    private fun fireEvent(packet: Packet<*>?, origin: TransferOrigin) =
        EventManager.callEvent(QueuePacketEvent(packet, origin)).action

    private fun isRescueDrainActive(): Boolean =
        runCatching { rescueActiveProvider?.invoke() == true }.getOrDefault(false)

    private fun queuedOutgoingMovements(): List<PacketSnapshot> = packetQueue
        .filter { it.origin == TransferOrigin.OUTGOING && it.packet is PlayerMoveC2SPacket }

    private fun logRescueDrain(report: RescueDrainReport) {
        if (report.dropped <= 0) return

        val message =
            "[PacketQueue][Rescue] tick=${report.tick} dropped=${report.dropped} " +
                "movements=${report.queuedMovementsBefore}->" +
                "${report.queuedMovementsAfter} queue=${report.queueSizeBefore}->" +
                "${report.queueSizeAfter} totalDropped=${report.totalDropped}"
        LiquidBounce.logger.info(message)
    }

    enum class Action(val priority: Int) {
        FLUSH(0),
        PASS(1),
        QUEUE(2)
    }

}

data class RescueMovementBaselineResult(
    val flushed: Int,
    val dropped: Int,
)

// 最近一次自救队列清理结果
data class RescueDrainReport(
    val active: Boolean,
    val tick: Long,
    val dropped: Int,
    val queuedMovementsBefore: Int,
    val queuedMovementsAfter: Int,
    val queueSizeBefore: Int,
    val queueSizeAfter: Int,
    val totalDropped: Long,
) {
    companion object {
        val INACTIVE = RescueDrainReport(
            active = false,
            tick = Long.MIN_VALUE,
            dropped = 0,
            queuedMovementsBefore = 0,
            queuedMovementsAfter = 0,
            queueSizeBefore = 0,
            queueSizeAfter = 0,
            totalDropped = 0,
        )
    }
}

data class PacketSnapshot(
    val packet: Packet<*>,
    val origin: TransferOrigin,
    val timestamp: Long
)

