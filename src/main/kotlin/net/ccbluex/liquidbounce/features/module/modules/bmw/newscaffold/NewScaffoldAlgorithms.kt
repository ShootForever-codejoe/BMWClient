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
import java.util.function.Predicate
import java.util.function.ToDoubleFunction
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.block.*
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.DoorBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.Fluids
import net.minecraft.item.BlockItem
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

// 放置目标和自救计划
internal data class BlockData(val pos: BlockPos, val facing: Direction)

internal data class RescueCandidate(
    val data: BlockData,
    val placementDistance: Int,
    val hitDistanceSquared: Double,
    val rayTraceReady: Boolean,
    val rayStability: Int,
    val directionOrder: Int,
)

internal data class RescueInitialPlan(
    val landing: BlockPos,
    val target: BlockData,
    val placementCount: Int,
    val completionTick: Int,
    val projectedFeet: Vec3d,
    val projectedFallDistance: Float,
    val estimatedDamage: Int,
)

internal enum class Mode(override val choiceName: String) : NamedChoice {
    TELLY("Telly"),
    SNAP("Snap"),
    NORMAL("Normal"),
}

internal enum class BlockSlotMode(override val choiceName: String) : NamedChoice {
    FARTHEST("Farthest"),
    MOST_BLOCKS("MostBlocks"),
}

internal enum class JumpMode(override val choiceName: String) : NamedChoice {
    NORMAL("Normal"),
    PARKOUR("Parkour"),
    NONE("None"),
}

internal data class SlotData(val slot: Int, val hand: Hand) : MinecraftShortcuts {
    fun check(): Boolean {
        val stack = if (hand == Hand.OFF_HAND) {
            player.offHandStack
        } else {
            player.inventory.getStack(slot)
        }
        return stack.isEmpty || stack.item !is BlockItem
    }
}

// 旋转计算
object RotationUtils {

    private const val FACE_INSET = 0.05

    // 把视线与目标平面的交点收进方块面内 防止瞄准边缘时射到邻块
    fun getClosestToBlockFace(pos: BlockPos?, face: Direction?, yaw: Float, pitch: Float): Rotation? {
        if (pos == null || face == null) {
            return null
        }

        val eyes = player.eyePos
        val direction = Vec3d.fromPolar(pitch, yaw)

        val planePoint = getFacePlanePoint(pos, face)
        val normal = Vec3d.of(face.vector)
        val denom = direction.dotProduct(normal)

        val intersection = if (abs(denom) < 1.0E-7) {
            planePoint
        } else {
            val t = planePoint.subtract(eyes).dotProduct(normal) / denom
            if (t <= 0) {
                planePoint
            } else {
                eyes.add(direction.multiply(t))
            }
        }

        val clamped = clampPointToFace(intersection, pos, face)
        return makeYawContinuous(
            Rotation.lookingAt(point = clamped, from = eyes),
            yaw
        )
    }

    // 保留服务器上一帧的圈数 避免 yaw 在 -180 和 180 之间突然跳变
    fun makeYawContinuous(rotation: Rotation, referenceYaw: Float): Rotation {
        rotation.yaw = referenceYaw + MathHelper.wrapDegrees(rotation.yaw - referenceYaw)
        return rotation
    }

    private fun getFacePlanePoint(pos: BlockPos, face: Direction): Vec3d {
        val x = pos.x.toDouble()
        val y = pos.y.toDouble()
        val z = pos.z.toDouble()
        return when (face) {
            Direction.DOWN -> Vec3d(x + 0.5, y, z + 0.5)
            Direction.UP -> Vec3d(x + 0.5, y + 1.0, z + 0.5)
            Direction.NORTH -> Vec3d(x + 0.5, y + 0.5, z)
            Direction.SOUTH -> Vec3d(x + 0.5, y + 0.5, z + 1.0)
            Direction.WEST -> Vec3d(x, y + 0.5, z + 0.5)
            Direction.EAST -> Vec3d(x + 1.0, y + 0.5, z + 0.5)
        }
    }

    private fun clampPointToFace(point: Vec3d, pos: BlockPos, face: Direction): Vec3d {
        val minX = pos.x.toDouble()
        val minY = pos.y.toDouble()
        val minZ = pos.z.toDouble()
        val maxX = minX + 1.0
        val maxY = minY + 1.0
        val maxZ = minZ + 1.0

        val innerMinX = minX + FACE_INSET
        val innerMinY = minY + FACE_INSET
        val innerMinZ = minZ + FACE_INSET
        val innerMaxX = maxX - FACE_INSET
        val innerMaxY = maxY - FACE_INSET
        val innerMaxZ = maxZ - FACE_INSET

        return when (face) {
            Direction.DOWN, Direction.UP -> Vec3d(
                point.x.coerceIn(innerMinX, innerMaxX),
                if (face == Direction.DOWN) minY else maxY,
                point.z.coerceIn(innerMinZ, innerMaxZ)
            )
            Direction.NORTH, Direction.SOUTH -> Vec3d(
                point.x.coerceIn(innerMinX, innerMaxX),
                point.y.coerceIn(innerMinY, innerMaxY),
                if (face == Direction.NORTH) minZ else maxZ
            )
            Direction.WEST, Direction.EAST -> Vec3d(
                if (face == Direction.WEST) minX else maxX,
                point.y.coerceIn(innerMinY, innerMaxY),
                point.z.coerceIn(innerMinZ, innerMaxZ)
            )
        }
    }

    fun normalizeYawDiff(yaw: Float, target: Float): Float {
        return abs(MathHelper.wrapDegrees(yaw - target))
    }

