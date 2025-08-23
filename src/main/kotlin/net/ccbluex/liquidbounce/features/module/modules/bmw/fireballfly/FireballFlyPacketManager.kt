package net.ccbluex.liquidbounce.features.module.modules.bmw.fireballfly

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.bmw.fireballfly.ModuleFireballFly.packetProcessQueue
import net.ccbluex.liquidbounce.features.module.modules.bmw.fireballfly.ModuleFireballFly.processPackets
import net.ccbluex.liquidbounce.utils.client.handlePacket

object FireballFlyPacketManager : EventListener {

    @Suppress("unused")
    private val handleTickPacketProcess = handler<TickPacketProcessEvent> {
        processPackets()

        packetProcessQueue.removeIf {
            handlePacket(it)
            true
        }
    }

}
