package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.event.events.PlayerUseMultiplier
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.entity.moving
import net.minecraft.util.Hand
import net.minecraft.item.consume.UseAction

object ModuleDropEatNoSlow : ClientModule("DropEatNoSlow", Category.BMW) {

    private var dropped = false

    @Suppress("unused")
    val playerUseMultiplierHandler = handler<PlayerUseMultiplier> { event ->
        if (player.activeItem.useAction != UseAction.EAT || player.itemUseTimeLeft <= 0) {
            dropped = false
            return@handler
        }

        if (!dropped && player.moving) {
            if ((if (player.activeHand == Hand.MAIN_HAND) {player.mainHandStack}
                else {player.offHandStack}).count > 1) {

                player.dropSelectedItem(false)
                dropped = true
            }
        } else {
            player.isSprinting = true
            event.forward = 1f
            event.sideways = 1f
        }
    }

    override fun enable() {
        dropped = false
    }

}
