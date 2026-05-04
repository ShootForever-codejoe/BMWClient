/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.event.events.ChatReceiveEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleAutoWalk
import net.ccbluex.liquidbounce.render.FULL_BOX
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.client.stripMinecraftColorCodes
import net.ccbluex.liquidbounce.utils.math.toVec3d
import net.minecraft.block.Block
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.util.math.BlockPos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

object ModuleColorBlindHelper : ClientModule("ColorBlindHelper", Category.BMW) {

    private val debugEnabled by boolean("Debug", false)
    private val heypixelEnabled by boolean("HeyPixel", false)
    private val heypixelIntervalSeconds by int("HeyPixelInterval", 15, 5..20, "seconds")
    private val autoStuck by boolean("AutoStuck", false)

    private const val SEARCH_HALF_SIZE = 30
    private const val UPDATE_EVERY_TICKS = 2
    private const val DEBUG_INTERVAL_TICKS = 10

    private var tickCounter = 0
    private var debugTickCounter = 0
    private var lastDebugMessage: String? = null
    private var heypixelTickCounter = 0
    private var heypixelIntervalTicks = heypixelIntervalSeconds * 20
    private var currentTargetBlockPos: BlockPos? = null
    private var targetStandPos: BlockPos? = null
    private var freeTargetPos: BlockPos? = null
    private var freeTargetStandPos: BlockPos? = null
    private var autowalkOwned = false
    private var pausedByHeypixel = true

