package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes.GrimVelocityNoXZ
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes.GrimVelocityStuck
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes.GrimVelocityReduce

object ModuleGrimVelocity : ClientModule("GrimVelocity", Category.BMW) {

    val modes = choices(
        "Mode", GrimVelocityStuck, arrayOf(
            GrimVelocityStuck,
            GrimVelocityNoXZ,
            GrimVelocityReduce
        )
    ).apply(::tagBy)

}
