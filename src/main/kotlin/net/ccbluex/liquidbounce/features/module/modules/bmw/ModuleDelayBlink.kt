package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.bmw.*
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket

object ModuleDelayBlink : ClientModule("DelayBlink", Category.BMW, disableOnQuit = true) {

    private val delay by int("Delay", 20, 0..200, "ticks")
    private val displayDelay by boolean("DisplayDelay", true)
    private val autoDisable by boolean("AutoDisable", true)

    private val packets = mutableListOf<Packet<*>>()
    private var ticks = 0
    private var full = false

    @Suppress("unused")
    private val tickHandler = tickHandler {
        ticks++
        if (ticks > delay) {
            ticks = delay
            if (!full) {
                full = true
                if (!displayDelay)
                    notifyAsMessage("Start Sending the Packets $delay Ticks Ago")
            }
        }
        if (displayDelay) {
            notifyAsMessage("Blink Delay: $ticks / $delay")
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (autoDisable && event.packet is PlayerInteractEntityC2SPacket) {
            enabled = false
            return@handler
        }

        if (event.origin == TransferOrigin.INCOMING) {
            return@handler
        }

        packets.add(event.packet)
        event.cancelEvent()
        if (ticks >= delay) {
            sendPacketSilently(packets.removeFirst())
        }
    }

    override fun enable() {
        packets.clear()
        ticks = 0
        full = false
        if (!displayDelay) {
            notifyAsMessage("Collecting Packets...")
        }
    }

    override fun disable() {
        packets.forEach {
            sendPacketSilently(it)
        }
    }

}
