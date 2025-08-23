package net.ccbluex.liquidbounce.features.module.modules.bmw.fireballfly

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.PacketSnapshot
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.interactItem
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.item.FireChargeItem
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.MathHelper

object ModuleFireballFly : ClientModule("FireballFly", Category.BMW, disableOnQuit = true) {

    private val fireballDelay by int("FireballDelay", 10, 1..200, "ticks")
    private val maxFireballCount by int("MaxFireballCount", 4, 1..64)
    private val slotResetDelay by int("SlotResetDelay", 5, 0..40, "ticks")

    private object Jump : ToggleableConfigurable(this, "Jump", true) {
        val jumpDelay by int("JumpDelay", 3, 0..20, "ticks")
    }

    private object Rotations : RotationsConfigurable(this) {
        val pitch by float("Pitch", 70f, 0f..90f)
        val backwards by boolean("Backwards", true)
    }

    init {
        tree(Jump)
        tree(Rotations)
    }

    private val delayedPacketQueue = mutableListOf<PacketSnapshot>()
    val packetProcessQueue = mutableListOf<Packet<*>>()

    private var canThrow = false
    private var canRotate = false
    private var delay = 0
    private var fireballCount = 0
    private var totalFireballCount = 0

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING || event.isCancelled) {
            return@handler
        }

        val packet = event.packet

        when (packet) {
            is ChatMessageC2SPacket, is GameMessageS2CPacket, is CommandExecutionC2SPacket -> {
                return@handler
            }

            is PlayerPositionLookS2CPacket, is DisconnectS2CPacket -> {
                clear(true)
                return@handler
            }

            is PlaySoundS2CPacket -> {
                if (packet.sound.value() == SoundEvents.ENTITY_PLAYER_HURT) {
                    return@handler
                }
            }

            is HealthUpdateS2CPacket -> {
                if (packet.health <= 0) {
                    clear(true)
                    return@handler
                }
            }
        }

        event.cancelEvent()
        delayedPacketQueue.add(PacketSnapshot(packet, event.origin, System.currentTimeMillis()))
    }

    @Suppress("unused")
    private val rotationUpdateEventHandler = handler<RotationUpdateEvent> {
        if (canRotate) {
            val rotation = Rotation(if (Rotations.backwards) invertYaw(player.yaw) else player.yaw, Rotations.pitch)
            RotationManager.setRotationTarget(
                rotation = rotation,
                configurable = Rotations,
                priority = Priority.IMPORTANT_FOR_PLAYER_LIFE,
                provider = this
            )
        }
    }


    fun processPackets() {
        delayedPacketQueue.removeIf {
            if (it.timestamp <= System.currentTimeMillis() - delay * 50) {
                packetProcessQueue.add(it.packet)
                true
            } else false
        }
    }

    private fun clear(handlePackets: Boolean = true) {
        if (handlePackets) {
            processPackets()
        } else {
            delayedPacketQueue.clear()
        }
    }

    private fun findFireballSlot(): Int? {
        return (0..8).firstOrNull {
            val stack = player.inventory.getStack(it)
            stack.item is FireChargeItem
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val bestMainHandSlot = findFireballSlot()
        if (bestMainHandSlot != null) {
            SilentHotbar.selectSlotSilently(this, bestMainHandSlot, slotResetDelay)
        } else {
            SilentHotbar.resetSlot(this)
        }
        if (canThrow) {
            canThrow = false

            if (Jump.enabled) {
                if (player.isOnGround) player.jump()
                waitTicks(Jump.jumpDelay)
            }

            interactItem(Hand.MAIN_HAND)
            fireballCount--
            notifyAsMessage("[Fireball Fly] Thrown a Fireball (${totalFireballCount - fireballCount} / $totalFireballCount)")

            if (fireballCount != 0) {
                waitTicks(fireballDelay - if (Jump.enabled) Jump.jumpDelay else 0)
                canThrow = true
            } else {
                canRotate = false
                waitTicks(delay + 5)
                enabled = false
                clear(true)
                canThrow = false
            }
        }
    }

    private fun invertYaw(yaw: Float): Float {
        return MathHelper.wrapDegrees(yaw + 180)
    }

    override fun enable() {
        clear(false)
        val bestMainHandSlot = findFireballSlot()
        if (bestMainHandSlot != null) {
            val count = player.inventory.getStack(bestMainHandSlot).count
            fireballCount = if (count < maxFireballCount) count else maxFireballCount
            totalFireballCount = fireballCount
            delay = fireballCount * fireballDelay
            canThrow = true
            canRotate = true
        } else {
            canThrow = false
            canRotate = false
        }
    }

    override fun disable() {
        clear(true)
        canThrow = false
        canRotate = false
    }

}
