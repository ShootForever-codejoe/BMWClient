package net.ccbluex.liquidbounce.features.module.modules.movement.noslow.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable

internal open class NoSlowMode(name: String, override val parent: ChoiceConfigurable<*>) : Choice(name) {
    companion object {
        var working = false
    }
}
