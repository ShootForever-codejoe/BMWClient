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

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.entity.TntEntity

object ModuleTNTWarning : ClientModule("TNTWarning", Category.BMW) {

    private val range by float("Range", 8f, 1f..16f, "blocks")
    private val thickness by int("Thickness", 24, 8..80, "px")
    private val alpha by int("Alpha", 140, 20..255)

    @Suppress("unused")
    private val overlayRenderHandler = handler<OverlayRenderEvent> { event ->
        val rangeSq = range * range
        val hasIgnitedTntNearby = world.entities.any { entity ->
            entity is TntEntity && entity.fuse > 0 && player.squaredDistanceTo(entity) <= rangeSq
        }

        if (!hasIgnitedTntNearby) {
            return@handler
        }

        val context = event.context
        val width = context.scaledWindowWidth
        val height = context.scaledWindowHeight
        val bandThickness = thickness.coerceAtMost(width.coerceAtMost(height) / 2)

        for (i in 0 until bandThickness) {
            val stepAlpha = ((bandThickness - i).toFloat() / bandThickness.toFloat() * alpha).toInt().coerceIn(0, 255)
            val color = (stepAlpha shl 24) or 0x00FF0000

            // 顶边
            context.fill(0, i, width, i + 1, color)
            // 底边
            context.fill(0, height - i - 1, width, height - i, color)
            // 左边
            context.fill(i, 0, i + 1, height, color)
            // 右边
            context.fill(width - i - 1, 0, width - i, height, color)
        }
    }

}
