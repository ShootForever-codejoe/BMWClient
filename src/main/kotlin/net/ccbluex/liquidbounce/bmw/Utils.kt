/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
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

package net.ccbluex.liquidbounce.bmw

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.NotificationEvent.Severity
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.bow.GrimNoSlowBow
import net.ccbluex.liquidbounce.features.module.modules.bmw.grimnoslow.food.GrimNoSlowFood
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.entity.getBoundingBoxAt
import net.ccbluex.liquidbounce.utils.math.toVec3d
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.PotionItem
import net.minecraft.network.packet.Packet
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper.wrapDegrees
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import java.lang.Math.clamp
import kotlin.jvm.optionals.getOrNull

fun notifyAsMessage(module: ClientModule?, content: String) {
    if (module == null) {
        mc.player?.sendMessage(Text.of("§7[§eBMW§7] §f$content"), false)
    } else {
        mc.player?.sendMessage(Text.of("§7[§eBMW§7] [§b${module.displayName()}§7] §f$content"), false)
    }
}

fun notifyAsMessage(content: String) {
    notifyAsMessage(null, content)
}

fun notifyAsNotification(module: ClientModule?, content: String, severity: Severity = Severity.INFO) {
    notification(module?.displayName() ?: "BMWClient", Text.of(content), severity)
}

fun notifyAsNotification(content: String, severity: Severity = Severity.INFO) {
    notifyAsNotification(null, content, severity)
}

fun notifyAsMessageAndNotification(module: ClientModule?, content: String, severity: Severity = Severity.INFO) {
    notifyAsMessage(module, content)
    notifyAsNotification(module, content, severity)
}

fun notifyAsMessageAndNotification(content: String, severity: Severity = Severity.INFO) {
    notifyAsMessageAndNotification(null, content, severity)
}

fun ClientModule.displayName(): String {
    if (!ModuleHud.spaceSeperatedNames) return name

    if (name.isEmpty()) return name

    val result = StringBuilder()
    result.append(name[0])

    for (i in 1 until name.length) {
        val currentChar = name[i]
        val previousChar = name[i - 1]

        if (currentChar.isUpperCase() && previousChar.isLowerCase()) {
            result.append(' ')
        }
        result.append(currentChar)
    }

    return result.toString()
}

fun sendPacketNoEvent(parent: EventListener, packet: Packet<*>) {
    sendPacketSilently(packet)
    val event = PacketEvent(TransferOrigin.OUTGOING, packet)
    EventManager.callEventExcept(event, arrayOf(parent))
}

fun normalizeYaw(yaw: Float): Float {
    return wrapDegrees(yaw)
}

fun normalizePitch(pitch: Float): Float {
    return clamp(pitch, -90.0f, 90.0f)
}

const val GRAVITY = 0.08
const val DRAG = 0.98

data class PlayerSimulationResult(
    val position: Vec3d,
    val velocity: Vec3d,
    val tick: Int,
    val stop: Boolean
)

fun simulatePlayerMovement(
    ticks: Int,
    stopWhen: (position: Vec3d, velocity: Vec3d, tick: Int) -> Boolean = { _, _, _ -> false }
): PlayerSimulationResult {
    var position = player.pos
    var velocity = player.velocity

    if (stopWhen(position, velocity, 0)) {
        return PlayerSimulationResult(position, velocity, 0, true)
    }

    val movementForward = player.input.movementForward.toDouble()
    val movementSideways = player.input.movementSideways.toDouble()

    repeat(ticks) { tick ->
        // Movement
        val inputVelocity = Entity.movementInputToVelocity(
            Vec3d(movementSideways * DRAG, 0.0, movementForward * DRAG),
            0.02f,
            player.yaw
        )
        velocity = velocity.add(inputVelocity)

        // Collisions
        velocity = Entity.adjustMovementForCollisions(
            player,
            velocity,
            player.getBoundingBoxAt(position),
            world,
            emptyList<VoxelShape>()
        )

        position = position.add(velocity)

        velocity = velocity.multiply(DRAG, DRAG, DRAG).subtract(0.0, GRAVITY, 0.0)

        if (stopWhen(position, velocity, tick + 1)) {
            return PlayerSimulationResult(position, velocity, tick + 1, true)
        }
    }

    return PlayerSimulationResult(position, velocity, ticks, false)
}

fun isOnGround(position: Vec3d) = world.getBlockCollisions(
    player,
    player.getBoundingBoxAt(position.add(0.0, -0.0001, 0.0))
).iterator().hasNext()

fun getStandingBlock(pos: Vec3d? = null): BlockPos? {
    val box = if (pos != null) {
        player.getBoundingBoxAt(pos)
    } else {
        player.boundingBox
    }

    return world.findSupportingBlockPos(
        player,
        box.offset(0.0, -0.0001, 0.0)
    ).getOrNull()
}

val BlockPos.topCenter: Vec3d
    get() = this.toVec3d().add(
    0.5,
    this.getState()?.getCollisionShape(world, this)?.boundingBox?.maxY ?: 1.0,
    0.5
)

/**
 * @return 0-8: slot, 9: offhand, -1: not found
 */
fun getWaterBucketSlot(): Int {
    for (i in 0..8) {
        if (player.inventory.getStack(i).item == Items.WATER_BUCKET) {
            return i
        }
    }

    if (player.getStackInHand(Hand.OFF_HAND).item == Items.WATER_BUCKET) {
        return 9
    }

    return -1
}

fun getOppositeHand(hand: Hand): Hand {
    return if (hand == Hand.MAIN_HAND) Hand.OFF_HAND else Hand.MAIN_HAND
}

fun isUsableItem(itemStack: ItemStack): Boolean {
    if (itemStack.item in arrayOf(
            Items.ENDER_PEARL,
            Items.TNT,
            Items.FIRE_CHARGE,
            Items.WIND_CHARGE,
        )) return true

    if (itemStack.useAction in GrimNoSlowFood.useActions
        || itemStack.useAction in GrimNoSlowBow.useActions
    ) return true

    if (itemStack.item is PotionItem) {
        return true
    }

    return false
}
