package net.ccbluex.liquidbounce.features.module.modules.combat

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import net.ccbluex.liquidbounce.config.types.NamedChoice
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.point.PointTracker
import net.ccbluex.liquidbounce.utils.aiming.projectiles.SituationalProjectileAngleCalculator
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.combat.TargetPriority
import net.ccbluex.liquidbounce.utils.combat.TargetTracker
import net.ccbluex.liquidbounce.utils.entity.PositionExtrapolation
import net.ccbluex.liquidbounce.utils.entity.SimulatedArrow
import net.ccbluex.liquidbounce.utils.inventory.*
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.render.WorldTargetRenderer
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.minecraft.entity.LivingEntity
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.math.Vec3d
import java.util.function.Function

//我对这个clicker进行了强奸，并且生下了delay，这个转头仅在射击的时候转头 made by ColumbinaHyposeleniaYS

object ModuleAutoShoot : ClientModule("AutoShoot", Category.COMBAT) {

    private val throwableType by enumChoice("ThrowableType", ThrowableType.EGG_AND_SNOWBALL)
    private val gravityType by enumChoice("GravityType", GravityType.AUTO).apply { tagBy(this) }

    private val customFilter by enumChoice("CustomFilter", Filter.WHITELIST)
    private val customItems by items("CustomItems", ReferenceOpenHashSet.of(Items.EGG, Items.SNOWBALL))

    private val timer = Chronometer()
    private val delay by float("Delay", 500f, 50f..2000f)

    internal val targetTracker = tree(TargetTracker(TargetPriority.DISTANCE, floatRange("Range", 3.0f..6f, 0f..256f)))
    private val pointTracker = tree(PointTracker(this))

    private val rotationConfigurable = tree(RotationsConfigurable(this))
    private val requirePredictedHit by boolean("RequirePredictedHit", true)

    private val swingMode by enumChoice("SwingMode", SwingMode.DO_NOT_HIDE)
    private val pitchAdjust by float("PitchAdjust", 0f, -30f..30f)
    private val verticalOffset by float("VerticalOffset", 0f, -2f..2f)

    private val targetRenderer = tree(WorldTargetRenderer(this))

    private val requiresKillAura by boolean("RequiresKillAura", false)
    private val notDuringCombat by boolean("NotDuringCombat", false)
    val constantLag by boolean("ConstantLag", false)
    private val requireLineOfSight by boolean("RequireLineOfSight", true)
    private val notDuringUsingItem by boolean("NotDuringUsingItem", true)
    private val notDuringScaffold by boolean("NotDuringScaffold", true)
    private val notDuringFreeze by boolean("NotDuringFreeze", true)

    private val selectSlotAutomatically by boolean("SelectSlotAutomatically", true)
    private val tickUntilReset by int("TicksUntilSlotReset", 1, 0..20)
    private val switchBack by boolean("SwitchBack", true)
    private val considerInventory by boolean("ConsiderInventory", true)

    private var rotationSet = 0
    private var pendingPlan: PendingThrow? = null

    private class PendingThrow(
        val slot: HotbarItemSlot,
        val rotation: Rotation,
        val target: LivingEntity
    )

    private fun findThrowableSlot(): HotbarItemSlot? {
        val slot = when (throwableType) {
            ThrowableType.EGG_AND_SNOWBALL -> Slots.OffhandWithHotbar.findClosestSlot(Items.EGG, Items.SNOWBALL)
            ThrowableType.CUSTOM -> Slots.OffhandWithHotbar.findClosestSlot {
                !it.isEmpty && customFilter(it.item, customItems)
            }
            ThrowableType.ANYTHING -> when {
                !player.mainHandStack.isEmpty -> Slots.Hotbar[player.inventory.selectedSlot]
                !player.offHandStack.isEmpty -> OffHandSlot
                else -> null
            }
        }
        return slot
    }

    private fun getRotation(target: LivingEntity, slot: HotbarItemSlot): Rotation? {
        return GravityType.from(slot).apply(target)?.let {
            Rotation(it.yaw, it.pitch - pitchAdjust)
        }
    }

    private fun isProjectileHitConfirmed(target: LivingEntity, rotation: Rotation, slot: HotbarItemSlot): Boolean {
        if (!requirePredictedHit || GravityType.from(slot) != GravityType.PROJECTILE) {
            return true
        }

        val trajectory = TrajectoryInfo.GENERIC
        val initialVelocity = rotation.directionVector
            .multiply(trajectory.initialVelocity)
            .add(if (trajectory.copiesPlayerVelocity) player.velocity else Vec3d.ZERO)
        val projectile = SimulatedArrow(
            world,
            player.eyePos,
            initialVelocity,
            collideEntities = false
        )
        val targetPosition = PositionExtrapolation.getBestForEntity(target)

        repeat(40) { ticks ->
            val lastPosition = projectile.pos
            projectile.tick()

            val predictedBox = target.dimensions
                .getBoxAt(targetPosition.getPositionInTicks(ticks.toDouble()))
                .expand(trajectory.hitboxRadius)

            if (predictedBox.raycast(lastPosition, projectile.pos).isPresent) {
                return true
            }

            if (projectile.inGround) {
                return false
            }
        }

        return false
    }

