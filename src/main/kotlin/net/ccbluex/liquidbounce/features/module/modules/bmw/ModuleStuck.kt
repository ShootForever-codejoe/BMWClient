package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.bmw.sendPacketNoEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

object ModuleStuck : ClientModule("Stuck", Category.BMW, disableOnQuit = true) {

    private val autoDisable by boolean("AutoDisable", true)

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        event.directionalInput = DirectionalInput.NONE
        event.jump = false
        player.movement.x = 0.0
        player.movement.y = 0.0
        player.movement.z = 0.0
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is PlayerPositionLookS2CPacket && autoDisable) {
            notifyAsMessage(ModuleStuck, "Auto disable for flag")
            enabled = false
        }

        if (packet is PlayerMoveC2SPacket) {
            event.cancelEvent()
        }

        if (packet is PlayerInteractItemC2SPacket) {
            event.cancelEvent()
            sendPacketNoEvent(
                ModuleStuck,
                PlayerMoveC2SPacket.LookAndOnGround(
                    player.yaw, player.pitch, player.isOnGround, player.horizontalCollision
                )
            )
            sendPacketNoEvent(
                ModuleStuck,
                PlayerInteractItemC2SPacket(
                    packet.hand, packet.sequence, player.yaw, player.pitch
                )
            )
        }

        if (packet is PlayerInteractEntityC2SPacket) {
            event.cancelEvent()
            sendPacketNoEvent(
                ModuleStuck,
                PlayerMoveC2SPacket.LookAndOnGround(
                    player.yaw, player.pitch, player.isOnGround, player.horizontalCollision
                )
            )
            sendPacketNoEvent(ModuleStuck, packet)
        }

        if (packet is PlayerInteractBlockC2SPacket) {
            event.cancelEvent()
            sendPacketNoEvent(
                ModuleStuck,
                PlayerMoveC2SPacket.LookAndOnGround(
                    player.yaw, player.pitch, player.isOnGround, player.horizontalCollision
                )
            )
            sendPacketNoEvent(ModuleStuck, packet)
        }
    }

}
