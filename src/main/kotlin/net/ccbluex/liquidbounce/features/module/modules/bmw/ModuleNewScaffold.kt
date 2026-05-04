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

package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.features.processors.RotationProcessor
import net.ccbluex.liquidbounce.utils.aiming.utils.raycast
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.math.scale
import net.minecraft.block.*
import net.minecraft.client.util.InputUtil
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper.wrapDegrees
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import java.util.*
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

/* 不能使用的狗屎。有会修的帮我修一下然后私信我呵呵。 */
@Suppress("unused")
object ModuleNewScaffold : ClientModule("NewScaffold", Category.BMW) {

    private val telly by boolean("Telly", true)
    private val snap by boolean("Snap", false)
    private val rotateSpeed by float("RotateSpeed", 180f, 1f..180f)
    private val rotateBackSpeed by float("RotateBackSpeed", 180f, 1f..180f)
    private val tellyTick by int("TellyTicks", 1, 0..6, "ticks")

    private enum class BypassMode(override val choiceName: String) : NamedChoice {
        GRIM_AC("GrimAC"),
        ACA("ACA")
    }

    private val bypassMode by multiEnumChoice("BypassMode", EnumSet.of(BypassMode.GRIM_AC, BypassMode.ACA))
    private val swordBridge by boolean("SwordBridge", false)
    private val noSwing by boolean("NoSwing", false)

    private var wasSprinting = false
    private var airTick = 0
    private var yLevel = 0
    private var blockPos: BlockPos? = null
    private var enumFacing: Direction? = null
    private var oldSlot = -1
    private var originalItem: ItemStack? = null
    private var isPlacing = false
    private var originalSelectedSlot = -1

    private class ScaffoldRotationProcessor(val speed: Float) : RotationProcessor {
        override fun process(
            rotationTarget: RotationTarget,
            currentRotation: Rotation,
            targetRotation: Rotation
        ): Rotation {
            return currentRotation.towardsLinear(targetRotation, speed, rotateBackSpeed)
        }
    }

    private fun isMoving(): Boolean {
        return player.input.movementSideways != 0.0f || player.input.movementForward != 0.0f ||
            mc.options.forwardKey.isPressed ||
            mc.options.backKey.isPressed ||
            mc.options.leftKey.isPressed ||
            mc.options.rightKey.isPressed ||
            mc.options.jumpKey.isPressed
    }