    fun yawDiffDirectly(yaw: Float, target: Float): Double {
        return MathHelper.wrapDegrees(yaw - target).toDouble()
    }

    fun smooth(diff: Float, step: Float): Float {
        val d = diff
        val s = step
        return when {
            abs(d) < s -> d
            d > 0 -> s
            else -> -s
        }
    }

}

@Suppress("TooManyFunctions")
// 所有放置共用同一套射线检测
object ClientRayTraceUtil : MinecraftShortcuts {

    private const val EPSILON = 1.0E-7
    private var eyePos: Vec3d? = null

    // 普通搭路可使用本刻缓存 自救的严格检查会显式传入眼位
    fun updateEyePos() {
        eyePos = mc.player?.eyePos
    }

    fun didHitBlockFace(rotation: Rotation, targetPos: BlockPos, expectedFace: Direction?, strict: Boolean): Boolean {
        return didHitBlockFace(
            player,
            rotation.yaw,
            rotation.pitch,
            targetPos,
            expectedFace,
            strict
        ) { obj: BlockState -> isIgnoredBlock(obj) }
    }

    @Suppress("LongParameterList")
    fun didHitBlockFace(
        rotation: Rotation,
        targetPos: BlockPos,
        expectedFace: Direction?,
        strict: Boolean,
        startEye: Vec3d,
        reachDistance: Double
    ): Boolean {
        return didHitBlockFace(
            player,
            rotation.yaw,
            rotation.pitch,
            targetPos,
            expectedFace,
            strict,
            startEye,
            reachDistance
        )
    }

    @Suppress("LongParameterList")
    fun didHitBlockFace(
        player: PlayerEntity?,
        yaw: Float,
        pitch: Float,
        targetPos: BlockPos,
        expectedFace: Direction?,
        strict: Boolean
    ): Boolean {
        return didHitBlockFace(
            player,
            yaw,
            pitch,
            targetPos,
            expectedFace,
            strict
        ) { obj: BlockState -> isIgnoredBlock(obj) }
    }

    @Suppress("LongParameterList")
    fun didHitBlockFace(
        player: PlayerEntity?,
        yaw: Float,
        pitch: Float,
        targetPos: BlockPos,
        expectedFace: Direction?,
        strict: Boolean,
        startEye: Vec3d,
        reachDistance: Double
    ): Boolean {
        return didHitBlockFace(
            player,
            yaw,
            pitch,
            targetPos,
            expectedFace,
            strict,
            startEye,
            reachDistance
        ) { obj: BlockState -> isIgnoredBlock(obj) }
    }

    @Suppress("LongParameterList")
    fun didHitBlockFace(
        player: PlayerEntity?,
        yaw: Float,
        pitch: Float,
        targetPos: BlockPos,
        expectedFace: Direction?,
        strict: Boolean,
        ignorePredicate: Predicate<BlockState>
    ): Boolean {
        if (player == null || expectedFace == null) return false

        val startEye = eyePos ?: player.eyePos ?: return false
        return didHitBlockFace(
            player,
            yaw,
            pitch,
            targetPos,
            expectedFace,
            strict,
            startEye,
            player.blockInteractionRange.toDouble(),
            ignorePredicate
        )
    }

    @Suppress("LongParameterList")
    fun didHitBlockFace(
        player: PlayerEntity?,
        yaw: Float,
        pitch: Float,
        targetPos: BlockPos,
        expectedFace: Direction?,
        strict: Boolean,
        startEye: Vec3d,
        reachDistance: Double,
        ignorePredicate: Predicate<BlockState>
    ): Boolean {
        if (player == null || expectedFace == null) return false

        val result = traceBlock(player, yaw, pitch, startEye, reachDistance, ignorePredicate)
        return !(result == null
            || targetPos.x != result.blockPos.x
            || targetPos.y != result.blockPos.y
            || targetPos.z != result.blockPos.z
            || (expectedFace != result.side && strict))
    }

    fun getFacedBlock(yaw: Float, pitch: Float): BlockHitResult? {
        return getFacedBlock(yaw, pitch) { obj: BlockState -> isIgnoredBlock(obj) }
    }

    fun getFacedBlock(
        yaw: Float,
        pitch: Float,
        ignorePredicate: Predicate<BlockState>
    ): BlockHitResult? {
        val currentPlayer = player ?: return null
        val startEye = eyePos ?: currentPlayer.eyePos ?: return null
        return traceBlock(
            currentPlayer,
            yaw,
            pitch,
            startEye,
            currentPlayer.blockInteractionRange.toDouble(),
            ignorePredicate
        )
    }

    fun getFacedBlock(
        yaw: Float,
        pitch: Float,
        startEye: Vec3d,
        reachDistance: Double
    ): BlockHitResult? {
        return getFacedBlock(
            yaw,
            pitch,
            startEye,
            reachDistance
        ) { obj: BlockState -> isIgnoredBlock(obj) }
    }

    fun getFacedBlock(
        yaw: Float,
        pitch: Float,
        startEye: Vec3d,
        reachDistance: Double,
        ignorePredicate: Predicate<BlockState>
    ): BlockHitResult? {
        val currentPlayer = player ?: return null
        return traceBlock(currentPlayer, yaw, pitch, startEye, reachDistance, ignorePredicate)
    }

    fun getFacedBlock(
        player: PlayerEntity?,
        yaw: Float,
        pitch: Float,
        startEye: Vec3d,
        reachDistance: Double
    ): BlockHitResult? {
        return getFacedBlock(
            player,
            yaw,
            pitch,
            startEye,
            reachDistance
        ) { obj: BlockState -> isIgnoredBlock(obj) }
    }

