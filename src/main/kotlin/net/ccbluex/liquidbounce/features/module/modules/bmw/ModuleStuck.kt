package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.bmw.*
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

object ModuleStuck : ClientModule("Stuck", Category.BMW) {

    private val autoReset by boolean("AutoReset", false)
    private val resetTicks by int("ResetTicks", 20, 1..200, "ticks")
    private val cancelC03Packet by boolean("CancelC03Packet", true)

    private var stuckTicks = 0
    private var isInAir = false

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> {
        player.movement.x = 0.0
        player.movement.y = 0.0
        player.movement.z = 0.0
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (!player.isOnGround) {
            isInAir = true

            if (event.packet is PlayerPositionLookS2CPacket) {
                notifyAsMessage("Stuck End for S08 Packet")
                enabled = false
            }

            if (cancelC03Packet && event.packet is PlayerMoveC2SPacket) {
                event.cancelEvent()
            }

            if (event.packet is PlayerInteractItemC2SPacket) {
                event.cancelEvent()
                sendPacketSilently(PlayerMoveC2SPacket.LookAndOnGround(
                    player.yaw, player.pitch, player.isOnGround, player.horizontalCollision
                ))
                sendPacketSilently(PlayerInteractItemC2SPacket(
                    event.packet.hand, event.packet.sequence, player.yaw, player.pitch
                ))
            }
        } else if (isInAir) {
            notifyAsMessage("Stuck End for OnGround")
            enabled = false
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!autoReset) {
            return@tickHandler
        }

        stuckTicks++
        if (stuckTicks >= resetTicks) {
            notifyAsMessage("Stuck Reset ($stuckTicks ticks)")
            enabled = false
            enabled = true
        }
    }

    override fun enable() {
        stuckTicks = 0
        isInAir = false
    }

}
