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

import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d

internal class FallingPlayer(private val player: ClientPlayerEntity) {

    var x: Double = player.x
        private set
    var y: Double = player.y
        private set
    var z: Double = player.z
        private set

    var motion: Vec3d = player.velocity
        private set

    var eyePos: Vec3d = player.eyePos
        private set

    private val yaw = (RotationManager.currentRotation ?: player.rotation).yaw
    private val strafe = player.input.movementSideways
    private val forward = player.input.movementForward
    private val jumpMovementFactor = if (player.isSprinting) 0.026f else 0.02f

    fun calculate(ticks: Int) {
        repeat(ticks) {
            calculateForTick()
        }
    }

    private fun calculateForTick() {
        updateVelocity(jumpMovementFactor, Vec3d(strafe.toDouble(), 0.0, forward.toDouble()))

        x += motion.x
        y += motion.y
        z += motion.z

        motion = motion.add(0.0, -GRAVITY, 0.0)
        eyePos = Vec3d(x, y + player.dimensions.eyeHeight, z)
        motion = Vec3d(
            motion.x * HORIZONTAL_DRAG,
            motion.y * VERTICAL_DRAG,
            motion.z * HORIZONTAL_DRAG
        )
    }

    private fun updateVelocity(speed: Float, input: Vec3d) {
        val lengthSquared = input.lengthSquared()
        if (lengthSquared < 1.0E-7) {
            return
        }

        val normalizedInput = (if (lengthSquared > 1.0) input.normalize() else input).multiply(speed.toDouble())
        val sinYaw = MathHelper.sin(yaw * (Math.PI.toFloat() / 180f))
        val cosYaw = MathHelper.cos(yaw * (Math.PI.toFloat() / 180f))
        val inputX = normalizedInput.x * cosYaw - normalizedInput.z * sinYaw
        val inputZ = normalizedInput.z * cosYaw + normalizedInput.x * sinYaw

        motion = motion.add(inputX, 0.0, inputZ)
    }

    private companion object {
        const val GRAVITY = 0.08
        const val HORIZONTAL_DRAG = 0.91
        const val VERTICAL_DRAG = 0.98
    }
}
