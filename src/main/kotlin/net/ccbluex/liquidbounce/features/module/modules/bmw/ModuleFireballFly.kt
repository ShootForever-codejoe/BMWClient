package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.minecraft.entity.Entity
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntityS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import org.lwjgl.glfw.GLFW
import java.lang.String
import java.util.concurrent.LinkedBlockingQueue
import kotlin.Int

@Suppress("unused")
object ModuleFireballFly : ClientModule("FireBallFly", Category.BMW) {

    private val velocityBeforeExplode by boolean("VelocityBeforeExplode", false)

    private val packets = LinkedBlockingQueue<Packet<*>>()
    private var target: Entity? = null
    private var s12count = 0

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is CommonPongC2SPacket || packet is KeepAliveC2SPacket) {
            event.cancelEvent()
            packets.add(packet)
        }

        if (packet is PlayerInteractEntityC2SPacket && target == null) {
            event.cancelEvent()
        }

        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            event.cancelEvent()
            packets.add(packet)
            s12count += 1
        }

        if (packet is ExplosionS2CPacket) {
            event.cancelEvent()
            packets.add(packet)
            s12count += 1
        }

        if (packet is BlockUpdateS2CPacket || packet is BlockEventS2CPacket || packet is BlockEntityUpdateS2CPacket) {
            event.cancelEvent()
            packets.add(packet)
        }

        if (packet is EntityS2CPacket && packet.getEntity(world) == mc.player) {
            event.cancelEvent()
            packets.add(packet)
        }

        if (packet is PlayerPositionLookS2CPacket) {
            packets.add(packet)
        }
    }

    @Suppress("unused")
    private val keyboardKeyEventHandler = handler<KeyboardKeyEvent> { event ->
        if (packets.isEmpty() || s12count <= 0 || event.key.code != GLFW.GLFW_KEY_K) return@handler

        var c0fId = -1
        var lastId = -1
        var packet = packets.take()
        while (!(packet is EntityVelocityUpdateS2CPacket
                || (packet is ExplosionS2CPacket
                && (packet.playerKnockback.get().x != 0.0
                || packet.playerKnockback.get().y != 0.0
                || packet.playerKnockback.get().z != 0.0)))
        ) {
            if (packet is CommonPongC2SPacket) {
                val newId: Int = packet.parameter

                if (c0fId != -1) {
                    // 连续性检查
                    if (c0fId - newId == 1) {
                        // 正常连续：更新状态
                        lastId = c0fId
                        c0fId = newId
                    } else if (lastId != -1 && lastId - newId == 1) {
                        // 与上一个ID连续（回退修正）
                        c0fId = lastId
                    }
                    // 删除原重置条件 !!!
                } else {
                    // 初始状态：直接记录
                    c0fId = newId
                    lastId = -1
                }

                // 新增：单一包状态遇到错误ID时覆盖而非重置
                if (c0fId != -1 && lastId == -1) {
                    // 用新包覆盖旧起点（避免状态被清空）
                    c0fId = newId
                }
            }
            sendPacketSilently(packet)
            packet = packets.take()
        }
        sendPacketSilently(packet)
        s12count -= 1
        notifyAsMessage("lastC0f : $lastId")
        notifyAsMessage("C0f : $c0fId")
        while (!(packet is CommonPongC2SPacket && packet.parameter == c0fId - 1)) {
            if (packet is CommonPongC2SPacket) {
                notifyAsMessage(String.valueOf(packet.parameter))
            }
            packet = packets.take()
            sendPacketSilently(packet)
        }
        sendPacketSilently(packets.take())
    }

    private fun blink() {
        val packets2 = LinkedBlockingQueue<EntityVelocityUpdateS2CPacket?>()
        val packets3 = LinkedBlockingQueue<ExplosionS2CPacket?>()
        val currentPackets = LinkedBlockingQueue<Packet<*>?>()
        while (!packets.isEmpty()) {
            val packet = packets.take()
            when (packet) {
                is EntityVelocityUpdateS2CPacket -> {
                    packets2.add(packet)
                }

                is ExplosionS2CPacket -> {
                    packets3.add(packet)
                }

                else -> {
                    currentPackets.add(packet)
                }
            }
        }
        if (velocityBeforeExplode) {
            while (!packets2.isEmpty()) {
                packets2.take()?.let { sendPacketSilently(it) }
            }
        }

        while (!packets3.isEmpty()) {
            packets3.take()?.let { sendPacketSilently(it) }
        }
        if (!velocityBeforeExplode) {
            while (!packets2.isEmpty()) {
                packets2.take()?.let { sendPacketSilently(it) }
            }
        }
        while (!currentPackets.isEmpty()) {
            currentPackets.take()?.let { sendPacketSilently(it) }
        }
        target = null
    }

    override fun enable() {
        s12count = 0
        target = if (ModuleKillAura.targetTracker.target != null) {
            ModuleKillAura.targetTracker.target
        } else {
            null
        }
    }

    override fun disable() {
        blink()
    }

}
