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
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.block.searchBlocksInCuboid
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.math.iterator
import net.minecraft.fluid.Fluids
import net.minecraft.item.BlockItem
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

object HelperBlockLava : ToggleableConfigurable(ModuleHelper, "BlockLava", true) {

    val lavaRange by float("LavaRange", 3f, 0f..5f)

    enum class ItemToBlockLava(override val choiceName: String) : NamedChoice {
        BLOCK("Block"),
        WATER("Water")
    }

    val itemToBlockLava by enumChoice("ItemToBlockLava", ItemToBlockLava.BLOCK)

    private val lavaBlacklist = mutableSetOf<BlockPos>()
    private var lastInteractTime: Long = 0

    override fun onEnabled() {
        lavaBlacklist.clear()
        lastInteractTime = 0
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        when (packet) {
            is PlayerInteractBlockC2SPacket -> {
                val stack = player.getStackInHand(packet.hand)
                if (stack.item == Items.LAVA_BUCKET) {
                    lastInteractTime = System.currentTimeMillis()
                }
            }

            is PlayerInteractItemC2SPacket -> {
                val stack = player.getStackInHand(packet.hand)
                if (stack.item == Items.LAVA_BUCKET) {
                    lastInteractTime = System.currentTimeMillis()
                }
            }

            is BlockUpdateS2CPacket -> {
                val newState = packet.state
                val newFluidState = newState.fluidState
                val isNewLava = newFluidState.isOf(Fluids.LAVA)

                val oldState = world.getBlockState(packet.pos)
                val oldFluidState = oldState.fluidState
                val wasLava = oldFluidState.isOf(Fluids.LAVA)

                if (isNewLava && !wasLava) {
                    if (System.currentTimeMillis() - lastInteractTime > 500) {
                        lavaBlacklist.add(packet.pos.toImmutable())
                    }
                } else if (!isNewLava && wasLava) {
                    lavaBlacklist.remove(packet.pos)
                }
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        lavaBlacklist.clear()
        lastInteractTime = 0
    }

    fun handle() {
        if (PlacementManager.requester == ModuleHelper) {
            return
        }

        val lavaRange = lavaRange

        for (pos in player.eyePos.searchBlocksInCuboid(lavaRange)) {
            val state = pos.getState() ?: continue
            val fluidState = state.fluidState

            if (!fluidState.isOf(Fluids.LAVA)) {
                continue
            }

            if (pos !in lavaBlacklist) {
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

            when (itemToBlockLava) {
                ItemToBlockLava.WATER -> {
                    if (getWaterBucketSlot() == -1) {
                        continue
                    }

                    PlacementManager.place(
                        ModuleHelper,
                        PlacementManager.PlaceWaterRequest(
                            blockBelow.topCenter,
                            PlacementManager.PlaceWaterDebug(
                                "No water bucket to block lava",
                                "Failed to block lava",
                                "Failed to recycle water"
                            )
                        )
                    )
                }

                ItemToBlockLava.BLOCK -> {
                    val blockSlot = Slots.OffhandWithHotbar.findSlot { it.item is BlockItem }
                    if (blockSlot == null) {
                        continue
                    }

                    PlacementManager.place(
                        ModuleHelper,
                        PlacementManager.PlaceBlockRequest(
                            blockBelow.topCenter,
                            if (blockSlot.useHand == Hand.MAIN_HAND) {
                                blockSlot.hotbarSlotForServer
                            } else {
                                9
                            },
                            "Failed to block lava"
                        )
                    )
                }
            }

            break
        }
    }

}
