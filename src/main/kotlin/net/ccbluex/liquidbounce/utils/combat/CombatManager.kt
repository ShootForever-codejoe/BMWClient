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
package net.ccbluex.liquidbounce.utils.combat

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.TargetChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.TargetData
import net.ccbluex.liquidbounce.utils.entity.getActualHealth
import net.ccbluex.liquidbounce.utils.entity.hasHealthScoreboard
import net.minecraft.entity.LivingEntity

/**
 * 战斗状态管理器
 */
@Suppress("TooManyFunctions")
object CombatManager : EventListener {

    private data class TargetVitals(
        val entityId: Int,
        val health: Float,
        val actualHealth: Float,
        val maxHealth: Float,
        val absorption: Float,
        val armor: Int,
    )

    private var currentHudTarget: LivingEntity? = null
    private var lastHudTargetVitals: TargetVitals? = null

    // useful for something like autoSoup
    private var pauseCombat: Int = 0

    // useful for something like autopot
    private var pauseRotation: Int = 0

    // useful for autoblock
    private var pauseBlocking: Int = 0

    const val PAUSE_COMBAT = 40 // 40 ticks = 2 seconds
    var duringCombat: Int = 0

    private fun updatePauseRotation() {
        if (pauseRotation <= 0) return

        pauseRotation--
    }

    private fun updatePauseCombat() {
        if (pauseCombat <= 0) return

        pauseCombat--
    }

    private fun updatePauseBlocking() {
        if (pauseBlocking <= 0) return

        pauseBlocking--
    }

    private fun updateDuringCombat() {
        if (duringCombat <= 0) return

        duringCombat--
    }

    /**
     * Update current rotation to new rotation step
     */
    fun update() {
        updatePauseRotation()
        updatePauseCombat()
        // TODO: implement this for killaura autoblock and other
        updatePauseBlocking()
        updateDuringCombat()
        updateHudTarget()
    }

    /**
     * 攻击事件会早于服务端和客户端更新实体生命值
     * 在战斗时间内持续观察目标 并在生命数据变化后的首个游戏刻发送更新事件
     */
    private fun updateHudTarget() {
        val target = currentHudTarget ?: return

        if (duringCombat <= 0 || target.isRemoved) {
            currentHudTarget = null
            lastHudTargetVitals = null
            return
        }

        publishHudTarget(target)
    }

    private fun publishHudTarget(target: LivingEntity, force: Boolean = false) {
        val vitals = TargetVitals(
            entityId = target.id,
            health = target.health.finiteOrZero(),
            actualHealth = target.getActualHealth().finiteOrZero(),
            maxHealth = target.maxHealth.finiteOrZero(),
            absorption = if (target.hasHealthScoreboard()) 0f else target.absorptionAmount.finiteOrZero(),
            armor = target.armor.coerceAtMost(20),
        )

        if (!force && vitals == lastHudTargetVitals) return

        lastHudTargetVitals = vitals
        EventManager.callEvent(TargetChangeEvent(TargetData.fromEntity(target)))
    }

    private fun Float.finiteOrZero() = if (isFinite()) this else 0f

    val tickHandler = handler<GameTickEvent> {
        update()
    }

    @Suppress("unused")
    val attackHandler = handler<AttackEntityEvent> { event ->
        val entity = event.entity

        if (entity is LivingEntity && entity.shouldBeAttacked()) {
            duringCombat = PAUSE_COMBAT
            currentHudTarget = entity

            // 先发送目标信息 收到服务端生命值后再发送最新数据
            publishHudTarget(entity, force = true)
        }
    }

    val shouldPauseCombat: Boolean
        get() = pauseCombat > 0
    val shouldPauseRotation: Boolean
        get() = pauseRotation > 0
    val shouldPauseBlocking: Boolean
        get() = pauseBlocking > 0
    val isInCombat: Boolean
        get() = this.duringCombat > 0 ||
            (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null)

    fun pauseCombatForAtLeast(pauseTime: Int) {
        pauseCombat = pauseCombat.coerceAtLeast(pauseTime)
    }

    fun pauseRotationForAtLeast(pauseTime: Int) {
        pauseRotation = pauseRotation.coerceAtLeast(pauseTime)
    }

    fun pauseBlockingForAtLeast(pauseTime: Int) {
        pauseBlocking = pauseBlocking.coerceAtLeast(pauseTime)
    }

}
