package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.GrimVelocityMode
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes.NoFallGrim
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket

object GrimVelocityJumpReset : GrimVelocityMode("JumpReset") {

    private val requireKillAura by boolean("RequireKillAura", true)

    private var jump = false
    private var damage = false

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (jump) {
            if (!InventoryManager.isInventoryOpen
                && mc.currentScreen !is GenericContainerScreen
                && player.isOnGround
                && !(NoFallGrim.running && NoFallGrim.jumping)
            ) {
                event.jump = true
            }
            jump = false
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (pause) return@handler

        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            damage = true
        }

        if (damage && packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            if (!requireKillAura || (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null)) {
                jump = true
            }
            damage = false
        }
    }

    override fun enable() {
        jump = false
        damage = false
    }

}
