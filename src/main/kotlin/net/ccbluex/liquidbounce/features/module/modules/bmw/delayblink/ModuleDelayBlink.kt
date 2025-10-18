package net.ccbluex.liquidbounce.features.module.modules.bmw.delayblink

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule

object ModuleDelayBlink : ClientModule("DelayBlink", Category.BMW, disableOnQuit = true) {

    val delay by int("Delay", 20, 0..200, "ticks")
    val displayDelay by boolean("DisplayDelay", true)
    val avoidArrow by boolean("AvoidArrow", true)
    val disableWhen by multiEnumChoice(
        "DisableWhen",
        DisableWhen.ATTACK
    )

    enum class DisableWhen(override val choiceName: String) : NamedChoice {
        FLAG("Flag"),
        ATTACK("Attack")
    }

    val delayPacketTypes by multiEnumChoice(
        "DelayPacketTypes",
        TransferOrigin.OUTGOING
    )

    override fun disable() {
        DelayBlinkPacketManager.clear = true
    }

}
