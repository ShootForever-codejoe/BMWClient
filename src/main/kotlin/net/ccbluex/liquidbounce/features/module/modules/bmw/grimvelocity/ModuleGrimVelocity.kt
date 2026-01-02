package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes.*
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

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
    private val pauseOnFlag by int("PauseOnFlag", 0, 0..20, "ticks")

    val shouldStopBacktrack: Boolean
        get() = stopBacktrack && modes.activeChoice.shouldStopBacktrack && running

    val pause: Boolean
        get() = pauseTicks > 0

    private var pauseTicks = 0

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (pauseTicks > 0) {
            pauseTicks--
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is PlayerPositionLookS2CPacket) {
            pauseTicks = pauseOnFlag
        }
    }

}
