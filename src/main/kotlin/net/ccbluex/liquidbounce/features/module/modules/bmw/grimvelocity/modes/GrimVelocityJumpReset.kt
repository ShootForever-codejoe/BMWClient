package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket

object GrimVelocityJumpReset : Choice("JumpReset") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleGrimVelocity.modes

    private val delayInAir by boolean("DelayInAir", false)
    private val requireKillAura by boolean("RequireKillAura", true)

    private var velocityInput = false
    private var damage = false
    private val packets = mutableListOf<Packet<*>>()
    private var delayPackets = false

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (velocityInput) {
            if (!InventoryManager.isInventoryOpen
                && mc.currentScreen !is GenericContainerScreen
                && player.isOnGround
            ) {
                event.jump = true
            }
            velocityInput = false
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (event.origin == TransferOrigin.OUTGOING || event.isCancelled) return@handler

        val packet = event.packet

        if (delayPackets) {
            event.cancelEvent()
            packets.add(packet)
            return@handler
        }

        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            damage = true
        }

        if (damage && packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            if (!requireKillAura || (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null)) {
                if (delayInAir && !player.isOnGround) {
                    delayPackets = true
                    event.cancelEvent()
                    packets.add(packet)
                } else {
                    velocityInput = true
                }
            }
            damage = false
        }
    }

    @Suppress("unused")
    private val tickPacketProcessEventHandler = handler<TickPacketProcessEvent> {
        if (delayPackets && player.isOnGround) {
            packets.removeIf {
                handlePacket(it)
                true
            }
            delayPackets = false
            velocityInput = true
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        waitUntil { delayPackets }
        repeat(60) {
            waitTicks(1)
            if (!delayPackets) return@tickHandler
        }
        packets.removeIf {
            handlePacket(it)
            true
        }
        delayPackets = false
    }

    override fun enable() {
        velocityInput = false
        damage = false
        packets.clear()
        delayPackets = false
    }

}
