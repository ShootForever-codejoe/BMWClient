package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBlock
import net.ccbluex.liquidbounce.utils.block.doPlacement
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockOffsetOptions
import net.ccbluex.liquidbounce.utils.block.targetfinding.BlockPlacementTargetFindingOptions
import net.ccbluex.liquidbounce.utils.block.targetfinding.CenterTargetPositionFactory
import net.ccbluex.liquidbounce.utils.block.targetfinding.FaceHandlingOptions
import net.ccbluex.liquidbounce.utils.block.targetfinding.PlayerLocationOnPlacement
import net.ccbluex.liquidbounce.utils.block.targetfinding.findBestBlockPlacementTarget
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.ItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.EntityType
import net.minecraft.entity.TntEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import kotlin.math.abs

@Suppress("unused")
object ModuleAntiTNT : ClientModule("AntiTNT", Category.BMW) {

    // 检测范围
    private val detectionRange by float("DetectionRange", 10.0f, 1.0f..20.0f)
    // 建造触发范围
    private val buildTriggerRange by float("BuildTriggerRange", 3.0f, 1.0f..10.0f)
    // 墙的高度
    private val wallHeight by int("WallHeight", 2, 1..5)
    // 自动切换方块
    private val autoSwitch by boolean("AutoSwitch", true)
    // 强制切换槽位
    private val forceSlotSwitch by boolean("ForceSlotSwitch", true)
    // 立即切换槽位
    private val immediateSlotSwitch by boolean("ImmediateSlotSwitch", true)
    // 平滑转头配置
    private val rotations = RotationsConfigurable(this)

    private var isBuildingWall = false
    private var wallPositions = mutableListOf<BlockPos>()
    private var currentWallIndex = 0
    private var lastTntPos: Vec3d? = null

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (isBuildingWall) {
            continueBuildingWall(player)
        }

        // 检测附近的TNT（使用建造触发范围来开始建造）
        val nearbyTnt = findNearbyTNT(buildTriggerRange)

