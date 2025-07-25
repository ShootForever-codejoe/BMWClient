/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
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
package net.ccbluex.liquidbounce.features.module.modules.player.autoqueue

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.presets.AutoQueueCustom
import net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.presets.AutoQueueGommeDuels
import net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.presets.AutoQueueHeypixel
import net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.presets.AutoQueueHypixelSW

object ModuleAutoQueue : ClientModule("AutoQueue", Category.PLAYER, aliases = arrayOf("AutoPlay")) {
    val presets = choices("Presets", AutoQueueHeypixel, arrayOf(
        AutoQueueHypixelSW,
        AutoQueueGommeDuels,
        AutoQueueHeypixel,
        AutoQueueCustom
    )).apply(::tagBy)
}
