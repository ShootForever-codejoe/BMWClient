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
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.*
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.toRadians
import net.ccbluex.liquidbounce.utils.entity.airTicks
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.onGroundTicks
import net.ccbluex.liquidbounce.utils.item.isFullBlock
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.block.*
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.ArrayDeque
import java.util.function.ToDoubleFunction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 抄ssng的，抄不好，总是坠机。谁修好了找我获取10个软妹币
 */
@Suppress("unused")
object ModuleNewScaffold : ClientModule("NewScaffold", Category.BMW) {

    private data class BlockData(val pos: BlockPos, val facing: Direction)

    private enum class Mode(override val choiceName: String) : NamedChoice {
        TELLY("Telly"),
        SNAP("Snap"),
        NORMAL("Normal")
    }

    private enum class BlockSlotMode(override val choiceName: String) : NamedChoice {
        FARTHEST("Farthest"),
        MOST_BLOCKS("MostBlocks")
    }

    private enum class JumpMode(override val choiceName: String) : NamedChoice {
        NORMAL("Normal"),
        PARKOUR("Parkour"),
        NONE("None")
    }

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
    private val safeMode by boolean("SafeMode", true)
    private val testOnGround by boolean("TestOnGround", true)
    private val fixRotation by boolean("FixRotation", false)
    private val randomSlow by boolean("SlowUpTelly", false)
    private val abuseRotation by boolean("AbuseRotation", false)
    private val blockSlotMode by enumChoice("BlockSlotMode", BlockSlotMode.MOST_BLOCKS)
    private val jumpMode by enumChoice("JumpMode", JumpMode.NORMAL)
    private val safeDistance by float("ClutchSafeDistance", 4.25f, 1.0f..5.0f)
    private val tellyEagleTick by int("EagleTick", 1, 1..5)
    private val keepEagleSneakTick by int("KeepEagleTick", 1, 1..5)
    private val debug by boolean("Debug", true)
    private val keepFov by boolean("KeepFov", true)
    private val fov by float("Fov", 1.1f, 1.0f..2.1f)
    private val duplicateRotPlace by boolean("DuplicateRotPlace", true)
    private val interactItem by boolean("InteractItemBeforePlace", true)
    private val mark by boolean("Mark", true)
    private val markSideColor by color("MarkSideColor", Color4b(255, 48, 48, 70))
    private val markLineColor by color("MarkLineColor", Color4b(255, 48, 48, 150))
    private val markDuration by int("MarkDuration", 1000, 100..3000, "ms")
    private val markFadeIn by int("MarkFadeIn", 100, 0..500, "ms")

    private data class PlacementMark(val position: BlockPos, val time: Long)

    private var slot: SlotData? = null
    private var blockSlot: SlotData? = null
    private var canPlace = false
    private var blockData: BlockData? = null
    private var lastBlockData: BlockData? = null
    private var rotateCount = 0
    private var posY = 0.0
    private val placementMarks = ArrayDeque<PlacementMark>()
    private var tellyJumpTicks = 0
    private var waitingForEagleSneak = false
    private var lastRotation: Rotation? = null
    private var rot: Rotation? = null
    private var boxExpand = 0.15
    private var oldSlot = 0
    private var placeCount = 0
    private var ups = 0
    private var skipTick = false


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

    override fun onEnabled() {
        placeCount = 0
        ups = 0
        boxExpand = (0.1..0.2).random()
        lastRotation = Rotation(player.yaw, player.pitch)
        slot = SlotData(player.inventory.selectedSlot, Hand.MAIN_HAND)
        oldSlot = player.inventory.selectedSlot
        blockSlot = null
        blockData = null
        canPlace = true
        placementMarks.clear()
        tellyJumpTicks = 0
        waitingForEagleSneak = false
        rot = null
        skipTick = false
        SilentHotbar.resetSlot(this)
    }

    override fun onDisabled() {
        slot?.let { player.inventory.selectedSlot = it.slot }
        player.inventory.selectedSlot = oldSlot
        SilentHotbar.resetSlot(this)
        mc.options.sneakKey.isPressed = false
        skipTick = false
    }

