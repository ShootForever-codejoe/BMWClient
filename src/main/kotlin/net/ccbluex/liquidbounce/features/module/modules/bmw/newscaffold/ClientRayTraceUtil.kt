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

import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.block.*
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.Fluids
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import java.util.function.Predicate
import kotlin.math.abs
import kotlin.math.sign

object ClientRayTraceUtil : MinecraftShortcuts {

    private const val EPSILON = 1.0E-7

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

    fun didHitBlockFace(
        player: PlayerEntity?,
        yaw: Float,
        pitch: Float,
        targetPos: BlockPos,
        expectedFace: Direction?,
        strict: Boolean,
        ignorePredicate: Predicate<BlockState>
    ): Boolean {
        if (player == null || expectedFace == null) {
            return false
        }

        val result = getFacedBlock(yaw, pitch, ignorePredicate)
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
    ): BlockHitResult? { // DDA步进扫描RayTrace，高版本原版自带的raytrace返回有问题我不知道为什么就写了这个
        val reachDistance = player.blockInteractionRange
        if (yaw == 0.0f && pitch == 0.0f) {
            return null
        }

        val startPos = player.eyePos
        var direction = Vec3d.fromPolar(pitch, yaw)
        val endPos = startPos!!.add(direction.multiply(reachDistance))
        if (direction.x == 0.0) direction = Vec3d(EPSILON, direction.y, direction.z)
        if (direction.y == 0.0) direction = Vec3d(direction.x, EPSILON, direction.z)
        if (direction.z == 0.0) direction = Vec3d(direction.x, direction.y, EPSILON)

        var currentPos = BlockPos.ofFloored(startPos)
        val stepX = sign(direction.x).toInt()
        val stepY = sign(direction.y).toInt()
        val stepZ = sign(direction.z).toInt()

        val nextBoundaryX = (if (stepX > 0) currentPos.x + 1 else currentPos.x).toDouble()
        val nextBoundaryY = (if (stepY > 0) currentPos.y + 1 else currentPos.y).toDouble()
        val nextBoundaryZ = (if (stepZ > 0) currentPos.z + 1 else currentPos.z).toDouble()

        var tMaxX = (nextBoundaryX - startPos.x) / direction.x
        var tMaxY = (nextBoundaryY - startPos.y) / direction.y
        var tMaxZ = (nextBoundaryZ - startPos.z) / direction.z

        val tDeltaX = stepX / direction.x
        val tDeltaY = stepY / direction.y
        val tDeltaZ = stepZ / direction.z

        val world = player.world
        var box: Box
        while (startPos.distanceTo(currentPos.toCenterPos()) <= reachDistance) {
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
        }
        return null
    }

    fun isIgnoredBlock(state: BlockState): Boolean {
        val block = state.block
        return block is PlantBlock || block is SnowBlock || block is AirBlock || block is FluidBlock
    }

    private fun getHitFaceFromBox(hit: Vec3d, box: Box): Direction {
        val eps = 1e-7

        if (abs(hit.x - box.minX) <= eps) return Direction.WEST
        if (abs(hit.x - box.maxX) <= eps) return Direction.EAST
        if (abs(hit.y - box.minY) <= eps) return Direction.DOWN
        if (abs(hit.y - box.maxY) <= eps) return Direction.UP
        if (abs(hit.z - box.minZ) <= eps) return Direction.NORTH
        if (abs(hit.z - box.maxZ) <= eps) return Direction.SOUTH

        val dxMin = abs(hit.x - box.minX)
        val dxMax = abs(hit.x - box.maxX)
        val dyMin = abs(hit.y - box.minY)
        val dyMax = abs(hit.y - box.maxY)
        val dzMin = abs(hit.z - box.minZ)
        val dzMax = abs(hit.z - box.maxZ)

        var m = dxMin
        var d = Direction.WEST
        if (dxMax < m) {
            m = dxMax
            d = Direction.EAST
        }
        if (dyMin < m) {
            m = dyMin
            d = Direction.DOWN
        }
        if (dyMax < m) {
            m = dyMax
            d = Direction.UP
        }
        if (dzMin < m) {
            m = dzMin
            d = Direction.NORTH
        }
        if (dzMax < m) { /* m = dzMax; */
            d = Direction.SOUTH
        }

        return d
    }

}
