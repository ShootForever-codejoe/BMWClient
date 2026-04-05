package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.config.types.nesting.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.fireballfly.ModuleFireballFly
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.BlockPos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.ranges.reversed

object ModuleOldAutoSave : ClientModule("OldAutoSave", Category.BMW) {

    private object AutoStuck : ToggleableConfigurable(this, "AutoStuck", true) {
        val stuckOnlyVoid by boolean("StuckOnlyVoid", true)
        val stuckFallDistance by int("StuckFallDistance", 5, 1..50, "blocks")
    }

    private object AutoScaffold : ToggleableConfigurable(this, "AutoScaffold", true) {
        val scaffoldOnlyVoid by boolean("ScaffoldOnlyVoid", false)
        val scaffoldVoidDistance by int("ScaffoldVoidDistance", 15, 1..50, "blocks")
    }

    init {
        tree(AutoStuck)
        tree(AutoScaffold)
    }

    private val pauseOnFlag by int("PauseOnFlag", 20, 0..100, "ticks")

    private const val BLOCK_EDGE = 0.3
    private const val RECEIVE_HIT_TICKS = 30

    private var lastY = 0
    private var stuckSaving = false
    private var scaffoldSaving = false
    private var wasSpectator = false
    private var receiveHitTicks = 0
    private var pauseTicks = 0
    private var damage = false

    private fun reset(disable: Boolean) {
        if (disable) {
            if (stuckSaving) ModuleFreeze.enabled = false
            if (scaffoldSaving) ModuleScaffold.enabled = false
        }

        lastY = 0
        stuckSaving = false
        scaffoldSaving = false
        receiveHitTicks = 0
        pauseTicks = 0
        damage = false
    }

    @Suppress("DEPRECATION")
    private fun aboveVoid(fromBottom: Boolean = true): Boolean {
        if (player.isOnGround) return false

        val yRange = if (fromBottom) {
            world.bottomY..player.y.toInt()
        } else {
            player.y.toInt() - AutoScaffold.scaffoldVoidDistance..player.y.toInt()
        }

        if (yRange.isEmpty()) return true

        val xOffsetRange = mutableListOf(0)
        val zOffsetRange = mutableListOf(0)
        if (player.x - floor(player.x) <= BLOCK_EDGE) {
            xOffsetRange.add(-1)
        } else if (ceil(player.x) - player.x <= BLOCK_EDGE) {
            xOffsetRange.add(1)
        }
        if (player.z - floor(player.z) <= BLOCK_EDGE) {
            zOffsetRange.add(-1)
        } else if (ceil(player.z) - player.z <= BLOCK_EDGE) {
            zOffsetRange.add(1)
        }

        for (xOffset in xOffsetRange) {
            for (zOffset in zOffsetRange) {
                for (y in yRange.reversed()) {
                    val blockState = world.getBlockState(BlockPos(
                        player.x.toInt() + xOffset,
                        y,
                        player.z.toInt() + zOffset
                    ))
                    if (!blockState.isAir && !blockState.isLiquid) {
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
            pauseTicks = pauseOnFlag
        }

        if (packet is EntityDamageS2CPacket && packet.entityId == player.id) {
            damage = true
        }

        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id && damage) {
            if (!ModuleFireballFly.running) receiveHitTicks = RECEIVE_HIT_TICKS
            damage = false
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

        if (pauseTicks > 0) pauseTicks--
        if (receiveHitTicks > 0) receiveHitTicks--
        if (player.hurtTime > 0) {
            receiveHitTicks = RECEIVE_HIT_TICKS
        }

        if (player.isOnGround) {
            lastY = player.y.toInt()
        }

        if (pauseTicks > 0) return@tickHandler

        if (AutoStuck.enabled) {
            if (player.y >= world.bottomY
                && (!AutoStuck.stuckOnlyVoid || aboveVoid())
                && !player.isOnGround
                && player.y <= lastY - AutoStuck.stuckFallDistance
            ) {
                if (!stuckSaving && !ModuleFreeze.enabled) {
                    ModuleFreeze.enabled = true
                    stuckSaving = true
                }
            } else {
                if (stuckSaving) {
                    stuckSaving = false
                }
            }
        }

        if (AutoScaffold.enabled) {
            if ((receiveHitTicks > 0 || CombatManager.isInCombat)
                && (!ModuleKillAura.running || ModuleKillAura.targetTracker.target == null)
                && aboveVoid(AutoScaffold.scaffoldOnlyVoid)
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

    override fun onEnabled() {
        reset(false)
        wasSpectator = false
    }

}