    @JvmStatic
    fun getFovMultiplier(original: Float): Float {
        val clientPlayer = mc.player ?: return original
        if (!running || !keepFov || !clientPlayer.moving) return original
        val speedLevel = (clientPlayer.getStatusEffect(StatusEffects.SPEED)?.amplifier ?: -1) + 1
        return fov + speedLevel * 0.13f
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        if (!mark) {
            placementMarks.clear()
            return@handler
        }
        val now = System.currentTimeMillis()
        while (placementMarks.isNotEmpty() && now - placementMarks.first.time >= markDuration) {
            placementMarks.removeFirst()
        }
        if (placementMarks.isEmpty()) return@handler
        val cameraOffset = mc.entityRenderDispatcher.camera.pos.negate()
        val fadeIn = markFadeIn.coerceAtMost(markDuration)
        renderEnvironmentForWorld(event.matrixStack) {
            placementMarks.forEach { markData ->
                val elapsed = now - markData.time
                val opacity = if (fadeIn > 0 && elapsed < fadeIn) {
                    elapsed.toFloat() / fadeIn
                } else {
                    1f - (elapsed - fadeIn).toFloat() / (markDuration - fadeIn).coerceAtLeast(1)
                }.coerceIn(0f, 1f)
                val smoothOpacity = opacity * opacity * (3f - 2f * opacity)
                val box = Box(markData.position).offset(cameraOffset)
                drawBox(box, markSideColor.fade(smoothOpacity), markLineColor.fade(smoothOpacity))
            }
        }
    }