    fun getFacedBlock(
        player: PlayerEntity?,
        yaw: Float,
        pitch: Float,
        startEye: Vec3d,
        reachDistance: Double,
        ignorePredicate: Predicate<BlockState>
    ): BlockHitResult? {
        if (player == null) return null
        return traceBlock(player, yaw, pitch, startEye, reachDistance, ignorePredicate)
    }

    @Suppress("LongMethod", "CognitiveComplexMethod", "NestedBlockDepth", "ComplexCondition")
    // 按方块网格逐格检查遮挡
    private fun traceBlock(
        player: PlayerEntity,
        yaw: Float,
        pitch: Float,
        startPos: Vec3d,
        reachDistance: Double,
        ignorePredicate: Predicate<BlockState>
    ): BlockHitResult? {
        if (!reachDistance.isFinite() || reachDistance < 0.0 ||
            !startPos.x.isFinite() || !startPos.y.isFinite() || !startPos.z.isFinite() ||
            !yaw.isFinite() || !pitch.isFinite()
        ) {
            return null
        }

        val direction = Vec3d.fromPolar(pitch, yaw)
        val endPos = startPos.add(direction.multiply(reachDistance))

        var currentPos = BlockPos.ofFloored(startPos)
        val stepX = sign(direction.x).toInt()
        val stepY = sign(direction.y).toInt()
        val stepZ = sign(direction.z).toInt()

        val nextBoundaryX = (if (stepX > 0) currentPos.x + 1 else currentPos.x).toDouble()
        val nextBoundaryY = (if (stepY > 0) currentPos.y + 1 else currentPos.y).toDouble()
        val nextBoundaryZ = (if (stepZ > 0) currentPos.z + 1 else currentPos.z).toDouble()

        var tMaxX = if (stepX == 0) {
            Double.POSITIVE_INFINITY
        } else {
            (nextBoundaryX - startPos.x) / direction.x
        }
        var tMaxY = if (stepY == 0) {
            Double.POSITIVE_INFINITY
        } else {
            (nextBoundaryY - startPos.y) / direction.y
        }
        var tMaxZ = if (stepZ == 0) {
            Double.POSITIVE_INFINITY
        } else {
            (nextBoundaryZ - startPos.z) / direction.z
        }

        val tDeltaX = if (stepX == 0) Double.POSITIVE_INFINITY else stepX / direction.x
        val tDeltaY = if (stepY == 0) Double.POSITIVE_INFINITY else stepY / direction.y
        val tDeltaZ = if (stepZ == 0) Double.POSITIVE_INFINITY else stepZ / direction.z

        val world = player.world
        var box: Box
        var entryDistance = 0.0
        while (entryDistance <= reachDistance + EPSILON) {
            if (!world.isAir(currentPos)) {
                val state = world.getBlockState(currentPos)
                if (!ignorePredicate.test(state)) {
                    val shape: VoxelShape
                    val fluidState = world.getFluidState(currentPos)
                    if (fluidState != null && fluidState.isStill && fluidState.fluid === Fluids.WATER) {
                        shape = VoxelShapes.fullCube()
                    } else {
                        shape = state.getCollisionShape(world, currentPos, ShapeContext.of(player))
                    }

                    if (!shape.isEmpty) {
                        for (localBox in shape.boundingBoxes) {
                            box = localBox.offset(currentPos)
                            val intercept = box.raycast(startPos, endPos)

                            if (intercept.isPresent) {
                                val hitVec = intercept.get()
                                val side = getHitFaceFromBox(hitVec, box)
                                val isInside = box.contains(startPos)
                                return BlockHitResult(hitVec, side, currentPos, isInside)
                            }
                        }
                    }
                }
            }
            val nextEntryDistance = minOf(tMaxX, tMaxY, tMaxZ)
            if (nextEntryDistance > reachDistance + EPSILON) {
                break
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    currentPos = currentPos.add(stepX, 0, 0)
                    tMaxX += tDeltaX
                } else {
                    currentPos = currentPos.add(0, 0, stepZ)
                    tMaxZ += tDeltaZ
                }
            } else {
                if (tMaxY < tMaxZ) {
                    currentPos = currentPos.add(0, stepY, 0)
                    tMaxY += tDeltaY
                } else {
                    currentPos = currentPos.add(0, 0, stepZ)
                    tMaxZ += tDeltaZ
                }
            }
            entryDistance = nextEntryDistance
        }
        return null
    }

    fun isIgnoredBlock(state: BlockState): Boolean {
        val block = state.block
        return block is PlantBlock || block is SnowBlock || block is AirBlock ||
            block is ShortPlantBlock || block is FluidBlock
    }

    private fun getHitFaceFromBox(hit: Vec3d, box: Box): Direction {
        return listOf(
            abs(hit.x - box.minX) to Direction.WEST,
            abs(hit.x - box.maxX) to Direction.EAST,
            abs(hit.y - box.minY) to Direction.DOWN,
            abs(hit.y - box.maxY) to Direction.UP,
            abs(hit.z - box.minZ) to Direction.NORTH,
            abs(hit.z - box.maxZ) to Direction.SOUTH,
        ).minBy { it.first }.second
    }

}

// 普通搭路和自救共用的方块搜索
internal class NewScaffoldBlockFinder : MinecraftShortcuts {

