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

package net.ccbluex.liquidbounce.features.module.modules.bmw.newscaffold

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.event.events.*
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.PacketQueueManager
import net.ccbluex.liquidbounce.utils.client.RescueMovementBaselineResult
import net.ccbluex.liquidbounce.utils.client.interactItem
import net.ccbluex.liquidbounce.utils.client.toRadians
import net.ccbluex.liquidbounce.utils.entity.airTicks
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.onGroundTicks
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.block.*
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Suppress("unused")
// 普通搭路和下落自救入口
object ModuleNewScaffold : ClientModule("NewScaffold", Category.BMW, disableOnQuit = true) {

    init {
        PacketQueueManager.registerRescueActiveProvider { isRescueActive() }
    }

    // 基础搭路行为 保留原配置键名以兼容现有配置文件
    private val mode by enumChoice("Mode", Mode.TELLY).apply { tagBy(this) }
    private val alwaysUpdateRot by boolean("AlwaysUpdateRotation", true)
    private val placeTick by int("PlaceTick", 1, 1..5, "ticks")
    private val rotTick by int("RotationTick", 3, 1..5, "ticks")
    private val spoofItem by boolean("SpoofItem", true)
    private val noSwing by boolean("NoSwing", false)
    private val eagle by boolean("Eagle", false)
    private val snap by boolean("Snap", false)
    private val noUpTelly by boolean("NoUpTelly", false)
    private val smoothed by boolean("HeypixelUpTelly", true)
    private val safeMode by boolean("SafeMode", false)
    private val testOnGround by boolean("TestOnGround", true)
    private val fixRotation by boolean("FixRotation", false)
    private val randomSlow by boolean("SlowUpTelly", false)
    private val abuseRotation by boolean("AbuseRotation", false)
    private val blockSlotMode by enumChoice("BlockSlotMode", BlockSlotMode.MOST_BLOCKS)
    private val jumpMode by enumChoice("JumpMode", JumpMode.NORMAL)
    // 射线接近交互距离上限时提前准备旋转
    private val safeDistance by float("ClutchSafeDistance", 4.25f, 1.0f..5.0f)
    private val tellyEagleTick by int("EagleTick", 1, 1..5)
    private val keepEagleSneakTick by int("KeepEagleTick", 1, 1..5)
    // 调试开关同时控制文件日志和屏幕状态面板
    private val debug by boolean("Debug", true)
    private val keepFov by boolean("KeepFov", true)
    private val fov by float("Fov", 1.1f, 1.0f..2.1f)
    private val duplicateRotPlace by boolean("DuplicateRotPlace", true)
    private val interactItem by boolean("InteractItemBeforePlace", false)
    // 世界标记只负责显示 不参与目标选择
    private val mark by boolean("Mark", true)
    private val markSideColor by color("MarkSideColor", Color4b(255, 48, 48, 70))
    private val markLineColor by color("MarkLineColor", Color4b(255, 48, 48, 150))
    private val markDuration by int("MarkDuration", 1000, 100..3000, "ms")
    private val markFadeIn by int("MarkFadeIn", 100, 0..500, "ms")

    private var blockSlot: SlotData? = null
    private var canPlace = false
    private var blockData: BlockData? = null
    private var lastBlockData: BlockData? = null
    private var rotateCount = 0
    private var posY = 0.0
    private val debugSupport = NewScaffoldDebug(this)
    private val blockFinder = NewScaffoldBlockFinder()
    private val rescuePlanner = NewScaffoldRescuePlanner(
        isSolid = blockFinder::isSolid,
        isPending = { position -> rescuePendingPlacements.any { it.pos == position } },
        fixRotation = { fixRotation },
        movementYaw = { lastMovementPacketYaw },
        log = ::debugLog,
    )
    private val rescueController = NewScaffoldRescueController(this)
    private var tellyJumpTicks = 0
    private var waitingForEagleSneak = false
    private var eagleSneakPressedByModule = false
    private var lastRotation: Rotation? = null
    private var rot: Rotation? = null
    private var oldSlot = 0
    private var placeCount = 0
    private var ups = 0
    private var skipTick = false
    private var clutching = false

    // 普通快速桥只复用自救的搜索和预瞄 不创建自救会话
    private var fastBridgeLandingTargetPos: BlockPos? = null
    private var fastBridgePrepared = false

    // 一次自救使用的运行状态
    private var rescueSlotLease: SlotData?
        get() = rescuePipeline.slotLease
        set(value) { rescuePipeline.slotLease = value }
    private var rescueSlotSwitchTick = Long.MIN_VALUE
    private var rescueLastPlanTick = Long.MIN_VALUE
    private var rescueLastInteractionEye: Vec3d? = null
    private var rescueLastPredictedEye: Vec3d? = null
    private var preparedRescueData: BlockData? = null
    private var preparedRescueRotation: Rotation? = null
    private var rescueAnchorPos: Vec3d? = null
    private var rescueNoTargetTicks = 0
    private var rescueChainPlacements = 0
    private var rescueTotalTicks = 0
    private var rescueRotationTimeouts = 0
    private var rescueLandingSolidTicks = 0
    private var rescueLandingTargetPos: BlockPos? = null
    private var rescueLandingLockPos: Vec3d? = null
    private var rescueLandingDescentTicks = 0
    private var rescuePlannedPlacements = 0
    private var rescueProjectedFallDistance = 0f
    private var rescueEstimatedDamage = 0
    private var rescueSlotReleasePending = false
    private var rescueLastSentSequence = -1
    private const val RESCUE_PIPELINE_LIMIT = 4
    private const val RESCUE_MAX_NO_TARGET_TICKS = 2
    private const val RESCUE_MAX_TICKS = 24
    private const val RESCUE_FORECAST_TICKS = 6
    private const val RESCUE_FALL_TRIGGER = -0.01
    private const val RESCUE_ORDINARY_GRACE_FALL = 1.0f
    private var rescuePlacementId = 0L
    private var rescuePlacePacketsThisTick = 0
    private var rescueInteractionInProgress = false
    private var rescueSearchState: String
        get() = rescuePlanner.searchState
        set(value) { rescuePlanner.searchState = value }
    @Volatile
    private var rescueServerCorrectionPending = false
    @Volatile
    private var rescueServerCorrectionGeneration = -1L

    // 最多保留四个未确认放置
    private val rescuePipeline = NewScaffoldRescuePipeline(
        capacity = RESCUE_PIPELINE_LIMIT,
        stateDescriptionAt = { position -> world.getBlockState(position).block.toString() },
        isStable = ::isStableRescueBlock,
        log = ::debugLog,
        tick = { debugTick },
        generation = { rescueController.generation },
    )
    private val rescuePendingPlacements get() = rescuePipeline.pending
    private val rescueConfirmedPlacements get() = rescuePipeline.confirmed
    private val rescueLastAckSequence get() = rescuePipeline.lastAckSequence
    private var rescueAwaitingPlacementPos: BlockPos?
        get() = rescuePipeline.awaitingPosition
        set(value) { rescuePipeline.awaitingPosition = value }
    private var rescueAwaitingPlacementTicks: Int
        get() = rescuePipeline.awaitingTicks
        set(value) { rescuePipeline.awaitingTicks = value }
    private var rescueAwaitingSequence: Int
        get() = rescuePipeline.awaitingSequence
        set(value) { rescuePipeline.awaitingSequence = value }
    private var rescueServerBlockUpdateConfirmed: Boolean
        get() = rescuePipeline.serverBlockUpdateConfirmed
        set(value) { rescuePipeline.serverBlockUpdateConfirmed = value }
    private var rescueLastConfirmedPlacementPos: BlockPos?
        get() = rescuePipeline.lastConfirmedPosition
        set(value) { rescuePipeline.lastConfirmedPosition = value }
    private val rescuePipelineRollbackPending: Boolean
        get() = rescuePipeline.rollbackPending
    private var reachableState = true
    private var rescueReason = "none"
    private var predictedYState = 0.0
    private var targetDistanceState = 0.0
    private var rotationHitsTargetState = false
    private var debugTick = 0L
    private var lastPlacementState = "idle"
    private var placePending = false
    private var lastMovementPacketYaw = 0f
    private var lastMovementPacketPitch = 0f
    private var lastMovementYawDelta = 0f
    private var lastPlacedYawDelta: Float? = null
    private var movementRotationPending = false
    private var movementPacketSentThisTick = false
    private var lastOutgoingMovementY = Double.NaN
    private var lastOutgoingMovementGround = false

    @JvmStatic
    // 让其它模块避开自救期间的移动包和旋转
    fun isRescueActive(): Boolean = running &&
        (rescueController.active || rescueAnchorPos != null || rescueLandingTargetPos != null)


    private val invalidBlocks = setOf(
        Blocks.ENCHANTING_TABLE,
        Blocks.OAK_SIGN,
        Blocks.CHEST,
        Blocks.ENDER_CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.ANVIL,
        Blocks.SAND,
        Blocks.COBWEB,
        Blocks.TORCH,
        Blocks.CRAFTING_TABLE,
        Blocks.FURNACE,
        Blocks.WATER_CAULDRON,
        Blocks.DISPENSER,
        Blocks.STONE_PRESSURE_PLATE,
        Blocks.BAMBOO_PRESSURE_PLATE,
        Blocks.NOTE_BLOCK,
        Blocks.DROPPER,
        Blocks.TNT,
        Blocks.REDSTONE_TORCH,
        Blocks.DAYLIGHT_DETECTOR,
        Blocks.BIRCH_SIGN,
        Blocks.SPRUCE_SIGN,
        Blocks.JUNGLE_SIGN,
        Blocks.ACACIA_SIGN,
        Blocks.DARK_OAK_SIGN,
        Blocks.MANGROVE_SIGN,
        Blocks.CHERRY_SIGN,
        Blocks.BAMBOO_SIGN,
        Blocks.CRIMSON_SIGN,
        Blocks.WARPED_SIGN,
        Blocks.OAK_HANGING_SIGN,
        Blocks.BIRCH_HANGING_SIGN,
        Blocks.SPRUCE_HANGING_SIGN,
        Blocks.JUNGLE_HANGING_SIGN,
        Blocks.ACACIA_HANGING_SIGN,
        Blocks.DARK_OAK_HANGING_SIGN,
        Blocks.MANGROVE_HANGING_SIGN,
        Blocks.CHERRY_HANGING_SIGN,
        Blocks.BAMBOO_HANGING_SIGN,
        Blocks.CRIMSON_HANGING_SIGN,
        Blocks.WARPED_HANGING_SIGN
    )
    private val slotManager = NewScaffoldSlotManager(this, { blockSlotMode }, invalidBlocks)

    @Suppress("LongMethod")
    override fun onEnabled() {
        placeCount = 0
        ups = 0
        lastRotation = Rotation(player.yaw, player.pitch)
        oldSlot = player.inventory.selectedSlot
        blockSlot = null
        blockData = null
        canPlace = true
        debugSupport.reset()
        tellyJumpTicks = 0
        waitingForEagleSneak = false
        eagleSneakPressedByModule = false
        rot = null
        skipTick = false
        clutching = false
        resetRescueState()
        reachableState = true
        rescueReason = "none"
        predictedYState = player.y
        targetDistanceState = 0.0
        rotationHitsTargetState = false
        debugTick = 0
        lastPlacementState = "idle"
        placePending = false
        lastMovementPacketYaw = RotationManager.serverRotation.yaw
        lastMovementPacketPitch = RotationManager.serverRotation.pitch
        lastMovementYawDelta = 0f
        lastPlacedYawDelta = null
        movementRotationPending = false
        movementPacketSentThisTick = false
        lastOutgoingMovementY = Double.NaN
        lastOutgoingMovementGround = player.isOnGround
        SilentHotbar.resetSlot(this)
    }