    @Suppress("unused")
    private val strafeHandler = handler<PlayerVelocityStrafe> {
        if (this.blockSlot == null || blockSlot!!.check()) {
            skipTick = false
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
        if (lastRotation != null && blockData != null && ClientRayTraceUtil.didHitBlockFace(
                player,
                lastRotation!!.yaw,
                lastRotation!!.pitch,
                blockData!!.pos,
                blockData!!.facing,
                true
            )
        ) {
            return lastRotation!!
        }
        if (lastRotation != null && blockData != null && !alwaysUpdateRot && player.airTicks >= rotTick) {
            if (!ClientRayTraceUtil.didHitBlockFace(
                    player,
                    rotation!!.yaw,
                    rotation.pitch,
                    blockData!!.pos,
                    blockData!!.facing,
                    true
                ) && player.airTicks >= rotTick
            ) {
                lastRotation!!.yaw += Math.random().toFloat()
                return lastRotation!!
            }
        }
        lastRotation = rotation
        return rotation!!
    }

    private fun place() {
        if (blockData != null) {
            if (!canPlace) {
                return
            }
            val block: BlockHitResult? =
                ClientRayTraceUtil.getFacedBlock(
                    RotationManager.currentRotation?.yaw ?: player.yaw,
                    RotationManager.currentRotation?.pitch ?: player.pitch
                )
            if (!ClientRayTraceUtil.didHitBlockFace(
                    player,
                    RotationManager.currentRotation?.yaw ?: player.yaw,
                    RotationManager.currentRotation?.pitch ?: player.pitch,
                    blockData!!.pos,
                    blockData!!.facing,
                    true
                )
            ) {
                return
            }
            selectBlockSlot()
            if (interactItem) {
                interaction.interactItem(player, Hand.MAIN_HAND)
            }
            val result = interaction.interactBlock(player, this.blockSlot!!.hand, block)
            if (result.isAccepted) {
                placeCount++
                if (mark) {
                    val markData = PlacementMark(
                        blockData!!.pos.offset(blockData!!.facing),
                        System.currentTimeMillis()
                    )
                    placementMarks.removeIf { it.position == markData.position }
                    placementMarks.addLast(markData)
                }
                if (noSwing) {
                    network.sendPacket(HandSwingC2SPacket(this.blockSlot!!.hand))
                } else {
                    player.swingHand(this.blockSlot!!.hand)
                }
            }
        }
    }


    private fun selectBlockSlot() {
        val selected = blockSlot ?: return
        if (selected.hand != Hand.MAIN_HAND) {
            return
        }

        if (spoofItem) {
            SilentHotbar.selectSlotSilently(this, selected.slot, 2)
        } else {
            SilentHotbar.resetSlot(this)
            player.inventory.selectedSlot = selected.slot
        }
        interaction.syncSelectedSlot()
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        this.blockSlot = null

        if (isValidBlock(player.offHandStack)) {
            this.blockSlot = SlotData(99, Hand.OFF_HAND)
        }

        if (blockSlot == null && blockSlotMode != BlockSlotMode.MOST_BLOCKS) {
            val selectedSlot = if (SilentHotbar.isSlotModifiedBy(this)) {
                SilentHotbar.serversideSlot
            } else {
                player.inventory.selectedSlot
            }
            if (isValidBlock(player.inventory.getStack(selectedSlot))) {
                this.blockSlot = SlotData(selectedSlot, Hand.MAIN_HAND)
            }
        }

        if (blockSlot == null) {
            val hotbarSlot = getHotbarBlockSlot()
            if (hotbarSlot != -1) {
                this.blockSlot = SlotData(hotbarSlot, Hand.MAIN_HAND)
            }
        }

        if (this.blockSlot == null || blockSlot!!.check()) {
            return@handler
        }
        if (player.isOnGround) {
            posY = floor(player.y - 1)
        }

        if (mc.options.jumpKey.isPressed) {
            posY = player.blockY - 1.0
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
        ) getBlockData(
            BlockPos(
                floor(player.x).toInt(),
                posY.toInt(),
                floor(player.z).toInt()
            )
        ) else null
        if (possible != null) {
            blockData = possible
        }

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
        val fallingPlayer = FallingPlayer(player) // 使用纯空中预测器计算位置
        fallingPlayer.calculate(1)
        var reachable = true
        val nextEyePos = fallingPlayer.eyePos // 预测一 tick 后的视线位置
        fallingPlayer.calculate(1)
        val predictedY = fallingPlayer.y // 预测两 tick 后的高度
        val placement: BlockData? = getBlockData(
            BlockPos(
                floor(player.x).toInt(), (player.blockY - 1), floor(
                    player.z
                ).toInt()
            )
        ) // 无samey搜寻方块
        var forceRotation = false
        if (placement != null) {
            if (safeMode && testOnGround && player.onGroundTicks == 1 && mc.options.jumpKey.isPressed) {
                forceRotation = true
            }
            val distance = nextEyePos.distanceTo(placement.pos.toCenterPos())
            if (distance >= safeDistance || placement.pos.y > predictedY) { // 这是大kb自救逻辑
                canPlace = true
                reachable = false
                lastBlockData = placement
                blockData = lastBlockData
            }
        }
        if (blockData != null) {
            val box = Box(this.blockData!!.pos)
                .withMinY(this.blockData!!.pos.y - 1.0)
                .withMaxY(this.blockData!!.pos.y + 1.0)
            if (blockData!!.pos.y > predictedY && !box.contains(player.pos)) { // 这是防止碰撞箱冲突
                canPlace = true
                reachable = false
                posY = player.blockY - 1.0 // 普通下落自救
                lastBlockData = getBlockData(
                    BlockPos(
                        floor(player.x).toInt(),
                        floor(posY).toInt(),
                        floor(player.z).toInt()
                    )
                )
                blockData = lastBlockData
            }
        }
        if (!reachable && rotateCount < 8) {
            if (debug && rotateCount == 1) {
                notifyAsMessage(ModuleNewScaffold, "Clutching...")
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
        }
        var rotationHitsTarget = didHitBlockFace(blockData, rot!!)
        if (!rotationHitsTarget && blockData != null && (safeMode || !reachable)) {
            var centerRotation = Rotation.lookingAt(
                blockData!!.pos.toCenterPos().add(Vec3d.of(blockData!!.facing.vector).multiply(0.5)),
                player.eyePos
            )
            if (fixRotation) {
                centerRotation = centerRotation.normalize()
            }
            if (didHitBlockFace(blockData, centerRotation)) {
                rot = centerRotation
                lastRotation = centerRotation
                rotationHitsTarget = true
            }
        }
        if (rotationHitsTarget) {
            skipTick = false
            rotateCount = 0
        }
        if (rot != null) {
            RotationManager.setRotationTarget(
                RotationTarget(
                    rotation = rot!!,
                    ticksUntilReset = 1,
                    resetThreshold = 1f,
                    considerInventory = true,
                    movementCorrection = MovementCorrection.SILENT,
                    whenReached = RestrictedSingleUseAction({ true }) {
                        if (abuseRotation) {
                            rotationAbuse(30f, RotationManager.currentRotation?.yaw ?: rot!!.yaw)
                        }
                        place()
                    }
                ),
                Priority.IMPORTANT_FOR_PLAYER_LIFE,
                ModuleNewScaffold
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
        if (waitingForEagleSneak) {
            tellyJumpTicks++
            if (tellyJumpTicks == tellyEagleTick && !mc.options.sneakKey.isPressed) {
                mc.options.sneakKey.isPressed = true
            }
            if (tellyJumpTicks == tellyEagleTick + keepEagleSneakTick) {
                mc.options.sneakKey.isPressed = false
                waitingForEagleSneak = false
                tellyJumpTicks = 0
            }
        }
    }

    private fun didHitBlockFace(blockData: BlockData?, rot: Rotation): Boolean {
        return blockData != null && ClientRayTraceUtil.didHitBlockFace(
            rot,
            blockData.pos,
            blockData.facing,
            true
        )
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
        if (skipTick) {
            event.directionalInput = DirectionalInput.NONE
        }
        if (mode == Mode.TELLY && eagle) {
            event.sneak = placeCount % 4 == 0
        }
    }

    @Suppress("unused")
    private val slowHandler = handler<PlayerUseMultiplier> {
        if (player.onGroundTicks == 1 && testOnGround && smoothed && !noUpTelly && safeMode && mc.options.jumpKey.isPressed) {
            player.input.movementForward *= 0.2f
            player.input.movementSideways *= 0.2f
        }
    }

    private fun doesNotContainBlock(): Boolean {
        return blockRelativeToPlayer().defaultState.isTransparent
    }

    private fun getHotbarBlockSlot(): Int {
        if (blockSlotMode == BlockSlotMode.MOST_BLOCKS) {
            return getMostBlocksHotbarSlot()
        }

        var slot = -1
        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            if (isValidBlock(stack)) {
                slot = i
            }
        }
        return slot
    }

    private fun getMostBlocksHotbarSlot(): Int {
        val selectedSlot = if (SilentHotbar.isSlotModifiedBy(this)) {
            SilentHotbar.serversideSlot
        } else {
            player.inventory.selectedSlot
        }
        var bestSlot = -1
        var bestCount = -1

        val selectedStack = player.inventory.getStack(selectedSlot)
        if (isValidBlock(selectedStack)) {
            bestSlot = selectedSlot
            bestCount = selectedStack.count
        }

        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            if (isValidBlock(stack) && stack.count > bestCount) {
                bestSlot = i
                bestCount = stack.count
            }
        }
        return bestSlot
    }

    private fun isValidBlock(stack: ItemStack): Boolean {
        val item = stack.item as? BlockItem ?: return false
        return stack.isFullBlock() && item.block !is FallingBlock && item.block !in invalidBlocks
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
            interaction.interactItem(player, Hand.MAIN_HAND)
        }
        appliedRotation.yaw = targetYaw
        if (times > 0) {
            interaction.interactItem(player, Hand.MAIN_HAND)
        }
    }

    private fun getBlockData(pos: BlockPos): BlockData? {
        val data: BlockData

        if (getPos(pos) == null) {
            val blockPos = getBlockPos()
            if (blockPos == null) return null

            val direction = getPlaceSide(blockPos)
            if (direction == null) return null

            data = BlockData(blockPos, direction)
        } else {
            data = getPos(pos)!!
        }

        if (ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(data.pos.offset(data.facing)))) {
            return data
        }

        return null
    }

