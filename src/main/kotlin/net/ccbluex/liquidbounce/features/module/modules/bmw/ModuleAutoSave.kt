package net.ccbluex.liquidbounce.features.module.modules.bmw

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
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.BlockPos
import kotlin.math.ceil
import kotlin.math.floor

object ModuleAutoSave : ClientModule("AutoSave", Category.BMW) {

    private object AutoStuck : ToggleableConfigurable(ModuleAutoSave, "AutoStuck", true) {
        val stuckOnlyVoid by boolean("StuckOnlyVoid", true)
        val stuckFallDistance by int("StuckFallDistance", 5, 1..50, "blocks")
    }

    private object AutoScaffold : ToggleableConfigurable(ModuleAutoSave, "AutoScaffold", true) {
        val scaffoldOnlyVoid by boolean("ScaffoldOnlyVoid", true)
        val scaffoldVoidDistance by int("ScaffoldVoidDistance", 1, 1..50, "blocks")
        val scaffoldOnlyDuringCombat by boolean("ScaffoldOnlyDuringCombat", false)
        val scaffoldRequireHit by boolean("ScaffoldRequireHit", true)
    }

    init {
        tree(AutoStuck)
        tree(AutoScaffold)
    }

    const val LOWEST_Y = -64
    const val EDGE = 0.3

    private var lastGroundY = LOWEST_Y
    private var stuckSaving = false
    private var scaffoldSaving = false
    private var wasSpectator = false

    private fun reset(disable: Boolean) {
        if (disable) {
            if (stuckSaving) ModuleStuck.enabled = false
            if (scaffoldSaving) ModuleScaffold.enabled = false
        }

        lastGroundY = LOWEST_Y
        stuckSaving = false
        scaffoldSaving = false
    }

    private fun aboveVoid(voidDistance: Int = -1): Boolean {
        if (player.isOnGround) return false

        val xRange = mutableListOf(0)
        val zRange = mutableListOf(0)
        if (player.x - floor(player.x) <= EDGE) {
            xRange.add(-1)
        } else if (ceil(player.x) - player.x <= EDGE) {
            xRange.add(1)
        }
        if (player.z - floor(player.z) <= EDGE) {
            zRange.add(-1)
        } else if (ceil(player.z) - player.z <= EDGE) {
            zRange.add(1)
        }

        for (xOffset in xRange) {
            for (zOffset in zRange) {
                for (y in if (voidDistance == -1) LOWEST_Y..lastGroundY else lastGroundY - voidDistance..lastGroundY) {
                    val block = BlockPos(player.x.toInt() + xOffset, y, player.z.toInt() + zOffset).getBlock()
                    block?.translationKey.let {
                        if (it != "block.minecraft.air") {
                            return false
                        }
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
        if (event.packet is PlayerPositionLookS2CPacket) {
            reset(true)
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
            if ((!AutoScaffold.scaffoldOnlyDuringCombat || CombatManager.isInCombat)
                && (!AutoScaffold.scaffoldRequireHit || player.hurtTime in 1..9)
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