    private fun getValidTarget(): LivingEntity? {
        if (requiresKillAura && !ModuleKillAura.running) {
            targetTracker.reset()
            return null
        }

        val target = targetTracker.selectFirst { !requireLineOfSight || player.canSee(it) } ?: return null

        if (notDuringCombat && CombatManager.isInCombat) return null
        if (requiresKillAura && !ModuleKillAura.running) return null
        if (notDuringUsingItem && player.isUsingItem) return null
        if (notDuringScaffold && ModuleScaffold.enabled) return null
        if (notDuringFreeze && ModuleFreeze.enabled) return null

        return target
    }

    override fun onDisabled() {
        targetTracker.reset()
        SilentHotbar.resetSlot(this)
        rotationSet = 0
        pendingPlan = null
    }

    @Suppress("unused")
    private val handleAutoShoot = tickHandler {
        if (rotationSet > 0) {
            rotationSet--
            if (rotationSet == 0) {
                pendingPlan?.let { pending ->
                    val slot = pending.slot
                    val target = pending.target
                    val rotation = pending.rotation

                    if (!isProjectileHitConfirmed(target, rotation, slot)) {
                        pendingPlan = null
                        return@tickHandler
                    }

                    if (slot != OffHandSlot && selectSlotAutomatically) {
                        SilentHotbar.selectSlotSilently(this, slot, tickUntilReset)
                    }

                    interactItem(
                        hand = slot.useHand,
                        yaw = rotation.yaw,
                        pitch = rotation.pitch,
                        swingMode = swingMode,
                    ).isAccepted

                    if (switchBack) {
                        SilentHotbar.resetSlot(this)
                    }

                    pendingPlan = null
                }
            }
            return@tickHandler
        }

        val target = getValidTarget() ?: return@tickHandler
        val slot = findThrowableSlot() ?: return@tickHandler
        val rotation = getRotation(target, slot)
        val targetRotation = rotation ?: return@tickHandler

        if (!isProjectileHitConfirmed(target, targetRotation, slot)) {
            return@tickHandler
        }

        if (!timer.hasElapsed(delay.toLong())) {
            return@tickHandler
        }

        RotationManager.setRotationTarget(
            rotationConfigurable.toRotationTarget(targetRotation, considerInventory = considerInventory),
            Priority.IMPORTANT_FOR_USAGE_2,
            this@ModuleAutoShoot
        )

        pendingPlan = PendingThrow(slot, targetRotation, target)
        rotationSet = 2
        timer.reset()
    }

    val renderHandler = handler<WorldRenderEvent> { event ->
        val matrixStack = event.matrixStack
        val target = targetTracker.target ?: return@handler

        renderEnvironmentForWorld(matrixStack) {
            targetRenderer.render(this, target, event.partialTicks)
        }
    }

    private enum class ThrowableType(override val choiceName: String) : NamedChoice {
        EGG_AND_SNOWBALL("EggAndSnowball"),
        CUSTOM("Custom"),
        ANYTHING("Anything");
    }

    private enum class GravityType(override val choiceName: String) : NamedChoice, Function<LivingEntity, Rotation?> {

        AUTO("Auto"),
        LINEAR("Linear"),
        PROJECTILE("Projectile");

        override fun apply(target: LivingEntity): Rotation? = when (this) {
            AUTO -> null
            LINEAR -> {
                val eyes = player.eyePos
                val point = pointTracker.findPoint(eyes, target, 0)
                Rotation.lookingAt(point.pos.add(0.0, verticalOffset.toDouble(), 0.0), eyes)
            }
            PROJECTILE -> {
                SituationalProjectileAngleCalculator.calculateAngleForEntity(
                    TrajectoryInfo.GENERIC,
                    target
                )
            }
        }

        companion object {
            @JvmStatic
            fun from(slot: HotbarItemSlot): GravityType =
                from(slot.itemStack.item)

            @JvmStatic
            fun from(item: Item): GravityType {
                return when (gravityType) {
                    AUTO -> {
                        when (item) {
                            Items.EGG, Items.SNOWBALL -> PROJECTILE
                            else -> LINEAR
                        }
                    }
                    else -> gravityType
                }
            }
        }
    }
}
