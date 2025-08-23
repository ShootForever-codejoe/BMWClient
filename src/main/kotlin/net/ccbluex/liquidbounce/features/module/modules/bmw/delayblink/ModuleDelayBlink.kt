package net.ccbluex.liquidbounce.features.module.modules.bmw.delayblink

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule

object ModuleDelayBlink : ClientModule("DelayBlink", Category.BMW, disableOnQuit = true) {

    val delay by int("Delay", 20, 0..200, "ticks")
    val displayDelay by boolean("DisplayDelay", true)
    val autoDisable by boolean("AutoDisable", true)

    enum class DelayPacketTypes(override val choiceName: String) : NamedChoice {
        OUTGOING("Outgoing"),
        INCOMING("Incoming")
    }

    val delayPacketTypes by multiEnumChoice("DelayPacketTypes", DelayPacketTypes.OUTGOING)

    override fun disable() {
        DelayBlinkPacketManager.clear = true
    }

}
