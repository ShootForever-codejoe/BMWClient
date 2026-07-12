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
import net.ccbluex.liquidbounce.bmw.simulatePlayerMovement
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.*
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.client.toRadians
import net.ccbluex.liquidbounce.utils.entity.airTicks
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.onGroundTicks
import net.ccbluex.liquidbounce.utils.item.isFullBlock
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.block.*
import net.minecraft.item.BlockItem
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
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

    private val mode by enumChoice("Mode", Mode.TELLY)
    private val alwaysUpdateRot by boolean("AlwaysUpdateRotation", true)
    private val placeTick by int("PlaceTick", 1, 1..5, "ticks")
    private val rotTick by int("RotationTick", 1, 1..5, "ticks")
    private val noSwing by boolean("NoSwing", false)
    private val eagle by boolean("Eagle", false)
    private val snap by boolean("Snap", false)
    private val noUpTelly by boolean("NoUpTelly", false)
    private val smoothed by boolean("HeypixelUpTelly", true)
    private val safeMode by boolean("SafeMode", false)
    private val testOnGround by boolean("TestOnGround", false)
    private val randomSlow by boolean("SlowUpTelly", false)
    private val blockSlotMode by enumChoice("BlockSlotMode", BlockSlotMode.FARTHEST)
    private val jumpMode by enumChoice("JumpMode", JumpMode.NORMAL)
    private val safeDistance by float("ClutchSafeDistance", 4.5f, 1.0f..5.0f)
    private val tellyEagleTick by int("EagleTick", 1, 1..5)
    private val keepEagleSneakTick by int("KeepEagleTick", 1, 1..5)
    private val debug by boolean("Debug", false)
    private val duplicateRotPlace by boolean("DuplicateRotPlace", true)
    private val interactItem by boolean("InteractItemBeforePlace", false)

    private var slot: SlotData? = null
    private var blockSlot: SlotData? = null
    private var canPlace = false
    private var blockData: BlockData? = null
    private var lastBlockData: BlockData? = null
    private var rotateCount = 0
    private var posY = 0.0
    private var lastPlacePosition: BlockPos? = null
    private var tellyJumpTicks = 0
    private var waitingForEagleSneak = false
    private var lastRotation: Rotation? = null
    private var rot: Rotation? = null
    private var boxExpand = 0.15
    private var oldSlot = 0
    private var placeCount = 0
    private var ups = 0
    private var skipTick = false
    private var lastPlacePitchDiff = 0.0

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
        lastPlacePosition = null
        tellyJumpTicks = 0
        waitingForEagleSneak = false
        rot = null
        skipTick = false
        lastPlacePitchDiff = 0.0
    }

    override fun onDisabled() {
        player.inventory.selectedSlot = slot!!.slot
        player.inventory.selectedSlot = oldSlot
        mc.options.sneakKey.isPressed = false
    }

    @Suppress("unused")
    private val playerTickHandler = handler<PlayerTickEvent> { event ->
        if (skipTick) {
            event.cancelEvent()
        }
    }

    @Suppress("unused")
    private val strafeHandler = handler<PlayerVelocityStrafe> {
        if (this.blockSlot == null || blockSlot!!.check()) {
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
            if (this.blockSlot!!.hand == Hand.MAIN_HAND) {
                player.inventory.selectedSlot = this.blockSlot!!.slot
                interaction.syncSelectedSlot()
            }
            if (duplicateRotPlace && abs(player.pitch - player.lastPitch) > 2.0) {
                val xDiff: Double = abs(abs(player.pitch - player.lastPitch) - lastPlacePitchDiff)
                if (xDiff < 0.0001) {
                    return
                }
            }
            if (interactItem) {
                interaction.interactItem(player, Hand.MAIN_HAND)
            }
            val result = interaction.interactBlock(player, this.blockSlot!!.hand, block)
            if (result == ActionResult.SUCCESS) {
                placeCount++
                lastPlacePosition = blockData!!.pos.offset(blockData!!.facing)
                if (abs(player.pitch - player.lastPitch) > 0.0) {
                    lastPlacePitchDiff = abs(player.pitch - player.lastPitch).toDouble()
                }
                if (noSwing) {
                    network.sendPacket(HandSwingC2SPacket(this.blockSlot!!.hand))
                } else {
                    player.swingHand(this.blockSlot!!.hand)
                }
            }
        }
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        this.blockSlot = null

        if (player.offHandStack.isFullBlock() && ScaffoldBlockItemSelection.isValidBlock(
                player.offHandStack
            )
        ) {
            this.blockSlot = SlotData(99, Hand.OFF_HAND)
        }

        if (blockSlot == null && blockSlotMode != BlockSlotMode.MOST_BLOCKS) {
            if (player.mainHandStack.isFullBlock() && ScaffoldBlockItemSelection.isValidBlock(
                    player.mainHandStack
                )
            ) {
                this.blockSlot = SlotData(player.inventory.selectedSlot, Hand.MAIN_HAND)
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

        if (this.blockSlot!!.hand == Hand.MAIN_HAND) {
            player.inventory.selectedSlot = this.blockSlot!!.slot
        }
        val simResult1 = simulatePlayerMovement(ticks = 1) // 玩家预测
        var reachable = true
        val nextEyePos: Vec3d = simResult1.position.add(0.0, player.standingEyeHeight.toDouble(), 0.0) // 1tick后的eye pos
        val simResult2 = simulatePlayerMovement(ticks = 2) // 再预测2tick
        val predictedY = simResult2.position.y // 2tick后的Y坐标
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
        if (didHitBlockFace(blockData, rot!!)) {
            skipTick = false
            rotateCount = 0
        }
        rot = rot?.normalize()
        if (rot != null) {
            RotationManager.setRotationTarget(
                RotationTarget(
                    rotation = rot!!,
                    ticksUntilReset = 1,
                    resetThreshold = 1f,
                    considerInventory = true,
                    movementCorrection = MovementCorrection.SILENT,
                    whenReached = RestrictedSingleUseAction({ true }) {
                        place()
                    }
                ),
                Priority.IMPORTANT_FOR_PLAYER_LIFE,
                ModuleNewScaffold
            )
        }
        if (blockData == null) return@handler
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
        return blockData == null || !ClientRayTraceUtil.didHitBlockFace(rot, blockData.pos, blockData.facing, true)
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
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
            if (stack.isFullBlock() && ScaffoldBlockItemSelection.isValidBlock(stack)) {
                slot = i
            }
        }
        return slot
    }

    private fun getMostBlocksHotbarSlot(): Int {
        val selectedSlot = player.inventory.selectedSlot
        var bestSlot = -1
        var bestCount = -1

        val selectedStack = player.inventory.getStack(selectedSlot)
        if (selectedStack.isFullBlock() && ScaffoldBlockItemSelection.isValidBlock(selectedStack)) {
            bestSlot = selectedSlot
            bestCount = selectedStack.count
        }

        for (i in 0..8) {
            val stack = player.inventory.getStack(i)
            if (stack.isFullBlock() && ScaffoldBlockItemSelection.isValidBlock(stack) && stack.count > bestCount) {
                bestSlot = i
                bestCount = stack.count
            }
        }
        return bestSlot
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
