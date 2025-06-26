package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.bmw.notifyAsNotification
import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent.Severity
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

object GrimVelocityStuck : Choice("Stuck") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleGrimVelocity.modes

    private var velocityInput = false
    private var hurtTime = false

    @Suppress("unused")
    private val tickHandler = tickHandler {
        hurtTime = player.hurtTime > 0
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> {
        if (!velocityInput || !hurtTime) return@handler

        player.movement.x = 0.0
        player.movement.y = 0.0
        player.movement.z = 0.0

        velocityInput = false
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (event.packet is EntityVelocityUpdateS2CPacket && event.packet.entityId == player.id) {
            velocityInput = true
        }

        if (event.packet is PlayerPositionLookS2CPacket) {
            velocityInput = false
        }
    }

    override fun enable() {
        notifyAsMessage("GrimVelocity-Stuck模式暂时无法使用")
        notifyAsNotification("GrimVelocity-Stuck模式暂时无法使用", Severity.ERROR)
        ModuleGrimVelocity.enabled = false
        velocityInput = false
        hurtTime = false
    }

}
