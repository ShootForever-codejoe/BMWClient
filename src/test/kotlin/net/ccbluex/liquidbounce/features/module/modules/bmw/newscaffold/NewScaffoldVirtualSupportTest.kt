/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 */

package net.ccbluex.liquidbounce.features.module.modules.bmw.newscaffold

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NewScaffoldVirtualSupportTest {

    @Test
    fun `pending cube accepts its visible north face`() {
        val hit = NewScaffoldVirtualSupport.intersect(
            eye = Vec3d(0.5, 0.5, -2.0),
            rotation = Rotation(0f, 0f),
            reach = 4.5,
            pos = BlockPos.ORIGIN,
            expectedFace = Direction.NORTH,
        )

        requireNotNull(hit)
        assertEquals(BlockPos.ORIGIN, hit.blockPos)
        assertEquals(Direction.NORTH, hit.side)
        assertTrue(kotlin.math.abs(hit.pos.z) < 1.0E-6)
    }

    @Test
    fun `pending cube rejects a different face and an expired reach`() {
        val eye = Vec3d(0.5, 0.5, -2.0)
        val rotation = Rotation(0f, 0f)

        assertNull(
            NewScaffoldVirtualSupport.intersect(
                eye, rotation, 4.5, BlockPos.ORIGIN, Direction.SOUTH,
            )
        )
        assertNull(
            NewScaffoldVirtualSupport.intersect(
                eye, rotation, 1.5, BlockPos.ORIGIN, Direction.NORTH,
            )
        )
    }

    @Test
    fun `pending cube supports the downward foot-facing ray`() {
        val hit = NewScaffoldVirtualSupport.intersect(
            eye = Vec3d(0.5, 3.0, 0.5),
            rotation = Rotation(0f, 90f),
            reach = 4.5,
            pos = BlockPos.ORIGIN,
            expectedFace = Direction.UP,
        )

        requireNotNull(hit)
        assertEquals(Direction.UP, hit.side)
        assertTrue(kotlin.math.abs(hit.pos.y - 1.0) < 1.0E-6)
    }
}
