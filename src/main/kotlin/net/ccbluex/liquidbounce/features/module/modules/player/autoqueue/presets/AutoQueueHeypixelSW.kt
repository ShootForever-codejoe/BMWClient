package net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.presets

import net.ccbluex.liquidbounce.bmw.HEYPIXEL_SW_END_MESSAGE
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.ChatReceiveEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.ModuleAutoQueue
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.minecraft.item.Items

object AutoQueueHeypixelSW : Choice("HeypixelSW") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleAutoQueue.presets

    private object ActionClick : Choice("Click") {
        override val parent: ChoiceConfigurable<*>
            get() = action
    }

    private object ActionCommand : Choice("Command") {
        override val parent: ChoiceConfigurable<*>
            get() = action

        @Suppress("unused")
        enum class Types(override val choiceName: String) : NamedChoice {
            SOLO("Solo"),
            DOUBLE("Double")
        }

        val type by enumChoice("Type", Types.SOLO)
    }

    private val action = choices(
        "Action", ActionCommand,
        arrayOf(
            ActionClick,
            ActionCommand
        )
    )
    private val delay by int("Delay", 10, 0..100, "ticks")

    @Suppress("unused")
    private val chatReceiveEventHandler = sequenceHandler<ChatReceiveEvent> { event ->
        val message = event.message

        if (event.type != ChatReceiveEvent.ChatType.GAME_MESSAGE) return@sequenceHandler
        if (!event.message.startsWith(HEYPIXEL_SW_END_MESSAGE)) return@sequenceHandler

        waitTicks(delay)

        when (action.activeChoice) {
            ActionClick -> {
                val slot = Slots.OffhandWithHotbar.findSlot(Items.EMERALD) ?: return@sequenceHandler
                SilentHotbar.selectSlotSilently(ModuleAutoQueue, slot, 10)
                waitTicks(1)
                interaction.interactItem(player, slot.useHand)
            }

            ActionCommand -> {
                network.sendCommand("play swr${ActionCommand.type.choiceName.lowercase()}")
            }
        }
    }

}
