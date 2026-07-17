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

package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.minecraft.entity.EntityType
import net.minecraft.entity.LightningEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.math.Vec3d
import java.util.IdentityHashMap
import kotlin.random.Random

@Suppress("MagicNumber")
object ModuleKillEffects : ClientModule("KillEffects", Category.RENDER) {

    private enum class Effect(override val choiceName: String) : NamedChoice {
        LIGHTNING("Lightning"),
        TOTEM("Totem"),
        SOUL("Soul"),
        ELECTRIC("Electric"),
        FIREWORK("Firework"),
        EXPLOSION("Explosion")
    }

    private val effects by multiEnumChoice("Effect", Effect.LIGHTNING)
    private val particleAmount by int("ParticleAmount", 30, 1..100)
    private val spread by float("Spread", 0.8f, 0.1f..2f)
    private val particleSpeed by float("ParticleSpeed", 0.25f, 0.05f..1f)
    private val trackingTime by int("TrackingTime", 10, 1..30, "s")

    private val attackedTargets = IdentityHashMap<LivingEntity, Long>()

    override fun onDisabled() {
        attackedTargets.clear()
    }

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        val target = event.entity as? LivingEntity ?: return@handler
        if (target !== player && target.isAlive) {
            attackedTargets[target] = System.currentTimeMillis()
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val now = System.currentTimeMillis()
        val iterator = attackedTargets.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val target = entry.key

            when {
                target.isDead || target.health <= 0f -> {
                    showEffects(target)
                    iterator.remove()
                }
                target.isRemoved || now - entry.value > trackingTime * 1000L -> iterator.remove()
            }
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        attackedTargets.clear()
    }

    private fun showEffects(target: LivingEntity) {
        effects.forEach { effect ->
            when (effect) {
                Effect.LIGHTNING -> spawnLightning(target.pos)
                Effect.TOTEM -> spawnParticles(target, ParticleTypes.TOTEM_OF_UNDYING)
                Effect.SOUL -> spawnParticles(target, ParticleTypes.SOUL)
                Effect.ELECTRIC -> spawnParticles(target, ParticleTypes.ELECTRIC_SPARK)
                Effect.FIREWORK -> spawnParticles(target, ParticleTypes.FIREWORK)
                Effect.EXPLOSION -> world.addParticle(
                    ParticleTypes.EXPLOSION_EMITTER,
                    target.x,
                    target.y + target.height * 0.5,
                    target.z,
                    0.0,
                    0.0,
                    0.0
                )
            }
        }
    }

    private fun spawnLightning(position: Vec3d) {
        val lightning = LightningEntity(EntityType.LIGHTNING_BOLT, world)
        lightning.setCosmetic(true)
        lightning.setPosition(position)
        world.addEntity(lightning)
    }

    private fun spawnParticles(target: LivingEntity, particle: net.minecraft.particle.SimpleParticleType) {
        val center = target.pos.add(0.0, target.height * 0.5, 0.0)
        repeat(particleAmount) {
            val offsetX = Random.nextDouble(-spread.toDouble(), spread.toDouble())
            val offsetY = Random.nextDouble(-spread.toDouble(), spread.toDouble())
            val offsetZ = Random.nextDouble(-spread.toDouble(), spread.toDouble())
            val direction = Vec3d(offsetX, offsetY, offsetZ).normalize().multiply(particleSpeed.toDouble())

            world.addParticle(
                particle,
                center.x + offsetX,
                center.y + offsetY,
                center.z + offsetZ,
                direction.x,
                direction.y,
                direction.z
            )
        }
    }
}
