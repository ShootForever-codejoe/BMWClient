package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.block.getBlock
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.BlockPos
import java.util.EnumSet
import kotlin.math.ceil
import kotlin.math.floor

object ModuleAutoSave : ClientModule("AutoSave", Category.BMW) {

    private enum class ScaffoldWhen(
        override val choiceName: String,
        val meets: () -> Boolean
    ) : NamedChoice {
        DURING_COMBAT("DuringCombat", {
            CombatManager.isInCombat
        }),
        RECEIVE_HIT("ReceiveHit", {
            receiveHitTicks > 0
        })
    }

    private object AutoStuck : ToggleableConfigurable(ModuleAutoSave, "AutoStuck", true) {
        val stuckOnlyVoid by boolean("StuckOnlyVoid", true)
        val stuckFallDistance by int("StuckFallDistance", 5, 1..50, "blocks")
    }

    private object AutoScaffold : ToggleableConfigurable(ModuleAutoSave, "AutoScaffold", true) {
        val scaffoldOnlyVoid by boolean("ScaffoldOnlyVoid", true)
        val scaffoldVoidDistance by int("ScaffoldVoidDistance", 1, 1..50, "blocks")
        val scaffoldWhen by multiEnumChoice<ScaffoldWhen>(
            "ScaffoldWhen",
            EnumSet.of(
                ScaffoldWhen.RECEIVE_HIT,
                ScaffoldWhen.DURING_COMBAT
            )
        )
    }

    init {
        tree(AutoStuck)
        tree(AutoScaffold)
    }

    private const val LOWEST_Y = -64
    private const val BLOCK_EDGE = 0.3
    private const val RECEIVE_HIT_TICKS = 50

    private var lastGroundY = LOWEST_Y
    private var stuckSaving = false
    private var scaffoldSaving = false
    private var wasSpectator = false
    private var receiveHitTicks = 0

    private fun reset(disable: Boolean) {
        if (disable) {
            if (stuckSaving) ModuleStuck.enabled = false
            if (scaffoldSaving) ModuleScaffold.enabled = false
        }

        lastGroundY = LOWEST_Y
        stuckSaving = false
        scaffoldSaving = false
        receiveHitTicks = 0
    }

    private fun aboveVoid(voidDistance: Int = -1): Boolean {
        if (player.isOnGround) return false

        val xRange = mutableListOf(0)
        val zRange = mutableListOf(0)
        if (player.x - floor(player.x) <= BLOCK_EDGE) {
            xRange.add(-1)
        } else if (ceil(player.x) - player.x <= BLOCK_EDGE) {
            xRange.add(1)
        }
        if (player.z - floor(player.z) <= BLOCK_EDGE) {
            zRange.add(-1)
        } else if (ceil(player.z) - player.z <= BLOCK_EDGE) {
            zRange.add(1)
        }

        for (xOffset in xRange) {
            for (zOffset in zRange) {
                for (y in if (voidDistance == -1) LOWEST_Y..lastGroundY else lastGroundY - voidDistance..lastGroundY) {
                    val block = BlockPos(player.x.toInt() + xOffset, y, player.z.toInt() + zOffset).getBlock()
                    if (block?.translationKey != "block.minecraft.air") {
                        return false
                    }
                }
            }
        }

        return true
    }

    @Suppress("unused")
    private val worldChangeEventHandler = handler<WorldChangeEvent> {
        reset(true)
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is PlayerPositionLookS2CPacket) {
            reset(true)
        }

        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            receiveHitTicks = RECEIVE_HIT_TICKS
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.isSpectator || player.abilities.flying) {
            if (!wasSpectator) {
                wasSpectator = true
                reset(true)
            }
            return@tickHandler
        } else {
            if (wasSpectator) wasSpectator = false
        }

        if (receiveHitTicks > 0) receiveHitTicks--
        if (player.hurtTime > 0) {
            receiveHitTicks = RECEIVE_HIT_TICKS
        }

        if (player.isOnGround) {
            lastGroundY = player.y.toInt() - 1
        }

        if (AutoStuck.enabled) {
            if (player.y >= LOWEST_Y + 2
                && (!AutoStuck.stuckOnlyVoid || aboveVoid())
                && !player.isOnGround
                && player.y <= lastGroundY + 1 - AutoStuck.stuckFallDistance
            ) {
                if (!stuckSaving && !ModuleStuck.enabled) {
                    ModuleStuck.enabled = true
                    stuckSaving = true
                }
            } else {
                if (stuckSaving) {
                    stuckSaving = false
                }
            }
        }

        if (AutoScaffold.enabled) {
            if (AutoScaffold.scaffoldWhen.all { it.meets() }
                && aboveVoid(
                    if (AutoScaffold.scaffoldOnlyVoid) -1
                    else AutoScaffold.scaffoldVoidDistance
                )
            ) {
                if (!scaffoldSaving && !ModuleScaffold.enabled) {
                    ModuleScaffold.enabled = true
                    scaffoldSaving = true
                }
            } else {
                if (scaffoldSaving) {
                    ModuleScaffold.enabled = false
                    scaffoldSaving = false
                }
            }
        }
    }

    override fun enable() {
        reset(false)
        wasSpectator = false
    }

}