    override fun onDisabled() {
        player.inventory.selectedSlot = oldSlot
        SilentHotbar.resetSlot(this)
        clearOwnedEagleSneak()
        skipTick = false
        clutching = false
        resetRescueState()
        debugSupport.reset()
        placePending = false
        lastOutgoingMovementY = Double.NaN
        lastOutgoingMovementGround = false
    }

    // 清空一次自救留下的状态
    private fun resetRescueState() {
        clearFastBridgeState()
        rescueController.reset()
        rescueSlotLease = null
        rescueSlotSwitchTick = Long.MIN_VALUE
        rescueLastPlanTick = Long.MIN_VALUE
        rescueLastInteractionEye = null
        rescueLastPredictedEye = null
        preparedRescueData = null
        preparedRescueRotation = null
        rescueAnchorPos = null
        rescueNoTargetTicks = 0
        rescueChainPlacements = 0
        rescueTotalTicks = 0
        rescueRotationTimeouts = 0
        rescueLandingSolidTicks = 0
        rescueLandingTargetPos = null
        rescueLandingLockPos = null
        rescueLandingDescentTicks = 0
        rescuePlannedPlacements = 0
        rescueProjectedFallDistance = 0f
        rescueEstimatedDamage = 0
        rescueSlotReleasePending = false
        rescueAwaitingPlacementPos = null
        rescueAwaitingPlacementTicks = 0
        rescueAwaitingSequence = -1
        rescueLastSentSequence = -1
        rescueServerBlockUpdateConfirmed = false
        rescueLastConfirmedPlacementPos = null
        rescuePipeline.resetForGeneration()
        rescuePlacementId = 0L
        rescuePlacePacketsThisTick = 0
        rescueInteractionInProgress = false
        rescueSearchState = "idle"
        rescueServerCorrectionPending = false
        rescueServerCorrectionGeneration = -1L
        reachableState = true
        rescueReason = "none"
        PacketQueueManager.resetRescueDrain()
    }

    // 退出快速桥时只清理由它持有的预瞄 避免误删真正自救的目标
    private fun clearFastBridgeState(clearPrepared: Boolean = true) {
        if (clearPrepared && fastBridgePrepared) {
            preparedRescueData = null
            preparedRescueRotation = null
        }
        fastBridgeLandingTargetPos = null
        fastBridgePrepared = false
    }

    private fun clearOwnedEagleSneak() {
        if (eagleSneakPressedByModule) {
            mc.options.sneakKey.isPressed = false
        }
        eagleSneakPressedByModule = false
        waitingForEagleSneak = false
        tellyJumpTicks = 0
    }

    // 在安全时机归还服务端槽位
    private fun releaseRescueSlotLease() {
        rescueSlotLease = null
        rescueSlotSwitchTick = Long.MIN_VALUE
        rescueSlotReleasePending = false
        SilentHotbar.resetSlot(this)
    }

    @JvmStatic
    fun getFovMultiplier(original: Float): Float {
        val clientPlayer = mc.player ?: return original
        if (!running || !keepFov || !clientPlayer.moving) return original
        val speedLevel = (clientPlayer.getStatusEffect(StatusEffects.SPEED)?.amplifier ?: -1) + 1
        return fov + speedLevel * 0.13f
    }

    internal val debugEnabled: Boolean get() = debug
    internal val markEnabled: Boolean get() = mark
    internal val markDurationMs: Int get() = markDuration
    internal val markFadeInMs: Int get() = markFadeIn
    internal val markSideColorValue: Color4b get() = markSideColor
    internal val markLineColorValue: Color4b get() = markLineColor

    internal fun debugStateLines(extra: List<String>): List<String> {
        val clientPlayer = mc.player ?: return listOf("NewScaffold DEBUG")
        val target = blockData?.let { "${it.pos.x},${it.pos.y},${it.pos.z}/${it.facing.name}" } ?: "none"
        val planned = blockData?.let { it.pos.offset(it.facing) }
            ?.let { "${it.x},${it.y},${it.z}" } ?: "none"
        val anchor = rescueAnchorPos?.let { formatVec(it) } ?: "none"
        val landing = currentRescueLanding()
            ?.let { "${it.x},${it.y},${it.z}" } ?: "none"
        val awaiting = rescueAwaitingPlacementPos?.let {
            "${it.x},${it.y},${it.z}(${rescueAwaitingPlacementTicks}t " +
                "seq=$rescueAwaitingSequence ack=${rescueLastAckSequence.get()} " +
                "update=$rescueServerBlockUpdateConfirmed)"
        } ?: "none"
        val virtualSupports = rescuePendingPlacements.count { !isStableRescueBlock(it.pos) }
        val velocity = clientPlayer.velocity
        return listOf(
            "NewScaffold DEBUG",
            "mode=$mode support=$target place=$planned",
            "state=${rescueController.state} generation=${rescueController.generation} " +
                "canPlace=$canPlace pending=$placePending clutch=$clutching",
            "fastBridge=${fastBridgeLandingTargetPos ?: "none"} prepared=$fastBridgePrepared",
            "reachable=$reachableState reason=$rescueReason rotHit=$rotationHitsTargetState",
            "anchor=$anchor landing=$landing",
            "chain=$rescueChainPlacements rescueTicks=$rescueTotalTicks " +
                "noTarget=$rescueNoTargetTicks planTick=$rescueLastPlanTick " +
                "planAge=${if (rescueLastPlanTick == Long.MIN_VALUE) "none" else debugTick - rescueLastPlanTick}",
            "forecastLinks=$rescuePlannedPlacements projectedFall=" +
                "${"%.2f".format(rescueProjectedFallDistance)} damage=$rescueEstimatedDamage",
            "slot=${rescueSlotLease?.hand}/${rescueSlotLease?.slot} switch=$rescueSlotSwitchTick",
            "await=$awaiting packets=$rescuePlacePacketsThisTick " +
                "pipeline=${rescuePendingPlacements.size}/$RESCUE_PIPELINE_LIMIT " +
                "virtual=$virtualSupports",
            "search=$rescueSearchState",
            "air=${clientPlayer.airTicks} ground=${clientPlayer.onGroundTicks} " +
                "fall=${"%.2f".format(clientPlayer.fallDistance)}",
            "pos=${formatVec(clientPlayer.pos)} vel=${formatVec(velocity)}",
            "outY=${if (lastOutgoingMovementY.isNaN()) "none" else "%.4f".format(lastOutgoingMovementY)} " +
                "outGround=$lastOutgoingMovementGround",
            "predY=${"%.2f".format(predictedYState)} dist=${"%.2f".format(targetDistanceState)} " +
                "placed=$placeCount last=$lastPlacementState",
            "interactionEye=${rescueLastInteractionEye?.let(::formatVec) ?: "none"} " +
                "movementEye=${rescueLastPredictedEye?.let(::formatVec) ?: "none"} " +
                "queue=${PacketQueueManager.lastRescueDrainReport.queuedMovementsBefore}->" +
                "${PacketQueueManager.lastRescueDrainReport.queuedMovementsAfter}"
        ) + extra
    }

    private fun debugLog(message: String) = debugSupport.log(message)

    private fun rescuePipelineSummary(): String = rescuePipeline.summary()

    @Suppress("unused")
    private val strafeHandler = handler<PlayerVelocityStrafe> {
        if (this.blockSlot == null || blockSlot!!.check()) {
            skipTick = false
            return@handler
        }
        if (rescueAnchorPos != null || rescueLandingTargetPos != null) {
            return@handler
        }
        if (player.onGroundTicks > (if (smoothed && safeMode && !testOnGround) 1 else 0)
            && !mc.options.jumpKey.isPressed
            && player.moving
            && mode == Mode.TELLY
        ) {
            when (jumpMode) {
                JumpMode.NONE -> {}
                JumpMode.NORMAL -> player.jump()
                JumpMode.PARKOUR -> {
                    val yaw = player.yaw.toRadians()
                    val forwardX = -sin(yaw)
                    val forwardZ = cos(yaw)

                    val frontPos1 = BlockPos(
                        (player.x + forwardX).toInt(),
                        (player.y - 0.1).toInt(),
                        (player.z + forwardZ).toInt()
                    )
                    val frontPos2 = BlockPos(
                        (player.x + forwardX * 2).toInt(),
                        (player.y - 0.1).toInt(),
                        (player.z + forwardZ * 2).toInt()
                    )

                    if (world.getBlockState(frontPos1).block is AirBlock ||
                        world.getBlockState(frontPos2).block is AirBlock
                    ) {
                        player.jump()
                    }
                }
            }


            if (eagle && mode == Mode.TELLY) {
                waitingForEagleSneak = true
                tellyJumpTicks = 0
            }
        }
    }

    private fun getBRot(forceRotation: Boolean): Rotation {
        var rotation: Rotation? = if (blockData != null) {
            RotationUtils.getClosestToBlockFace(
                blockData!!.pos,
                blockData!!.facing,
                RotationManager.serverRotation.yaw,
                RotationManager.serverRotation.pitch
            )
        } else null
        if (rotation == null) {
            rotation = if (RotationUtils.normalizeYawDiff(
                    player.yaw + 100f,
                    RotationManager.serverRotation.yaw
                ) < RotationUtils.normalizeYawDiff(
                    player.yaw - 100f, RotationManager.serverRotation.yaw
                )
            ) {
                Rotation(player.yaw + 100f, RotationManager.serverRotation.pitch)
            } else {
                Rotation(player.yaw - 100f, RotationManager.serverRotation.pitch)
            }
        }
        if (skipTick) {
            return RotationUtils.getClosestToBlockFace(
                blockData?.pos,
                blockData?.facing,
                RotationManager.serverRotation.yaw,
                RotationManager.serverRotation.pitch
            ) ?: Rotation(player.yaw, player.pitch)
        }
        val diff: Double = RotationUtils.yawDiffDirectly(rotation.yaw, RotationManager.serverRotation.yaw)
        if (mode == Mode.TELLY) {
            if (mc.options.jumpKey.isPressed && noUpTelly) {
                return rotation
            }
            if (mc.options.jumpKey.isPressed && randomSlow) {
                ups++
                if (ups % 2 == 0) {
                    return rotation
                }
            }
            if (smoothed && (player.airTicks < rotTick || safeMode)
            ) {
                if (player.onGroundTicks > 0) {
                    if (safeMode && (!testOnGround || mc.options.jumpKey.isPressed)) {
                        when (player.onGroundTicks) {
                            1 -> {
                                if (!forceRotation) {
                                    rotation.yaw = RotationManager.serverRotation.yaw + RotationUtils.smooth(
                                        diff.toFloat(),
                                        (diff / 2f).toFloat()
                                    )
                                    rotation.pitch = 75.5f
                                } else {
                                    rotation = RotationUtils.getClosestToBlockFace(
                                        blockData?.pos,
                                        blockData?.facing,
                                        player.yaw,
                                        RotationManager.serverRotation.pitch
                                    )
                                }
                                player.jumpingCooldown = 2
                            }

                            2 -> {
                                return Rotation(player.yaw, 75.5f)
                            }
                        }
                    } else {
                        return Rotation(player.yaw, 75.5f)
                    }
                } else {
                    var smooth = when (player.airTicks) {
                        1 -> 80f
                        else -> 50.0f
                    }
                    smooth -= (0.001f..0.005f).random()
                    rotation.yaw =
                        RotationManager.serverRotation.yaw + RotationUtils.smooth(diff.toFloat(), smooth)
                }
            } else {
                if (snap && mc.options.jumpKey.isPressed) {
                    if (lastBlockData == null || player.airTicks < rotTick) {
                        return Rotation(player.yaw, 85.0f + Math.random().toFloat())
                    }
                } else {
                    if (player.airTicks < rotTick) {
                        return Rotation(player.yaw, 85.0f + Math.random().toFloat())
                    }
                }
            }
        }
        if (lastRotation != null && blockData != null && didHitBlockFace(
                blockData,
                lastRotation!!,
                rescueInteractionEyeOrNull()
            )
        ) {
            return lastRotation!!
        }
        if (lastRotation != null && blockData != null && !alwaysUpdateRot && player.airTicks >= rotTick) {
            if (!didHitBlockFace(blockData, rotation!!, rescueInteractionEyeOrNull()) &&
                player.airTicks >= rotTick
            ) {
                lastRotation!!.yaw += Math.random().toFloat()
                return lastRotation!!
            }
        }
        lastRotation = rotation
        return rotation!!
    }