    private fun getPlaceSide(blockPos: BlockPos): Direction? {
        val blockData = ArrayList<BlockData>()

        val pos = BlockPos(
            floor(player.x).toInt(),
            floor(player.y).toInt(),
            floor(player.z).toInt()
        )

        if (isAirBlock(blockPos.east()) && blockPos.east() != pos) {
            blockData.add(BlockData(blockPos.east(), Direction.EAST))
        }

        if (isAirBlock(blockPos.north()) && blockPos.north() != pos) {
            blockData.add(BlockData(blockPos.north(), Direction.NORTH))
        }

        if (isAirBlock(blockPos.south()) && blockPos.south() != pos) {
            blockData.add(BlockData(blockPos.south(), Direction.SOUTH))
        }

        if (isAirBlock(blockPos.west()) && blockPos.west() != pos) {
            blockData.add(BlockData(blockPos.west(), Direction.WEST))
        }

        if (blockData.isEmpty()) return null

        blockData.sortWith(Comparator.comparingDouble { vec3: BlockData ->
            vec3.pos.getSquaredDistance(pos)
        })

        blockData.removeIf { blockData1: BlockData ->
            !ClientRayTraceUtil.isIgnoredBlock(
                world.getBlockState(
                    blockData1.pos.offset(blockData1.facing)
                )
            )
        }

        return blockData.firstOrNull()?.facing
    }


