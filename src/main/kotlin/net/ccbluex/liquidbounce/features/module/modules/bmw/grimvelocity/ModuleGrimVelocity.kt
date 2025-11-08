package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes.*

object ModuleGrimVelocity : ClientModule("GrimVelocity", Category.BMW) {

    val modes = choices(
        "Mode", GrimVelocityAttackReduce, arrayOf(
            GrimVelocityJumpReset,
            GrimVelocityFull,
            GrimVelocityDelay,
            GrimVelocityAttackReduce
        )
    ).apply(::tagBy)

    private val stopBacktrack by boolean("StopBacktrack", true)

    val shouldStopBacktrack: Boolean
        get() = stopBacktrack && modes.activeChoice.shouldStopBacktrack && running

}
