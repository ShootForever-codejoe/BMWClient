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

import net.ccbluex.liquidbounce.event.events.ChatReceiveEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.client.util.ScreenshotRecorder

@Suppress("unused")
object ModuleAutoScreenShot : ClientModule("AutoScreenShot", Category.BMW) {

    private val delay by int("Delay", 10, 0..100, "ticks")

    override fun onEnabled() {
        ScreenshotRecorder.takeScreenshot(mc.framebuffer)
    }

    @Suppress("unused")
    private val chatReceiveEventHandler = sequenceHandler<ChatReceiveEvent> { event ->
        if (event.type != ChatReceiveEvent.ChatType.GAME_MESSAGE) return@sequenceHandler

        if (event.message.startsWith("恭喜! ${player.name.string} 在地图")) {
            waitTicks(delay)
            ScreenshotRecorder.takeScreenshot(mc.framebuffer)
        }
    }

}