    private fun getBlockPos(): BlockPos? {
        val playerPos = BlockPos(
            floor(player.x).toInt(),
            floor(player.y).toInt(),
            floor(player.z).toInt()
        )

        val positions = ArrayList<BlockPos?>()

        val searchBlock = searchBlocks()
        for (block in searchBlock.entries) {
            if (isPosSolid(block.key)) {
                positions.add(block.key)
            }
        }

        positions.removeIf { pos: BlockPos? -> pos!!.y >= playerPos.y }

        if (positions.isEmpty()) return null

        positions.sortWith(Comparator.comparingDouble<BlockPos?>(ToDoubleFunction { vec3: BlockPos? ->
            vec3!!.getSquaredDistance(
                playerPos
            )
        }))

        return positions.first()
    }

    private fun isAirBlock(blockPos: BlockPos?): Boolean {
        return ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(blockPos))
    }

    private fun getBlock(pos: BlockPos?): Block {
        return world.getBlockState(pos).block
    }

    private fun searchBlocks(): MutableMap<BlockPos, Block> {
        val blocks = HashMap<BlockPos, Block>()
        for (x in 5 downTo -4) {
            for (y in 5 downTo -4) {
                for (z in 5 downTo -4) {
                    val blockPos = BlockPos(player.blockX + x, player.blockY + y, player.blockZ + z)
                    val block = getBlock(blockPos)
                    blocks.put(blockPos, block)
                }
            }
        }
        return blocks
    }

    private fun getPos(pos: BlockPos): BlockData? {
        if (isPosSolid(pos.add(-1, 0, 0))) {
            return BlockData(pos.add(-1, 0, 0), Direction.EAST)
        } else if (isPosSolid(pos.add(1, 0, 0))) {
            return BlockData(pos.add(1, 0, 0), Direction.WEST)
        } else if (isPosSolid(pos.add(0, 0, 1))) {
            return BlockData(pos.add(0, 0, 1), Direction.NORTH)
        } else if (isPosSolid(pos.add(0, 0, -1))) {
            return BlockData(pos.add(0, 0, -1), Direction.SOUTH)
        } else if (isPosSolid(pos.add(0, -1, 0))) {
            return BlockData(pos.add(0, -1, 0), Direction.UP)
        }
        return null
    }

    private fun isPosSolid(pos: BlockPos?): Boolean {
        val block = world.getBlockState(pos).block
        if (block is TrapdoorBlock || block is DoorBlock || block is FenceGateBlock) {
            return false
        }
        return !listOf(
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
            Blocks.PLAYER_HEAD
        ).contains(block) && !ClientRayTraceUtil.isIgnoredBlock(world.getBlockState(pos))
    }

    private data class SlotData(val slot: Int, val hand: Hand) {
        fun check(): Boolean {
            if (hand == Hand.OFF_HAND) {
                val stack = player.offHandStack
                return stack.isEmpty || stack.item !is BlockItem
            }

            return player.inventory.getStack(slot).isEmpty
                    || player.inventory.getStack(slot).item !is BlockItem
        }
    }

    private fun blockRelativeToPlayer(): Block {
        val pos = BlockPos(
            floor(player.x).toInt(),
            floor(player.y).toInt() - 1,
            floor(player.z).toInt()
        )
        return player.world.getBlockState(pos).block
    }

}
