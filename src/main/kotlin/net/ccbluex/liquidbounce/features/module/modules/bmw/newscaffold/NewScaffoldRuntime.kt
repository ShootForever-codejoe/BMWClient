/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.features.module.modules.bmw.newscaffold
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.HashMap
import kotlin.math.abs
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironmentForGUI
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.item.isFullBlock
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.block.Block
import net.minecraft.block.FallingBlock
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape

// 自救状态和自然运动预测
internal enum class RescueSessionState {
    IDLE,
    ARMED,
    PLACING,
    DESCENDING,
}

internal data class RescuePoseSample(
    val position: Vec3d,
    val velocity: Vec3d,
    val eye: Vec3d,
)

internal data class RescuePosePrediction(
    val current: RescuePoseSample,
    val afterOne: RescuePoseSample,
    val afterTwo: RescuePoseSample,
)

@Suppress("TooManyFunctions")
// 管理一次自救的状态和预测
internal class NewScaffoldRescueController(
    private val host: EventListener,
) : EventListener, MinecraftShortcuts {

    var state: RescueSessionState = RescueSessionState.IDLE
        private set
    @Volatile
    var generation: Long = 0L
        private set
    var attemptLockedUntilGround: Boolean = false
        private set
    val active: Boolean get() = state != RescueSessionState.IDLE

    override fun parent(): EventListener = host

    fun reset(clearAttemptLock: Boolean = true) {
        state = RescueSessionState.IDLE
        if (clearAttemptLock) attemptLockedUntilGround = false
    }

    fun onGround() {
        attemptLockedUntilGround = false
    }

    fun arm() {
        // generation 隔离上一次自救晚到的 ACK 和方块更新
        generation++
        state = RescueSessionState.ARMED
    }

    fun markPlacing() {
        if (active) state = RescueSessionState.PLACING
    }

    fun beginDescent() {
        state = RescueSessionState.DESCENDING
    }

    fun finish() {
        state = RescueSessionState.IDLE
        attemptLockedUntilGround = false
    }

    fun abort(lockUntilGround: Boolean) {
        // 失败后通常锁到落地 防止同一次下落反复建立锚点
        state = RescueSessionState.IDLE
        if (lockUntilGround) attemptLockedUntilGround = true
    }

    fun predict(player: ClientPlayerEntity, ignoreInput: Boolean): RescuePosePrediction {
        val samples = predictSequence(player, ticks = 2, ignoreInput = ignoreInput)
        return RescuePosePrediction(samples[0], samples[1], samples[2])
    }

    fun predictSequence(
        player: ClientPlayerEntity,
        ticks: Int,
        ignoreInput: Boolean,
    ): List<RescuePoseSample> {
        require(ticks >= 0)
        // 第零项是真实姿态 后续项按原版重力和碰撞逐刻推进
        val samples = ArrayList<RescuePoseSample>(ticks + 1)
        var sample = RescuePoseSample(player.pos, player.velocity, player.eyePos)
        samples += sample
        repeat(ticks) {
            sample = simulate(player, sample, ignoreInput)
            samples += sample
        }
        return samples
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(
        priority = EventPriorityConvention.FINAL_DECISION
    ) { event ->
        if (active) {
            // 只清方向输入 不修改位置、速度或 onGround
            event.directionalInput = DirectionalInput.NONE
        }
    }

    private fun simulate(
        player: ClientPlayerEntity,
        sample: RescuePoseSample,
        ignoreInput: Boolean,
    ): RescuePoseSample {
        var velocity = sample.velocity
        if (!ignoreInput) {
            val strafe = player.input.movementSideways.toDouble()
            val forward = player.input.movementForward.toDouble()
            val input = Vec3d(strafe, 0.0, forward)
            val lengthSquared = input.lengthSquared()
            if (lengthSquared >= 1.0E-7) {
                val speed = if (player.isSprinting) 0.026 else 0.02
                val normalized = (if (lengthSquared > 1.0) input.normalize() else input).multiply(speed)
                val yaw = player.yaw
                val sinYaw = MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE)
                val cosYaw = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE)
                velocity = velocity.add(
                    normalized.x * cosYaw - normalized.z * sinYaw,
                    0.0,
                    normalized.z * cosYaw + normalized.x * sinYaw,
                )
            }
        }

        // 先裁剪碰撞位移 再应用阻力和下一刻重力 顺序与客户端物理一致
        velocity = Entity.adjustMovementForCollisions(
            player,
            velocity,
            player.dimensions.getBoxAt(sample.position),
            player.world,
            emptyList<VoxelShape>()
        )
        val position = sample.position.add(velocity)
        velocity = velocity.multiply(0.91, 0.98, 0.91).subtract(0.0, 0.08, 0.0)
        val eye = position.add(0.0, player.dimensions.eyeHeight.toDouble(), 0.0)
        return RescuePoseSample(position, velocity, eye)
    }
}

