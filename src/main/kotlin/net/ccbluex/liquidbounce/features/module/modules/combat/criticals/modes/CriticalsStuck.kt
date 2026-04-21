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
 * along with  If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.combat.criticals.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket

/*
 * 由KL(2642856929)编写
 */
object CriticalsStuck : Choice("Stuck") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleCriticals.modes

    private val stuckTicks by int("StuckTicks", 4, 1..10)
    private val cooldownTicks by int("CooldownTicks", 2, 1..10)
    private val range by float("Range", 2.5f, 0f..6f)
    private val releaseOnHurt by boolean("ReleaseOnHurt", false)
    private val bypassPost by boolean("BypassPost", true)

    // New模式变量
    private var newModeActive = false
    private var newModeSkipTicks = 0
    private var newModeDelayedSkip = 0
    private var newModeCycling = false
    private var newModeCooldown = 0
    private var storedSkipTicks = 0
    private var pausedForAttack = false
    private var resumeSkipTicks = 0
    private var skipTicks = 0

    override fun enable() {
        newModeActive = false
        newModeSkipTicks = 0
        newModeDelayedSkip = 0
        newModeCycling = false
        newModeCooldown = 0
        storedSkipTicks = 0
        pausedForAttack = false
        resumeSkipTicks = 0
        skipTicks = 0
    }

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (releaseOnHurt && skipTicks > 0) {
            if (packet is EntityVelocityUpdateS2CPacket) {
                if (packet.entityId == player.id) {
                    storedSkipTicks = skipTicks
                    skipTicks = 0
                    ModuleFreeze.interact()
                }
            } else if (packet is ExplosionS2CPacket) {
                storedSkipTicks = skipTicks
                skipTicks = 0
                ModuleFreeze.interact()
            }
        }
    }

    @Suppress("unused")
    private val attackEntityEventHandler = handler<AttackEntityEvent> {
        if (bypassPost && skipTicks > 0) {
            resumeSkipTicks = skipTicks
            skipTicks = 0
            ModuleFreeze.interact()
            pausedForAttack = true
        }
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        // ReleaseOnHurt
        if (storedSkipTicks > 0) {
            skipTicks = storedSkipTicks
            storedSkipTicks = 0
        }
        if (pausedForAttack && resumeSkipTicks > 0) {
            skipTicks = resumeSkipTicks
            resumeSkipTicks = 0
            pausedForAttack = false
        }

        val hasTarget = ModuleKillAura.running && ModuleKillAura.targetTracker.target != null
        val inAir = !player.isOnGround
        val falling = player.velocity.y < 0

        var inRange = false
        if (hasTarget) {
            val distance = player.distanceTo(ModuleKillAura.targetTracker.target)
            inRange = distance <= range
        }

        if (!inAir || !hasTarget || !inRange) {
            if (newModeCycling) {
                newModeCycling = false
                newModeActive = false
                newModeSkipTicks = 0
                newModeDelayedSkip = 0
                newModeCooldown = 0
            }
        } else if (falling) {
            if (!newModeCycling && !newModeActive) {
                newModeCycling = true
                newModeActive = true
                newModeSkipTicks = stuckTicks + 1
                newModeDelayedSkip = 1
            }

            if (newModeCooldown > 0) {
                newModeCooldown--
                if (newModeCooldown == 0) {
                    newModeActive = true
                    newModeSkipTicks = stuckTicks + 1
                    newModeDelayedSkip = 1
                }
            }
        }

        if (newModeDelayedSkip > 0) {
            newModeDelayedSkip--
            if (newModeDelayedSkip == 0 && newModeActive) {
                skipTicks = stuckTicks
            }
        }

        if (newModeSkipTicks > 0) {
            newModeSkipTicks--
            if (newModeSkipTicks <= 0) {
                newModeActive = false
                if (newModeCycling) {
                    newModeCooldown = cooldownTicks
                }
            }
        }
    }

    @Suppress("unused")
    private val playerTickEventHandler = handler<PlayerTickEvent> { event ->
        if (skipTicks > 0) {
            event.cancelEvent()
            skipTicks--
            if (skipTicks == 0) {
                ModuleFreeze.interact()
            }
        }
    }

}
