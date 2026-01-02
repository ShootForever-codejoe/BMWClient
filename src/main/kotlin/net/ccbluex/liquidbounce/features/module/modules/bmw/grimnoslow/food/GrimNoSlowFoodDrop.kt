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

package net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PlayerUseMultiplier
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.entity.moving
import net.minecraft.item.consume.UseAction
import net.minecraft.util.Hand

internal class GrimNoSlowFoodDrop(
    override val parent: ChoiceConfigurable<*>
) : Choice("Drop") {

    private var dropped = false

    @Suppress("unused")
    private val playerUseMultiplierHandler = handler<PlayerUseMultiplier> { event ->
        if (player.activeItem.useAction != UseAction.EAT || player.itemUseTimeLeft <= 0) {
            dropped = false
            return@handler
        }

        if (!dropped && player.moving) {
            if ((if (player.activeHand == Hand.MAIN_HAND) {player.mainHandStack}
                else {player.offHandStack}).count > 1) {

                player.dropSelectedItem(false)
                dropped = true
            }
        } else {
            player.isSprinting = true
            event.forward = 1f
            event.sideways = 1f
        }
    }

    override fun enable() {
        dropped = false
    }

}
