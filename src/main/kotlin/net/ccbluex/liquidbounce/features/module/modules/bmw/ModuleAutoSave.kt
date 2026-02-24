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

object ModuleAutoSave : ClientModule("AutoSave", Category.BMW) {

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

        val xOffsets = getBlockOffsets(player.x)
        val zOffsets = getBlockOffsets(player.z)

        return checkVoidBlocks(xOffsets, zOffsets, yRange)
    }

    private fun getBlockOffsets(coordinate: Double): List<Int> {
        val offsets = mutableListOf(0)
        if (coordinate - floor(coordinate) <= BLOCK_EDGE) {
            offsets.add(-1)
        } else if (ceil(coordinate) - coordinate <= BLOCK_EDGE) {
            offsets.add(1)
        }
        return offsets
    }

    private fun checkVoidBlocks(xOffsets: List<Int>, zOffsets: List<Int>, yRange: IntRange): Boolean {
        for (xOffset in xOffsets) {
            for (zOffset in zOffsets) {
                for (y in yRange.reversed()) {
                    val blockPos = BlockPos(
                        player.x.toInt() + xOffset,
                        y,
                        player.z.toInt() + zOffset
                    )
                    val blockState = world.getBlockState(blockPos)
                    if (!blockState.isAir && !blockState.isLiquid) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun checkAutoStuckCondition(): Boolean {
        val isAboveVoid = if (AutoStuck.stuckOnlyVoid) aboveVoid() else true
        return player.y >= world.bottomY
            && isAboveVoid
            && !player.isOnGround
            && player.y <= lastY - AutoStuck.stuckFallDistance
    }

    private fun checkAutoScaffoldCondition(): Boolean {
        val isInCombat = receiveHitTicks > 0 || CombatManager.isInCombat
        val noTarget = ModuleKillAura.targetTracker.target == null
        val aboveVoidResult = aboveVoid(AutoScaffold.scaffoldOnlyVoid)
        return isInCombat && noTarget && aboveVoidResult
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
            if (checkAutoStuckCondition()) {
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
            if (checkAutoScaffoldCondition()) {
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
