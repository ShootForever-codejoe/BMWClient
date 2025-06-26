package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.ccbluex.liquidbounce.utils.client.chat
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket

object GrimVelocityReduce : Choice("Reduce") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleGrimVelocity.modes

    private val onlyGround by boolean("OnlyGround", false)
    private val debugMessage by boolean("DebugMessage", false)

    private val reduceFactor by float("Factor", 0.6f, 0.0f..1.0f)
    private val minHurtTime by int("MinHurtTime", 5, 0..10)
    private val maxHurtTime by int("MaxHurtTime", 10, 0..20)

    private var lastAttackTime = 0L

    init {
        // Tick-based handler to replace LivingUpdateEvent
        handler<GameTickEvent> {
            val player = mc.player ?: return@handler
            if (player.hurtTime in minHurtTime..maxHurtTime) {
                lastAttackTime = System.currentTimeMillis()
            }
        }
        handler<GameTickEvent> {
            if (debugMessage) chat("GameTickEvent triggered!")
        }
        handler<WorldChangeEvent> {
            if (debugMessage) chat("WorldChangeEvent triggered!")
        }
    }

    /**
     * Handles incoming velocity packets and modifies them based on settings.
     */
    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == mc.player?.id) {
            val player = mc.player ?: return@handler
            if (onlyGround && !player.isOnGround) return@handler

            if (player.hurtTime in minHurtTime..maxHurtTime) {
                if (debugMessage) {
                    notifyAsMessage("Velocity packet detected: X=${packet.velocityX}, Y=${packet.velocityY}, Z=${packet.velocityZ}")
                    notifyAsMessage("Reducing velocity: X=${packet.velocityX}, Y=${packet.velocityY}, Z=${packet.velocityZ}")
                }
                packet.velocityX = (packet.velocityX * reduceFactor).toInt()
                packet.velocityY = (packet.velocityY * reduceFactor).toInt()
                packet.velocityZ = (packet.velocityZ * reduceFactor).toInt()
            }
        } else if (packet is ExplosionS2CPacket) {
            event.cancelEvent() // Cancel explosion knockback
        }
    }

    @Suppress("unused")
    private val worldChangeEventHandler = handler<WorldChangeEvent> {
        lastAttackTime = 0L
    }

}
