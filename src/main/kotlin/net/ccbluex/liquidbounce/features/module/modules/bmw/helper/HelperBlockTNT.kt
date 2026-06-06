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

package net.ccbluex.liquidbounce.features.module.modules.bmw.helper

import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFoodNoC0F
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection.isValidBlock
import net.ccbluex.liquidbounce.utils.block.placer.BlockPlacer
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.TntEntity
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.math.abs

object HelperBlockTNT : ToggleableConfigurable(ModuleHelper, "BlockTNT", true) {

    private val blockPlacer = tree(
        BlockPlacer(
            "Placer",
            ModuleHelper,
            Priority.NOT_IMPORTANT, ::slotFinder
        )
    )
    private val detectionRange by float("DetectionRange", 10.0f, 1.0f..20.0f, "blocks")
    private val buildTriggerRange by float("BuildTriggerRange", 3.0f, 1.0f..10.0f, "blocks")

    private val wallHeight by int("WallHeight", 2, 1..5, "blocks")
    private val wallWidth by int("WallWidth", 2, 1..5, "blocks")
    private val minBlockCount by int("MinBlockCount", 1, 0..8, "blocks")

    private val notDuringCombat by boolean("NotDuringCombat", true)
    private val preferHarderBlocks by boolean("PreferHarderBlocks", true)
    private var lastExplosivePos: Vec3d? = null
    private var lastWallCenter: BlockPos? = null
    private var lastExplosiveId: Int? = null
    private val knownExplosiveIds = mutableSetOf<Int>()
    private val cooldownTimer = Chronometer()
    private var wallPositions = mutableSetOf<BlockPos>()

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val player = mc.player ?: return@tickHandler
        val world = mc.world ?: return@tickHandler

        if (!canProceedWithBuilding()) return@tickHandler

        val nearbyExplosive = findNearbyExplosive(buildTriggerRange) ?: run {
            if (lastExplosiveId != null && findNearbyExplosive(detectionRange) == null) {
                blockPlacer.clear()
                lastExplosiveId = null
                lastWallCenter = null
            }
            return@tickHandler
        }

        lastWallCenter?.let { center ->
            if (player.pos.squaredDistanceTo(Vec3d.ofCenter(center)) > buildTriggerRange * buildTriggerRange) {
                blockPlacer.clear()
                lastWallCenter = null
                findNearbyExplosive(buildTriggerRange)?.let { explosive ->
                    if (canBuildWall(player, explosive.pos)) {
                        startNewWall(player, explosive)
                    }
                }
            }
        }

        if (!knownExplosiveIds.contains(nearbyExplosive.id) || needsRebuildForExplosive(nearbyExplosive, player)) {
            startNewWall(player, nearbyExplosive)
        }

        blockPlacer.update(wallPositions)

        if (blockPlacer.isDone() && wallPositions.isNotEmpty()) {
            if (lastExplosiveId != null) {
                cooldownTimer.reset()
                lastExplosiveId = null
                lastWallCenter = null
            }
            wallPositions.clear()
        }
    }

    private fun startNewWall(player: ClientPlayerEntity, nearbyExplosive: Entity) {
        if (!canBuildWall(player, nearbyExplosive.pos) || slotFinder(null) == null) {
            return
        }

        val wallPositionsToCheck = getWallPositions(player, nearbyExplosive.pos)
        lastWallCenter = wallPositionsToCheck.firstOrNull() ?: return

        wallPositions.clear()
        wallPositions.addAll(wallPositionsToCheck.filter { pos ->
            pos != BlockPos.ofFloored(nearbyExplosive.pos) && player.world.getBlockState(pos).isAir
        })

        lastExplosivePos = nearbyExplosive.pos
        lastExplosiveId = nearbyExplosive.id
        knownExplosiveIds.add(nearbyExplosive.id)
    }

    private fun canProceedWithBuilding(): Boolean {
        if (player.mainHandStack.item == Items.TNT) {
            return false
        }
        return !player.isUsingItem
            && (!notDuringCombat || !CombatManager.isInCombat)
            && !ModuleScaffold.running
            && !GrimNoSlowFoodNoC0F.working
    }

    private fun needsRebuildForExplosive(explosive: Entity, player: ClientPlayerEntity): Boolean {
        if (!knownExplosiveIds.contains(explosive.id)) return true
        val center = lastWallCenter ?: return true
        return player.blockPos != center
    }

    private fun findNearbyExplosive(range: Float): Entity? {
        val player = mc.player ?: return null
        val world = mc.world ?: return null
        val searchBox = Box(
            player.pos.x - range, player.pos.y - range, player.pos.z - range,
            player.pos.x + range, player.pos.y + range, player.pos.z + range
        )
        return world.getEntitiesByType(EntityType.TNT, searchBox) { e -> e is TntEntity && e.fuse > 0 }
            .plus(
                world.getEntitiesByType(EntityType.CREEPER, searchBox) { e ->
                    e is CreeperEntity && e.isIgnited && e.fuseSpeed > 0
                }
            )
            .filter { entity -> player.pos.squaredDistanceTo(entity.pos) <= range * range }
            .minByOrNull { it.pos.squaredDistanceTo(player.pos) }
    }

    @JvmStatic
    @Suppress("UnusedParameter", "unused")
    private fun slotFinder(pos: BlockPos?): HotbarItemSlot? {
        return Slots.OffhandWithHotbar
            .filter { slot ->
                val stack = slot.itemStack
                stack.count >= minBlockCount && stack.item != Items.TNT && isValidBlock(stack)
            }
            .maxByOrNull { slot ->
                val stack = slot.itemStack
                val block = (stack.item as? BlockItem)?.block
                val hardness = block?.hardness ?: 0f
                if (preferHarderBlocks) hardness else stack.count.toFloat()
            }
    }

    private fun getWallPositions(player: ClientPlayerEntity, explosivePos: Vec3d): List<BlockPos> {
        val playerPos = player.blockPos
        val direction = explosivePos.subtract(player.pos)
        val length = direction.length()
        val normalizedDirection = if (length > 0) direction.multiply(1.0 / length) else Vec3d.ZERO
        val wallDirection = calculateWallDirection(normalizedDirection)
        val wallCenter = playerPos.add(wallDirection.x.toInt(), 0, wallDirection.z.toInt())

        val wallPositions = mutableListOf<BlockPos>()
        for (y in 0 until wallHeight) {
            for (x in 0 until wallWidth) {
                for (z in 0 until wallWidth) {
                    val pos = wallCenter.add(x, y, z)
                    if (x == 0 && z == 0) {
                        wallPositions.add(pos)
                    } else if (wallWidth > 1) {
                        wallPositions.add(pos)
                    }
                }
            }
        }
        return wallPositions
    }

    private fun canBuildWall(player: ClientPlayerEntity, explosivePos: Vec3d): Boolean {
        val explosiveBlockPos = BlockPos.ofFloored(explosivePos)
        val wallPositionsToCheck = getWallPositions(player, explosivePos)

        if (wallPositionsToCheck.contains(explosiveBlockPos)) {
            return false
        }

        return wallPositionsToCheck.all { pos ->
            val state = player.world.getBlockState(pos)
            state != null && state.isAir
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

    override fun onDisabled() {
        blockPlacer.disable()
        wallPositions.clear()
        lastExplosivePos = null
        lastExplosiveId?.let { knownExplosiveIds.remove(it) }
        lastExplosiveId = null
        lastWallCenter = null
        cooldownTimer.reset()
    }

}
