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

import net.ccbluex.liquidbounce.bmw.isUsableItem
import net.ccbluex.liquidbounce.event.events.PlayerInteractItemEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFoodNoC0F
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.item.Items

object ModuleFuckOffhand : ClientModule("FuckOffhand", Category.BMW) {

    @Suppress("unused")
    private val playerInteractItemEventHandler = handler<PlayerInteractItemEvent> { event ->
        if (!mc.options.useKey.isPressed || player.isUsingItem || GrimNoSlowFoodNoC0F.working) {
            return@handler
        }

        val offHandStack = player.getStackInHand(Hand.OFF_HAND)
        if (offHandStack.isEmpty || (offHandStack.item != Items.SNOWBALL && offHandStack.item != Items.EGG)) {
            return@handler
        }

        val mainHandStack = player.getStackInHand(Hand.MAIN_HAND)
        if (isUsableItem(mainHandStack)) {
            return@handler
        }

        event.cancelEvent()
        swapHands()
        interaction.interactItem(player, Hand.MAIN_HAND)
        swapHands()
    }

    private fun swapHands() {
        val mainStack = player.getStackInHand(Hand.MAIN_HAND)
        val offStack = player.getStackInHand(Hand.OFF_HAND)

        player.setStackInHand(Hand.MAIN_HAND, offStack)
        player.setStackInHand(Hand.OFF_HAND, mainStack)

        network.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
            )
        )
    }

}
