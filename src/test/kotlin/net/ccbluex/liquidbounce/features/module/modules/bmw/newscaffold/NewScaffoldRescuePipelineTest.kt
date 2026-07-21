/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.features.module.modules.bmw.newscaffold

import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NewScaffoldRescuePipelineTest {

    @Test
    fun `ack and local solid retire final landing while tombstone catches delayed rollback`() {
        val harness = PipelineHarness()
        val intermediate = BlockPos(4, 63, 6)
        val landing = BlockPos(5, 63, 6)
        harness.solid += intermediate
        harness.solid += landing

        assertTrue(harness.pipeline.enqueue(1L, intermediate, 100, harness.generation))
        assertTrue(harness.pipeline.enqueue(2L, landing, 101, harness.generation))
        harness.pipeline.noteAck(101)

        assertEquals(RescuePipelineResult.READY, harness.pipeline.process())
        assertTrue(harness.pipeline.isEmpty)
        assertTrue(intermediate in harness.pipeline.confirmed)
        assertTrue(landing in harness.pipeline.confirmed)
        assertEquals(landing, harness.pipeline.lastConfirmedPosition)
        assertNull(harness.pipeline.awaitingPosition)

        harness.pipeline.noteBlockUpdate(landing, authoritative = false)

        assertTrue(harness.pipeline.rollbackPending)
    }

    @Test
    fun `authoritative air rejects pending link and delayed rollback remains visible`() {
        val rejectedHarness = PipelineHarness()
        val rejected = BlockPos(1, 40, 1)
        rejectedHarness.solid += rejected
        assertTrue(rejectedHarness.pipeline.enqueue(1L, rejected, 10, rejectedHarness.generation))

        rejectedHarness.pipeline.noteBlockUpdate(rejected, authoritative = false)

        assertEquals(RescuePipelineResult.ABORT, rejectedHarness.pipeline.process())
        assertTrue(rejectedHarness.pipeline.isEmpty)

        val rollbackHarness = PipelineHarness()
        val confirmed = BlockPos(2, 40, 1)
        rollbackHarness.solid += confirmed
        assertTrue(rollbackHarness.pipeline.enqueue(2L, confirmed, 11, rollbackHarness.generation))
        rollbackHarness.pipeline.noteBlockUpdate(confirmed, authoritative = true)
        assertEquals(RescuePipelineResult.READY, rollbackHarness.pipeline.process())
        assertTrue(confirmed in rollbackHarness.pipeline.confirmed)

        rollbackHarness.pipeline.noteBlockUpdate(confirmed, authoritative = false)

        assertTrue(rollbackHarness.pipeline.rollbackPending)
        rollbackHarness.pipeline.clearRollback()
        assertFalse(rollbackHarness.pipeline.rollbackPending)
    }

    @Test
    fun `old generation cannot consume ack or block update from new generation`() {
        val harness = PipelineHarness()
        val position = BlockPos(8, 20, 9)
        harness.solid += position
        assertTrue(harness.pipeline.enqueue(1L, position, 50, harness.generation))

        harness.generation++
        harness.pipeline.noteAck(999)
        harness.pipeline.noteBlockUpdate(position, authoritative = true)

        assertEquals(RescuePipelineResult.ABORT, harness.pipeline.process())
        assertTrue(harness.pipeline.isEmpty)
        assertFalse(harness.pipeline.serverBlockUpdateConfirmed)
        assertTrue(harness.logs.any { "stale generation" in it })
    }

    @Test
    fun `slot lease survives ack reset but ends at generation boundary`() {
        val harness = PipelineHarness()
        val lease = SlotData(4, Hand.MAIN_HAND)
        harness.pipeline.slotLease = lease
        harness.pipeline.noteAck(20)
        harness.pipeline.noteAck(18)

        harness.pipeline.reset(clearWatch = false)

        assertSame(lease, harness.pipeline.slotLease)
        assertEquals(20, harness.pipeline.lastAckSequence.get())

        harness.pipeline.resetForGeneration()

        assertNull(harness.pipeline.slotLease)
        assertEquals(-1, harness.pipeline.lastAckSequence.get())
    }

    @Test
    fun `four link window does not stall a three block bridge before delayed updates`() {
        val harness = PipelineHarness(capacity = 4)
        repeat(4) { index ->
            val pos = BlockPos(index, 30, 0)
            harness.solid += pos
            assertTrue(harness.pipeline.enqueue(index.toLong(), pos, index, harness.generation))
        }

        assertFalse(harness.pipeline.enqueue(5L, BlockPos(5, 30, 0), 5, harness.generation))
        assertEquals(RescuePipelineResult.FULL, harness.pipeline.process())
    }

    private class PipelineHarness(capacity: Int = 2) {
        var generation = 7L
        val solid = HashSet<BlockPos>()
        val logs = ArrayList<String>()

        val pipeline = NewScaffoldRescuePipeline(
            capacity = capacity,
            stateDescriptionAt = { pos -> if (pos in solid) "stone" else "air" },
            isStable = { it in solid },
            log = { logs += it },
            generation = { generation },
        )
    }
}