    private val unsupportedBlocks = setOf(
        Blocks.ANVIL,
        Blocks.AIR,
        Blocks.WATER,
        Blocks.FIRE,
        Blocks.LAVA,
        Blocks.SKELETON_SKULL,
        Blocks.OAK_SIGN,
        Blocks.TRAPPED_CHEST,
        Blocks.CHEST,
        Blocks.ENCHANTING_TABLE,
        Blocks.ENDER_CHEST,
        Blocks.CRAFTING_TABLE,
        Blocks.DAYLIGHT_DETECTOR,
        Blocks.COBWEB,
        Blocks.SHORT_GRASS,
        Blocks.FLOWER_POT,
        Blocks.CHORUS_FLOWER,
        Blocks.SUNFLOWER,
        Blocks.CORNFLOWER,
        Blocks.TORCHFLOWER,
        Blocks.OAK_BUTTON,
        Blocks.ACACIA_BUTTON,
        Blocks.BIRCH_BUTTON,
        Blocks.CRIMSON_BUTTON,
        Blocks.CHERRY_BUTTON,
        Blocks.DARK_OAK_BUTTON,
        Blocks.JUNGLE_BUTTON,
        Blocks.STONE_BUTTON,
        Blocks.WARPED_BUTTON,
        Blocks.SPRUCE_BUTTON,
        Blocks.NOTE_BLOCK,
        Blocks.PLAYER_HEAD,
    )

    fun findPlacement(pos: BlockPos): BlockData? {
        // 先查目标格相邻支撑 找不到时再扩大到玩家周围
        val adjacent = getPos(pos)
        val data = if (adjacent == null) {
            val support = getBlockPos() ?: return null
            val direction = getPlaceSide(support) ?: return null
            BlockData(support, direction)
        } else {
            adjacent
        }

        return data.takeIf {
            ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(it.pos.offset(it.facing)))
        }
    }

    fun groundDropBlocks(): Int? {
        // null 表示脚下直到世界底部都没有完整支撑
        val x = floor(player.x).toInt()
        val z = floor(player.z).toInt()
        val startY = floor(player.y).toInt() - 1
        for (y in startY downTo world.bottomY) {
            if (isSolid(BlockPos(x, y, z))) return startY - y
        }
        return null
    }

    fun blockRelativeToPlayer(): Block {
        val pos = BlockPos(
            floor(player.x).toInt(),
            floor(player.y).toInt() - 1,
            floor(player.z).toInt(),
        )
        return player.world.getBlockState(pos).block
    }

    fun isSolid(pos: BlockPos?): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block
        if (block is TrapdoorBlock || block is DoorBlock || block is FenceGateBlock) return false
        return block !in unsupportedBlocks && !ClientRayTraceUtil.isIgnoredBlock(state)
    }

    private fun getPlaceSide(blockPos: BlockPos): Direction? {
        // 同距离优先选择更靠近玩家的空面 减少无意义的大角度转头
        val candidates = ArrayList<BlockData>()
        val playerPos = BlockPos(
            floor(player.x).toInt(),
            floor(player.y).toInt(),
            floor(player.z).toInt(),
        )

        if (isAir(blockPos.east()) && blockPos.east() != playerPos) {
            candidates += BlockData(blockPos.east(), Direction.EAST)
        }
        if (isAir(blockPos.north()) && blockPos.north() != playerPos) {
            candidates += BlockData(blockPos.north(), Direction.NORTH)
        }
        if (isAir(blockPos.south()) && blockPos.south() != playerPos) {
            candidates += BlockData(blockPos.south(), Direction.SOUTH)
        }
        if (isAir(blockPos.west()) && blockPos.west() != playerPos) {
            candidates += BlockData(blockPos.west(), Direction.WEST)
        }
        if (candidates.isEmpty()) return null

        candidates.sortWith(compareBy { it.pos.getSquaredDistance(playerPos) })
        candidates.removeIf { data ->
            !ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(data.pos.offset(data.facing)))
        }
        return candidates.firstOrNull()?.facing
    }

    private fun getBlockPos(): BlockPos? {
        val playerPos = BlockPos(
            floor(player.x).toInt(),
            floor(player.y).toInt(),
            floor(player.z).toInt(),
        )
        val positions = ArrayList<BlockPos?>()
        for ((position) in searchBlocks()) {
            if (isSolid(position)) positions += position
        }
        positions.removeIf { it!!.y >= playerPos.y }
        if (positions.isEmpty()) return null
        positions.sortWith(
            Comparator.comparingDouble<BlockPos?>(
                ToDoubleFunction { it!!.getSquaredDistance(playerPos) }
            )
        )
        return positions.first()
    }

    private fun isAir(pos: BlockPos?): Boolean =
        ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(pos))

    private fun searchBlocks(): MutableMap<BlockPos, Block> {
        val blocks = HashMap<BlockPos, Block>()
        for (x in 5 downTo -4) {
            for (y in 5 downTo -4) {
                for (z in 5 downTo -4) {
                    val pos = BlockPos(player.blockX + x, player.blockY + y, player.blockZ + z)
                    blocks[pos] = world.getBlockState(pos).block
                }
            }
        }
        return blocks
    }

    private fun getPos(pos: BlockPos): BlockData? = when {
        isSolid(pos.add(-1, 0, 0)) -> BlockData(pos.add(-1, 0, 0), Direction.EAST)
        isSolid(pos.add(1, 0, 0)) -> BlockData(pos.add(1, 0, 0), Direction.WEST)
        isSolid(pos.add(0, 0, 1)) -> BlockData(pos.add(0, 0, 1), Direction.NORTH)
        isSolid(pos.add(0, 0, -1)) -> BlockData(pos.add(0, 0, -1), Direction.SOUTH)
        isSolid(pos.add(0, -1, 0)) -> BlockData(pos.add(0, -1, 0), Direction.UP)
        else -> null
    }
}

