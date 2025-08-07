package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket

object GrimVelocityJumpReset : Choice("JumpReset") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleGrimVelocity.modes

    private var jump = false

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (jump) {
            event.jump = true
            jump = false
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (!player.isOnGround) {
            return@handler
        }

        val packet = event.packet

        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            jump = true
        }

        if (packet is ExplosionS2CPacket) {
            jump = true
        }
    }

    override fun enable() {
        jump = false
    }

}
