package net.ccbluex.liquidbounce.bmw

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.HeypixelSWKillEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket

const val HEYPIXEL_SW_END_MESSAGE = "可以用 /hub 退出观察者模式并返回大厅"

object HeypixelSWKillEventListener : EventListener {

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet !is GameMessageS2CPacket) return@handler

        val message = packet.content.string

        val patterns = listOf(
            Regex("(.+?) 被 (.+?) 击败.*"),
            Regex("(.+?) 被炸成了粉尘, 最终还是被 (.+?) 击败.*"),
            Regex("(.+?) 消逝了, 最终还是被 (.+?) 击败.*"),
            Regex("(.+?) 被架在了烧烤架上, 熟透了, 最终还是被 (.+?) 击败.*"),
            Regex("(.+?) 跑得很快, 但是他还是摔了一跤, 最终被 (.+?) 击败.*"),
            Regex("(.+?) 被 (.+?) 用弓箭射穿了.*"),
            Regex("(.+?) 被重压地无法呼吸, 最终还是被 (.+?) 击败.*")
        )

        for (pattern in patterns) {
            val match = pattern.find(message) ?: continue
            var victim = match.groupValues[1].trim()
            var killer = match.groupValues[2].trim()

            // 删去标签
            val regex = Regex("[\\u4e00-\\u9fffA-Za-z0-9_]+$")
            victim = regex.find(victim)?.value ?: continue
            killer = regex.find(killer)?.value ?: continue

            if (victim.isNotEmpty() && killer.isNotEmpty()) {
                EventManager.callEvent(HeypixelSWKillEvent(victim, killer))
                return@handler
            }
        }
    }

}
