package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.aiming.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.NormalRotationMode
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.combat.attack
import net.ccbluex.liquidbounce.utils.combat.getEntitiesBoxInRange
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.minecraft.entity.decoration.EndCrystalEntity

object ModuleAttackCrystal : ClientModule("AttackCrystal", Category.BMW) {

    private val range by float("Range", 3f, 0f..4.5f)
    private val delay by int("Delay", 0, 0..1000, "ms")
    private val swingMode by enumChoice("SwingMode", SwingMode.DO_NOT_HIDE)

    private val rotationMode = choices(this, "RotationMode") {
        arrayOf(
            NormalRotationMode(it, this),
            NoRotationMode(it, this)
        )
    }

    init {
        tree(rotationMode)
    }

    private val chronometer = Chronometer()

    private var destroying = false

    @Suppress("unused")
    private val tickHandler = tickHandler {
        val rangeD = range.toDouble()

        if (destroying) {
            val stillHas = world.getEntitiesBoxInRange(player.getCameraPosVec(1f), rangeD) {
                it is EndCrystalEntity
            }.isNotEmpty()
            if (!stillHas) {
                destroying = false
            }
            return@tickHandler
        }

        if (!chronometer.hasAtLeastElapsed(delay.toLong())) return@tickHandler

        val crystal = world.getEntitiesBoxInRange(player.getCameraPosVec(1f), rangeD) {
            it is EndCrystalEntity
        }.firstOrNull() as? EndCrystalEntity ?: return@tickHandler

        destroying = true
        ModuleKillAura.enabled = false

        val (rotation: Rotation, _) = raytraceBox(
            player.eyePos,
            crystal.boundingBox,
            range = rangeD,
            wallsRange = rangeD,
            futureTarget = crystal.boundingBox,
            prioritizeVisible = true
        ) ?: return@tickHandler

        rotationMode.activeChoice.rotate(rotation, isFinished = {
            true
        }, onFinished = {
            if (!chronometer.hasAtLeastElapsed(delay.toLong())) return@rotate
            crystal.attack(swingMode)
            chronometer.reset()
        })
    }

    override fun onEnabled() {
        chronometer.reset()
        destroying = false
    }

    override fun onDisabled() {
        destroying = false
        ModuleKillAura.enabled = true
    }

}
