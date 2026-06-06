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
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.TagEntityEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.stripMinecraftColorCodes
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import java.awt.Color

/**
 * Teams module
 *
 * Prevents KillAura from attacking teammates.
 */
object ModuleTeams : ClientModule("Teams", Category.MISC) {

    private val matches by multiEnumChoice("Matches",
        Matches.SCOREBOARD_TEAM,
        Matches.NAME_COLOR
    )

    private val armorColor by multiEnumChoice("ArmorColor",
        EquipmentSlotChoice.HEAD
    )

    private val colorSources by multiEnumChoice(
        "ColorSources",
        ColorSource.TEAM,
        ColorSource.ARMOR
    )

    private enum class ColorSource(
        override val choiceName: String,
        val entityToColor: (Entity) -> Int?,
    ) : NamedChoice {
        TEAM("Team", { entity ->
            entity.scoreboardTeam?.color?.colorValue
        }),
        ARMOR("Armor", { entity ->
            val armorColorSlots = armorColor
            if (entity is LivingEntity && armorColorSlots.isNotEmpty()) {
                armorColorSlots.firstNotNullOfOrNull { it.getArmorColor(entity) }
            } else {
                null
            }
        }),
    }

    @Suppress("unused")
    val entityTagEvent = handler<TagEntityEvent> { event ->
        val entity = event.entity

        if (entity is LivingEntity && isInClientPlayersTeam(entity)) {
            event.dontTarget()
        }

        getTeamColor(entity)?.let { color ->
            event.color(color, Priority.IMPORTANT_FOR_USAGE_1)
        }

        // Resolve tag color from sources (first found)
        val color = colorSources.firstNotNullOfOrNull { it.entityToColor(entity) }
        event.color(Color4b.fullAlpha(color ?: return@handler), Priority.IMPORTANT_FOR_USAGE_1)
    }

    /**
     * Check if [entity] is in your own team using scoreboard,
     * name color, armor color or team prefix.
     */
    private fun isInClientPlayersTeam(entity: LivingEntity) =
        matches.any { it.testMatches(entity) } || checkArmor(entity)

    /**
     * Checks if the color of any armor piece matches.
     */
    private fun checkArmor(entity: LivingEntity) =
        entity is PlayerEntity && armorColor.any { it.matchesArmorColor(entity) }

    /**
     * Returns the team color of the [entity] or null if the entity is not in a team.
     */
    private fun getTeamColor(entity: Entity)
        = entity.displayName?.style?.color?.rgb?.let { Color4b(Color(it)) }

    @Suppress("unused")
    private enum class Matches(
        override val choiceName: String,
        val testMatches: (suspected: LivingEntity) -> Boolean
    ) : NamedChoice {
        /**
         * Check if [LivingEntity] is in your own team using scoreboard,
         */
        SCOREBOARD_TEAM("ScoreboardTeam", { suspected ->
            player.isTeammate(suspected)
        }),

        /**
         * Checks if both names have the same color.
         */
        NAME_COLOR("NameColor", { suspected ->
            val targetColor = player.displayName?.style?.color
            val clientColor = suspected.displayName?.style?.color

            targetColor != null
                && clientColor != null
                && targetColor == clientColor
        }),

        /**
         * Prefix check - this works on Hypixel BedWars, GommeHD Skywars and many other servers.
         */
        PREFIX("Prefix", { suspected ->
            val targetSplit = suspected.displayName
                ?.string
                ?.stripMinecraftColorCodes()
                ?.split(" ")

            val clientSplit = player.displayName
                ?.string
                ?.stripMinecraftColorCodes()
                ?.split(" ")

            targetSplit != null
                && clientSplit != null
                && targetSplit.size > 1
                && clientSplit.size > 1
                && targetSplit[0] == clientSplit[0]
        })
    }

    @Suppress("unused")
    enum class EquipmentSlotChoice(
        override val choiceName: String,
        @JvmField val slot: EquipmentSlot,
    ) : NamedChoice {
        MAINHAND("Mainhand", EquipmentSlot.MAINHAND),
        OFFHAND("Offhand", EquipmentSlot.OFFHAND),
        FEET("Feet", EquipmentSlot.FEET),
        LEGS("Legs", EquipmentSlot.LEGS),
        CHEST("Chest", EquipmentSlot.CHEST),
        HEAD("Head", EquipmentSlot.HEAD),
        BODY("Body", EquipmentSlot.BODY);

        fun getArmorColor(entity: LivingEntity): Int? {
            val itemStack = entity.getEquippedStack(this.slot)
            return itemStack[DataComponentTypes.DYED_COLOR]?.rgb?.let { it or -16777216 }
        }

        /**
         * Checks if the color of the item in the [EquipmentSlotChoice.slot] of
         * the [player] matches the user's armor color in the same slot.
         */
        fun matchesArmorColor(suspected: PlayerEntity): Boolean {
            // returns false if the armor is not dyeable (e.g., iron armor)
            // to avoid a false positive from `null == null`
            val ownColor = getArmorColor(player) ?: return false
            val otherColor = getArmorColor(suspected) ?: return false

            return ownColor == otherColor
        }
    }
}
