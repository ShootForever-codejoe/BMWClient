package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.event.events.HeypixelSWKillEvent
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.Items
import net.minecraft.screen.slot.SlotActionType

object ModuleAutoReport : ClientModule("AutoReport", Category.BMW) {

    @Suppress("unused")
    private val HeypixelSWKillEventHandler = sequenceHandler<HeypixelSWKillEvent> { event ->
        val victim = event.victim
        val killer = event.killer

        if (victim != player.name.string
            || FriendManager.isFriend(killer)
            || killer in ModuleIRC.users
        ) return@sequenceHandler

        player.networkHandler?.sendChatCommand("report $killer")

        repeat(4) {
            waitTicks(5)

            val screen = mc.currentScreen as? GenericContainerScreen ?: run {
                return@repeat
            }

            val slots = screen.screenHandler.slots
            val swordSlot = slots.find { it.stack.item == Items.DIAMOND_SWORD } ?: run {
                return@repeat
            }

            interaction.clickSlot(
                screen.screenHandler.syncId,
                swordSlot.id,
                0,
                SlotActionType.PICKUP,
                player
            )

            notifyAsMessage(ModuleAutoReport, "Reported $killer")
            mc.currentScreen = null
            return@sequenceHandler
        }
    }

}