internal data class PendingRescuePlacement(
    val id: Long,
    val pos: BlockPos,
    val sequence: Int,
    val generation: Long = 0L,
    var authoritative: Boolean? = null,
    var waitTicks: Int = 0,
)

internal enum class RescuePipelineResult {
    READY,
    FULL,
    ABORT,
}

@Suppress("TooManyFunctions", "LongParameterList")
// 对齐交互确认和方块更新
internal class NewScaffoldRescuePipeline(
    private val capacity: Int,
    private val stateDescriptionAt: (BlockPos) -> String,
    private val isStable: (BlockPos) -> Boolean,
    private val log: (String) -> Unit,
    private val tick: () -> Long = { 0L },
    private val generation: () -> Long = { 0L },
) {
    val pending = ArrayDeque<PendingRescuePlacement>()
    val confirmed = HashMap<BlockPos, Int>()
    val lastAckSequence = AtomicInteger(-1)

    var slotLease: SlotData? = null

    var awaitingPosition: BlockPos? = null
    var awaitingTicks: Int = 0
    var awaitingSequence: Int = -1
    var serverBlockUpdateConfirmed: Boolean = false
    var lastConfirmedPosition: BlockPos? = null
    var rollbackPending: Boolean = false
        private set

    val isFull: Boolean get() = pending.size >= capacity
    val isEmpty: Boolean get() = pending.isEmpty()

    fun reset(clearWatch: Boolean = true) {
        pending.clear()
        awaitingPosition = null
        awaitingTicks = 0
        awaitingSequence = -1
        serverBlockUpdateConfirmed = false
        lastConfirmedPosition = null
        rollbackPending = false
        if (clearWatch) confirmed.clear()
    }

    fun resetForGeneration() {
        reset(clearWatch = true)
        slotLease = null
        lastAckSequence.set(-1)
    }

    fun enqueue(id: Long, pos: BlockPos, sequence: Int, generation: Long = 0L): Boolean {
        // 一次交互只入队一次 队列满时由上层停止继续发包
        if (pending.size >= capacity) return false
        pending.addLast(PendingRescuePlacement(id, pos, sequence, generation))
        awaitingPosition = pos
        awaitingTicks = 0
        awaitingSequence = sequence
        serverBlockUpdateConfirmed = false
        return true
    }

    fun noteAck(sequence: Int) {
        lastAckSequence.updateAndGet { previous -> maxOf(previous, sequence) }
    }

    fun markRollback() {
        rollbackPending = true
    }

    fun noteBlockUpdate(pos: BlockPos, authoritative: Boolean) {
        // authoritative=false 是服务器回滚 必须立即终止依赖它的桥链
        val activeGeneration = generation()
        pending.firstOrNull { it.pos == pos && it.generation == activeGeneration }?.let { pendingEntry ->
            pendingEntry.authoritative = authoritative
            if (pos == awaitingPosition) {
                serverBlockUpdateConfirmed = authoritative
            }
        }
        confirmed[pos]?.let {
            if (authoritative) {
                confirmed[pos] = 0
            } else {
                rollbackPending = true
            }
        }
    }

    fun clearRollback() {
        rollbackPending = false
    }

    fun ageConfirmed() {
        // 已确认方块只短暂保留 用于识别稍后到达的回滚更新
        if (confirmed.isEmpty()) return
        val iterator = confirmed.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val age = entry.value + 1
            if (age > 80) iterator.remove() else entry.setValue(age)
        }
    }

    fun summary(): String {
        if (pending.isEmpty()) return "[]"
        return pending.joinToString(prefix = "[", postfix = "]", separator = ";") {
            "${it.id}:g${it.generation}:${it.pos.x},${it.pos.y},${it.pos.z}:" +
                "s${it.sequence}:a${it.authoritative}:w${it.waitTicks}"
        }
    }

    @Suppress("LongMethod", "CognitiveComplexMethod")
    fun process(): RescuePipelineResult {
        if (pending.isEmpty()) {
            awaitingPosition = null
            awaitingTicks = 0
            awaitingSequence = -1
            serverBlockUpdateConfirmed = false
            return RescuePipelineResult.READY
        }

        pending.forEach { it.waitTicks++ }
        while (pending.isNotEmpty()) {
            val current = pending.first
            val activeGeneration = generation()
            if (current.generation != activeGeneration) {
                log(
                    "rescue pipeline stale generation id=${current.id} " +
                        "entryGeneration=${current.generation} activeGeneration=$activeGeneration tick=${tick()}"
                )
                clearPendingAfterAbort()
                return RescuePipelineResult.ABORT
            }
            val stateDescription = stateDescriptionAt(current.pos)
            val localSolid = isStable(current.pos)
            val acknowledged = lastAckSequence.get() >= current.sequence
            val rejected = current.authoritative == false
            // 本地已有完整方块且 ACK 或权威更新至少满足一个才允许推进队首
            val accepted = localSolid && (current.authoritative == true || acknowledged)

            if (rejected) {
                log(
                        "rescue pipeline rejected id=${current.id} sequence=${current.sequence} " +
                        "pos=${current.pos} ack=${lastAckSequence.get()} state=$stateDescription " +
                        "authoritative=${current.authoritative}; aborting"
                )
                clearPendingAfterAbort()
                return RescuePipelineResult.ABORT
            }

            if (accepted) {
                pending.removeFirst()
                confirmed[current.pos] = 0
                lastConfirmedPosition = current.pos
                log(
                        "rescue pipeline confirmed id=${current.id} sequence=${current.sequence} " +
                        "pos=${current.pos} source=${if (current.authoritative == true) "block-update" else "ack"} " +
                        "state=$stateDescription remaining=${pending.size}"
                )
                continue
            }

            if (current.waitTicks > 40) {
                log(
                        "rescue pipeline timeout id=${current.id} sequence=${current.sequence} " +
                        "pos=${current.pos} ack=${lastAckSequence.get()} state=$stateDescription; aborting"
                )
                clearPendingAfterAbort()
                return RescuePipelineResult.ABORT
            }

            awaitingPosition = current.pos
            awaitingTicks = current.waitTicks
            awaitingSequence = current.sequence
            serverBlockUpdateConfirmed = current.authoritative == true
            if (current.waitTicks == 1 || current.waitTicks % 4 == 0) {
                log(
                        "rescue pipeline in-flight id=${current.id} sequence=${current.sequence} " +
                        "pos=${current.pos} ack=$acknowledged localSolid=$localSolid " +
                        "authoritative=${current.authoritative} state=$stateDescription depth=${pending.size}"
                )
            }
            break
        }

        if (pending.isEmpty()) {
            awaitingPosition = null
            awaitingTicks = 0
            awaitingSequence = -1
            serverBlockUpdateConfirmed = false
        } else {
            val first = pending.first
            awaitingPosition = first.pos
            awaitingTicks = first.waitTicks
            awaitingSequence = first.sequence
            serverBlockUpdateConfirmed = first.authoritative == true
        }
        return if (pending.size >= capacity) RescuePipelineResult.FULL else RescuePipelineResult.READY
    }

    private fun clearPendingAfterAbort() {
        pending.clear()
        awaitingPosition = null
        awaitingTicks = 0
        awaitingSequence = -1
        serverBlockUpdateConfirmed = false
        lastConfirmedPosition = null
    }
}

