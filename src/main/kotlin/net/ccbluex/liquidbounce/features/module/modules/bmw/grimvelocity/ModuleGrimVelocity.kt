package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes.*

object ModuleGrimVelocity : ClientModule("GrimVelocity", Category.BMW) {

    val modes = choices(
        "Mode", GrimVelocityReduce, arrayOf(
            GrimVelocityReduce,
            GrimVelocityNoXZ
        )
    ).apply(::tagBy)

}
