package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket

object GrimVelocityJumpReset : Choice("JumpReset") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleGrimVelocity.modes

    private var jump = false
    private var damage = false

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (jump
            && !InventoryManager.isInventoryOpen
            && mc.currentScreen !is GenericContainerScreen
            && ModuleKillAura.targetTracker.target != null
        ) {
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

        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            damage = true
        }

        if (damage && (
                (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id)
                    || packet is ExplosionS2CPacket)
        ) {
            jump = true
            damage = false
        }
    }

    override fun enable() {
        jump = false
        damage = false
    }

}
