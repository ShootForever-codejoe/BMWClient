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
        val scaffoldOnlyDuringCombat by boolean("ScaffoldOnlyDuringCombat", true)
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

    private fun reset(disable: Boolean) {
        lastGroundY = LOWEST_Y
        if (disable) {
            if (stuckSaving) ModuleStuck.enabled = false
            if (scaffoldSaving) ModuleScaffold.enabled = false
        }
        stuckSaving = false
        scaffoldSaving = false
    }

    private fun aboveVoid(voidDistance: Int = -1): Boolean {
        if (player.isOnGround) return false

        var xMinOffset = 0
        var xMaxOffset = 0
        var zMinOffset = 0
        var zMaxOffset = 0
        if (player.x - floor(player.x) <= EDGE) {
            xMinOffset = -1
        }
        if (ceil(player.x) - player.x <= EDGE) {
            xMaxOffset = 1
        }
        if (player.z - floor(player.z) <= EDGE) {
            zMinOffset = -1
        }
        if (ceil(player.z) - player.z <= EDGE) {
            zMaxOffset = 1
        }

        for (xOffset in xMinOffset..xMaxOffset) {
            for (zOffset in zMinOffset..zMaxOffset) {
                for (y in
                if (voidDistance == -1) LOWEST_Y..lastGroundY
                else lastGroundY - voidDistance..lastGroundY
                ) {
                    val block = BlockPos(player.x.toInt() + xOffset, y, player.z.toInt() + zOffset).getBlock()
                    block?.translationKey?.let {
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
        if (player.isOnGround) {
            lastGroundY = player.y.toInt() - 1
        }

        if (AutoStuck.enabled) {
            if ((!AutoStuck.stuckOnlyVoid || aboveVoid()) && !player.isOnGround && player.y <= lastGroundY + 1 - AutoStuck.stuckFallDistance) {
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
    }

}