// 整条自救链只租用一个方块槽位
internal class NewScaffoldSlotManager(
    private val owner: EventListener,
    private val mode: () -> BlockSlotMode,
    private val invalidBlocks: Set<Block>,
) : MinecraftShortcuts {

    fun normalHotbarSlot(): Int {
        if (mode() == BlockSlotMode.MOST_BLOCKS) return mostBlocksHotbarSlot()
        var slot = -1
        for (index in 0..8) {
            if (isValid(player.inventory.getStack(index))) slot = index
        }
        return slot
    }

    fun chooseRescueLease(): SlotData? {
        // 副手无需切槽 其次复用服务端当前槽 最后才选数量最多的主手槽
        if (isValid(player.offHandStack)) return SlotData(99, Hand.OFF_HAND)

        val selected = serverSlot()
        if (selected in 0..8 && isValid(player.inventory.getStack(selected))) {
            return SlotData(selected, Hand.MAIN_HAND)
        }

        return (0..8)
            .map { index -> index to player.inventory.getStack(index) }
            .filter { (_, stack) -> isValid(stack) }
            .maxWithOrNull(
                compareBy<Pair<Int, ItemStack>> { it.second.count }
                    .thenBy { (index, _) -> abs(index - player.inventory.selectedSlot) }
            )
            ?.first
            ?.let { SlotData(it, Hand.MAIN_HAND) }
    }

    fun isLeaseValid(lease: SlotData?): Boolean {
        if (lease == null) return false
        return if (lease.hand == Hand.OFF_HAND) {
            isValid(player.offHandStack)
        } else {
            lease.slot in 0..8 && isValid(player.inventory.getStack(lease.slot))
        }
    }

    fun isValid(stack: ItemStack): Boolean {
        // 沙子等下落方块不能作为桥面 黑名单方块也不参与选择
        val item = stack.item as? BlockItem ?: return false
        return stack.isFullBlock() && item.block !is FallingBlock && item.block !in invalidBlocks
    }

    private fun mostBlocksHotbarSlot(): Int {
        val selected = serverSlot()
        var bestSlot = -1
        var bestCount = -1
        val selectedStack = player.inventory.getStack(selected)
        if (isValid(selectedStack)) {
            bestSlot = selected
            bestCount = selectedStack.count
        }
        for (index in 0..8) {
            val stack = player.inventory.getStack(index)
            if (isValid(stack) && stack.count > bestCount) {
                bestSlot = index
                bestCount = stack.count
            }
        }
        return bestSlot
    }

    private fun serverSlot(): Int = if (SilentHotbar.isSlotModifiedBy(owner)) {
        SilentHotbar.serversideSlot
    } else {
        player.inventory.selectedSlot
    }
}

