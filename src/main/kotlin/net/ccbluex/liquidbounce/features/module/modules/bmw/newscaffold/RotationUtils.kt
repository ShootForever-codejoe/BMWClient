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

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.abs

/**
 * 为 SSNGScaffold 提供的Rotation工具集合。
 */
object RotationUtils {

    /**
     * 计算一个能够看向 [pos] 指定 [face] 的 Rotation，并尽量贴近给定的 [yaw]/[pitch]。
     *
     * 实现思路：从玩家眼睛位置沿参考旋转方向投射一条射线，求其与方块面所在平面的
     * 交点，再将交点约束到该面的实际范围内，最终返回看向该约束点的 Rotation。
     * 这样可以在保证视线落在方块面上的前提下，让转头幅度最小。
     */
    fun getClosestToBlockFace(pos: BlockPos?, face: Direction?, yaw: Float, pitch: Float): Rotation? {
        if (pos == null || face == null) {
            return null
        }

        val eyes = player.eyePos
        val direction = Vec3d.fromPolar(pitch, yaw)

        // 计算 ray (eyes + t * direction, t > 0) 与 face 平面的交点
        val planePoint = getFacePlanePoint(pos, face)
        val normal = Vec3d.of(face.vector)
        val denom = direction.dotProduct(normal)

        // 如果射线与平面几乎平行，则直接看向面中心
        val intersection = if (abs(denom) < 1.0E-7) {
            planePoint
        } else {
            val t = planePoint.subtract(eyes).dotProduct(normal) / denom
            if (t <= 0) {
                // 射线反向，使用面中心
                planePoint
            } else {
                eyes.add(direction.multiply(t))
            }
        }

        // 将交点约束到面范围内
        val clamped = clampPointToFace(intersection, pos, face)
        return Rotation.lookingAt(point = clamped, from = eyes)
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

        return when (face) {
            Direction.DOWN, Direction.UP -> Vec3d(
                point.x.coerceIn(minX, maxX),
                if (face == Direction.DOWN) minY else maxY,
                point.z.coerceIn(minZ, maxZ)
            )
            Direction.NORTH, Direction.SOUTH -> Vec3d(
                point.x.coerceIn(minX, maxX),
                point.y.coerceIn(minY, maxY),
                if (face == Direction.NORTH) minZ else maxZ
            )
            Direction.WEST, Direction.EAST -> Vec3d(
                if (face == Direction.WEST) minX else maxX,
                point.y.coerceIn(minY, maxY),
                point.z.coerceIn(minZ, maxZ)
            )
        }
    }

    /**
     * 返回两个 yaw 之间的绝对角度差（0..180）。
     */
    fun normalizeYawDiff(yaw: Float, target: Float): Float {
        return abs(MathHelper.wrapDegrees(yaw - target))
    }

    /**
     * 返回从 [target] 到 [yaw] 的有符号角度差（-180..180）。
     */
    fun yawDiffDirectly(yaw: Float, target: Float): Double {
        return MathHelper.wrapDegrees(yaw - target).toDouble()
    }

    /**
     * 平滑限制：当差值绝对值小于 [step] 时返回差值本身，否则按符号返回 [step]
     */
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
