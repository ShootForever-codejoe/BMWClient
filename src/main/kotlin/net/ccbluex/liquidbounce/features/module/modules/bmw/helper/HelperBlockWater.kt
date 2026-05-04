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
import net.ccbluex.liquidbounce.bmw.topCenter
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
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

object HelperBlockWater : ToggleableConfigurable(ModuleHelper, "BlockWater", true) {

    val waterRange by float("WaterRange", 3f, 0f..5f)

    private val waterBlacklist = mutableSetOf<BlockPos>()
    private var lastInteractTime: Long = 0

    override fun onEnabled() {
        waterBlacklist.clear()
        lastInteractTime = 0
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        when (packet) {
            is PlayerInteractBlockC2SPacket -> {
                val stack = player.getStackInHand(packet.hand)
                if (stack.item == net.minecraft.item.Items.WATER_BUCKET) {
                    lastInteractTime = System.currentTimeMillis()
                }
            }

            is PlayerInteractItemC2SPacket -> {
                val stack = player.getStackInHand(packet.hand)
                if (stack.item == net.minecraft.item.Items.WATER_BUCKET) {
                    lastInteractTime = System.currentTimeMillis()
                }
            }

            is BlockUpdateS2CPacket -> {
                val newState = packet.state
                val newFluidState = newState.fluidState
                val isNewWater = newFluidState.isOf(Fluids.WATER)

                val oldState = world.getBlockState(packet.pos)
                val oldFluidState = oldState.fluidState
                val wasWater = oldFluidState.isOf(Fluids.WATER)

                if (isNewWater && !wasWater) {
                    if (System.currentTimeMillis() - lastInteractTime > 500) {
                        waterBlacklist.add(packet.pos.toImmutable())
                    }
                } else if (!isNewWater && wasWater) {
                    waterBlacklist.remove(packet.pos)
                }
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        waterBlacklist.clear()
        lastInteractTime = 0
    }

    fun handle() {
        if (PlacementManager.requester == ModuleHelper) {
            return
        }

        val waterRange = waterRange

        for (pos in player.eyePos.searchBlocksInCuboid(waterRange)) {
            val state = pos.getState() ?: continue
            val fluidState = state.fluidState

            if (!fluidState.isOf(Fluids.WATER)) {
                continue
            }

            if (pos !in waterBlacklist) {
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
                    "Failed to block water"
                )
            )

            break
        }
    }

}
