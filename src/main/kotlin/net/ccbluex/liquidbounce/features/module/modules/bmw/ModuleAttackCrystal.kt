package net.ccbluex.liquidbounce.features.module.modules.bmw

import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.utils.facingEnemy
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceBox
import net.ccbluex.liquidbounce.utils.combat.attack
import net.ccbluex.liquidbounce.utils.combat.getEntitiesBoxInRange
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.entity.decoration.EndCrystalEntity

object ModuleAttackCrystal : ClientModule("AttackCrystal", Category.BMW) {

    private val range by float("Range", 3f, 0f..4.5f)
    private val swingMode by enumChoice("SwingMode", SwingMode.DO_NOT_HIDE)

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

        val crystal = world.getEntitiesBoxInRange(player.getCameraPosVec(1f), rangeD) {
            it is EndCrystalEntity
        }.firstOrNull() as? EndCrystalEntity ?: return@tickHandler

        destroying = true
        ModuleKillAura.enabled = false

        val (rotation: Rotation, _) = raytraceBox(
            player.eyePos,
            crystal.boundingBox,
            range = rangeD,
            wallsRange = 0.0,
            futureTarget = crystal.boundingBox,
            prioritizeVisible = true
        ) ?: return@tickHandler

        RotationManager.setRotationTarget(
            RotationTarget(
                rotation,
                ticksUntilReset = 1,
                resetThreshold = 1f,
                considerInventory = false,
                movementCorrection = MovementCorrection.SILENT,
                whenReached = RestrictedSingleUseAction({
                    facingEnemy(
                        toEntity = crystal,
                        rotation = RotationManager.serverRotation,
                        range = rangeD,
                        wallsRange = 0.0
                    )
                }, {
                    crystal.attack(swingMode)
                })
            ),
            priority = Priority.IMPORTANT_FOR_USER_SAFETY,
            provider = ModuleAttackCrystal,
        )
    }

    override fun onDisabled() {
        destroying = false
    }

}
