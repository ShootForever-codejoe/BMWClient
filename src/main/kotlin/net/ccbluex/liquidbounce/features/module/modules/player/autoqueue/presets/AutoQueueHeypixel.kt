package net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.presets

import net.ccbluex.liquidbounce.bmw.HEYPIXEL_END_MESSAGE
import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.ChatReceiveEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.ModuleAutoQueue

object AutoQueueHeypixel : Choice("Heypixel") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleAutoQueue.presets

    private val spectatorCheck by boolean("SpectatorCheck", false)

    @Suppress("unused")
    private val chatReceiveEventHandler = handler<ChatReceiveEvent> { event ->
        val message = event.message

        if (event.type != ChatReceiveEvent.ChatType.GAME_MESSAGE) {
            return@handler
        }

        if (event.message.contains(HEYPIXEL_END_MESSAGE)) {
            if (!spectatorCheck || (!player.isSpectator && !player.abilities.flying)) {
                network.sendCommand("again")
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (spectatorCheck) {
            waitUntil { player.isSpectator || player.abilities.flying }
            network.sendCommand("again")
            waitUntil { !player.isSpectator && !player.abilities.flying }
        }
    }

}
