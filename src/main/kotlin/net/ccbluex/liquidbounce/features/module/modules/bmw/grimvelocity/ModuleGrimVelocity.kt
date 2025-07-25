package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes.*

@Suppress("unused")
object ModuleGrimVelocity : ClientModule("GrimVelocity", Category.BMW) {

    val modes = choices(
        "Mode", GrimVelocityStuck, arrayOf(
            GrimVelocityNoXZ,
            GrimVelocityStuck
        )
    ).apply(::tagBy)

}
