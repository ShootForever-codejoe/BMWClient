package net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.presets

import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.bmw.HEYPIXEL_END_MESSAGE
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.ChatReceiveEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.ModuleAutoQueue

object AutoQueueHeypixelSW : Choice("HeypixelSW") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleAutoQueue.presets

    private val spectatorCheck by boolean("SpectatorCheck", false)
    private val delay by float("Delay", 2f, 0f..10f, "seconds")

    @Suppress("unused")
    private val chatReceiveEventHandler = sequenceHandler<ChatReceiveEvent> { event ->
        val message = event.message

        if (event.type != ChatReceiveEvent.ChatType.GAME_MESSAGE) {
            return@sequenceHandler
        }

        if (event.message.contains(HEYPIXEL_END_MESSAGE)) {
            if (!spectatorCheck || (!player.isSpectator && !player.abilities.flying)) {
                delay((delay * 1000f).toLong())
                network.sendCommand("again")
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (spectatorCheck) {
            waitUntil { player.isSpectator || player.abilities.flying }
            delay((delay * 1000f).toLong())
            network.sendCommand("again")
            waitUntil { !player.isSpectator && !player.abilities.flying }
        }
    }

}
