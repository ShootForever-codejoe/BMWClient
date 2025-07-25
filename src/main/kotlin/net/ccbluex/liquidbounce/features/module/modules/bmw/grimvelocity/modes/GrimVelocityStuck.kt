package net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.modes

import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimvelocity.ModuleGrimVelocity
import net.ccbluex.liquidbounce.utils.block.getBlock
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object GrimVelocityStuck : Choice("Stuck") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleGrimVelocity.modes

    private val skipTicks by int("SkipTicks", 3, 0..20, "ticks")

    private const val LOWEST_Y = -64

    private var velocityInputTicks = 0

    @Suppress("unused")
    private val playerTickEventHandler = handler<PlayerTickEvent> { event ->
        if (velocityInputTicks > 0) {
            event.cancelEvent()
            velocityInputTicks--
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            if (velocityInputTicks > 0) {
                event.cancelEvent()
            } else {
                var blockPos: BlockPos
                val x = player.x.toInt()
                var y = player.y.toInt()
                val z = player.z.toInt()
                do {
                    y--
                    if (y < LOWEST_Y) return@handler
                    blockPos = BlockPos(x, y, z)
                } while (blockPos.getBlock()?.translationKey == "block.minecraft.air")

                velocityInputTicks = skipTicks
                event.cancelEvent()
                interaction.sendSequencedPacket(world) { sequence ->
                    PlayerInteractBlockC2SPacket(
                        player.activeHand,
                        BlockHitResult(player.pos, Direction.UP, blockPos, false),
                        sequence
                    )
                }
            }
        }

        if (packet is BlockUpdateS2CPacket) {
            velocityInputTicks = 0
        }

        if (packet is PlayerPositionLookS2CPacket) {
            velocityInputTicks = 0
        }
    }

    override fun enable() {
        velocityInputTicks = 0
    }

}
