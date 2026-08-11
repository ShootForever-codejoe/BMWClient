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
package net.ccbluex.liquidbounce.features.module.modules.movement.noweb.modes

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.noweb.ModuleNoWeb
import net.ccbluex.liquidbounce.features.module.modules.movement.noweb.ModuleNoWeb.modes
import net.ccbluex.liquidbounce.features.module.modules.movement.noweb.NoWebMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raycast
import net.ccbluex.liquidbounce.utils.block.DIRECTIONS_EXCLUDING_DOWN
import net.ccbluex.liquidbounce.utils.block.doPlacement
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.block.immutable
import net.ccbluex.liquidbounce.utils.block.liquid.TimedPickupTracker
import net.ccbluex.liquidbounce.utils.block.liquid.planPlacementAtPos
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findClosestSlot
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.centerOnSide
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.block.CobwebBlock
import net.minecraft.fluid.Fluids
import net.minecraft.item.Items
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

object NoWebPlaceWater : NoWebMode("PlaceWater") {
    override val parent: ChoiceConfigurable<NoWebMode>
        get() = modes

    private const val MAX_TRACKED_WEBS = 8
    private const val PICKUP_TRACKER_CAPACITY = 16
    private const val SAME_WEB_RETRY_DELAY_MS = 250L

    private object Pickup : ToggleableConfigurable(this@NoWebPlaceWater, "Pickup", true) {
        // Keep a hard lower bound so water has enough time to spread at least one block.
        val pickupSpan by floatRange("PickupSpan", 0.8F..3.0F, 0.5F..20.0F, "s")
    }

    private val rotations = tree(RotationsConfigurable(this))
    private val pickupTracker = TimedPickupTracker(PICKUP_TRACKER_CAPACITY)
    private val trackedWebs = ObjectLinkedOpenHashSet<BlockPos>()

    private var currentAction: UseAction? = null
    private var lastSuccessfulWeb: BlockPos? = null
    private var lastSuccessfulAt = 0L

    init {
        tree(Pickup)
    }

    private data class UseAction(
        val slot: HotbarItemSlot,
        val rotation: Rotation,
        val resolveHitResult: (BlockHitResult) -> BlockHitResult?,
        val onSuccess: (BlockHitResult) -> Unit,
    )

    override fun disable() {
        SilentHotbar.resetSlot(this)
        resetState()
    }

    override fun handleEntityCollision(pos: BlockPos): Boolean {
        trackedWebs.add(pos.immutable)
        while (trackedWebs.size > MAX_TRACKED_WEBS) {
            trackedWebs.removeFirst()
        }

        // Do not cancel web slowdown in this mode.
        return false
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        resetState()
    }

    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        currentAction = null

        trackedWebs.removeIf { trackedPos -> trackedPos.getState()?.block !is CobwebBlock }

        val now = System.currentTimeMillis()
        val placeAction = Slots.OffhandWithHotbar.findClosestSlot(Items.WATER_BUCKET)?.let { waterSlot ->
            trackedWebs.firstNotNullOfOrNull { webPos ->
                if (lastSuccessfulWeb == webPos && now - lastSuccessfulAt <= SAME_WEB_RETRY_DELAY_MS) {
                    return@firstNotNullOfOrNull null
                }

                buildPlaceAction(webPos, waterSlot)
            }
        }

        currentAction = placeAction ?: buildPickupAction()

        val action = currentAction ?: return@handler
        RotationManager.setRotationTarget(
            action.rotation,
            configurable = rotations,
            priority = Priority.IMPORTANT_FOR_PLAYER_LIFE,
            provider = ModuleNoWeb,
        )
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val action = currentAction ?: return@handler
        val resolvedHitResult = action.resolveHitResult(raycast()) ?: return@handler

        SilentHotbar.selectSlotSilently(this, action.slot, 1)
        val onSuccess = {
            action.onSuccess(resolvedHitResult)
            true
        }

        doPlacement(
            resolvedHitResult,
            hand = action.slot.useHand,
            onItemUseSuccess = onSuccess,
            onPlacementSuccess = onSuccess,
        )

