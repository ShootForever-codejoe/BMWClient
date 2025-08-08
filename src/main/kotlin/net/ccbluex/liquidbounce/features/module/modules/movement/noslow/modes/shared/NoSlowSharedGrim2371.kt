package net.ccbluex.liquidbounce.features.module.modules.movement.noslow.modes.shared

import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.noslow.modes.NoSlowMode
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.util.Hand

/**
 * Bypassing Grim 2.3.71
 * @from https://github.com/GrimAnticheat/Grim/issues/2216
 */
internal class NoSlowSharedGrim2371(override val parent: ChoiceConfigurable<*>) : NoSlowMode("Grim2371", parent) {

    val repeatable = tickHandler {
        working = false

        repeat(2) {
            waitTicks(1)
            working = true
            val hand: Hand = Hand.MAIN_HAND.takeIf { player.getActiveHand() == Hand.MAIN_HAND } ?: Hand.OFF_HAND
            interaction.sendSequencedPacket(world) { sequence ->
                PlayerInteractItemC2SPacket(
                    hand, sequence,
                    player.yaw, player.pitch
                )
            }
        }

        working = false
    }

}