    private fun getPlayerGameMode(player: PlayerEntity): String {
        try {
            val im = mc.interactionManager
            if (im != null) {
                try {
                    val f = im::class.java.getDeclaredField("currentGameMode")
                    f.isAccessible = true
                    val gm = f.get(im)
                    if (gm != null) return gm.toString()
                } catch (_: Throwable) { }
                try {
                    val m = im::class.java.getMethod("getCurrentGameMode")
                    val gm = m.invoke(im)
                    if (gm != null) return gm.toString()
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }

        try {
            if (player.isSpectator) return "SPECTATOR"
        } catch (_: Throwable) { }

        try {
            val isCreativeMethod = player::class.java.getMethod("isCreative")
            val res = isCreativeMethod.invoke(player)
            if (res is Boolean && res) return "CREATIVE"
        } catch (_: Throwable) { }

        try {
            val abilitiesField = player::class.java.getDeclaredField("abilities")
            abilitiesField.isAccessible = true
            val abilities = abilitiesField.get(player)
            if (abilities != null) {
                try {
                    val creativeField = abilities::class.java.getDeclaredField("creativeMode")
                    creativeField.isAccessible = true
                    val creative = creativeField.getBoolean(abilities)
                    if (creative) return "CREATIVE"
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }

        return "SURVIVAL"
    }


    @Suppress("unused")
    private val chatHandler = handler<ChatReceiveEvent> { event ->
        val msg = event.message.stripMinecraftColorCodes()

        val clientPlayer = mc.player

        val lostByChat = Regex("""你输了！\s*剩余\s*\d+\s*名玩家""").containsMatchIn(msg)
        val isSpectator = clientPlayer?.let { getPlayerGameMode(it) == "SPECTATOR" } ?: false

        if (isSpectator || lostByChat) {
            if (!pausedByHeypixel) {
                pausedByHeypixel = true
                if (ModuleAutoWalk.enabled) ModuleAutoWalk.enabled = false
                ModuleSpeed.enabled = false
                stopAutoWalkIfOwned()
                notifyAsMessage(ModuleColorBlindHelper, "ColorBlindHelper paused: 检测到失败提示。")
            }
            return@handler
        }

        if (msg.contains("游戏将在 5 秒后开始")) {
            if (pausedByHeypixel) {
                pausedByHeypixel = false
                mc.player?.let { player ->
                    try {
                        player.inventory.selectedSlot = 4
                    } catch (_: Throwable) {
                    }
                }
                notifyAsMessage(ModuleColorBlindHelper, "ColorBlindHelper resumed: 新一局即将开始，已切到第5格。")
                notifyAsMessage(ModuleColorBlindHelper, "AutoStuck也许被妖猫patch，不建议开启！！！")
            }
            return@handler
        }
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        val targetPos = currentTargetBlockPos ?: freeTargetPos ?: return@handler
        renderEnvironmentForWorld(event.matrixStack) {
            withPositionRelativeToCamera(targetPos.toVec3d()) {
                drawBox(
                    FULL_BOX,
                    faceColor = Color4b(255, 140, 0, 60),
                    outlineColor = Color4b(255, 200, 0, 220)
                )
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> { _ ->
        val player = mc.player ?: return@handler

        heypixelIntervalTicks = heypixelIntervalSeconds * 20

        if (heypixelEnabled) {
            heypixelTickCounter++
            if (heypixelTickCounter >= heypixelIntervalTicks) {
                heypixelTickCounter = 0
                try {
                    player.inventory.selectedSlot = 4
                } catch (_: Throwable) {
                    try {
                        val f = player.inventory::class.java.getDeclaredField("selectedSlotIndex")
                        f.isAccessible = true
                        f.setInt(player.inventory, 4)
                    } catch (_: Throwable) {
                    }
                }
            }
        } else {
            heypixelTickCounter = 0
        }

        if (pausedByHeypixel) {
            stopAutoWalkIfOwned()
            ModuleSpeed.enabled = false
            return@handler
        }

        if (autoStuck) {
            val under1 = player.blockPos.down(1).getState()
            val under2 = player.blockPos.down(2).getState()
            val under3 = player.blockPos.down(3).getState()
            val under4 = player.blockPos.down(4).getState()
            val under5 = player.blockPos.down(5).getState()
            val isAirBelow =
                (under1?.isAir ?: true) && (under2?.isAir ?: true) && (under3?.isAir ?: true) && (under4?.isAir
                    ?: true) && (under5?.isAir ?: true)

            if (isAirBelow) {
                ModuleFreeze.enabled = true
                ModuleAutoWalk.enabled = false
                ModuleSpeed.enabled = false
                return@handler
            } else {
                ModuleFreeze.enabled = false
            }
        } else {
            ModuleFreeze.enabled = false
        }

        tickCounter++
        if (tickCounter >= UPDATE_EVERY_TICKS) {
            tickCounter = 0
            if (heypixelEnabled) checkHeypixelNameColors(player)
        }
        val hotbarBlock = findTargetBlockFromHotbar(player)

        if (hotbarBlock != null) {
            freeTargetPos = null
            freeTargetStandPos = null
            if (currentTargetBlockPos == null || tickCounter == 0) {
                updateTarget(player, hotbarBlock)
            }
            navigateToTarget(player, targetStandPos)
        } else {
            currentTargetBlockPos = null
            targetStandPos = null
            if (freeTargetStandPos == null || tickCounter == 0) {
                updateFreeTarget(player)
            }
            navigateToTarget(player, freeTargetStandPos)
        }

        if (debugEnabled) {
            debugTickCounter++
            if (debugTickCounter >= DEBUG_INTERVAL_TICKS) {
                debugTickCounter = 0
                val mode = getPlayerGameMode(player)
                val distMsg = computeCurrentDistanceMessage(player)
                val msg = "Mode: $mode | $distMsg"
                if (msg != lastDebugMessage) {
                    notifyAsMessage(ModuleColorBlindHelper, msg)
                    lastDebugMessage = msg
                }
            }
        } else {
            lastDebugMessage = null
            debugTickCounter = 0
        }
    }

    private fun navigateToTarget(player: PlayerEntity, standPos: BlockPos?) {
        if (standPos == null) {
            stopAutoWalkIfOwned()
            ModuleSpeed.enabled = false
            return
        }

        if (player.blockPos == standPos && player.isOnGround) {
            stopAutoWalkIfOwned()
            ModuleSpeed.enabled = false
            return
        }

        val targetX = standPos.x + 0.5
        val targetZ = standPos.z + 0.5
        val dx = targetX - player.x
        val dz = targetZ - player.z
        val dy = (standPos.y + 0.5) - player.y

        val yaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
        val pitch = (-Math.toDegrees(atan2(dy, hypot(dx, dz)))).toFloat()
        player.yaw = yaw
        player.pitch = pitch

        if (!ModuleAutoWalk.enabled) {
            ModuleAutoWalk.enabled = true
            autowalkOwned = true
        }

        val horizontalDist = hypot(dx, dz).toFloat()
        ModuleSpeed.enabled = horizontalDist > 5.0f
    }

    fun enable() {
        tickCounter = 0
        debugTickCounter = 0
        currentTargetBlockPos = null
        targetStandPos = null
        freeTargetPos = null
        freeTargetStandPos = null
        autowalkOwned = false
        lastDebugMessage = null
        pausedByHeypixel = heypixelEnabled
    }

    fun disable() {
        stopAutoWalkIfOwned()
        ModuleSpeed.enabled = false
        ModuleFreeze.enabled = false
        currentTargetBlockPos = null
        targetStandPos = null
        freeTargetPos = null
        freeTargetStandPos = null
        ModuleAutoWalk.enabled = false
        pausedByHeypixel = false
    }

    private fun findTargetBlockFromHotbar(player: PlayerEntity): Block? {
        for (slot in 0..8) {
            val stack = player.inventory.getStack(slot)
            val item = stack?.item
            if (item is BlockItem) return item.block
        }
        return null
    }

    private fun updateTarget(player: PlayerEntity, targetBlock: Block) {
        val base = player.blockPos
        val baseX = base.x
        val baseY = base.y
        val baseZ = base.z

        var best: BlockPos? = null
        var bestDistSq = Double.MAX_VALUE

        for (x in (baseX - SEARCH_HALF_SIZE)..<baseX + SEARCH_HALF_SIZE) {
            for (z in (baseZ - SEARCH_HALF_SIZE)..<baseZ + SEARCH_HALF_SIZE) {
                for (downOffset in 1..2) {
                    val pos = BlockPos(x, baseY - downOffset, z)
                    val state = pos.getState() ?: continue
                    val block = state.block ?: continue
                    if (block !== targetBlock) continue

                    val above1 = pos.up()
                    val above2 = pos.up(2)
                    val s1 = above1.getState() ?: continue
                    val s2 = above2.getState() ?: continue
                    if (!s1.isAir || !s2.isAir) continue

                    val cx = x + 0.5
                    val cy = pos.y + 0.5
                    val cz = z + 0.5
                    val dx = cx - player.x
                    val dy = cy - player.y
                    val dz = cz - player.z
                    val distSq = dx * dx + dy * dy + dz * dz

                    if (distSq < bestDistSq) {
                        bestDistSq = distSq
                        best = pos
                    }
                }
            }
        }

        currentTargetBlockPos = best
        targetStandPos = best?.up()
    }

    private fun updateFreeTarget(player: PlayerEntity) {
        val base = player.blockPos
        val baseX = base.x
        val baseY = base.y
        val baseZ = base.z

        var bestPos: BlockPos? = null
        var bestVariety = -1

        for (x in (baseX - SEARCH_HALF_SIZE)..<baseX + SEARCH_HALF_SIZE) {
            for (z in (baseZ - SEARCH_HALF_SIZE)..<baseZ + SEARCH_HALF_SIZE) {
                for (downOffset in 1..2) {
                    val pos = BlockPos(x, baseY - downOffset, z)
                    val state = pos.getState() ?: continue
                    if (state.isAir) continue

                    val above1 = pos.up()
                    val above2 = pos.up(2)
                    val s1 = above1.getState() ?: continue
                    val s2 = above2.getState() ?: continue
                    if (!s1.isAir || !s2.isAir) continue

                    val blockTypes = mutableSetOf<Block>()
                    for (nx in (x - 2)..(x + 2)) {
                        for (nz in (z - 2)..(z + 2)) {
                            val nPos = BlockPos(nx, pos.y, nz)
                            val nBlock = nPos.getState()?.block ?: continue
                            if (!nBlock.defaultState.isAir) blockTypes.add(nBlock)
                        }
                    }

                    val variety = blockTypes.size
                    if (variety > bestVariety) {
                        bestVariety = variety
                        bestPos = pos
                    }
                }
            }
        }

        freeTargetPos = bestPos
        freeTargetStandPos = bestPos?.up()
    }

    private fun checkHeypixelNameColors(player: PlayerEntity) {
        val world = mc.world ?: return

        var othersWhiteCount = 0
        for (p in world.players) {
            if (p === player) continue
            val c = p.displayName?.style?.color?.rgb
            if (c == null || c == 0xFFFFFF) {
                othersWhiteCount++
            }
        }
        if (othersWhiteCount == 0) {
            if (!pausedByHeypixel) {
                pausedByHeypixel = true
                stopAutoWalkIfOwned()
                ModuleSpeed.enabled = false
                notifyAsMessage(ModuleColorBlindHelper, "ColorBlindHelper paused: 检测到成功提示")
            }
        }
    }

    private fun stopAutoWalkIfOwned() {
        if (autowalkOwned && ModuleAutoWalk.enabled) {
            ModuleAutoWalk.enabled = false
        }
        autowalkOwned = false
    }

    private fun computeCurrentDistanceMessage(player: PlayerEntity): String {
        val standPos = targetStandPos ?: freeTargetStandPos ?: return "No Target!"
        val dx = (standPos.x + 0.5) - player.x
        val dy = (standPos.y + 0.5) - player.y
        val dz = (standPos.z + 0.5) - player.z
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        return "Target distance: %.2f".format(dist)
    }

}