// 日志、屏幕信息和世界标记
internal class NewScaffoldDebug(
    private val host: ModuleNewScaffold
) : EventListener, MinecraftShortcuts {

    private data class Mark(val position: BlockPos, val time: Long)

    private val lines = ArrayDeque<String>()
    private val marks = ArrayDeque<Mark>()

    override fun parent(): EventListener = host

    fun reset() {
        lines.clear()
        marks.clear()
    }

    fun log(message: String) {
        if (!host.debugEnabled) return

        val line = "[NewScaffoldDebug] $message"
        LiquidBounce.logger.info(line)
        // 文件日志保留完整内容 屏幕只从固定长度环形缓冲区读取末尾几行
        lines.addLast(line)
        while (lines.size > 64) {
            lines.removeFirst()
        }
    }

    fun mark(position: BlockPos) {
        // 同一位置刷新时间而不是重复叠加标记
        if (!host.markEnabled) return
        val mark = Mark(position, System.currentTimeMillis())
        marks.removeIf { it.position == position }
        marks.addLast(mark)
        while (marks.size > 32) marks.removeFirst()
    }

    @Suppress("unused")
    private val overlayHandler = handler<net.ccbluex.liquidbounce.event.events.OverlayRenderEvent> { event ->
        if (!host.debugEnabled || !host.running || mc.player == null || mc.world == null) return@handler

        val stateLines = host.debugStateLines(lines.toList().takeLast(6))
        renderEnvironmentForGUI(event) {
            val x = 4
            val y = 4
            val lineHeight = mc.textRenderer.fontHeight + 1
            val width = stateLines.maxOfOrNull { mc.textRenderer.getWidth(it) }?.plus(8) ?: 8
            val height = stateLines.size * lineHeight + 6
            event.context.fill(x - 2, y - 2, x + width, y + height, 0xAA101018.toInt())
            stateLines.forEachIndexed { index, line ->
                val color = if (index == 0) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt()
                event.context.drawTextWithShadow(mc.textRenderer, line, x, y + index * lineHeight, color)
            }
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<net.ccbluex.liquidbounce.event.events.WorldRenderEvent> { event ->
        if (!host.markEnabled) {
            marks.clear()
            return@handler
        }

        val now = System.currentTimeMillis()
        // 过期标记从队首清理 渲染时再计算平滑淡入淡出
        while (marks.isNotEmpty() && now - marks.first.time >= host.markDurationMs) {
            marks.removeFirst()
        }
        if (marks.isEmpty()) return@handler

        val cameraOffset = mc.entityRenderDispatcher.camera.pos.negate()
        val fadeIn = host.markFadeInMs.coerceAtMost(host.markDurationMs)
        renderEnvironmentForWorld(event.matrixStack) {
            marks.forEach { mark ->
                val elapsed = now - mark.time
                val opacity = if (fadeIn > 0 && elapsed < fadeIn) {
                    elapsed.toFloat() / fadeIn
                } else {
                    1f - (elapsed - fadeIn).toFloat() /
                        (host.markDurationMs - fadeIn).coerceAtLeast(1)
                }.coerceIn(0f, 1f)
                val smoothOpacity = opacity * opacity * (3f - 2f * opacity)
                val box = Box(mark.position).offset(cameraOffset)
                drawBox(
                    box,
                    host.markSideColorValue.fade(smoothOpacity),
                    host.markLineColorValue.fade(smoothOpacity)
                )
            }
        }
    }
}

