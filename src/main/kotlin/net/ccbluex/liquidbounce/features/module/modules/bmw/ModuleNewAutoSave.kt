package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.BlockPos
import kotlin.math.*
import kotlin.random.Random

object ModuleNewAutoSave : ClientModule("NewAutoSave", Category.BMW) {

    private val scaffoldOnlyVoid by boolean("ScaffoldOnlyVoid", true)
    private val scaffoldVoidDistance by int("ScaffoldVoidDistance", 20, 1..50, "blocks")
    private val maxSavingTime by int("MaxSavingTime", 30, 0..100, "ticks")
    private val maxSavingHeight by float("MaxSavingHeight", 0.3f, 0f..2f, "from ground")
    private val pauseOnFlag by int("PauseOnFlag", 20, 0..100, "ticks")
    private val notDuringCombat by boolean("NotDuringCombat", false)

    private const val BLOCK_EDGE = 0.3
    private const val MAX_TRY_COUNT = 3

    private var lastY = -128
    private var savingTicks = 0
    private var wasSpectator = false
    private var pauseTicks = 0
    private var damage = false
    private var tryCount = 0

    private fun reset(disable: Boolean) {
        if (disable) {
            if (savingTicks > 0) ModuleScaffold.enabled = false
        }

        lastY = -128
        savingTicks = 0
        pauseTicks = 0
        damage = false
        wasSpectator = false
        tryCount = 0
    }

    @Suppress("DEPRECATION")
    private fun aboveVoid(): Boolean {
        if (player.isOnGround) return false

        val yRange = if (scaffoldOnlyVoid) {
            world.bottomY..player.y.toInt()
        } else {
            player.y.toInt() - scaffoldVoidDistance..player.y.toInt()
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

    private fun reachable(): Boolean {
        val playerX = player.x
        val playerY = player.eyeY
        val playerZ = player.z

        val searchRadius = player.blockInteractionRange.toInt() + 1
        val searchRadiusSquared = player.blockInteractionRange.sq()

        for (dx in -searchRadius..searchRadius) {
            for (dy in -searchRadius..0) {
                for (dz in -searchRadius..searchRadius) {
                    val distanceSquared = dx * dx + dy * dy + dz * dz
                    if (distanceSquared <= searchRadiusSquared) {
                        val blockPos = BlockPos(
                            playerX.toInt() + dx,
                            playerY.toInt() + dy,
                            playerZ.toInt() + dz
                        )
                        val blockState = world.getBlockState(blockPos)
                        if (!blockState.isAir && !blockState.isReplaceable) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun canSave(): Boolean {
        if (notDuringCombat && ModuleKillAura.running && ModuleKillAura.targetTracker.target != null) {
            return false
        }
        if (player.y > lastY + maxSavingHeight) return false
        if (ModuleScaffold.enabled) return false
        if (ModuleFreeze.enabled) return false
        if (player.isInFluid) return false
        if (!aboveVoid()) return false
        return reachable()
    }

    @Suppress("unused")
    private val worldChangeEventHandler = handler<WorldChangeEvent> {
        reset(true)
    }

    @Suppress("unused")
    private val playerTickEventHandler = handler<PlayerTickEvent> { event ->
        if (savingTicks > 0) {
            event.cancelEvent()
        }
    }

    private val yawOffset = FloatOffsetGenerator()
    private val pitchOffset = FloatOffsetGenerator()

    private class FloatOffsetGenerator : FloatIterator() {
        private var prev = 0f
        override fun hasNext() = true
        override fun nextFloat(): Float {
            var offset: Float
            do {
                offset = Random.nextDouble(0.002, 0.01).toFloat()
            } while (abs(offset - prev) < 1.0E-6F)
            return offset.also { prev = it }
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is PlayerPositionLookS2CPacket) {
            if (savingTicks > 0) {
                reset(true)
            }
            pauseTicks = pauseOnFlag
        }

        if (savingTicks > 0) {
            val yaw = RotationManager.currentRotation?.yaw ?: player.yaw
            val pitch = RotationManager.currentRotation?.pitch ?: player.pitch
            val yawOffset = yawOffset.nextFloat()
            val pitchOffset = pitchOffset.nextFloat()

            when (packet) {
                is PlayerInteractItemC2SPacket -> {
                    event.cancelEvent()
                    sendPacketSilently(
                        PlayerMoveC2SPacket.LookAndOnGround(
                            yaw + yawOffset,
                            pitch + pitchOffset,
                            player.isOnGround,
                            player.horizontalCollision
                        )
                    )
                    sendPacketSilently(
                        PlayerInteractItemC2SPacket(
                            packet.hand,
                            packet.sequence,
                            yaw + yawOffset,
                            pitch + pitchOffset,
                        )
                    )
                }

                is PlayerInteractEntityC2SPacket -> {
                    event.cancelEvent()
                    sendPacketSilently(
                        PlayerMoveC2SPacket.LookAndOnGround(
                            yaw + yawOffset,
                            pitch + pitchOffset,
                            player.isOnGround,
                            player.horizontalCollision
                        )
                    )
                    sendPacketSilently(packet)
                }

                is PlayerInteractBlockC2SPacket -> {
                    event.cancelEvent()
                    sendPacketSilently(
                        PlayerMoveC2SPacket.LookAndOnGround(
                            yaw + yawOffset,
                            pitch + pitchOffset,
                            player.isOnGround,
                            player.horizontalCollision
                        )
                    )
                    sendPacketSilently(packet)
                }
            }
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
            lastY = player.y.toInt()
        }

        if (pauseTicks > 0) {
            pauseTicks--
            return@tickHandler
        }

        if (savingTicks > 0) {
            if (aboveVoid()) {
                savingTicks--
                if (savingTicks == 0) {
                    ModuleScaffold.enabled = false
                    tryCount++
                    waitTicks(2)
                }
            } else {
                ModuleScaffold.enabled = false
                savingTicks = 0
            }
            return@tickHandler
        }

        if (tryCount >= MAX_TRY_COUNT) {
            if (!aboveVoid()) {
                tryCount = 0
            }
            return@tickHandler
        }

        if (canSave()) {
            ModuleScaffold.enabled = true
            savingTicks = maxSavingTime
        }
    }

    override fun onEnabled() {
        reset(false)
        wasSpectator = false
    }

    override fun onDisabled() {
        reset(true)
    }

}