        if (nearbyTnt != null && !isBuildingWall) {
            // 立即切换到方块槽位
            if (immediateSlotSwitch) {
                switchToBlockSlot(player)
            }

            // 开始建造防护墙
            startBuildingWall(player, nearbyTnt.pos)
            notifyAsMessage("[AntiTNT] Found TNT within $buildTriggerRange blocks, start building protecting wall")
        } else if (isBuildingWall) {
            // 检查TNT是否还存在（使用更大的检测范围）
            val tntStillExists = findNearbyTNT(detectionRange)

            if (tntStillExists == null) {
                // TNT已经爆炸或超出检测范围，停止建造
                stopBuildingWall()
            }
        }
    }

    private fun stopBuildingWall() {
        isBuildingWall = false
        wallPositions.clear()
        currentWallIndex = 0
        notifyAsMessage("[AntiTNT] TNT has exploded or been without $detectionRange blocks, stop building protecting wall")
    }

    private fun findNearbyTNT(
        range: Float
    ): TntEntity? {
        val searchBox = Box(
            player.pos.x - range, player.pos.y - range, player.pos.z - range,
            player.pos.x + range, player.pos.y + range, player.pos.z + range
        )

        return world.getEntitiesByType(
            EntityType.TNT,
            searchBox
        ) { entity -> entity is TntEntity && entity.fuse > 0 }.filter { tnt ->
            val distance = player.pos.squaredDistanceTo(tnt.pos)
            distance <= range * range
        }.minByOrNull { it.pos.squaredDistanceTo(player.pos) }
    }

    private fun switchToBlockSlot(player: ClientPlayerEntity) {
        if (!forceSlotSwitch) return

        // 查找最佳方块槽位
        val blockSlot = findBestBlockSlot()

        // 切换到方块槽位
        if (blockSlot is HotbarItemSlot) {
            player.inventory.selectedSlot = blockSlot.hotbarSlot
        }
    }

    private fun findBestBlockSlot(): ItemSlot? {
        return if (autoSwitch) {
            // 优先查找石头、黑石、圆石等硬方块
            Slots.OffhandWithHotbar.findSlot { itemStack ->
                itemStack.item is BlockItem && itemStack.count > 0 &&
                    (itemStack.item == Items.STONE ||
                        itemStack.item == Items.BLACKSTONE ||
                        itemStack.item == Items.COBBLESTONE ||
                        itemStack.item == Items.OBSIDIAN)
            } ?: Slots.OffhandWithHotbar.findSlot { itemStack ->
                itemStack.item is BlockItem && itemStack.count > 0
            }
        } else {
            Slots.OffhandWithHotbar.findSlot { itemStack ->
                itemStack.item is BlockItem && itemStack.count > 0
            }
        }
    }

    private fun startBuildingWall(player: ClientPlayerEntity, tntPos: Vec3d) {
        isBuildingWall = true
        currentWallIndex = 0
        lastTntPos = tntPos

        // 计算防护墙位置（2x2的墙）
        val playerPos = player.blockPos
        val direction = tntPos.subtract(player.pos)
        val length = direction.length()
        val normalizedDirection = if (length > 0) direction.multiply(1.0 / length) else Vec3d.ZERO

        // 确定墙的方向（面向TNT）
        val wallDirection = calculateWallDirection(normalizedDirection)

        // 计算多层2x2墙的位置
        val wallCenter = playerPos.add(
            wallDirection.x.toInt(),
            0,
            wallDirection.z.toInt()
        )

        wallPositions.clear()

        // 为每一层添加方块位置
        for (y in 0 until wallHeight) {
            val basePos = wallCenter.add(0, y, 0)
            wallPositions.addAll(
                listOf(
                    basePos,
                    basePos.add(1, 0, 0),
                    basePos.add(0, 0, 1),
                    basePos.add(1, 0, 1)
                )
            )
        }

        // 过滤掉已经有方块的位置
        wallPositions.removeAll { pos ->
            world.getBlockState(pos).isAir
        }
    }

    private fun calculateWallDirection(normalizedDirection: Vec3d): Vec3d {
        return when {
            abs(normalizedDirection.x) > abs(normalizedDirection.z) -> {
                if (normalizedDirection.x > 0) Vec3d(1.0, 0.0, 0.0) else Vec3d(-1.0, 0.0, 0.0)
            }

            else -> {
                if (normalizedDirection.z > 0) Vec3d(0.0, 0.0, 1.0) else Vec3d(0.0, 0.0, -1.0)
            }
        }
    }

    private fun continueBuildingWall(player: ClientPlayerEntity) {
        if (currentWallIndex >= wallPositions.size) {
            // 建造完成
            isBuildingWall = false
            wallPositions.clear()
            currentWallIndex = 0
            notifyAsMessage("[AntiTNT] Finish building protecting wall")
            return
        }

        val targetPos = wallPositions[currentWallIndex]

        // 查找方块快捷栏
        val blockSlot = findBlockSlot() ?: return

        // 确保使用正确的方块槽位
        if (forceSlotSwitch && blockSlot is HotbarItemSlot &&
            player.inventory.selectedSlot != blockSlot.hotbarSlot
        ) {
            player.inventory.selectedSlot = blockSlot.hotbarSlot
        }

        // 放置方块
        placeBlockAtPosition(player, targetPos, blockSlot)

        // 移动到下一个位置
        currentWallIndex++

        // 显示建造进度
        if (currentWallIndex % 4 == 0) {
            val progress = (currentWallIndex * 100 / wallPositions.size).coerceAtMost(100)
            notifyAsMessage("[AntiTNT] Building progress: $progress%")
        }
    }

    private fun findBlockSlot(): ItemSlot? {
        return if (autoSwitch) {
            // 优先查找石头、黑石、圆石等硬方块
            Slots.OffhandWithHotbar.findSlot { itemStack ->
                itemStack.item is BlockItem && itemStack.count > 0 &&
                    (itemStack.item == Items.STONE ||
                        itemStack.item == Items.BLACKSTONE ||
                        itemStack.item == Items.COBBLESTONE ||
                        itemStack.item == Items.OBSIDIAN)
            } ?: Slots.OffhandWithHotbar.findSlot { itemStack ->
                itemStack.item is BlockItem && itemStack.count > 0
            }
        } else {
            Slots.OffhandWithHotbar.findSlot { itemStack ->
                itemStack.item is BlockItem && itemStack.count > 0
            }
        }
    }

    private fun placeBlockAtPosition(
        player: ClientPlayerEntity,
        targetPos: BlockPos,
        blockSlot: ItemSlot
    ) {
        // 使用方块放置API
        val itemStack = blockSlot.itemStack
        val searchOptions = BlockPlacementTargetFindingOptions(
            BlockOffsetOptions(
                listOf(Vec3i.ZERO),
                BlockPlacementTargetFindingOptions.Companion.PRIORITIZE_LEAST_BLOCK_DISTANCE,
            ),
            FaceHandlingOptions(CenterTargetPositionFactory),
            stackToPlaceWith = itemStack,
            PlayerLocationOnPlacement(position = player.pos),
        )

        val placementTarget = findBestBlockPlacementTarget(targetPos, searchOptions) ?: return

        // 计算朝向目标位置的旋转
        val rotation = placementTarget.rotation

        // 平滑转头
        RotationManager.setRotationTarget(
            rotations.toRotationTarget(rotation),
            Priority.IMPORTANT_FOR_USAGE_3,
            this@ModuleAntiTNT
        )

        // 射线追踪获取放置目标
        val rayTraceResult = raytraceBlock(
            4.5,
            rotation,
            placementTarget.interactedBlockPos,
            placementTarget.interactedBlockPos.getState()!!
        ) ?: return

        // 放置方块 - 根据槽位类型确定使用的手
        val hand = if (blockSlot is HotbarItemSlot) {
            blockSlot.useHand
        } else {
            Hand.MAIN_HAND // 默认使用主手
        }

        doPlacement(rayTraceResult, hand = hand)
    }
}