// 估算落点和摔落伤害
internal object NewScaffoldFallSafety {

    fun projectedFallDistance(
        currentFallDistance: Float,
        currentFeetY: Double,
        projectedFeetY: Double,
    ): Float = currentFallDistance + max(0.0, currentFeetY - projectedFeetY).toFloat()

    fun estimatedDamage(fallDistance: Float, jumpBoostLevel: Int = 0): Int {
        val safeDistance = 3.0f + jumpBoostLevel
        return ceil(max(0.0f, fallDistance - safeDistance).toDouble()).toInt()
    }
}

// 判断桥长和最终落脚范围
internal object NewScaffoldInterceptionMath {

    fun placementCount(firstPlacement: BlockPos, landing: BlockPos): Int = 1 +
        abs(firstPlacement.x - landing.x) +
        abs(firstPlacement.y - landing.y) +
        abs(firstPlacement.z - landing.z)

    fun canCompleteAt(
        landing: BlockPos,
        predictedFeet: Vec3d,
        playerWidth: Double = 0.6,
    ): Boolean {
        val landingTop = landing.y + 1.0
        val halfWidth = playerWidth / 2.0
        val overlapEpsilon = 0.01
        val overlapsX = predictedFeet.x + halfWidth > landing.x + overlapEpsilon &&
            predictedFeet.x - halfWidth < landing.x + 1.0 - overlapEpsilon
        val overlapsZ = predictedFeet.z + halfWidth > landing.z + overlapEpsilon &&
            predictedFeet.z - halfWidth < landing.z + 1.0 - overlapEpsilon
        return predictedFeet.y >= landingTop - 0.01 &&
            overlapsX && overlapsZ
    }
}

// 客户端更新未到时临时使用已发出的方块
internal object NewScaffoldVirtualSupport {

    private const val FACE_EPSILON = 1.0E-5

    @Suppress("ComplexCondition")
    fun intersect(
        eye: Vec3d,
        rotation: Rotation,
        reach: Double,
        pos: BlockPos,
        expectedFace: Direction,
    ): BlockHitResult? {
        if (reach < 0.0 || !reach.isFinite() ||
            !eye.x.isFinite() || !eye.y.isFinite() || !eye.z.isFinite() ||
            !rotation.yaw.isFinite() || !rotation.pitch.isFinite()
        ) {
            return null
        }

        val end = eye.add(Vec3d.fromPolar(rotation.pitch, rotation.yaw).multiply(reach))
        val box = Box(pos)
        val hit = box.raycast(eye, end).orElse(null) ?: return null
        if (!liesOnFace(hit, pos, expectedFace)) return null
        return BlockHitResult(hit, expectedFace, pos, box.contains(eye))
    }

    private fun liesOnFace(hit: Vec3d, pos: BlockPos, face: Direction): Boolean = when (face) {
        Direction.WEST -> abs(hit.x - pos.x) <= FACE_EPSILON
        Direction.EAST -> abs(hit.x - (pos.x + 1.0)) <= FACE_EPSILON
        Direction.DOWN -> abs(hit.y - pos.y) <= FACE_EPSILON
        Direction.UP -> abs(hit.y - (pos.y + 1.0)) <= FACE_EPSILON
        Direction.NORTH -> abs(hit.z - pos.z) <= FACE_EPSILON
        Direction.SOUTH -> abs(hit.z - (pos.z + 1.0)) <= FACE_EPSILON
    }
}

