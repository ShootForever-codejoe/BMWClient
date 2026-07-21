/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 */

package net.ccbluex.liquidbounce.features.module.modules.bmw.newscaffold

import kotlin.test.Test
import kotlin.test.assertEquals

class NewScaffoldFallSafetyTest {

    @Test
    fun `projected fall distance includes descent to bridge top`() {
        assertEquals(
            4.5f,
            NewScaffoldFallSafety.projectedFallDistance(
                currentFallDistance = 1.5f,
                currentFeetY = 64.0,
                projectedFeetY = 61.0,
            ),
        )
    }

    @Test
    fun `damage budget follows vanilla safe distance and jump boost`() {
        assertEquals(0, NewScaffoldFallSafety.estimatedDamage(3.0f))
        assertEquals(1, NewScaffoldFallSafety.estimatedDamage(3.1f))
        assertEquals(5, NewScaffoldFallSafety.estimatedDamage(7.2f))
        assertEquals(0, NewScaffoldFallSafety.estimatedDamage(4.0f, jumpBoostLevel = 1))
    }
}