    @Suppress("LongMethod", "ReturnCount", "CognitiveComplexMethod", "NestedBlockDepth")
    // 发送当前方块并准备下一格
    private fun place() {
        val target = blockData ?: run {
            lastPlacementState = "skip:no-target"
            debugLog("place skipped: no target")
            return
        }
        val rescueActive = rescueController.active || rescueAnchorPos != null
        val fastBridgeActive = !rescueActive && fastBridgeLandingTargetPos != null
        val placementPos = target.pos.offset(target.facing)
        val landing = currentRescueLanding()
        if (rescueActive && placementPos == landing &&
            !NewScaffoldInterceptionMath.canCompleteAt(
                placementPos,
                player.pos,
                player.dimensions.width.toDouble(),
            )
        ) {
            lastPlacementState = "skip:missed-intercept"
            debugLog(
                "rescue final intercept missed landing=$placementPos feet=${formatVec(player.pos)} " +
                    "top=${placementPos.y + 1.0} chain=$rescueChainPlacements/" +
                    "$rescuePlannedPlacements; replanning lower"
            )
            abortRescue("missed-intercept", lockUntilGround = false)
            return
        }
        if (rescueActive && rescuePendingPlacements.size >= RESCUE_PIPELINE_LIMIT) {
            lastPlacementState = "skip:pipeline-full"
            debugLog(
                "place skipped: rescue pipeline full depth=${rescuePendingPlacements.size}/$RESCUE_PIPELINE_LIMIT " +
                    "await=$rescueAwaitingPlacementPos ticks=$rescueAwaitingPlacementTicks"
            )
            return
        }
        if (rescueActive && isRescuePlacementPending(placementPos)) {
            lastPlacementState = "skip:pending-target"
            debugLog(
                "place skipped: target already in rescue pipeline pos=$placementPos " +
                    "pending=${rescuePipelineSummary()}"
            )
            return
        }
        if (!canPlace) {
            lastPlacementState = "skip:canPlace=false"
            return
        }
        val selectedBlockSlot = blockSlot ?: run {
            lastPlacementState = "skip:no-slot"
            debugLog("place skipped: no block slot")
            return
        }
        val rotation = if (rescueActive) {
            rescueServerRotation()
        } else {
            RotationManager.currentRotation ?: RotationManager.serverRotation
        }
        val block = getPlacementHitResult(rotation) ?: run {
            lastPlacementState = "skip:no-hit"
            debugLog("place skipped: no hit result target=$target rotation=$rotation")
            return
        }

        val yawDelta = lastMovementYawDelta
        val repeatsLargeDelta = movementRotationPending && yawDelta > 2f &&
            lastPlacedYawDelta?.let { abs(yawDelta - it) < 0.0001f } == true
        if (duplicateRotPlace && !rescueActive && repeatsLargeDelta) {
            nudgeRotationForDuplicatePlace()
            lastPlacementState = "skip:duplicate-rotation"
            debugLog("place delayed by duplicate rotation yawDelta=${"%.3f".format(yawDelta)}")
            return
        }
        selectBlockSlot()
        if (rescueActive && rescueSlotSwitchTick >= debugTick) {
            lastPlacementState = "skip:slot-switch-cooldown"
            debugLog(
                "rescue placement delayed after slot switch tick=$debugTick " +
                    "switchTick=$rescueSlotSwitchTick slot=${selectedBlockSlot.slot}"
            )
            return
        }
        if (abuseRotation && !clutching) {
            rotationAbuse(30f, rotation.yaw)
        }
        if (interactItem && !clutching) {
            interaction.interactItem(player, selectedBlockSlot.hand, rotation.yaw, rotation.pitch)
        }
        val phase = if (movementPacketSentThisTick) "post-move" else "pre-move"
        val serverHitsTarget = didHitBlockFace(
            target,
            rescueServerRotation(),
            if (rescueActive) rescueEyePos() else null
        )
        if (rescueActive) {
            if (rescuePlacePacketsThisTick >= 1) {
                lastPlacementState = "skip:multi-place-guard"
                debugLog(
                    "INVARIANT multi-place blocked tick=$debugTick support=${target.pos} place=$placementPos " +
                        "packets=$rescuePlacePacketsThisTick"
                )
                return
            }
            rescuePlacementId++
            rescueLastSentSequence = -1
        }
        val stackBefore = if (selectedBlockSlot.hand == Hand.OFF_HAND) {
            player.offHandStack.count
        } else {
            player.inventory.getStack(selectedBlockSlot.slot).count
        }
        rescueInteractionInProgress = rescueActive
        val result = try {
            interaction.interactBlock(player, selectedBlockSlot.hand, block)
        } finally {
            rescueInteractionInProgress = false
        }
        val stackAfter = if (selectedBlockSlot.hand == Hand.OFF_HAND) {
            player.offHandStack.count
        } else {
            player.inventory.getStack(selectedBlockSlot.slot).count
        }
        val placementEye = rescueEyePos()
        val hitDistance = placementEye.distanceTo(block.pos)
        val rescuePacketSent = rescueActive && rescueLastSentSequence >= 0 &&
            phase == "pre-move" && serverHitsTarget
        val placementAttemptValid = if (rescueActive) rescuePacketSent else result.isAccepted
        if (placementAttemptValid) {
            placeCount++
            lastPlacementState = if (result.isAccepted) {
                "accepted:${placementPos.x},${placementPos.y},${placementPos.z}"
            } else {
                "sent:${placementPos.x},${placementPos.y},${placementPos.z}"
            }
            debugLog(
                "place packet id=${if (rescueActive) rescuePlacementId else "normal"} " +
                    "clientResult=${if (result.isAccepted) "accepted" else "sent-only"} " +
                    "support=${target.pos} place=$placementPos face=${target.facing} " +
                    "hit=${block.blockPos} side=${block.side} result=$result " +
                    "rescue=$rescueActive generation=${rescueController.generation} phase=$phase " +
                        "serverHit=$serverHitsTarget " +
                    "hand=${selectedBlockSlot.hand} slot=${selectedBlockSlot.slot} count=$stackBefore->$stackAfter " +
                    "eye=${formatVec(placementEye)} hitDistance=${"%.3f".format(hitDistance)} " +
                    "reach=${"%.3f".format(player.blockInteractionRange)} " +
                    "rotation=${rotation.yaw},${rotation.pitch} serverRotation=${RotationManager.serverRotation}"
            )
            if (rescueActive) {
                if (rescueLastSentSequence < 0) {
                    debugLog(
                        "rescue pipeline enqueue skipped: no outgoing sequence " +
                            "id=$rescuePlacementId place=$placementPos"
                    )
                    abortRescue("missing-interact-sequence", lockUntilGround = true)
                    return
                }
                if (!rescuePipeline.enqueue(
                        id = rescuePlacementId,
                        pos = placementPos,
                        sequence = rescueLastSentSequence,
                        generation = rescueController.generation
                    )) {
                    debugLog(
                        "rescue pipeline enqueue rejected/full id=$rescuePlacementId " +
                            "generation=${rescueController.generation} depth=${rescuePendingPlacements.size}"
                    )
                    abortRescue("pipeline-full-at-send", lockUntilGround = true)
                    return
                }
                rescueController.markPlacing()
                rescueChainPlacements++
                rescueRotationTimeouts = 0
                preparedRescueData = null
                preparedRescueRotation = null
                rotateCount = 0
                debugLog(
                    "rescue pipeline enqueue id=$rescuePlacementId sequence=$rescueAwaitingSequence " +
                        "depth=${rescuePendingPlacements.size}/$RESCUE_PIPELINE_LIMIT place=$placementPos " +
                        "immediateState=${world.getBlockState(placementPos).block} " +
                        "landing=${currentRescueLanding()} anchor=$rescueAnchorPos " +
                        "generation=${rescueController.generation}"
                )
            }
            if (movementRotationPending) {
                lastPlacedYawDelta = yawDelta
                movementRotationPending = false
            }
            if (mark) {
                debugSupport.mark(blockData!!.pos.offset(blockData!!.facing))
            }
            if (noSwing) {
                network.sendPacket(HandSwingC2SPacket(selectedBlockSlot.hand))
            } else {
                player.swingHand(selectedBlockSlot.hand)
            }

            if (rescueActive && rescueAnchorPos != null) {
                val landing = currentRescueLanding() ?: getRescueLandingPos(rescueAnchorPos!!)
                val pipelineEye = predictedRescueEye()
                val nextPrepared = primeNextBridgeTarget(target, landing, pipelineEye, rescue = true)
                if (!nextPrepared) {
                    debugLog(
                        "rescue preaim deferred current=$placementPos landing=$landing " +
                            "depth=${rescuePendingPlacements.size}/$RESCUE_PIPELINE_LIMIT " +
                            "state=${world.getBlockState(placementPos).block}"
                    )
                }
            } else if (fastBridgeActive) {
                val landing = fastBridgeLandingTargetPos ?: return
                if (placementPos == landing) {
                    debugLog("fast bridge complete landing=$landing")
                    clearFastBridgeState()
                } else {
                    // 当前交互成功后马上瞄准下一格 让本刻的移动包提前携带旋转
                    val nextEye = rescueController.predict(player, ignoreInput = false).afterOne.eye
                    if (!primeNextBridgeTarget(target, landing, nextEye, rescue = false)) {
                        debugLog("fast bridge preaim ended current=$placementPos landing=$landing")
                        clearFastBridgeState()
                    }
                }
            }
        } else {
            lastPlacementState = "rejected:$result"
            debugLog(
                "place rejected id=${if (rescueActive) rescuePlacementId else "normal"} result=$result " +
                    "support=${target.pos} place=$placementPos hit=${block.blockPos} side=${block.side} " +
                    "phase=$phase serverHit=$serverHitsTarget count=$stackBefore->$stackAfter " +
                    "pos=${"%.2f %.2f %.2f".format(
                        player.x, player.y, player.z
                    )}"
            )
            if (rescueActive && rescueLastSentSequence < 0) {
                debugLog(
                    "rescue aborted: interact packet was cancelled or queued id=$rescuePlacementId " +
                        "place=$placementPos clientResult=$result"
                )
                abortRescue("interact-cancelled", lockUntilGround = true)
            }
        }
    }

    // 发包前用真实眼位再次确认目标面
    private fun getPlacementHitResult(rotation: Rotation): BlockHitResult? {
        val target = blockData ?: return null
        val interactionEye = player.eyePos
        val rescueActive = rescueAnchorPos != null || rescueController.active
        val traced = if (rescueActive) {
            ClientRayTraceUtil.getFacedBlock(
                player,
                rotation.yaw,
                rotation.pitch,
                interactionEye,
                player.blockInteractionRange.toDouble()
            )
        } else {
            ClientRayTraceUtil.getFacedBlock(rotation.yaw, rotation.pitch)
        }
        val exactHit = if (rescueActive) {
            rescuePlanner.pendingAwareHit(target, rotation, interactionEye)
        } else {
            traced?.takeIf { it.blockPos == target.pos && it.side == target.facing }
        }
        if (exactHit != null) {
            if (ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(target.pos)) &&
                isRescuePlacementPending(target.pos)
            ) {
                debugLog(
                    "rescue using virtual support=${target.pos} face=${target.facing} " +
                        "hit=${formatVec(exactHit.pos)} pending=${rescuePipelineSummary()}"
                )
            }
            return exactHit
        }

        if (rescueAnchorPos != null) {
            val eye = interactionEye
            val closestHit = getClosestPointOnFace(target, eye)
            debugLog(
                "rescue strict ray changed before place support=${target.pos} " +
                    "place=${target.pos.offset(target.facing)} expectedFace=${target.facing} " +
                    "actual=${traced?.blockPos}/${traced?.side} eye=${formatVec(eye)} " +
                    "closestHit=${formatVec(closestHit)} " +
                    "closestDistance=${"%.3f".format(eye.distanceTo(closestHit))} " +
                    "reach=${player.blockInteractionRange} rotation=$rotation"
            )
        }
        return null
    }

    private fun nudgeRotationForDuplicatePlace() {
        val current = RotationManager.currentRotation ?: return
        val blockData = blockData ?: return
        val previousDelta = lastPlacedYawDelta ?: return
        val step = RotationUtil.gcd.toFloat().coerceAtLeast(0.001f)

        for (multiplier in intArrayOf(1, -1, 2, -2, 3, -3)) {
            val candidate = current.copy(
                yaw = current.yaw + step * multiplier,
                isNormalized = true
            )
            val candidateDelta = abs(
                RotationUtils.yawDiffDirectly(candidate.yaw, lastMovementPacketYaw).toFloat()
            )
            if (abs(candidateDelta - previousDelta) >= 0.0001f &&
                didHitBlockFace(blockData, candidate)
            ) {
                current.yaw = candidate.yaw
                current.isNormalized = true
                return
            }
        }
    }

    @Suppress("unused")
    private val rescueItemPacketGuard = handler<PacketEvent>(
        priority = EventPriorityConvention.SAFETY_FEATURE
    ) { event ->
        if (event.origin != TransferOrigin.OUTGOING || event.isCancelled) return@handler
        val packet = event.packet as? PlayerInteractItemC2SPacket ?: return@handler
        if (rescueAnchorPos == null && rescueLandingTargetPos == null) return@handler
        event.cancelEvent()
        debugLog(
            "outgoing interact-item cancelled during rescue tick=$debugTick " +
                "sequence=${packet.sequence} hand=${packet.hand} " +
                "anchor=$rescueAnchorPos landing=$rescueLandingTargetPos"
        )
    }

    @Suppress("unused")
    private val rescueSilentSlotGuard = handler<SelectHotbarSlotSilentlyEvent>(
        priority = EventPriorityConvention.SAFETY_FEATURE
    ) { event ->
        if (!rescueController.active || event.requester === this) return@handler
        event.cancelEvent()
        debugLog(
            "rescue blocked foreign silent slot requester=${event.requester?.javaClass?.simpleName} " +
                "requested=${event.slot} lease=${rescueSlotLease?.hand}/${rescueSlotLease?.slot}"
        )
    }

    @Suppress("unused", "ComplexCondition")
    // 记录真正发出的移动包和旋转
    private val movementPacketHandler = handler<PacketEvent>(priority = Short.MIN_VALUE) { event ->
        if (event.isCancelled) {
            if (debug && event.origin == TransferOrigin.OUTGOING &&
                (rescueAnchorPos != null || rescueLandingTargetPos != null) &&
                event.packet is PlayerMoveC2SPacket
            ) {
                debugLog(
                    "outgoing movement already cancelled tick=$debugTick " +
                        "packet=${event.packet::class.simpleName} current=${RotationManager.currentRotation}"
                )
            }
            return@handler
        }

        val rescueIncomingTracked = rescueAnchorPos != null || rescueLandingTargetPos != null ||
            rescueSlotReleasePending || rescueController.attemptLockedUntilGround
        if (event.origin == TransferOrigin.INCOMING && rescueIncomingTracked) {
            val packet = event.packet
            val expectedGeneration = rescueController.generation
            if (mc.isOnThread) {
                handleRescueIncomingPacket(packet, expectedGeneration)
            } else {
                mc.execute { handleRescueIncomingPacket(packet, expectedGeneration) }
            }
            return@handler
        }

        if (event.origin != TransferOrigin.OUTGOING) return@handler
        when (val packet = event.packet) {
            is PlayerInteractBlockC2SPacket -> {
                val target = blockData
                val hit = packet.blockHitResult
                val rescueOrLanding = rescueAnchorPos != null || rescueLandingTargetPos != null
                val matchesTarget = target != null &&
                    hit.blockPos == target.pos && hit.side == target.facing
                val allowRescuePacket = rescueAnchorPos != null && rescueInteractionInProgress &&
                    matchesTarget && rescuePlacePacketsThisTick == 0
                if (rescueOrLanding && !allowRescuePacket) {
                    event.cancelEvent()
                    debugLog(
                        "INVARIANT outgoing block packet cancelled tick=$debugTick " +
                            "sequence=${packet.sequence} support=${hit.blockPos} side=${hit.side} " +
                            "expected=${target?.pos}/${target?.facing} inPlace=$rescueInteractionInProgress " +
                            "alreadySent=$rescuePlacePacketsThisTick landing=$rescueLandingTargetPos"
                    )
                    return@handler
                }
                if (allowRescuePacket) {
                    rescuePlacePacketsThisTick++
                    rescueLastSentSequence = packet.sequence
                    debugLog(
                            "outgoing rescue interact tick=$debugTick generation=${rescueController.generation} " +
                                "id=$rescuePlacementId " +
                            "sequence=${packet.sequence} support=${hit.blockPos} " +
                            "place=${hit.blockPos.offset(hit.side)} side=${hit.side} hand=${packet.hand}"
                    )
                }
            }

            is PlayerMoveC2SPacket -> {
                movementPacketSentThisTick = true
                if (debug && (rescueAnchorPos != null || rescueLandingTargetPos != null)) {
                    debugLog(
                        "outgoing movement flags tick=$debugTick changePosition=${packet.changePosition} " +
                            "changeLook=${packet.changeLook} yaw=${packet.yaw} pitch=${packet.pitch} " +
                            "current=${RotationManager.currentRotation} server=${RotationManager.serverRotation}"
                    )
                }
                if (packet.changePosition) {
                    lastOutgoingMovementY = packet.y
                    lastOutgoingMovementGround = packet.onGround
                    if (debug && (rescueAnchorPos != null || rescueLandingTargetPos != null)) {
                        debugLog(
                            "outgoing movement tick=$debugTick pos=${"%.3f %.3f %.3f".format(
                                packet.x, packet.y, packet.z
                            )} y=${"%.4f".format(packet.y)} ground=${packet.onGround} " +
                                "actual=${formatVec(player.pos)} velocity=${formatVec(player.velocity)} " +
                                "landingY=${rescueLandingTargetPos?.y ?: "none"}"
                        )
                    }
                }
                if (!packet.changeLook) return@handler

                lastMovementYawDelta = abs(
                    RotationUtils.yawDiffDirectly(packet.yaw, lastMovementPacketYaw).toFloat()
                )
                lastMovementPacketYaw = packet.yaw
                lastMovementPacketPitch = packet.pitch
                movementRotationPending = true
            }
        }
    }

    @Suppress("unused")
    private val inputHandleHandler = handler<InputHandleEvent> {
        if ((rescueAnchorPos != null || fastBridgePrepared) && preparedRescueData != null && rot != null) {
            RotationManager.currentRotation = rot
            if (debug && rescueAnchorPos != null) {
                debugLog(
                    "rescue rotation commit tick=$debugTick rotation=${rot!!.yaw},${rot!!.pitch} " +
                        "pos=${formatVec(player.pos)} velocity=${formatVec(player.velocity)}"
                )
            }
        }
        if (placePending) {
            placePending = false
            place()
        }
    }


    @Suppress("CognitiveComplexMethod")
    private fun selectBlockSlot(forceRescueLeaseRefresh: Boolean = false) {
        // 普通搭路可按配置换槽 自救期间必须始终复用 lease
        val rescue = rescueController.active || rescueAnchorPos != null
        val selected = (if (rescue) rescueSlotLease else null) ?: blockSlot ?: return
        if (selected.hand != Hand.MAIN_HAND) {
            return
        }

        if (spoofItem) {
            val previousServerSlot = SilentHotbar.serversideSlot
            val ownsLease = SilentHotbar.isSlotModifiedBy(this) &&
                previousServerSlot == selected.slot
            if (forceRescueLeaseRefresh || !ownsLease) {
                SilentHotbar.selectSlotSilently(this, selected.slot, if (rescue) 64 else 2)
                if (rescue && previousServerSlot != selected.slot) {
                    rescueSlotSwitchTick = debugTick
                }
            }
        } else {
            SilentHotbar.resetSlot(this)
            if (player.inventory.selectedSlot != selected.slot) {
                player.inventory.selectedSlot = selected.slot
                if (rescue) rescueSlotSwitchTick = debugTick
            }
        }
        interaction.syncSelectedSlot()
    }

    @Suppress("unused", "ComplexCondition")
    // 每个客户端刻完成搜索、预测和旋转提交
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        if (player.isDead || player.isSpectator) {
            val rescueStatePresent = rescueController.active || rescueAnchorPos != null ||
                rescueLandingTargetPos != null || rescueLandingLockPos != null ||
                rescueSlotLease != null || rescuePendingPlacements.isNotEmpty()
            if (rescueStatePresent) {
                debugLog(
                    "rescue cleared: invalid player dead=${player.isDead} spectator=${player.isSpectator}"
                )
                abortRescue("invalid-player", lockUntilGround = false)
            }
            if (player.isSpectator) enabled = false
            return@handler
        }
        if (rescuePipelineRollbackPending) {
            val rollbackAnchor = rescueAnchorPos ?: rescueLandingLockPos
            debugLog(
                "rescue aborted after confirmed block rollback anchor=$rollbackAnchor " +
                    "pending=${rescuePipelineSummary()}"
            )
            abortRescue("pipeline-rollback", lockUntilGround = true)
            ClientRayTraceUtil.updateEyePos()
            debugTick++
            return@handler
        }
        if (rescueServerCorrectionPending &&
            rescueServerCorrectionGeneration == rescueController.generation
        ) {
            val descentLanding = rescueLandingTargetPos
            val landingCenter = descentLanding?.let { landing ->
                Vec3d(landing.x + 0.5, player.y, landing.z + 0.5)
            }
            val correctionCompletesDescent = rescueController.state == RescueSessionState.DESCENDING &&
                descentLanding != null && isStableRescueBlock(descentLanding) && landingCenter != null &&
                horizontalDistance(player.pos, landingCenter) <= 1.25 &&
                player.y >= descentLanding.y + 0.5 && player.y <= descentLanding.y + 3.0
            if (correctionCompletesDescent) {
                debugLog(
                    "rescue descent accepted server correction landing=$descentLanding " +
                        "correctedPos=${formatVec(player.pos)} velocity=${formatVec(player.velocity)}"
                )
                rescueServerCorrectionPending = false
                rescueServerCorrectionGeneration = -1L
                rescueLandingLockPos = player.pos
                rescueReason = "descent-correction"
                ClientRayTraceUtil.updateEyePos()
                debugTick++
                return@handler
            }
            rescueAnchorPos?.let { anchor ->
                debugLog(
                    "rescue aborted after server correction oldAnchor=${formatVec(anchor)} " +
                        "correctedPos=${formatVec(player.pos)} velocity=${formatVec(player.velocity)}"
                )
            }
            abortRescue("server-correction", lockUntilGround = false)
            ClientRayTraceUtil.updateEyePos()
            debugTick++
            return@handler
        }
        if (rescueServerCorrectionPending) {
            rescueServerCorrectionPending = false
            rescueServerCorrectionGeneration = -1L
        }
        if (debug && (rescueAnchorPos != null || rescueLandingTargetPos != null) && debugTick % 4L == 0L) {
            val reference = rescueAnchorPos ?: rescueLandingLockPos
            debugLog(
                "natural rotation-pre tick=$debugTick reference=${reference?.let(::formatVec)} " +
                    "actual=${formatVec(player.pos)} velocity=${formatVec(player.velocity)} " +
                    "ground=${player.isOnGround} landing=$rescueLandingTargetPos"
            )
        }
        ClientRayTraceUtil.updateEyePos()
        debugTick++
        if (player.isOnGround) {
            rescueController.onGround()
            if (rescueAnchorPos == null && rescueLandingTargetPos == null) {
                if (rescueSlotReleasePending || rescueSlotLease != null) {
                    releaseRescueSlotLease()
                }
                PacketQueueManager.resetRescueDrain()
            }
        }
        if (rescueSlotReleasePending) {
            blockData = null
            lastBlockData = null
            placePending = false
            canPlace = false
            clutching = false
            skipTick = false
            reachableState = true
            rescueReason = "abort-slot-hold"
            if (debugTick % 4L == 0L) {
                debugLog(
                    "rescue abort slot held until ground slot=${rescueSlotLease?.slot} " +
                        "hand=${rescueSlotLease?.hand} pos=${formatVec(player.pos)}"
                )
            }
            return@handler
        }
        ageRescueConfirmedPlacements()
        movementPacketSentThisTick = false
        placePending = false
        rescuePlacePacketsThisTick = 0
        rescueInteractionInProgress = false
        clutching = false
        rescueReason = "none"
        if (rescueAnchorPos == null) rescueLandingTargetPos?.let { landingPos ->
            rescueLandingDescentTicks++
            val landingStable = isStableRescueBlock(landingPos)
            val lock = rescueLandingLockPos
            if (player.isOnGround && landingStable) {
                finishRescueLanding(landingPos, rescueLandingDescentTicks)
                return@handler
            }
            if (!landingStable || rescueLandingDescentTicks > 20) {
                debugLog(
                    "rescue landing descent aborted target=$landingPos stable=$landingStable " +
                        "ticks=$rescueLandingDescentTicks pos=${formatVec(player.pos)} " +
                        "velocity=${formatVec(player.velocity)}"
                )
                abortRescue("landing-descent-invalid", lockUntilGround = true)
                return@handler
            } else {
                blockData = null
                lastBlockData = null
                preparedRescueData = null
                preparedRescueRotation = null
                placePending = false
                canPlace = false
                clutching = true
                skipTick = true
                rescueReason = "landing-descent"
                reachableState = true
                debugLog(
                    "rescue landing descending target=$landingPos lock=${lock?.let(::formatVec)} " +
                        "pos=${formatVec(player.pos)} velocity=${formatVec(player.velocity)} " +
                        "ground=${player.isOnGround} ticks=$rescueLandingDescentTicks"
                )
                return@handler
            }
        }
        val rescuePlacing = (rescueController.active &&
            rescueController.state != RescueSessionState.DESCENDING) || rescueAnchorPos != null
        if (rescuePlacing) {
            if (!slotManager.isLeaseValid(rescueSlotLease)) {
                debugLog(
                    "rescue aborted: leased slot invalid slot=${rescueSlotLease?.slot} " +
                        "hand=${rescueSlotLease?.hand} pending=${rescuePendingPlacements.size}"
                )
                abortRescue("slot-empty", lockUntilGround = true)
                return@handler
            }
            this.blockSlot = rescueSlotLease
        } else {
            this.blockSlot = null

            if (slotManager.isValid(player.offHandStack)) {
                this.blockSlot = SlotData(99, Hand.OFF_HAND)
            }

            if (blockSlot == null && blockSlotMode != BlockSlotMode.MOST_BLOCKS) {
                val selectedSlot = if (SilentHotbar.isSlotModifiedBy(this)) {
                    SilentHotbar.serversideSlot
                } else {
                    player.inventory.selectedSlot
                }
                if (slotManager.isValid(player.inventory.getStack(selectedSlot))) {
                    this.blockSlot = SlotData(selectedSlot, Hand.MAIN_HAND)
                }
            }

            if (blockSlot == null) {
                val hotbarSlot = slotManager.normalHotbarSlot()
                if (hotbarSlot != -1) {
                    this.blockSlot = SlotData(hotbarSlot, Hand.MAIN_HAND)
                }
            }
        }

        val noValidBlockSlot = this.blockSlot == null || blockSlot!!.check()
        if (noValidBlockSlot && rescueAnchorPos == null) {
            blockData = null
            reachableState = false
            rescueReason = "no-block-slot"
            debugLog("tick=$debugTick no valid block slot")
            return@handler
        } else if (noValidBlockSlot) {
            blockData = null
            debugLog(
                "tick=$debugTick no valid block slot, preserving active rescue until ack/landing check " +
                    "anchor=$rescueAnchorPos await=$rescueAwaitingPlacementPos"
            )
        }
        if (player.isOnGround && rescueAnchorPos == null) {
            posY = floor(player.y - 1)
            preparedRescueData = null
            preparedRescueRotation = null
            clearFastBridgeState(clearPrepared = false)
        }

        if (mc.options.jumpKey.isPressed) {
            posY = player.blockY - 1.0
        }
        var rescueCompletedThisTick = false
        var rescueWaitingForConfirmation = false
        rescueAnchorPos?.let rescueState@ { anchor ->
            rescueTotalTicks++
            val landingPos = currentRescueLanding() ?: getRescueLandingPos(anchor)
            when (processRescuePipeline()) {
                RescuePipelineResult.ABORT -> {
                    debugLog(
                        "rescue aborted: pipeline rejection anchor=${formatVec(anchor)} " +
                            "landing=$landingPos"
                    )
                    abortRescue("pipeline-rejected", lockUntilGround = true)
                    rescueCompletedThisTick = true
                    return@rescueState
                }

                RescuePipelineResult.FULL -> {
                    rescueWaitingForConfirmation = true
                    debugLog(
                        "rescue pipeline full depth=${rescuePendingPlacements.size}/$RESCUE_PIPELINE_LIMIT " +
                            "await=$rescueAwaitingPlacementPos sequence=$rescueAwaitingSequence"
                    )
                }

                RescuePipelineResult.READY -> Unit
            }

            if (rescuePendingPlacements.any { it.pos == landingPos }) {
                rescueWaitingForConfirmation = true
            }

            val missingIntermediateSupport = rescuePendingPlacements.firstOrNull { pending ->
                pending.pos != landingPos && !isStableRescueBlock(pending.pos)
            }
            if (missingIntermediateSupport != null) {
                if (missingIntermediateSupport.waitTicks == 1 ||
                    missingIntermediateSupport.waitTicks % 4 == 0
                ) {
                    debugLog(
                        "rescue virtual support active pos=${missingIntermediateSupport.pos} " +
                            "sequence=${missingIntermediateSupport.sequence} " +
                            "wait=${missingIntermediateSupport.waitTicks} " +
                            "depth=${rescuePendingPlacements.size}/$RESCUE_PIPELINE_LIMIT"
                    )
                }
            }

            val landingState = world.getBlockState(landingPos)
            val landingAwaitingConfirmation = rescueAwaitingPlacementPos == landingPos
            val landingServerConfirmed = rescueLastConfirmedPlacementPos == landingPos
            if (isStableRescueBlock(landingPos) && !landingAwaitingConfirmation && landingServerConfirmed) {
                rescueLandingSolidTicks++
                rescueCompletedThisTick = true
                preparedRescueData = null
                preparedRescueRotation = null
                blockData = null
                debugLog(
                        "rescue landing stabilizing anchor=${formatVec(anchor)} landing=$landingPos " +
                            "state=${landingState.block} solidTicks=$rescueLandingSolidTicks/1 " +
                            "serverConfirmed=$landingServerConfirmed chainPlacements=$rescueChainPlacements"
                )
                if (rescueLandingSolidTicks >= 1) {
                    debugLog(
                        "rescue chain complete anchor=${formatVec(anchor)} landing=$landingPos " +
                            "totalTicks=$rescueTotalTicks chainPlacements=$rescueChainPlacements"
                    )
                    rescueLandingTargetPos = landingPos
                    rescueLandingLockPos = anchor
                    rescueLandingDescentTicks = 0
                    rescueAnchorPos = null
                    clearRescuePipelineState(clearWatch = false)
                    preparedRescueData = null
                    preparedRescueRotation = null
                    rescueNoTargetTicks = 0
                    rescueTotalTicks = 0
                    rescueRotationTimeouts = 0
                    rescueLastConfirmedPlacementPos = null
                    if (player.isOnGround) {
                        finishRescueLanding(landingPos, ticks = 0)
                        return@rescueState
                    }
                    rescueController.beginDescent()
                }
            } else if (rescueLandingSolidTicks > 0) {
                debugLog(
                    "rescue landing rollback/wait landing=$landingPos state=${landingState.block} " +
                        "awaitingConfirmation=$landingAwaitingConfirmation " +
                        "serverConfirmed=$landingServerConfirmed " +
                        "previousSolidTicks=$rescueLandingSolidTicks"
                )
                rescueLandingSolidTicks = 0
            }

            if (rescueTotalTicks > RESCUE_MAX_TICKS && rescueAnchorPos != null) {
                debugLog(
                    "rescue aborted: total timeout anchor=${formatVec(anchor)} landing=$landingPos " +
                        "totalTicks=$rescueTotalTicks confirmed=$rescueChainPlacements " +
                        "await=$rescueAwaitingPlacementPos search=$rescueSearchState"
                )
                abortRescue("total-timeout", lockUntilGround = true)
                rescueCompletedThisTick = true
            }
        }
        if (noValidBlockSlot && rescueAnchorPos != null &&
            !rescueWaitingForConfirmation && !rescueCompletedThisTick
        ) {
            debugLog(
                "rescue aborted: no blocks remain after confirmation anchor=$rescueAnchorPos " +
                "confirmed=$rescueChainPlacements landing=${currentRescueLanding()}"
            )
            abortRescue("no-blocks", lockUntilGround = true)
            return@handler
        }
        val possible: BlockData? = if (ClientRayTraceUtil.isIgnoredBlock(
                world.getBlockState(
                    BlockPos(
                        floor(player.x).toInt(),
                        floor(player.y).toInt(),
                        floor(player.z).toInt()
                    )
                )
            )
        ) blockFinder.findPlacement(
            BlockPos(
                floor(player.x).toInt(),
                posY.toInt(),
                floor(player.z).toInt()
            )
        ) else null
        blockData = possible

        lastBlockData = possible


        if (mode == Mode.NORMAL) {
            canPlace = true
        } else if (mode == Mode.SNAP) {
            canPlace = doesNotContainBlock()
        } else {
            canPlace = player.airTicks >= placeTick
            if (safeMode && testOnGround && !canPlace && mc.options.jumpKey.isPressed) {
                canPlace = player.onGroundTicks == 1
            }
        }

        selectBlockSlot()
        val rescuePrediction = rescueController.active || rescueAnchorPos != null || rescueLandingTargetPos != null
        val posePrediction = rescueController.predict(player, ignoreInput = rescuePrediction)
        var reachable = true
        val nextEyePos = posePrediction.afterOne.eye
        val rescuePlanningEye = nextEyePos
        rescueLastInteractionEye = posePrediction.current.eye
        rescueLastPredictedEye = rescuePlanningEye
        val predictedY = posePrediction.afterTwo.position.y
        predictedYState = predictedY
        val predictedLanding = rescueLandingTargetPos ?: getRescueLandingPos(posePrediction.afterTwo.position)
        val fallingNow = player.airTicks > 0 && player.velocity.y < RESCUE_FALL_TRIGGER
        val jumpBoostLevel = (player.getStatusEffect(StatusEffects.JUMP_BOOST)?.amplifier ?: -1) + 1
        val directGroundDrop = if (fallingNow) blockFinder.groundDropBlocks() else 0
        val naturalImpactFallDistance = if (directGroundDrop == null) {
            Float.POSITIVE_INFINITY
        } else {
            player.fallDistance + directGroundDrop
        }
        val damagingFall = directGroundDrop == null || NewScaffoldFallSafety.estimatedDamage(
            naturalImpactFallDistance,
            jumpBoostLevel,
        ) > 0
        val ordinaryPlacementPos = possible?.let { it.pos.offset(it.facing) }
        val ordinaryBridgeCanCatch = ordinaryPlacementPos != null &&
            player.fallDistance < RESCUE_ORDINARY_GRACE_FALL &&
            NewScaffoldInterceptionMath.canCompleteAt(
                ordinaryPlacementPos,
                posePrediction.afterOne.position,
                player.dimensions.width.toDouble(),
            )
        val rescueSearchEligible = rescueAnchorPos == null && rescueLandingTargetPos == null &&
            !rescueController.attemptLockedUntilGround && fallingNow && damagingFall &&
            !ordinaryBridgeCanCatch
        val bridgeCandidatePlan = if (rescueSearchEligible) {
            val forecast = rescueController.predictSequence(
                player,
                ticks = RESCUE_FORECAST_TICKS,
                ignoreInput = true,
            )
            rescuePlanner.bestInitialPlan(
                samples = forecast,
                currentFallDistance = player.fallDistance,
                jumpBoostLevel = jumpBoostLevel,
            )
        } else {
            null
        }
        val initialRescuePlan = bridgeCandidatePlan?.takeIf { it.estimatedDamage > 0 }
        val fastBridgePlan = bridgeCandidatePlan?.takeIf { it.estimatedDamage == 0 }
        val initialPlanEligible = initialRescuePlan != null

        // 零伤害方案属于普通搭路 记录落点但绝不进入 ARMED 或 Clutching
        if (fastBridgePlan != null && rescueAnchorPos == null && !rescueController.active) {
            // 一条桥开始后固定落点 防止玩家水平漂移令路径每刻左右跳动
            if (fastBridgeLandingTargetPos == null) {
                fastBridgeLandingTargetPos = fastBridgePlan.landing
            }
        }
        if (rescueSearchEligible && bridgeCandidatePlan == null && debugTick % 2L == 0L) {
            debugLog(
                "initial intercept unavailable pos=${formatVec(player.pos)} " +
                    "velocity=${formatVec(player.velocity)} fall=${"%.2f".format(player.fallDistance)} " +
                    "groundDrop=${directGroundDrop ?: "void"} " +
                    "forecastTicks=$RESCUE_FORECAST_TICKS search={$rescueSearchState}"
            )
        }
        if (fallingNow && damagingFall && ordinaryBridgeCanCatch && debugTick % 2L == 0L) {
            debugLog(
                "rescue deferred to normal bridge place=$ordinaryPlacementPos " +
                    "fall=${"%.2f".format(player.fallDistance)} velocityY=${"%.3f".format(player.velocity.y)}"
            )
        }
        if (fastBridgePlan != null && debugTick % 2L == 0L) {
            debugLog(
                "fast bridge plan landing=${fastBridgePlan.landing} links=${fastBridgePlan.placementCount} " +
                    "fall=${"%.2f".format(fastBridgePlan.projectedFallDistance)}"
            )
        }
        val rescuePos = initialRescuePlan?.landing ?: fastBridgePlan?.landing ?: predictedLanding
        val preparedWasFastBridge = fastBridgePrepared
        val retainedPlacement = preparedRescueData?.takeIf {
            isPreparedRescueUsable(it, rescueEyePos())
        }
        val retainedFastBridge = retainedPlacement != null && preparedWasFastBridge
        if (preparedRescueData != null && retainedPlacement == null) {
            debugLog(
                "prepared ${if (preparedWasFastBridge) "fast bridge" else "rescue"} target expired " +
                    "target=$preparedRescueData"
            )
            preparedRescueData = null
            preparedRescueRotation = null
            if (preparedWasFastBridge) clearFastBridgeState(clearPrepared = false)
        }
        if (fastBridgeLandingTargetPos == null && fastBridgePlan != null) {
            fastBridgeLandingTargetPos = fastBridgePlan.landing
        }
        val activeFastBridgePlan = fastBridgePlan?.takeIf {
            it.landing == fastBridgeLandingTargetPos
        }
        val placement: BlockData? = if (
            rescueCompletedThisTick || rescueWaitingForConfirmation
        ) {
            null
        } else {
            retainedPlacement ?: when {
                initialPlanEligible -> initialRescuePlan?.target ?: possible
                activeFastBridgePlan != null -> activeFastBridgePlan.target
                fastBridgeLandingTargetPos != null ->
                    getRescueBlockData(fastBridgeLandingTargetPos!!, rescuePlanningEye)
                rescueController.active || rescueAnchorPos != null ->
                    getRescueBlockData(rescuePos, rescuePlanningEye)
                else -> possible ?: if (!fallingNow) {
                    getRescueBlockData(rescuePos, rescuePlanningEye)
                } else {
                    null
                }
            }
        }
        if (placement != null) {
            rescueNoTargetTicks = 0
            blockData = placement
            lastBlockData = placement
        } else if (
            rescueController.active && rescuePendingPlacements.isNotEmpty() &&
            !rescueCompletedThisTick && !rescueWaitingForConfirmation &&
            rescuePendingPlacements.size >= RESCUE_PIPELINE_LIMIT
        ) {
            rescueNoTargetTicks = 0
            blockData = null
            lastBlockData = null
            if (debugTick % 4L == 0L) {
                debugLog(
                    "rescue chain waiting for pipeline support anchor=$rescueAnchorPos " +
                        "landing=$rescuePos depth=${rescuePendingPlacements.size}"
                )
            }
        } else if (rescueController.active && !rescueCompletedThisTick && !rescueWaitingForConfirmation) {
            rescueNoTargetTicks++
            blockData = null
            lastBlockData = null
            debugLog(
                "rescue chain waiting: no target anchor=$rescueAnchorPos landing=$rescuePos " +
                    "ticks=$rescueNoTargetTicks"
            )
            if (rescueNoTargetTicks >= RESCUE_MAX_NO_TARGET_TICKS) {
                debugLog(
                    "rescue aborted: chain target unavailable anchor=$rescueAnchorPos " +
                        "landing=$rescuePos ticks=$rescueNoTargetTicks " +
                        "pending=${rescuePipelineSummary()}"
                )
                abortRescue("chain-no-target", lockUntilGround = true)
                reachableState = true
                return@handler
            }
        } else if (rescueCompletedThisTick || rescueWaitingForConfirmation) {
            blockData = null
            lastBlockData = null
            canPlace = false
        }
        var forceRotation = false
        targetDistanceState = placement?.let {
            rescuePlanningEye.distanceTo(getClosestPointOnFace(it, rescuePlanningEye))
        } ?: 0.0
        if (placement != null) {
            if (safeMode && testOnGround && player.onGroundTicks == 1 && mc.options.jumpKey.isPressed) {
                forceRotation = true
            }
            val distance = targetDistanceState
            val anchorWasActive = rescueAnchorPos != null
            val falling = fallingNow
            val shouldArmEarly = !anchorWasActive &&
                !rescueController.attemptLockedUntilGround &&
                falling && initialPlanEligible && initialRescuePlan != null
            val retainedRescuePlacement = retainedPlacement != null && !retainedFastBridge
            val needsRotationPreparation = anchorWasActive || retainedRescuePlacement || shouldArmEarly
            val needsFastBridgePreparation = retainedFastBridge ||
                (fastBridgeLandingTargetPos != null && !shouldArmEarly)
            val ordinaryPlacementNeedsRotation = !falling &&
                (distance >= safeDistance || placement.pos.y > predictedY)
            if (needsRotationPreparation || needsFastBridgePreparation || ordinaryPlacementNeedsRotation) {
                canPlace = true
                // 普通快速桥不能清空移动输入 只有真正自救或普通距离保护才等待旋转
                if (!needsFastBridgePreparation || needsRotationPreparation || ordinaryPlacementNeedsRotation) {
                    reachable = false
                }
                if (!anchorWasActive && shouldArmEarly) {
                    clearFastBridgeState()
                    val queuedBeforeRescue = PacketQueueManager.packetQueue.size
                    val flushedControlBacklog = if (PacketQueueManager.isLagging) {
                        PacketQueueManager.flushOutgoingRescueControlBacklog()
                    } else {
                        0
                    }
                    val movementBaseline = if (PacketQueueManager.isLagging) {
                        PacketQueueManager.flushLatestOutgoingRescueMovementBaseline()
                    } else {
                        RescueMovementBaselineResult(0, 0)
                    }
                    clearOwnedEagleSneak()
                    rescueAnchorPos = player.pos
                    rescueLandingTargetPos = rescuePos
                    rescuePlannedPlacements = initialRescuePlan?.placementCount ?: 0
                    rescueProjectedFallDistance = initialRescuePlan?.projectedFallDistance ?: player.fallDistance
                    rescueEstimatedDamage = initialRescuePlan?.estimatedDamage ?: 0
                    rescueController.arm()
                    if (queuedBeforeRescue > 0 || movementBaseline.dropped > 0 ||
                        movementBaseline.flushed > 0 || flushedControlBacklog > 0
                    ) {
                        debugLog(
                            "rescue backlog reconciled queue=$queuedBeforeRescue " +
                                "movementBaseline=${movementBaseline.flushed} " +
                                "movementDropped=${movementBaseline.dropped} " +
                                "controlFlushed=$flushedControlBacklog " +
                                "generation=${rescueController.generation}"
                        )
                    }
                    if (!spoofItem) {
                        SilentHotbar.resetSlot(this)
                    }
                    rescueSlotLease = slotManager.chooseRescueLease()
                    if (rescueSlotLease == null) {
                        abortRescue("no-slot", lockUntilGround = true)
                        return@handler
                    }
                    rescueSlotSwitchTick = Long.MIN_VALUE
                    selectBlockSlot(forceRescueLeaseRefresh = true)
                    rescueLastPlanTick = debugTick
                    rescueNoTargetTicks = 0
                    rescueChainPlacements = 0
                    rescueTotalTicks = 0
                    rescueRotationTimeouts = 0
                    rescueLandingSolidTicks = 0
                    clearRescuePipelineState()
                    rescueSearchState = "engaged"
                    debugLog(
                        "rescue anchor engaged anchor=${formatVec(rescueAnchorPos!!)} " +
                            "landing=$rescueLandingTargetPos " +
                            "support=${placement.pos} place=${placement.pos.offset(placement.facing)} " +
                            "face=${placement.facing} eye=${formatVec(rescueEyePos())} " +
                            "velocity=${formatVec(player.velocity)} " +
                            "slotLease=${rescueSlotLease?.hand}/${rescueSlotLease?.slot} " +
                            "forecastLinks=$rescuePlannedPlacements " +
                            "projectedFall=${"%.2f".format(rescueProjectedFallDistance)} " +
                            "damage=$rescueEstimatedDamage"
                    )
                }
                rescueReason = when {
                    retainedFastBridge -> "fast-bridge-ready"
                    needsFastBridgePreparation && !needsRotationPreparation -> "fast-bridge-preaim"
                    retainedPlacement != null -> "rotation-wait"
                    anchorWasActive -> "chain-extend"
                    needsRotationPreparation -> "falling-target"
                    distance >= safeDistance -> "distance"
                    else -> "predicted-height"
                }
                if (preparedRescueData != placement) {
                    preparedRescueData = placement
                    preparedRescueRotation = null
                    rescueLastPlanTick = debugTick
                }
                fastBridgePrepared = needsFastBridgePreparation && !needsRotationPreparation
                lastBlockData = placement
                blockData = lastBlockData
            }
        } else if (
            rescueAnchorPos != null && rescuePendingPlacements.isNotEmpty() &&
            !rescueCompletedThisTick && !rescueWaitingForConfirmation
        ) {
            canPlace = false
            reachable = false
            rotateCount = 0
            rescueReason = "pipeline-wait"
        } else if (rescueAnchorPos != null && !rescueCompletedThisTick && !rescueWaitingForConfirmation) {
            canPlace = false
            reachable = false
            rescueReason = "chain-search"
        } else if (rescueWaitingForConfirmation) {
            canPlace = false
            reachable = false
            rotateCount = 0
            rescueReason = "await-block-confirmation"
        } else if (rescueCompletedThisTick) {
            canPlace = false
            reachable = true
            rescueReason = "landing-stabilize"
        }
        if (blockData != null && preparedRescueData == null) {
            val box = Box(this.blockData!!.pos)
                .withMinY(this.blockData!!.pos.y - 1.0)
                .withMaxY(this.blockData!!.pos.y + 1.0)
            if (blockData!!.pos.y > predictedY && !box.contains(player.pos)) {
                canPlace = true
                reachable = false
                posY = player.blockY - 1.0
                val fallback = getRescueBlockData(
                    BlockPos(
                        floor(player.x).toInt(),
                        floor(posY).toInt(),
                        floor(player.z).toInt()
                    ),
                    rescuePlanningEye
                )
                if (fallback != null) {
                    lastBlockData = fallback
                    blockData = fallback
                } else {
                    blockData = null
                    lastBlockData = null
                    canPlace = false
                    reachable = true
                    rescueReason = "no-local-target"
                }
            }
        }
        if (!reachable && blockData != null && !rescueWaitingForConfirmation &&
            rescuePendingPlacements.isEmpty() && rotateCount >= 8
        ) {
            if (rescueAnchorPos != null) rescueRotationTimeouts++
            debugLog(
                "rescue rotation timed out target=$blockData timeout=$rescueRotationTimeouts/2 " +
                    "anchor=$rescueAnchorPos flyingRot=${lastMovementPacketYaw},${lastMovementPacketPitch}"
            )
            preparedRescueData = null
            preparedRescueRotation = null
            blockData = null
            lastBlockData = null
            canPlace = false
            reachable = rescueAnchorPos == null
            rescueReason = "rotation-timeout"
            rotateCount = 0
            if (rescueAnchorPos != null && rescueRotationTimeouts >= 2) {
                debugLog(
                    "rescue aborted: repeated rotation timeout anchor=$rescueAnchorPos " +
                        "search=$rescueSearchState"
                )
                abortRescue("rotation-abort", lockUntilGround = true)
                reachable = true
            }
        }
        if (reachable && rescueAnchorPos == null && rescueReason != "rotation-timeout") {
            rescueReason = "none"
        }
        reachableState = reachable
        // reachable 也会被普通距离保护置为 false 不能再把它当成自救标志
        clutching = rescueController.active || rescueAnchorPos != null || rescueLandingTargetPos != null
        if (clutching) {
            if (debug && rotateCount == 1) {
                notifyAsMessage(ModuleNewScaffold, "Clutching...")
            }
            if (rotateCount == 0) {
                debugLog(
                    "rescue start reason=$rescueReason target=$blockData " +
                        "distance=${"%.2f".format(targetDistanceState)} " +
                        "predictedY=${"%.2f".format(predictedY)}"
                )
            }
            skipTick = true
            rotateCount++
        } else {
            skipTick = false
            rotateCount = 0
        }
        rot = getBRot(forceRotation)
        if (duplicateRotPlace && rot != null) {
            rot!!.pitch -= (0.001f..0.003f).random()
            rot!!.yaw -= (0.0001f..0.0003f).random()
            do {
                rot!!.pitch -= (0.001f..0.003f).random()
            } while (rot!!.pitch > 90f)
            if (rot!!.pitch < -90f) {
                rot!!.pitch = -90f
            }
        }
        if (fixRotation) {
            rot = rot?.normalize()
        } else {
            rot?.isNormalized = true
        }
        val movementEye = if (
            rescueController.active || rescueAnchorPos != null || rescueLandingTargetPos != null || fastBridgePrepared
        ) {
            rescuePlanningEye
        } else {
            null
        }
        var rotationHitsTarget = didHitBlockFace(blockData, rot!!, movementEye)
        if (!rotationHitsTarget && blockData != null && (safeMode || !reachable)) {
            var centerRotation = Rotation.lookingAt(
                blockData!!.pos.toCenterPos().add(Vec3d.of(blockData!!.facing.vector).multiply(0.5)),
                movementEye ?: player.eyePos
            )
            centerRotation = RotationUtils.makeYawContinuous(
                centerRotation,
                RotationManager.serverRotation.yaw
            )
            if (fixRotation) {
                centerRotation = centerRotation.normalize()
            }
            if (didHitBlockFace(blockData, centerRotation, movementEye)) {
                rot = centerRotation
                lastRotation = centerRotation
                rotationHitsTarget = true
            }
        }
        if ((clutching || fastBridgePrepared) && preparedRescueData != null && blockData != null) {
            rot = preparedRescueRotation ?: getPredictiveRescueRotation(blockData!!, rescuePlanningEye).also {
                preparedRescueRotation = it
                rescueLastPlanTick = debugTick
            }
            rotationHitsTarget = didHitBlockFace(blockData, rot!!, rescuePlanningEye)
        }
        if (rotationHitsTarget && !clutching) {
            skipTick = false
            rotateCount = 0
        }
        rotationHitsTargetState = rotationHitsTarget
        val placementServerRotation = if (rescueAnchorPos != null || fastBridgePrepared) {
            rescueServerRotation()
        } else {
            RotationManager.serverRotation
        }
        val serverRotationHitsTarget = blockData?.let {
            didHitBlockFace(
                it,
                placementServerRotation,
                if (rescueAnchorPos != null || rescueController.active || fastBridgePrepared) rescueEyePos() else null
            )
        } == true
        if (rot != null) {
            RotationManager.setRotationTarget(
                RotationTarget(
                    rotation = rot!!,
                    ticksUntilReset = 1,
                    resetThreshold = 1f,
                    considerInventory = true,
                    movementCorrection = MovementCorrection.SILENT
                ),
                if (rescueAnchorPos != null) {
                    Priority.IMPORTANT_FOR_USER_SAFETY
                } else {
                    Priority.IMPORTANT_FOR_PLAYER_LIFE
                },
                ModuleNewScaffold
            )
            val placementRotationReady = if (rescueAnchorPos != null || fastBridgePrepared) {
                serverRotationHitsTarget
            } else {
                rotationHitsTarget
            }
            placePending = blockData != null && canPlace && blockSlot != null && placementRotationReady
        }
        if (debug && (debugTick % 2L == 0L || clutching || placePending)) {
            val plannedPos = blockData?.let { it.pos.offset(it.facing) }
            val anchorDrift = rescueAnchorPos?.let { horizontalDistance(player.pos, it) } ?: 0.0
            val serverRay = blockData?.let {
                if (rescueController.active || rescueAnchorPos != null) {
                    ClientRayTraceUtil.getFacedBlock(
                        player,
                        RotationManager.serverRotation.yaw,
                        RotationManager.serverRotation.pitch,
                        player.eyePos,
                        player.blockInteractionRange.toDouble()
                    )
                } else {
                    ClientRayTraceUtil.getFacedBlock(
                        RotationManager.serverRotation.yaw,
                        RotationManager.serverRotation.pitch
                    )
                }
            }
            debugLog(
                "tick=$debugTick pos=${"%.2f %.2f %.2f".format(player.x, player.y, player.z)} " +
                    "air=${player.airTicks} ground=${player.onGroundTicks} vel=${"%.3f %.3f %.3f".format(
                        player.velocity.x, player.velocity.y, player.velocity.z
                    )} support=${blockData?.pos} place=$plannedPos face=${blockData?.facing} " +
                    "landing=${currentRescueLanding()} await=$rescueAwaitingPlacementPos " +
                    "canPlace=$canPlace pending=$placePending clutch=$clutching " +
                    "reachable=$reachable reason=$rescueReason serverReady=$serverRotationHitsTarget " +
                    "rotHit=$rotationHitsTarget rot=${rot?.yaw},${rot?.pitch} " +
                    "serverRot=${RotationManager.serverRotation.yaw},${RotationManager.serverRotation.pitch} " +
                    "flyingRot=${lastMovementPacketYaw},${lastMovementPacketPitch} " +
                    "serverRay=${serverRay?.blockPos}/${serverRay?.side} " +
                    "horizontalDrift=${"%.6f".format(anchorDrift)} actualY=${"%.4f".format(player.y)} " +
                    "velocityY=${"%.4f".format(player.velocity.y)} rescueTicks=$rescueTotalTicks " +
                    "generation=${rescueController.generation} planAge=" +
                    "${if (rescueLastPlanTick == Long.MIN_VALUE) "none" else debugTick - rescueLastPlanTick} " +
                    "pipeline=${rescuePendingPlacements.size}/$RESCUE_PIPELINE_LIMIT " +
                    "pending=${rescuePipelineSummary()} search={$rescueSearchState}"
            )
        }
        if (blockData == null) return@handler
        if (player.isSpectator) {
            enabled = false
            return@handler
        }
        if (mode == Mode.TELLY) {
            return@handler
        }
        val rescueTracking = rescueController.active || rescueAnchorPos != null || rescueLandingTargetPos != null
        if (!rescueTracking && waitingForEagleSneak) {
            tellyJumpTicks++
            if (tellyJumpTicks == tellyEagleTick && !mc.options.sneakKey.isPressed) {
                mc.options.sneakKey.isPressed = true
                eagleSneakPressedByModule = true
            }
            if (tellyJumpTicks == tellyEagleTick + keepEagleSneakTick) {
                clearOwnedEagleSneak()
            }
        }
    }

    private fun didHitBlockFace(
        blockData: BlockData?,
        rot: Rotation,
        eye: Vec3d? = null
    ): Boolean = rescuePlanner.didHit(blockData, rot, eye)

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(
        priority = EventPriorityConvention.FINAL_DECISION
    ) { event ->
        if (clutching || rescueController.active) {
            event.directionalInput = DirectionalInput.NONE
        }
        val rescueTracking = rescueController.active || rescueAnchorPos != null || rescueLandingTargetPos != null
        if (!rescueTracking && mode == Mode.TELLY && eagle) {
            event.sneak = placeCount % 4 == 0
        }
    }

    @Suppress("unused")
    private val slowHandler = handler<PlayerUseMultiplier> {
        if (rescueController.active || rescueAnchorPos != null || rescueLandingTargetPos != null) {
            return@handler
        }
        if (player.onGroundTicks == 1 && testOnGround && smoothed && !noUpTelly && safeMode && mc.options.jumpKey.isPressed) {
            player.input.movementForward *= 0.2f
            player.input.movementSideways *= 0.2f
        }
    }

    private fun doesNotContainBlock(): Boolean {
        return blockFinder.blockRelativeToPlayer().defaultState.isTransparent
    }

    private fun rotationAbuse(step: Float, targetYaw: Float) {
        val appliedRotation = RotationManager.currentRotation ?: return
        val previousRotation = RotationManager.previousRotation ?: RotationManager.serverRotation
        val change = RotationUtils.yawDiffDirectly(targetYaw, previousRotation.yaw)
        val times = (abs(change) / step).toInt()
        var currentYaw = previousRotation.yaw

        repeat(times) {
            currentYaw += RotationUtils.smooth(change.toFloat(), step)
            appliedRotation.yaw = currentYaw
            interaction.interactItem(
                player,
                Hand.MAIN_HAND,
                currentYaw,
                appliedRotation.pitch
            )
        }
        appliedRotation.yaw = targetYaw
    }

    private fun getRescueLandingPos(anchor: Vec3d): BlockPos {
        return rescuePlanner.landingBelow(anchor)
    }

    private fun currentRescueLanding(): BlockPos? =
        rescueLandingTargetPos ?: rescueAnchorPos?.let(::getRescueLandingPos)

    private fun isRescuePlacementPending(pos: BlockPos): Boolean =
        rescuePendingPlacements.any { it.pos == pos }

    private fun getRescueBlockData(pos: BlockPos, eye: Vec3d = rescueEyePos()): BlockData? =
        rescuePlanner.findPlacement(pos, eye)

    private fun getImmediateRescueNextData(
        current: BlockData,
        landing: BlockPos,
        eye: Vec3d,
    ): BlockData? = rescuePlanner.immediateNext(current, landing, eye)

    @Suppress("ReturnCount")
    // 两种桥共用同一套逐格预瞄 普通桥不会写入自救管线
    private fun primeNextBridgeTarget(
        current: BlockData,
        landing: BlockPos,
        eye: Vec3d,
        rescue: Boolean,
    ): Boolean {
        if (rescue) {
            if (rescueAnchorPos == null || rescuePendingPlacements.size >= RESCUE_PIPELINE_LIMIT) {
                return false
            }
        } else if (fastBridgeLandingTargetPos != landing || rescueController.active) {
            return false
        }

        val currentPlaced = current.pos.offset(current.facing)
        if (currentPlaced == landing) return false
        val currentDx = currentPlaced.x - landing.x
        val currentDy = currentPlaced.y - landing.y
        val currentDz = currentPlaced.z - landing.z
        val currentDistance = currentDx * currentDx + currentDy * currentDy + currentDz * currentDz

        val next = getImmediateRescueNextData(current, landing, eye)
            ?: getRescueBlockData(landing, eye)?.takeIf { data ->
                val nextPos = data.pos.offset(data.facing)
                val dx = nextPos.x - landing.x
                val dy = nextPos.y - landing.y
                val dz = nextPos.z - landing.z
                nextPos != currentPlaced && dx * dx + dy * dy + dz * dz < currentDistance
            }
            ?: return false

        val nextRotation = getPredictiveRescueRotation(next, eye)
        if (!didHitBlockFace(next, nextRotation, eye)) return false

        preparedRescueData = next
        preparedRescueRotation = nextRotation
        fastBridgePrepared = !rescue
        rescueLastPlanTick = debugTick
        blockData = next
        lastBlockData = next
        rot = nextRotation
        lastRotation = nextRotation
        RotationManager.currentRotation = nextRotation
        debugLog(
            "${if (rescue) "rescue" else "fast bridge"} preaim next " +
                "support=${next.pos} place=${next.pos.offset(next.facing)} " +
                "face=${next.facing} rotation=${nextRotation.yaw},${nextRotation.pitch} " +
                "remaining=${next.pos.offset(next.facing).getSquaredDistance(landing)} " +
                "depth=${rescuePendingPlacements.size}/$RESCUE_PIPELINE_LIMIT"
        )
        return true
    }

    private fun isPreparedRescueUsable(
        data: BlockData,
        eye: Vec3d = rescueEyePos()
    ): Boolean {
        if ((rescueController.active || fastBridgePrepared) && rescueLastPlanTick != Long.MIN_VALUE &&
            debugTick - rescueLastPlanTick > 1L
        ) {
            return false
        }
        if (ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(data.pos)) &&
            !isRescuePlacementPending(data.pos)
        ) {
            return false
        }

        val placementPos = data.pos.offset(data.facing)
        if (isRescuePlacementPending(placementPos) ||
            !ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(placementPos))
        ) {
            return false
        }

        if (!isWithinRescueReach(data, eye)) return false

        return didHitBlockFace(data, rescueServerRotation(), eye)
    }

    private fun getPredictiveRescueRotation(data: BlockData, nextEyePos: Vec3d): Rotation =
        rescuePlanner.predictiveRotation(data, nextEyePos)

    private fun rescueEyePos(): Vec3d = player.eyePos

    private fun rescueInteractionEyeOrNull(): Vec3d? =
        if (rescueController.active || rescueAnchorPos != null || rescueLandingTargetPos != null) {
            player.eyePos
        } else {
            null
        }

    private fun predictedRescueEye(): Vec3d =
        rescueController.predict(player, ignoreInput = true).afterOne.eye

    private fun rescueServerRotation(): Rotation = Rotation(
        lastMovementPacketYaw,
        lastMovementPacketPitch,
        isNormalized = true
    )

    private fun getClosestPointOnFace(data: BlockData, eye: Vec3d, inset: Double = 0.02): Vec3d =
        rescuePlanner.closestPoint(data, eye, inset)

    private fun isWithinRescueReach(data: BlockData, eye: Vec3d): Boolean =
        rescuePlanner.withinReach(data, eye)

    private fun isStableRescueBlock(pos: BlockPos): Boolean = rescuePlanner.isStable(pos)

    // 玩家落地后结束整条链
    private fun finishRescueLanding(landing: BlockPos, ticks: Int) {
        debugLog(
            "rescue landing reached ground target=$landing pos=${formatVec(player.pos)} " +
                "ticks=$ticks ground=${player.isOnGround}"
        )
        clearFastBridgeState()
        rescueAnchorPos = null
        rescueLandingTargetPos = null
        rescueLandingLockPos = null
        rescueLandingDescentTicks = 0
        rescueLandingSolidTicks = 0
        preparedRescueData = null
        preparedRescueRotation = null
        rescueNoTargetTicks = 0
        rescueTotalTicks = 0
        rescueRotationTimeouts = 0
        clearRescuePipelineState()
        rescueController.finish()
        releaseRescueSlotLease()
        PacketQueueManager.resetRescueDrain()
        clutching = false
        skipTick = false
        blockData = null
        lastBlockData = null
        placePending = false
    }

    // 所有失败都从这里退出
    private fun abortRescue(reason: String, lockUntilGround: Boolean) {
        val deferSlotRelease = lockUntilGround && !player.isOnGround && rescueSlotLease != null
        debugLog(
            "rescue abort reason=$reason tick=$debugTick anchor=$rescueAnchorPos " +
                "landing=$rescueLandingTargetPos pending=${rescuePipelineSummary()} " +
                "deferSlotRelease=$deferSlotRelease"
        )
        clearFastBridgeState()
        rescueAnchorPos = null
        rescueLandingTargetPos = null
        rescueLandingLockPos = null
        rescueLandingDescentTicks = 0
        preparedRescueData = null
        preparedRescueRotation = null
        rescueNoTargetTicks = 0
        rescueTotalTicks = 0
        rescueRotationTimeouts = 0
        rescueLastPlanTick = Long.MIN_VALUE
        rescueLastInteractionEye = null
        rescueLastPredictedEye = null
        rescueServerCorrectionPending = false
        rescueServerCorrectionGeneration = -1L
        rescuePipeline.clearRollback()
        clearRescuePipelineState()
        blockData = null
        lastBlockData = null
        placePending = false
        clutching = false
        skipTick = false
        reachableState = true
        rescueReason = reason
        rescueController.abort(lockUntilGround)
        if (deferSlotRelease) {
            rescueSlotReleasePending = true
        } else {
            releaseRescueSlotLease()
        }
        PacketQueueManager.resetRescueDrain()
    }

    private fun clearRescuePipelineState(clearWatch: Boolean = true) =
        rescuePipeline.reset(clearWatch)

    private fun ageRescueConfirmedPlacements() = rescuePipeline.ageConfirmed()

    private fun formatVec(vec: Vec3d): String =
        "%.3f %.3f %.3f".format(vec.x, vec.y, vec.z)

    private fun horizontalDistance(first: Vec3d, second: Vec3d): Double {
        val dx = first.x - second.x
        val dz = first.z - second.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    private fun handleRescueIncomingPacket(packet: Packet<*>, expectedGeneration: Long) {
        // 网络线程捕获 generation 回到主线程后先丢弃过期消息
        if (expectedGeneration != rescueController.generation) {
            debugLog(
                "stale rescue incoming ignored packet=${packet::class.simpleName} " +
                    "packetGeneration=$expectedGeneration activeGeneration=${rescueController.generation}"
            )
            return
        }

        if (packet is PlayerRespawnS2CPacket) {
            debugLog(
                "rescue cooldown released by respawn generation=$expectedGeneration " +
                    "slot=${rescueSlotLease?.hand}/${rescueSlotLease?.slot}"
            )
            releaseRescueSlotLease()
            resetRescueState()
            return
        }

        if (packet is PlayerPositionLookS2CPacket &&
            rescueAnchorPos == null && rescueLandingLockPos == null
        ) {
            debugLog(
                "rescue post-abort correction generation=$expectedGeneration " +
                    "slot=${rescueSlotLease?.hand}/${rescueSlotLease?.slot}"
            )
            releaseRescueSlotLease()
            rescueController.reset()
            PacketQueueManager.resetRescueDrain()
            return
        }
        val anchor = rescueAnchorPos ?: rescueLandingLockPos ?: return
        when (packet) {
            is PlayerPositionLookS2CPacket -> {
                rescueServerCorrectionPending = true
                rescueServerCorrectionGeneration = rescueController.generation
                debugLog(
                    "server position correction tick=$debugTick anchor=${formatVec(anchor)} " +
                        "rescue will abort packet=${packet.toString().take(260)}"
                )
            }

            is PlayerActionResponseS2CPacket -> {
                val previousAck = rescueLastAckSequence.get()
                rescuePipeline.noteAck(packet.sequence)
                if (packet.sequence > previousAck) {
                    debugLog(
                        "server action ack tick=$debugTick sequence=${packet.sequence} " +
                            "awaitSequence=$rescueAwaitingSequence await=$rescueAwaitingPlacementPos " +
                            "lastSent=$rescueLastSentSequence"
                    )
                }
            }

            is BlockUpdateS2CPacket -> logRescueBlockUpdate(packet.pos, packet.state)
            is ChunkDeltaUpdateS2CPacket -> packet.visitUpdates { pos, state ->
                logRescueBlockUpdate(pos, state)
            }
        }
    }

    private fun logRescueBlockUpdate(pos: BlockPos, state: BlockState) {
        // 单方块更新和区块批量更新最终都走这里 保证回滚处理一致
        val anchor = rescueAnchorPos ?: rescueLandingLockPos ?: return
        val authoritative = state.isFullCube(world, pos)
        rescuePipeline.noteBlockUpdate(pos, authoritative)
        rescuePendingPlacements.firstOrNull { it.pos == pos }?.let { pending ->
            pending.authoritative = authoritative
            if (pos == rescueAwaitingPlacementPos) {
                rescueServerBlockUpdateConfirmed = pending.authoritative == true
            }
        }
        rescueConfirmedPlacements[pos]?.let {
            if (authoritative) {
                rescueConfirmedPlacements[pos] = 0
            } else {
                rescuePipeline.markRollback()
                debugLog(
                    "rescue confirmed block rolled back tick=$debugTick pos=$pos " +
                        "state=${state.block}; aborting optimistic chain"
                )
            }
        }
        val landing = currentRescueLanding() ?: getRescueLandingPos(anchor)
        val dx = pos.x - landing.x
        val dz = pos.z - landing.z
        val relevant = pos == rescueAwaitingPlacementPos || pos == landing ||
            (pos.y == landing.y && dx * dx + dz * dz <= 36)
        if (!relevant) return
        debugLog(
            "server block update tick=$debugTick pos=$pos state=${state.toString().take(180)} " +
                "await=$rescueAwaitingPlacementPos landing=$landing placementId=$rescuePlacementId " +
                "pending=${rescuePipelineSummary()}"
        )
    }

    private fun processRescuePipeline(): RescuePipelineResult = rescuePipeline.process()

    override fun children(): List<EventListener> = listOf(debugSupport, rescueController)

}
