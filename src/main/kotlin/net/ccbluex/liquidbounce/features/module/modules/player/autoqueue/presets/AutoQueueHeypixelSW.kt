package net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.presets

import net.ccbluex.liquidbounce.bmw.HEYPIXEL_SW_END_MESSAGE
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.ChatReceiveEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
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
        enum class Types(override val choiceName: String, val command: String) : NamedChoice {
            SOLO("Solo", "play swrsolo"),
            DOUBLE("Double", "play swrdouble"),
            AGAIN("Again", "again")
        }

        val type by enumChoice("Type", Types.AGAIN)
    }

    private val action = choices(
        "Action", ActionCommand,
        arrayOf(
            ActionClick,
            ActionCommand
        )
    )
    private val delay by int("Delay", 10, 0..100, "ticks")

    private var queueTicks = 0

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (queueTicks > 0) queueTicks--
    }

    @Suppress("unused")
    private val chatReceiveEventHandler = sequenceHandler<ChatReceiveEvent> { event ->
        val message = event.message

        if (event.type != ChatReceiveEvent.ChatType.GAME_MESSAGE) return@sequenceHandler

        if (event.message.startsWith("您已经连接到当前玩法服务器了!") && queueTicks > 0) {
            notifyAsMessage(ModuleAutoQueue, "布吉岛拒绝你进入下一局")
            network.sendCommand("hub")
            return@sequenceHandler
        }

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
                network.sendCommand(ActionCommand.type.command)
            }
        }

        queueTicks = 60
    }

    override fun enable() {
        queueTicks = 0
    }

}