    private fun getVec3d(pos: BlockPos, face: Direction): Vec3d {
        var x = pos.x.toDouble() + 0.5
        var y = pos.y.toDouble() + 0.5
        var z = pos.z.toDouble() + 0.5
        if (face != Direction.UP && face != Direction.DOWN) {
            y += 0.08
        } else {
            x += (-0.3..0.3).random()
            z += (-0.3..0.3).random()
        }
        if (face == Direction.WEST || face == Direction.EAST) {
            z += (-0.3..0.3).random()
        }
        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += (-0.3..0.3).random()
        }
        return Vec3d(x, y, z)
    }

    private fun isValidStack(stack: ItemStack): Boolean {
        if (stack.item !is BlockItem || stack.count <= 1) {
            return false
        }

        val block = (stack.item as BlockItem).block
        val defaultState = block.defaultState

        return when {
            !defaultState.isSolidSurface(world, BlockPos.ORIGIN, player, Direction.UP) -> {
                false
            }
            block is FallingBlock -> false
            else -> true
        }
    }

    private fun rotate(rotation: Rotation, speed: Float) {
        RotationManager.setRotationTarget(
            RotationTarget(
                rotation,
                processors = listOf(ScaffoldRotationProcessor(speed)),
                ticksUntilReset = 1,
                resetThreshold = 1f,
                considerInventory = true,
                movementCorrection = MovementCorrection.SILENT
            ),
            Priority.IMPORTANT_FOR_PLAYER_LIFE,
            ModuleNewScaffold
        )
    }

    private fun calculate(position: BlockPos, enumFacing: Direction): Rotation {
        val x = position.x.toDouble() + 0.5
        val y = position.y.toDouble() + 0.5
        val z = position.z.toDouble() + 0.5
        return calculate(
            player.pos,
            Vec3d(
                enumFacing.doubleVector.x * 0.5 + x,
                enumFacing.doubleVector.y * 0.5 + y,
                enumFacing.doubleVector.z * 0.5 + z
            )
        )
    }

    private fun calculate(from: Vec3d, to: Vec3d): Rotation {
        val diff = to.subtract(from)
        val distance = hypot(diff.x, diff.z)
        val yaw = (atan2(diff.z, diff.x) * 57.2957763671875).toFloat() - 90.0f
        val pitch = (-(atan2(diff.y, distance) * 57.2957763671875)).toFloat()
        return Rotation(yaw, pitch)
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (bypassMode.contains(BypassMode.GRIM_AC) && player.isSprinting) {
            wasSprinting = true
            player.isSprinting = false
        }
        var slotID = -1
        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            if (!isValidStack(stack)) continue
            slotID = i
            break
        }
        if (swordBridge && originalItem == null) {
            originalItem = player.getStackInHand(Hand.MAIN_HAND).copy()
            originalSelectedSlot = player.inventory.selectedSlot
        }
        if (slotID != -1 && player.inventory.selectedSlot != slotID) {
            player.inventory.selectedSlot = slotID
        }
        if (player.isOnGround) {
            yLevel = floor(player.y).toInt() - 1
        }
        getBlockInfo()
        if (telly) {
            if (player.isOnGround) {
                airTick = 0
                blockPos = null
                enumFacing = null
                val rotation = Rotation(player.yaw, player.pitch)
                rotate(rotation, rotateBackSpeed)
            } else {
                if (airTick.toFloat() >= tellyTick) {
                    val rotation = getRotation(blockPos!!, enumFacing!!)
                    val currentRotateSpeed = if (bypassMode.contains(BypassMode.ACA)) 90.0f else rotateSpeed
                    rotate(rotation, currentRotateSpeed)
                    place()
                }
                ++airTick
            }
        } else {
            if (blockPos == null) {
                rotate(
                    Rotation(
                        wrapDegrees((player.yaw - 180.0f)),
                        89.64f
                    ),
                    rotateSpeed
                )
            }
            if (onAir() || !snap) {
                val rotation: Rotation = getRotation(blockPos!!, enumFacing!!)
                val currentRotateSpeed = if (bypassMode.contains(BypassMode.ACA)) 90.0f else rotateSpeed
                rotate(rotation, currentRotateSpeed)
            }
            place()
        }
        if (bypassMode.contains(BypassMode.GRIM_AC) && wasSprinting && player.isOnGround && isMoving()) {
            player.isSprinting = true
            wasSprinting = false
        }
    }

    fun place() {
        if (!onAir()) {
            return
        }
        val currentRotation = RotationManager.currentRotation ?: player.rotation
        val hasRotated = raycast(currentRotation, player.blockInteractionRange).blockPos == blockPos
        if (hasRotated) {
            isPlacing = true
            val interactionResult = interaction.interactBlock(
                player,
                Hand.MAIN_HAND,
                BlockHitResult(getVec3d(blockPos!!, enumFacing!!), enumFacing, blockPos, false)
            )
            if (interactionResult == ActionResult.SUCCESS && !noSwing) {
                player.swingHand(Hand.MAIN_HAND)
            }
            isPlacing = false
        }
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (player.isOnGround && !mc.options.jumpKey.isPressed && isMoving() && telly) {
            event.jump = true
        }
    }

    private fun getYLevel(): Int {
        if (!mc.options.jumpKey.isPressed && isMoving() && player.velocity.y <= 0.25 && telly) {
            return yLevel
        }
        return floor(player.y).toInt() - 1
    }

    private fun getBlockInfo() {
        val baseVec: Vec3d = player.pos
        val base: BlockPos =
            BlockPos.ofFloored(baseVec.x, getYLevel().toDouble(), baseVec.z)
        val baseX = base.x
        val baseZ = base.z
        if (isSolidAndNonInteractive(world.getBlockState(base), base)) {
            return
        }
        if (checkBlock(baseVec, base)) {
            return
        }
        for (d in 1..6) {
            if (checkBlock(baseVec, BlockPos(baseX, getYLevel() - d, baseZ))) {
                return
            }
            for (x in 0..d) {
                for (z in 0..d - x) {
                    val y = d - x - z
                    for (rev1 in 0..1) {
                        for (rev2 in 0..1) {
                            if (!checkBlock(
                                    baseVec,
                                    BlockPos(
                                        baseX + (if (rev1 == 0) x else -x),
                                        getYLevel() - y,
                                        baseZ + (if (rev2 == 0) z else -z)
                                    )
                                )
                            ) continue
                            return
                        }
                    }
                }
            }
        }
    }

    private fun isSolidAndNonInteractive(state: BlockState, pos: BlockPos): Boolean {
        val hasCollision: Boolean = !state.getCollisionShape(world, pos).isEmpty
        val hasNoMenu = state.createScreenHandlerFactory(world, pos) == null
        return hasCollision && hasNoMenu
    }

    private fun checkBlock(baseVec: Vec3d, pos: BlockPos): Boolean {
        if (world.getBlockState(pos).block !is AirBlock && world.getBlockState(pos)
                .block !is LilyPadBlock
        ) {
            return false
        }
        if (pos.y > getYLevel()) {
            return false
        }
        val center = Vec3d(pos.x.toDouble() + 0.5, pos.y.toDouble(), pos.z.toDouble() + 0.5)
        for (dir in Direction.entries) {
            var relevant: Vec3d? = null
            val hit = center.add(
                dir.doubleVector.scale(0.5)
            )
            val baseBlock: BlockPos = pos.offset(dir)
            val baseBlockPos = BlockPos(baseBlock.x, baseBlock.y, baseBlock.z)
            if (!isSolidAndNonInteractive(
                    world.getBlockState(baseBlockPos),
                    baseBlockPos
                ) || !((hit.subtract(baseVec).also { relevant = it }).lengthSquared() <= 20.25) || !(relevant!!.dotProduct(
                    dir.doubleVector
                ) >= 0.0) || dir.opposite == Direction.UP && isMoving() && !mc.options.jumpKey.isPressed
            ) continue
            blockPos = BlockPos(baseBlock as Vec3i)
            enumFacing = dir.opposite
            return true
        }
        return false
    }

    override fun onEnabled() {
        oldSlot = player.inventory.selectedSlot
        if (swordBridge) {
            originalItem = player.getStackInHand(Hand.MAIN_HAND).copy()
            originalSelectedSlot = player.inventory.selectedSlot
        }
        airTick = 0
        blockPos = null
        enumFacing = null
    }

    override fun onDisabled() {
        val isHoldingShift: Boolean = InputUtil.isKeyPressed(
            mc.window.handle,
            mc.options.sneakKey.boundKey.code
        )
        mc.options.sneakKey.isPressed = isHoldingShift
        if (oldSlot != -1) {
            player.inventory.selectedSlot = oldSlot
        }
        originalItem = null
        originalSelectedSlot = -1
        isPlacing = false
    }

    private fun getRotation(pos: BlockPos, direction: Direction): Rotation {
        val rotations: Rotation = if (onAir()) {
            calculate(pos, direction)
        } else {
            calculate(player.pos, pos.up().toCenterPos())
        }
        val reverseYaw = Rotation(
            wrapDegrees((player.yaw - 180.0f)),
            rotations.pitch
        )
        val hasRotated = raycast(reverseYaw, player.blockInteractionRange).blockPos == pos
        if (hasRotated) {
            return reverseYaw
        }
        return rotations
    }

    private fun onAir(): Boolean {
        val baseVec = player.pos
        val base = BlockPos.ofFloored(baseVec.x, getYLevel().toDouble(), baseVec.z)
        return world.getBlockState(base).block is AirBlock ||
            world.getBlockState(base).block is LilyPadBlock
    }

}