        currentAction = null
    }

    private fun buildPlaceAction(
        webPos: BlockPos,
        waterSlot: HotbarItemSlot,
    ): UseAction? {
        val webBox = Box(webPos)
        val eyes = player.eyePos

        return when {
            // Eye inside web => top placement is usually blocked by the web volume, so prefer side usage.
            webBox.contains(eyes) -> buildDirectionalPlaceAction(webPos, waterSlot, DIRECTIONS_EXCLUDING_DOWN)
            // Eye above web => standard "place on top" path is most reliable.
            eyes.y > webBox.maxY -> buildTopPlaceAction(webPos, waterSlot)
            // Otherwise keep side-only fallback to avoid clicking through the lower half.
            else -> buildDirectionalPlaceAction(webPos, waterSlot, Direction.HORIZONTAL)
        }
    }

    private fun buildTopPlaceAction(
        webPos: BlockPos,
        waterSlot: HotbarItemSlot,
    ): UseAction? {
        val plan = planPlacementAtPos(webPos.up(), waterSlot) ?: return null

        return UseAction(
            slot = plan.hotbarItemSlot,
            rotation = plan.placementTarget.rotation,
            resolveHitResult = { rayTraceResult ->
                if (plan.doesCorrespondTo(rayTraceResult)) rayTraceResult else null
            },
            onSuccess = {
                markWebPlacementSuccess(webPos)
                pickupTracker.record(plan.targetPos)
            },
        )
    }

    private fun buildDirectionalPlaceAction(
        webPos: BlockPos,
        waterSlot: HotbarItemSlot,
        directions: Array<Direction>,
    ): UseAction? {
        val side = pickBestSide(webPos, directions) ?: return null
        val faceCenter = Box(webPos).centerOnSide(side)
        val fallbackHitResult = BlockHitResult(faceCenter, side, webPos, false)

        return UseAction(
            slot = waterSlot,
            rotation = Rotation.lookingAt(point = faceCenter, from = player.eyePos),
            resolveHitResult = { rayTraceResult ->
                resolveDirectionalPlacementHitResult(rayTraceResult, webPos, side, fallbackHitResult)
            },
            onSuccess = { placementHitResult ->
                markWebPlacementSuccess(webPos)
                recordDirectionalWaterCandidates(webPos, side, placementHitResult)
            },
        )
    }

    private fun buildPickupAction(): UseAction? {
        if (!Pickup.enabled) {
            return null
        }

        val pickupSpanStartMs = (Pickup.pickupSpan.start * 1000.0F).toLong()
        val pickupSpanEndMs = (Pickup.pickupSpan.endInclusive * 1000.0F).toLong()

        pickupTracker.prune(pickupSpanEndMs, TimedPickupTracker.PickupFilter.WATER)

        val maxRangeSq = player.blockInteractionRange.sq()
        val pickupPos = pickupTracker.firstEligible(pickupSpanStartMs) { pos ->
            Box(pos).squaredMagnitude(player.eyePos) <= maxRangeSq
        } ?: return null

        val bucketSlot = Slots.OffhandWithHotbar.findClosestSlot(Items.BUCKET) ?: return null
        val pickupCenter = Vec3d.ofCenter(pickupPos)

        return UseAction(
            slot = bucketSlot,
            rotation = Rotation.lookingAt(point = pickupCenter, from = player.eyePos),
            resolveHitResult = { rayTraceResult ->
                resolvePickupHitResult(rayTraceResult, pickupPos, pickupCenter)
            },
            onSuccess = {
                pickupTracker.prune(0L, TimedPickupTracker.PickupFilter.WATER)
            },
        )
    }

    private fun markWebPlacementSuccess(webPos: BlockPos) {
        lastSuccessfulWeb = webPos
        lastSuccessfulAt = System.currentTimeMillis()
        trackedWebs.remove(webPos)
    }

    private fun recordDirectionalWaterCandidates(
        webPos: BlockPos,
        side: Direction,
        placementHitResult: BlockHitResult,
    ) {
        val sidePos = webPos.offset(side)
        val inferredWaterPos = placementHitResult.blockPos
        val candidates = linkedSetOf<BlockPos>(inferredWaterPos, webPos, sidePos)

        // Bucket placement near webs can resolve to adjacent cells depending on stance and hit face.
        // Track all six neighbors to keep pickup robust without branching per edge-case.
        for (direction in Direction.entries) {
            candidates += webPos.offset(direction)
        }

        candidates.forEach(pickupTracker::record)
    }

    private fun resolveDirectionalPlacementHitResult(
        rayTraceResult: BlockHitResult,
        webPos: BlockPos,
        side: Direction,
        fallbackHitResult: BlockHitResult,
    ): BlockHitResult {
        if (rayTraceResult.type != HitResult.Type.BLOCK) {
            return fallbackHitResult
        }

        val directWebFace = rayTraceResult.blockPos == webPos && rayTraceResult.side == side
        val oppositeAdjacentFace =
            rayTraceResult.blockPos == webPos.offset(side) && rayTraceResult.side == side.opposite

        return if (directWebFace || oppositeAdjacentFace) rayTraceResult else fallbackHitResult
    }

    private fun resolvePickupHitResult(
        rayTraceResult: BlockHitResult,
        pickupPos: BlockPos,
        pickupCenter: Vec3d,
    ): BlockHitResult {
        val fluidTraceResult = raycast(includeFluids = true)
        return when {
            // Prefer fluid-inclusive trace so we can hit source blocks hidden behind web geometry.
            fluidTraceResult.type == HitResult.Type.BLOCK && fluidTraceResult.blockPos == pickupPos -> {
                fluidTraceResult
            }

            rayTraceResult.type == HitResult.Type.BLOCK && rayTraceResult.blockPos == pickupPos -> {
                rayTraceResult
            }

            rayTraceResult.type == HitResult.Type.BLOCK && rayTraceResult.blockPos == pickupPos -> {
                // We clicked the neighbor face but the target cell is the source block.
                BlockHitResult(pickupCenter, rayTraceResult.side.opposite, pickupPos, false)
            }

            // Final fallback keeps vanilla-style right-click on the expected source cell.
            else -> BlockHitResult(pickupCenter, Direction.UP, pickupPos, false)
        }
    }

    private fun pickBestSide(
        webPos: BlockPos,
        directions: Array<Direction>,
    ): Direction? {
        return directions
            .filter { side ->
                val adjacentState = webPos.offset(side).getState() ?: return@filter false
                // Find a replaceable side to place water
                adjacentState.isAir || adjacentState.fluidState.fluid == Fluids.LAVA
            }
            .maxByOrNull { side ->
                player.rotationVector.dotProduct(side.doubleVector)
            }
    }

    private fun resetState() {
        currentAction = null
        trackedWebs.clear()
        pickupTracker.clear()
    }
}
