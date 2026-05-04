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

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule

object ModuleHelper : ClientModule("Helper", Category.BMW) {

    init {
        treeAll(
            HelperPutOutFire,
            HelperBlockLava,
            HelperBlockWater,
            HelperBlockTNT
        )
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (HelperPutOutFire.enabled) {
            HelperPutOutFire.handle()
        }

        if (HelperBlockLava.enabled) {
            HelperBlockLava.handle()
        }

        if (HelperBlockWater.enabled) {
            HelperBlockWater.handle()
        }
    }

}
