package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.bmw.notifyAsMessage
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.Items
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket
import net.minecraft.screen.slot.SlotActionType

object ModuleAutoReport : ClientModule("AutoReport", Category.BMW) {

    private var stage = 0
    private var targetSlots = mutableListOf<Int>()
    private var currentTarget = 0
    private var reportDelay = 0
    private var guiLoadDelay = 0
    private var killerName: String? = null
    private var playerName: String? = null
    private var lastPlayerNameCheckTime = 0L
    private const val PLAYER_NAME_CHECK_INTERVAL = 5000L

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPlayerNameCheckTime >= PLAYER_NAME_CHECK_INTERVAL) {
            playerName = player.name.string
            lastPlayerNameCheckTime = currentTime
        }
        if (!enabled || killerName == null) return@tickHandler

        when (stage) {
            0 -> {
                player.networkHandler?.sendChatCommand("report")
                stage = 1
                guiLoadDelay = 2
                return@tickHandler
            }

            1 -> {
                val screen = mc.currentScreen as? GenericContainerScreen ?: return@tickHandler
                if (screen.title.string.contains("举报", ignoreCase = true)) {
                    if (guiLoadDelay > 0) {
                        guiLoadDelay--
                        return@tickHandler
                    }

                    targetSlots.clear()
                    screen.screenHandler.slots.forEachIndexed { index, slot ->
                        val item = slot.stack.item
                        if (item == Items.PLAYER_HEAD || item == Items.SKELETON_SKULL) {
                            targetSlots.add(index)
                        }
                    }

                    if (targetSlots.isEmpty()) {
                        resetState()
                        return@tickHandler
                    }

                    stage = 2
                    return@tickHandler
                }
            }

            2 -> {
                val screen = mc.currentScreen as? GenericContainerScreen ?: run {
                    resetState()
                    return@tickHandler
                }

                if (currentTarget < targetSlots.size) {
                    val slotIndex = targetSlots[currentTarget]
                    val slot = screen.screenHandler.getSlot(slotIndex)

                    if (slot.stack.name.string.contains(killerName ?: "")) {
                        mc.interactionManager?.clickSlot(
                            screen.screenHandler.syncId,
                            slotIndex,
                            0,
                            SlotActionType.PICKUP,
                            player
                        )
                        reportDelay = 5
                        stage = 3
                        currentTarget++
                        return@tickHandler
                    }
                    currentTarget++
                } else {
                    resetState()
                    return@tickHandler
                }
            }

            3 -> {
                reportDelay--
                if (reportDelay <= 0) {
                    stage = 4
                }
                return@tickHandler
            }

            4 -> {
                val screen = mc.currentScreen as? GenericContainerScreen ?: run {
                    resetState()
                    return@tickHandler
                }

                val slots = screen.screenHandler.slots
                val swordSlot = slots.find { it.stack.item == Items.DIAMOND_SWORD } ?: run {
                    resetState()
                    return@tickHandler
                }

                mc.interactionManager?.clickSlot(
                    screen.screenHandler.syncId,
                    swordSlot.id,
                    0,
                    SlotActionType.PICKUP,
                    player
                )
                notifyAsMessage("[AutoReport] Successfully Reported $killerName")
                mc.currentScreen = null
                resetState()
                return@tickHandler
            }
        }
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet !is GameMessageS2CPacket) return@handler

        val message = packet.content.string

        val patterns = listOf(
            Regex("(.+?)被(.+?)击败"),
            Regex("(.+?)被炸成了粉尘, 最终还是被(.+?)击败!"),
            Regex("(.+?)消逝了, 最终还是被(.+?)击败!"),
            Regex("(.+?)被架在了烧烤架上, 熟透了, 最终还是被(.+?)击败!"),
            Regex("(.+?)跑得很快, 但是他还是摔了一跤, 最终被(.+?)击败"),
            Regex("(.+?)被(.+?)用弓箭射穿了"),
            Regex("(.+?)被重压地无法呼吸, 最终还是被(.+?)击败!")
        )

        for (pattern in patterns) {
            val match = pattern.find(message) ?: continue
            val victim = match.groupValues[1].trim()
            val killer = match.groupValues[2].trim()

            if (victim == playerName && !FriendManager.isFriend(killer)) {
                killerName = killer
                stage = 0
                return@handler
            }
        }
    }

    private fun resetState() {
        stage = 0
        currentTarget = 0
        targetSlots.clear()
        reportDelay = 0
        guiLoadDelay = 0
        killerName = null
    }

    override fun enable() {
        resetState()
    }

}
