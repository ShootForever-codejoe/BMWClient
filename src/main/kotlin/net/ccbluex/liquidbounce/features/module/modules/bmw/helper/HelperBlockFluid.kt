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

import net.ccbluex.liquidbounce.bmw.PlacementManager
import net.ccbluex.liquidbounce.bmw.getWaterBucketSlot
import net.ccbluex.liquidbounce.bmw.topCenter
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ScaffoldBlockItemSelection.isValidBlock
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.block.searchBlocksInCuboid
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.math.iterator
import net.minecraft.fluid.Fluid
import net.minecraft.item.BlockItem
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.registry.Registries
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

class HelperBlockFluid(
    val fluid: Fluid,
) : ToggleableConfigurable(
    ModuleHelper,
    "Block${getFluidName(fluid).replaceFirstChar { it.uppercase() }}",
    true
) {

    companion object {
        fun getFluidName(fluid: Fluid): String {
            return Registries.FLUID.getId(fluid).path
        }
    }

    private val range by float("Range", 3f, 0f..5f)

    enum class ItemToBlock(override val choiceName: String) : NamedChoice {
        BLOCK("Block"),
        WATER("Water")
    }
    private val itemToBlock by enumChoice("ItemToBlock", ItemToBlock.BLOCK)

    private val onlyOnGround by boolean("OnlyOnGround", false)

    private val shouldBlock = mutableSetOf<BlockPos>()
    private var lastInteractTime: Long = 0

    override fun onEnabled() {
        shouldBlock.clear()
        lastInteractTime = 0
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        when (packet) {
            is PlayerInteractBlockC2SPacket -> {
                if (player.getStackInHand(packet.hand).item == fluid.bucketItem) {
                    lastInteractTime = System.currentTimeMillis()
                }
            }

            is PlayerInteractItemC2SPacket -> {
                if (player.getStackInHand(packet.hand).item == fluid.bucketItem) {
                    lastInteractTime = System.currentTimeMillis()
                }
            }

            is BlockUpdateS2CPacket -> {
                val newIsCorrectFluid = packet.state.fluidState.isOf(fluid)
                val oldWasCorrectFluid = world.getBlockState(packet.pos).fluidState.isOf(fluid)

                if (newIsCorrectFluid && !oldWasCorrectFluid) {
                    if (System.currentTimeMillis() - lastInteractTime > 500) {
                        shouldBlock.add(packet.pos.toImmutable())
                    }
                } else if (!newIsCorrectFluid && oldWasCorrectFluid) {
                    shouldBlock.remove(packet.pos)
                }
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        shouldBlock.clear()
        lastInteractTime = 0
    }

    fun handle() {
        if (PlacementManager.requester == ModuleHelper
            || ModuleScaffold.running
            || (onlyOnGround && !player.isOnGround)
        ) {
            return
        }

        for (pos in player.eyePos.searchBlocksInCuboid(range)) {
            if (pos !in shouldBlock || pos.y != player.y.toInt()) {
                continue
            }

            val state = pos.getState() ?: continue
            val fluidState = state.fluidState
            if (!fluidState.isOf(fluid)) {
                continue
            }

            val blockBelow = pos.down()
            val stateBelow = blockBelow.getState() ?: continue
            if (stateBelow.isAir) {
                continue
            }

            val collisionShape = stateBelow.getCollisionShape(world, blockBelow)
            if (collisionShape.isEmpty) {
                continue
            }

            when (itemToBlock) {
                ItemToBlock.WATER -> {
                    if (getWaterBucketSlot() == -1) {
                        continue
                    }

                    PlacementManager.place(
                        ModuleHelper,
                        PlacementManager.PlaceWaterRequest(
                            blockBelow.topCenter,
                            debug = PlacementManager.PlaceWaterDebug(
                                "No water bucket to block ${getFluidName(fluid)}",
                                "Failed to block ${getFluidName(fluid)}",
                                "Failed to recycle water"
                            )
                        )
                    )
                }

                ItemToBlock.BLOCK -> {
                    val targetBox = Box(blockBelow)
                    val hasPlayer = world.players.any { playerEntity ->
                        playerEntity.boundingBox.intersects(targetBox)
                    }
                    if (hasPlayer) {
                        continue
                    }

                    val blockSlot = Slots.OffhandWithHotbar.findSlot { stack ->
                        stack.item is BlockItem && stack.item != Items.TNT && isValidBlock(stack)
                    }
                    if (blockSlot == null) {
                        continue
                    }

                    PlacementManager.place(
                        ModuleHelper,
                        PlacementManager.PlaceBlockRequest(
                            blockBelow.topCenter,
                            slot = if (blockSlot.useHand == Hand.MAIN_HAND) {
                                blockSlot.hotbarSlotForServer
                            } else {
                                9
                            },
                            debug = "Failed to block ${getFluidName(fluid)}"
                        )
                    )
                }
            }

            break
        }
    }

}
