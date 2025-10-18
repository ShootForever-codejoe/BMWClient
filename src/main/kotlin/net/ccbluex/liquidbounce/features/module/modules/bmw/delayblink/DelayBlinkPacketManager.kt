package net.ccbluex.liquidbounce.features.module.modules.bmw.delayblink

import com.google.common.collect.Queues
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.bmw.notifyAsMessageAndNotification
import net.ccbluex.liquidbounce.bmw.sendPacketNoEvent
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.ModuleAutoDodge.EvadingPacket
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.ModuleAutoDodge.getInflictedHit
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Vec3d

object DelayBlinkPacketManager : EventListener {

    var clear = false
    private var enabled = false
    private var ticks = 0
    private var full = false
    private val packets = Queues.newConcurrentLinkedQueue<DelayPacket>()
    private val positions
        get() = packets
            .map { it.packet }
            .filterIsInstance<PlayerMoveC2SPacket>()
            .filter { it.changePosition }
            .map { Vec3d(it.x, it.y, it.z) }

    private fun findAvoidingArrowPacket(): EvadingPacket? {
        var packetIndex = 0
        var bestPacketIdx: Int? = null
        var bestTimeToImpact = 0

        for (position in positions) {
            packetIndex += 1

            val inflictedHit = getInflictedHit(position)

            if (inflictedHit == null) {
                return EvadingPacket(packetIndex, null)
            } else if (inflictedHit.tickDelta > bestTimeToImpact) {
                bestTimeToImpact = inflictedHit.tickDelta
                bestPacketIdx = packetIndex
            }
        }

        return EvadingPacket(bestPacketIdx ?: return null, bestTimeToImpact)
    }

    private fun flushPosition(count: Int) {
        var counter = 0

        for (delayPacket in packets.iterator()) {
            val packet = delayPacket.packet

            if (packet is PlayerMoveC2SPacket && packet.changePosition) {
                counter++
            }

            handle(delayPacket)
            packets.remove(delayPacket)

            if (counter >= count) break
        }
    }

    private fun handle(delayPacket: DelayPacket) {
        if (delayPacket.origin == TransferOrigin.OUTGOING) {
            sendPacketNoEvent(DelayBlinkPacketManager, delayPacket.packet)
        } else {
            handlePacket(delayPacket.packet)
        }
    }

    @Suppress("unused")
    private val tickPacketProcessEventHandler = handler<TickPacketProcessEvent> {
        if (!enabled) return@handler

        if (clear) {
            packets.removeIf {
                handle(it)
                true
            }
            clear = false
            enabled = false
            notifyAsMessage(ModuleDelayBlink, "Already handled all packets")
            return@handler
        }

        packets.removeIf {
            if (it.ticks + ModuleDelayBlink.delay < ticks) {
                handle(it)
                true
            } else false
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (!enabled) return@handler

        val packet = event.packet

        when (packet) {
            is ChatMessageS2CPacket,
            is GameMessageS2CPacket,
            is ChatMessageC2SPacket,
            is CommandExecutionC2SPacket,
            is KeepAliveS2CPacket,
            is KeepAliveC2SPacket -> {
                return@handler
            }

            is PlayerPositionLookS2CPacket -> {
                if (ModuleDelayBlink.DisableWhen.FLAG in ModuleDelayBlink.disableWhen) {
                    notifyAsMessage(ModuleDelayBlink, "Auto disable for flag")
                    ModuleDelayBlink.enabled = false
                }
                return@handler
            }

            is DisconnectS2CPacket,
            is PlayerRespawnS2CPacket,
            is GameJoinS2CPacket -> {
                ModuleDelayBlink.enabled = false
                return@handler
            }

            is PlaySoundS2CPacket -> {
                if (packet.sound.value() == SoundEvents.ENTITY_PLAYER_HURT) {
                    return@handler
                }
            }

            is HealthUpdateS2CPacket -> {
                if (packet.health <= 0) {
                    ModuleDelayBlink.enabled = false
                    return@handler
                }
            }
        }

        if (ModuleDelayBlink.DisableWhen.ATTACK in ModuleDelayBlink.disableWhen
            && packet is PlayerInteractEntityC2SPacket
        ) {
            ModuleDelayBlink.enabled = false
            notifyAsMessage(ModuleDelayBlink, "Auto disable for attacking")
        }

        if (event.origin in ModuleDelayBlink.delayPacketTypes) {
            event.cancelEvent()
            packets.add(DelayPacket(packet, ticks, event.origin))
        }
    }

    @Suppress("unused")
    private val checkEnabledHandler = tickHandler {
        waitUntil { ModuleDelayBlink.enabled }
        enabled = true
        packets.clear()
        ticks = 0
        full = false
        notifyAsMessage(ModuleDelayBlink, "Start collecting packets...")
        waitUntil { !enabled }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (!enabled) return@tickHandler

        ticks++
        if (ticks <= ModuleDelayBlink.delay) {
            if (ModuleDelayBlink.displayDelay) {
                notifyAsMessage(ModuleDelayBlink, "Delay: $ticks / ${ModuleDelayBlink.delay} ticks")
            }
        } else if (!full) {
            notifyAsMessage(ModuleDelayBlink, "Start handling the packets ${ModuleDelayBlink.delay} ticks ago...")
            full = true
        }

        if (ModuleDelayBlink.avoidArrow) {
            val position = positions.firstOrNull() ?: return@tickHandler

            if (getInflictedHit(position) == null) return@tickHandler

            val evadingPacket = findAvoidingArrowPacket()

            if (evadingPacket == null) {
                notifyAsMessageAndNotification(ModuleDelayBlink, "Failed to avoid arrow")
            } else {
                flushPosition(evadingPacket.idx)
                if (evadingPacket.ticksToImpact != null) {
                    notifyAsMessageAndNotification(ModuleDelayBlink, "Trying to avoid arrow...")
                } else {
                    notifyAsMessageAndNotification(ModuleDelayBlink, "Arrow avoided")
                }
            }
        }
    }

    data class DelayPacket(val packet: Packet<*>, val ticks: Int, val origin: TransferOrigin)

}