@Suppress("TooManyFunctions")
// 搜索首块和后续桥面
internal class NewScaffoldRescuePlanner(
    private val isSolid: (BlockPos?) -> Boolean,
    private val isPending: (BlockPos) -> Boolean,
    private val fixRotation: () -> Boolean,
    private val movementYaw: () -> Float,
    private val log: (String) -> Unit,
) : MinecraftShortcuts {

    var searchState: String = "idle"

    private companion object {
        const val STABLE_RAY_SAMPLE_COUNT = 5
        const val RAY_EYE_EPSILON = 0.015
    }

    fun landingBelow(position: Vec3d): BlockPos = BlockPos(
        floor(position.x).toInt(),
        floor(position.y).toInt() - 1,
        floor(position.z).toInt(),
    )

    @Suppress("LongMethod", "LoopWithTooManyJumpStatements")
    // 选择伤害最低且来得及完成的桥面
    fun bestInitialPlan(
        samples: List<RescuePoseSample>,
        currentFallDistance: Float,
        jumpBoostLevel: Int = 0,
    ): RescueInitialPlan? {
        if (samples.size < 2) return null
        val firstInteractionEye = samples[1].eye
        val previousSearchState = searchState
        val candidates = ArrayList<RescueInitialPlan>()
        val visitedLandings = HashSet<BlockPos>()

        // 每个预测刻都可能形成不同高度的落脚点 逐个验证能否及时搭到
        for (guessTick in 1 until samples.size) {
            val guessedLanding = landingBelow(samples[guessTick].position)
            if (!visitedLandings.add(guessedLanding)) continue
            val target = findPlacement(guessedLanding, firstInteractionEye, emitLog = false) ?: continue
            val firstPlacement = target.pos.offset(target.facing)
            val placementCount = NewScaffoldInterceptionMath.placementCount(firstPlacement, guessedLanding)
            if (placementCount !in 1 until samples.size) continue

            val completion = samples[placementCount]
            val landingTop = guessedLanding.y + 1.0
            if (!NewScaffoldInterceptionMath.canCompleteAt(
                    guessedLanding,
                    completion.position,
                    player.dimensions.width.toDouble(),
                )
            ) {
                continue
            }

            val projectedFallDistance = NewScaffoldFallSafety.projectedFallDistance(
                currentFallDistance,
                samples[0].position.y,
                landingTop,
            )
            candidates += RescueInitialPlan(
                landing = guessedLanding,
                target = target,
                placementCount = placementCount,
                completionTick = placementCount,
                projectedFeet = completion.position,
                projectedFallDistance = projectedFallDistance,
                estimatedDamage = NewScaffoldFallSafety.estimatedDamage(
                    projectedFallDistance,
                    jumpBoostLevel,
                ),
            )
        }

        searchState = previousSearchState
        // 先保命 再选更高、更短且更近的方案
        val selected = candidates.minWithOrNull(
            compareBy<RescueInitialPlan> { it.estimatedDamage }
                .thenByDescending { it.landing.y }
                .thenBy { it.placementCount }
                .thenBy {
                    firstInteractionEye.squaredDistanceTo(closestPoint(it.target, firstInteractionEye))
                }
        ) ?: return null

        val target = findPlacement(selected.landing, firstInteractionEye, emitLog = true) ?: return null
        val result = selected.copy(target = target)
        log(
            "bridge intercept selected landing=${result.landing} support=${target.pos} " +
                "place=${target.pos.offset(target.facing)} links=${result.placementCount} " +
                "completeTick=${result.completionTick} eye=${format(firstInteractionEye)} " +
                "projectedFeet=${format(result.projectedFeet)} " +
                "fall=${"%.2f".format(result.projectedFallDistance)} " +
                "damage=${result.estimatedDamage}"
        )
        return result
    }

    @Suppress("LongMethod", "CognitiveComplexMethod", "NestedBlockDepth")
    // 每次向落脚点推进一格
    fun findPlacement(landing: BlockPos, eye: Vec3d, emitLog: Boolean = true): BlockData? {
        if (isPending(landing)) {
            searchState = "pending-target pos=$landing"
            return null
        }
        if (!ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(landing))) {
            searchState = "landing-occupied pos=$landing state=${world.getBlockState(landing).block}"
            return null
        }

        findExact(landing, eye)?.let { exact ->
            val hit = closestPoint(exact, eye)
            val rotation = predictiveRotation(exact, eye)
            val exactHit = didHit(exact, rotation, eye)
            if (exactHit) {
                val stability = rayStability(exact, rotation, eye)
                searchState =
                    "exact support=${exact.pos} place=$landing d=${"%.2f".format(eye.distanceTo(hit))} " +
                        "stability=$stability/$STABLE_RAY_SAMPLE_COUNT"
                if (stability == STABLE_RAY_SAMPLE_COUNT) {
                    if (emitLog) {
                        log(
                            "chain search exact landing=$landing support=${exact.pos} " +
                                "place=$landing face=${exact.facing} " +
                                "eye=${format(eye)} hit=${format(hit)} " +
                                "hitDistance=${"%.3f".format(eye.distanceTo(hit))} " +
                                "reach=${player.blockInteractionRange} rotation=$rotation rayReady=true " +
                                "stability=$stability/$STABLE_RAY_SAMPLE_COUNT"
                        )
                    }
                    return exact
                }
                if (emitLog) {
                    log(
                        "chain exact candidate fragile landing=$landing support=${exact.pos} " +
                            "face=${exact.facing} eye=${format(eye)} rotation=$rotation " +
                            "stability=$stability/$STABLE_RAY_SAMPLE_COUNT"
                    )
                }
            }
            if (!exactHit && emitLog) {
                log(
                    "chain exact candidate ray-miss landing=$landing support=${exact.pos} face=${exact.facing} " +
                        "eye=${format(eye)} closestHit=${format(hit)} rotation=$rotation"
                )
            }
        }

        val radius = ceil(player.blockInteractionRange).toInt() + 1
        val directions = arrayOf(
            Direction.UP,
            Direction.EAST,
            Direction.WEST,
            Direction.SOUTH,
            Direction.NORTH,
            Direction.DOWN,
        )
        val candidates = ArrayList<RescueCandidate>()
        var supports = 0
        var faces = 0
        var occupied = 0
        var notProgressing = 0
        var outOfReach = 0
        var rayMisses = 0
        var nearestRejectedDistance = Double.POSITIVE_INFINITY
        var nearestRejected: String? = null

        for (x in -radius..radius) {
            for (y in -1..1) {
                for (z in -radius..radius) {
                    val supportPos = landing.add(x, y, z)
                    if (!isSolid(supportPos) && !isPending(supportPos)) continue
                    supports++

                    directions.forEachIndexed { directionOrder, direction ->
                        faces++
                        val placementPos = supportPos.offset(direction)
                        if (placementPos.y != landing.y) {
                            notProgressing++
                            return@forEachIndexed
                        }
                        if (isPending(placementPos) ||
                            !ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(placementPos))
                        ) {
                            occupied++
                            return@forEachIndexed
                        }

                        val supportDx = supportPos.x - landing.x
                        val supportDy = supportPos.y - landing.y
                        val supportDz = supportPos.z - landing.z
                        val placementDx = placementPos.x - landing.x
                        val placementDy = placementPos.y - landing.y
                        val placementDz = placementPos.z - landing.z
                        val supportDistance =
                            supportDx * supportDx + supportDy * supportDy + supportDz * supportDz
                        val placementDistance =
                            placementDx * placementDx + placementDy * placementDy + placementDz * placementDz
                        if (placementDistance >= supportDistance) {
                            notProgressing++
                            return@forEachIndexed
                        }

                        val data = BlockData(supportPos, direction)
                        val hit = closestPoint(data, eye)
                        val hitDistanceSquared = eye.squaredDistanceTo(hit)
                        val reach = player.blockInteractionRange
                        if (hitDistanceSquared > reach * reach) {
                            outOfReach++
                            if (hitDistanceSquared < nearestRejectedDistance) {
                                nearestRejectedDistance = hitDistanceSquared
                                nearestRejected = "$supportPos->$placementPos/${direction.name}"
                            }
                            return@forEachIndexed
                        }

                        val rotation = predictiveRotation(data, eye)
                        val rayReady = didHit(data, rotation, eye)
                        val rayStability = if (rayReady) {
                            rayStability(data, rotation, eye)
                        } else {
                            0
                        }
                        if (!rayReady) rayMisses++
                        candidates += RescueCandidate(
                            data,
                            placementDistance,
                            hitDistanceSquared,
                            rayReady,
                            rayStability,
                            directionOrder,
                        )
                    }
                }
            }
        }

        val selected = candidates.asSequence().filter { it.rayTraceReady }.minWithOrNull(
            compareBy<RescueCandidate> { it.placementDistance }
                .thenByDescending { it.rayStability }
                .thenBy { it.hitDistanceSquared }
                .thenBy { it.directionOrder }
                .thenBy { it.data.pos.x }
                .thenBy { it.data.pos.z }
        )
        searchState =
            "supports=$supports faces=$faces occupied=$occupied noProgress=$notProgressing " +
                "outReach=$outOfReach rayMiss=$rayMisses valid=${candidates.size - rayMisses}/${candidates.size}"

        if (selected == null) {
            if (emitLog) {
                log(
                    "chain search none landing=$landing eye=${format(eye)} radius=$radius $searchState " +
                        "nearestOutReach=${nearestRejected ?: "none"}" +
                        if (nearestRejected != null) {
                            " distance=${"%.3f".format(sqrt(nearestRejectedDistance))}"
                        } else {
                            ""
                        }
                )
            }
            return null
        }

        val data = selected.data
        val place = data.pos.offset(data.facing)
        val hit = closestPoint(data, eye)
        if (emitLog) {
            log(
                "chain search selected landing=$landing support=${data.pos} place=$place face=${data.facing} " +
                "remainingSq=${selected.placementDistance} hit=${format(hit)} " +
                "hitDistance=${"%.3f".format(eye.distanceTo(hit))} reach=${player.blockInteractionRange} " +
                "rayReady=${selected.rayTraceReady} " +
                "stability=${selected.rayStability}/$STABLE_RAY_SAMPLE_COUNT $searchState"
            )
        }
        return data
    }

    @Suppress("CognitiveComplexMethod", "ReturnCount")
    fun immediateNext(current: BlockData, landing: BlockPos, eye: Vec3d): BlockData? {
        // 高度不同先处理竖直方向 同层时再沿 X/Z 最短路径推进
        val placed = current.pos.offset(current.facing)
        if (placed == landing || (!isStable(placed) && !isPending(placed))) return null

        if (placed.y != landing.y) {
            val direction = if (landing.y < placed.y) Direction.DOWN else Direction.UP
            val placementPos = placed.offset(direction)
            if (isPending(placementPos) ||
                !ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(placementPos))
            ) {
                return null
            }
            val candidate = BlockData(placed, direction)
            if (!withinReach(candidate, eye)) return null
            val rotation = predictiveRotation(candidate, eye)
            return candidate.takeIf { didHit(it, rotation, eye) }
        }

        val dx = landing.x - placed.x
        val dz = landing.z - placed.z
        if (dx == 0 && dz == 0) return null
        val directions = ArrayList<Direction>(2)
        if (dx > 0) directions += Direction.EAST
        if (dx < 0) directions += Direction.WEST
        if (dz > 0) directions += Direction.SOUTH
        if (dz < 0) directions += Direction.NORTH
        directions.sortBy { direction ->
            val next = placed.offset(direction)
            val remX = next.x - landing.x
            val remZ = next.z - landing.z
            remX * remX + remZ * remZ
        }

        for (direction in directions) {
            val placementPos = placed.offset(direction)
            if (placementPos.y != landing.y || isPending(placementPos) ||
                !ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(placementPos))
            ) {
                continue
            }
            val candidate = BlockData(placed, direction)
            if (!withinReach(candidate, eye)) continue
            val rotation = predictiveRotation(candidate, eye)
            if (didHit(candidate, rotation, eye)) return candidate
        }
        return null
    }

    fun predictiveRotation(data: BlockData, eye: Vec3d): Rotation {
        // 中心点被遮挡时继续尝试面内采样点 尽量在一个客户端刻内找到合法射线
        var fallback: Rotation? = null
        for (hit in faceAimPoints(data, eye)) {
            if (eye.squaredDistanceTo(hit) >
                player.blockInteractionRange * player.blockInteractionRange
            ) {
                continue
            }
            val rotation = normalize(
                RotationUtils.makeYawContinuous(Rotation.lookingAt(hit, eye), movementYaw())
            )
            if (fallback == null) fallback = rotation
            if (didHit(data, rotation, eye)) return rotation
        }
        return fallback ?: normalize(
            RotationUtils.makeYawContinuous(
                Rotation.lookingAt(closestPoint(data, eye), eye),
                movementYaw(),
            )
        )
    }

    private fun rayStability(data: BlockData, rotation: Rotation, eye: Vec3d): Int {
        // 用很小的眼位扰动过滤只在单个浮点位置成立的脆弱射线
        val offsets = arrayOf(
            Vec3d.ZERO,
            Vec3d(RAY_EYE_EPSILON, 0.0, 0.0),
            Vec3d(-RAY_EYE_EPSILON, 0.0, 0.0),
            Vec3d(0.0, 0.0, RAY_EYE_EPSILON),
            Vec3d(0.0, 0.0, -RAY_EYE_EPSILON),
        )
        return offsets.count { offset -> didHit(data, rotation, eye.add(offset)) }
    }

    fun didHit(data: BlockData?, rotation: Rotation, eye: Vec3d? = null): Boolean {
        if (data == null) return false
        if (eye == null && !isPending(data.pos)) {
            return ClientRayTraceUtil.didHitBlockFace(rotation, data.pos, data.facing, true)
        }
        return pendingAwareHit(data, rotation, eye ?: player.eyePos) != null
    }

    @Suppress("ReturnCount")
    fun pendingAwareHit(data: BlockData, rotation: Rotation, eye: Vec3d): BlockHitResult? {
        val reach = player.blockInteractionRange.toDouble()
        val realHit = ClientRayTraceUtil.getFacedBlock(
            player,
            rotation.yaw,
            rotation.pitch,
            eye,
            reach,
        )
        if (realHit != null && realHit.blockPos == data.pos && realHit.side == data.facing) {
            return realHit
        }
        if (!isPending(data.pos)) return null

        // 方块更新可能晚于 ACK 此时按已发送方块构造临时碰撞面
        val virtualHit = NewScaffoldVirtualSupport.intersect(
            eye,
            rotation,
            reach,
            data.pos,
            data.facing,
        ) ?: return null

        if (realHit != null && realHit.blockPos != data.pos &&
            eye.squaredDistanceTo(realHit.pos) <= eye.squaredDistanceTo(virtualHit.pos) + 1.0E-7
        ) {
            return null
        }
        return virtualHit
    }

    fun closestPoint(data: BlockData, eye: Vec3d, inset: Double = 0.02): Vec3d {
        val minX = data.pos.x.toDouble()
        val minY = data.pos.y.toDouble()
        val minZ = data.pos.z.toDouble()
        val maxX = minX + 1.0
        val maxY = minY + 1.0
        val maxZ = minZ + 1.0
        return when (data.facing) {
            Direction.DOWN -> Vec3d(
                eye.x.coerceIn(minX + inset, maxX - inset), minY,
                eye.z.coerceIn(minZ + inset, maxZ - inset),
            )
            Direction.UP -> Vec3d(
                eye.x.coerceIn(minX + inset, maxX - inset), maxY,
                eye.z.coerceIn(minZ + inset, maxZ - inset),
            )
            Direction.NORTH -> Vec3d(
                eye.x.coerceIn(minX + inset, maxX - inset),
                eye.y.coerceIn(minY + inset, maxY - inset), minZ,
            )
            Direction.SOUTH -> Vec3d(
                eye.x.coerceIn(minX + inset, maxX - inset),
                eye.y.coerceIn(minY + inset, maxY - inset), maxZ,
            )
            Direction.WEST -> Vec3d(
                minX, eye.y.coerceIn(minY + inset, maxY - inset),
                eye.z.coerceIn(minZ + inset, maxZ - inset),
            )
            Direction.EAST -> Vec3d(
                maxX, eye.y.coerceIn(minY + inset, maxY - inset),
                eye.z.coerceIn(minZ + inset, maxZ - inset),
            )
        }
    }

    fun withinReach(data: BlockData, eye: Vec3d): Boolean {
        // 距离按方块面最近点计算 不能用方块中心误杀极限距离目标
        val reach = player.blockInteractionRange
        return eye.squaredDistanceTo(closestPoint(data, eye)) <= reach * reach
    }

    fun isStable(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return !ClientRayTraceUtil.isIgnoredBlock(state) && state.isFullCube(world, pos)
    }

    private fun findExact(pos: BlockPos, eye: Vec3d): BlockData? {
        val candidates = arrayOf(
            BlockData(pos.down(), Direction.UP),
            BlockData(pos.west(), Direction.EAST),
            BlockData(pos.east(), Direction.WEST),
            BlockData(pos.north(), Direction.SOUTH),
            BlockData(pos.south(), Direction.NORTH),
            BlockData(pos.up(), Direction.DOWN),
        )
        return candidates.firstOrNull { data ->
            data.pos.y <= pos.y && (isSolid(data.pos) || isPending(data.pos)) &&
                ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(pos)) && withinReach(data, eye)
        }
    }

    private fun faceAimPoints(data: BlockData, eye: Vec3d): List<Vec3d> {
        val points = ArrayList<Vec3d>(10)
        doubleArrayOf(0.49, 0.4, 0.3, 0.25, 0.2, 0.15, 0.1, 0.05, 0.02).forEach { inset ->
            points += closestPoint(data, eye, inset)
        }
        points += data.pos.toCenterPos().add(Vec3d.of(data.facing.vector).multiply(0.5))
        return points.distinct()
    }

    private fun normalize(rotation: Rotation): Rotation {
        if (fixRotation()) return rotation.normalize()
        rotation.isNormalized = true
        return rotation
    }

    private fun format(vec: Vec3d): String =
        "%.3f %.3f %.3f".format(vec.x, vec.y, vec.z)
}

