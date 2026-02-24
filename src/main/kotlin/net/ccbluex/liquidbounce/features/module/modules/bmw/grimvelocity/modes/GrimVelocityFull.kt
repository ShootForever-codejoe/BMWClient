package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import com.google.common.collect.Queues
import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.GrimVelocityMode
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raycast
import net.ccbluex.liquidbounce.utils.block.getBlock
import net.ccbluex.liquidbounce.utils.block.isInteractable
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.consume.UseAction
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.block.BlockState

object GrimVelocityFull : GrimVelocityMode("Full") {

    private val maxStuckTicks by int("MaxStuckTicks", 5, 1..100, "ticks")
    private val onlyOnGround by boolean("OnlyOnGround", false)
    private val delayInAir by boolean("DelayInAir", true)

    private val debug by boolean("Debug", false)

    private var canCancel = false
    private var delay = false
    private var needClick = false
    private var waitForUpdate = false
    private var shouldSkip = false
    private val delayedPacketQueue = Queues.newConcurrentLinkedQueue<Packet<*>>()

    override val shouldStopBacktrack: Boolean
        get() = delay || needClick || waitForUpdate || shouldSkip

    override fun enable() {
        canCancel = false
        delay = false
        needClick = false
        waitForUpdate = false
        shouldSkip = false
        delayedPacketQueue.clear()
    }

    override fun disable() {
        delayedPacketQueue.removeIf {
            handlePacket(it)
            true
        }
    }

    @Suppress("unused", "DEPRECATION")
    private val packetEventHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is PlayerInteractEntityC2SPacket || packet is PlayerInteractBlockC2SPacket) {
            shouldSkip = true
        }

        if (packet is PlayerMoveC2SPacket && packet.changePosition && waitForUpdate) {
            event.cancelEvent()
        }

        if (event.isCancelled || event.origin != TransferOrigin.INCOMING) {
            return@sequenceHandler
        }

        if (waitForUpdate && packet is BlockUpdateS2CPacket && packet.pos.equals(player.blockPos)) {
            waitTicks(1)
            waitForUpdate = false
            needClick = false
            return@sequenceHandler
        }

        if (waitForUpdate) {
            return@sequenceHandler
        }

        if (delay) {
            when (packet) {
                is ChatMessageS2CPacket,
                is GameMessageS2CPacket -> {
                    return@sequenceHandler
                }

                is PlayerPositionLookS2CPacket,
                is DisconnectS2CPacket,
                is PlayerRespawnS2CPacket,
                is GameJoinS2CPacket -> {
                    waitForUpdate = false
                    needClick = false
                    delayedPacketQueue.removeIf {
                        handlePacket(it)
                        true
                    }
                    delay = false
                    return@sequenceHandler
                }
            }

            event.cancelEvent()
            delayedPacketQueue.add(packet)
            return@sequenceHandler
        }

        if (pause) return@sequenceHandler

        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            canCancel = true
        }

        if (((packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id)
                || packet is ExplosionS2CPacket)
            && canCancel
        ) {
            val hitResult = raycast(rotation = Rotation(player.yaw, 90f))
            val pos = hitResult.blockPos.offset(hitResult.side)
            val blockState = world.getBlockState(hitResult.blockPos)
            if (canCancelVelocity(hitResult, blockState)) {
                event.cancelEvent()
                delay = true
                needClick = true
            }
            canCancel = false
        }
    }

    private fun canCancelVelocity(hitResult: BlockHitResult, blockState: BlockState): Boolean {
        if (player.activeItem.useAction == UseAction.EAT) return false
        if (player.activeItem.useAction == UseAction.DRINK) return false
        if (InventoryManager.isInventoryOpen) return false
        if (mc.currentScreen is GenericContainerScreen) return false
        if (onlyOnGround && !player.isOnGround) return false
        if (hitResult.blockPos.getBlock().isInteractable(blockState)) return false
        return blockState.isSolid
    }

    @Suppress("unused")
    private val playerTickEventHandler = handler<PlayerTickEvent> { event ->
        if (needClick && (player.isOnGround || !delayInAir)) {
            val pitch = 90f - (0.01f..0.1f).random()
            val hitResult = raycast(rotation = Rotation(player.yaw, pitch))
            val pos = hitResult.blockPos.offset(hitResult.side)

            if (pos.equals(player.blockPos) && !shouldSkip) {
                delayedPacketQueue.removeIf {
                    handlePacket(it)
                    true
                }
                delay = false

                if (RotationManager.serverRotation.pitch != pitch) {
                    network.sendPacket(
                        PlayerMoveC2SPacket.LookAndOnGround(
                            player.yaw,
                            pitch,
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

                if (interaction.interactBlock(player, Hand.MAIN_HAND, hitResult) == ActionResult.SUCCESS) {
                    player.swingHand(Hand.MAIN_HAND)
                }

                waitForUpdate = true
                needClick = false
            }
        }

        if (waitForUpdate) {
            event.cancelEvent()
        }

        shouldSkip = false
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        tickUntil { waitForUpdate }

        repeat(maxStuckTicks) {
            waitTicks(1)
            if (!waitForUpdate) return@tickHandler
        }

        if (debug) notifyAsMessage(ModuleGrimVelocity, "Max stuck ticks")

        waitForUpdate = false
        needClick = false
    }

}
