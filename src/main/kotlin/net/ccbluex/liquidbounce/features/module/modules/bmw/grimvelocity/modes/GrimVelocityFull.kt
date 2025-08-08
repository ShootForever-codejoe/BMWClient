package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import com.google.common.collect.Queues
import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raycast
import net.ccbluex.liquidbounce.utils.client.PacketSnapshot
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.consume.UseAction
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult

object GrimVelocityFull : Choice("Full") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleGrimVelocity.modes

    private val maxStuckTicks by int("MaxStuckTicks", 10, 1..100, "ticks")

    private const val BLOCK_HIT_PITCH = 89.79f

    private var canCancel = false
    private var delay = false
    private var needClick = false
    private var waitForUpdate = false
    private var hitResult: BlockHitResult? = null
    private var shouldSkip = false
    private val delayedPacketQueue = Queues.newConcurrentLinkedQueue<PacketSnapshot>()

    private fun reset() {
        if (!delayedPacketQueue.isEmpty()) {
            delayedPacketQueue.forEach { handlePacket(it.packet) }
            delayedPacketQueue.clear()
        }
        canCancel = false
        delay = false
        needClick = false
        waitForUpdate = false
        hitResult = null
        shouldSkip = false
    }

    override fun disable() {
        reset()
    }

    @Suppress("unused")
    private val packetEventHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is PlayerInteractEntityC2SPacket || packet is PlayerInteractBlockC2SPacket) {
            shouldSkip = true
        }

        if (packet is PlayerMoveC2SPacket && packet.changePosition && waitForUpdate) {
            event.cancelEvent()
        }

        if (event.isCancelled || event.origin == TransferOrigin.OUTGOING) {
            return@sequenceHandler
        }

        if (packet is BlockUpdateS2CPacket && packet.pos.equals(player.blockPos)) {
            waitTicks(1)
            waitForUpdate = false
            needClick = false
            return@sequenceHandler
        }

        if (waitForUpdate) {
            return@sequenceHandler
        }

        if (delay) {
            delayedPacketQueue.add(PacketSnapshot(packet, event.origin, System.currentTimeMillis()))
            event.cancelEvent()
            return@sequenceHandler
        }

        if (packet is EntityDamageS2CPacket
            && packet.entityId == player.id
            && player.activeItem.useAction != UseAction.EAT
            && player.activeItem.useAction != UseAction.DRINK
            && !InventoryManager.isInventoryOpen
            && mc.currentScreen !is GenericContainerScreen
        ) {
            canCancel = true
        }

        if (((packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id)
                || packet is ExplosionS2CPacket)
            && canCancel
            && player.activeItem.useAction != UseAction.EAT
            && player.activeItem.useAction != UseAction.DRINK
            && !InventoryManager.isInventoryOpen
            && mc.currentScreen !is GenericContainerScreen
        ) {
            event.cancelEvent()
            delay = true
            canCancel = false
            needClick = true
        }
    }

    @Suppress("unused")
    private val playerTickEventHandler = handler<PlayerTickEvent> { event ->
        if (needClick) {
            hitResult = raycast(rotation = Rotation(player.yaw, BLOCK_HIT_PITCH))
            val pos = hitResult!!.blockPos.offset(hitResult!!.side)
            if (!pos.equals(player.blockPos) || shouldSkip) {
                hitResult = null
            }
        }

        if (hitResult != null) {
            delay = false
            delayedPacketQueue.forEach { handlePacket(it.packet) }
            delayedPacketQueue.clear()

            if (interaction.interactBlock(player, Hand.MAIN_HAND, hitResult) == ActionResult.SUCCESS) {
                player.swingHand(Hand.MAIN_HAND)
            }

            if (RotationManager.serverRotation.pitch != BLOCK_HIT_PITCH) {
                network.sendPacket(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        player.yaw,
                        BLOCK_HIT_PITCH,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
            } else {
                network.sendPacket(
                    PlayerMoveC2SPacket.OnGroundOnly(
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
            }

            waitForUpdate = true
            hitResult = null
            needClick = false
        }

        if (waitForUpdate) {
            event.cancelEvent()
        }

        shouldSkip = false
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        waitUntil { waitForUpdate }
        repeat(maxStuckTicks) {
            waitTicks(1)
            if (!waitForUpdate) return@tickHandler
        }
        reset()
    }

}
