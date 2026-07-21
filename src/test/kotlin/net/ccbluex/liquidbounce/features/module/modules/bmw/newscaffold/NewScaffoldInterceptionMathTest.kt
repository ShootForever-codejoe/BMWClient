/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 */

package net.ccbluex.liquidbounce.features.module.modules.bmw.newscaffold

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class NewScaffoldInterceptionMathTest {

    @Test
    fun `bridge length includes first interaction and remaining manhattan links`() {
        assertEquals(
            3,
            NewScaffoldInterceptionMath.placementCount(
                firstPlacement = BlockPos(160, 59, 195),
                landing = BlockPos(160, 59, 197),
            ),
        )
    }

    @Test
    fun `completion requires feet above final cube in its column`() {
        val landing = BlockPos(160, 59, 197)

        assertTrue(NewScaffoldInterceptionMath.canCompleteAt(landing, Vec3d(160.7, 60.4, 197.2)))
        assertFalse(NewScaffoldInterceptionMath.canCompleteAt(landing, Vec3d(160.7, 59.8, 197.2)))
        assertFalse(NewScaffoldInterceptionMath.canCompleteAt(landing, Vec3d(161.4, 60.4, 197.2)))
    }

    @Test
    fun `completion accepts a vanilla edge catch from the adjacent centre column`() {
        val landing = BlockPos(147, 27, 198)

        assertTrue(
            NewScaffoldInterceptionMath.canCompleteAt(
                landing,
                Vec3d(148.014, 29.121, 198.138),
            )
        )
    }
}
