package com.wuwaconfig.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `comparison()` is what the deploy-history screen renders as the before/after
 * arrow. Every delta is nullable and the nullability is load-bearing: a missing
 * baseline must read as "unknown", never as a 0 improvement.
 */
class DeployRecordTest {
    private fun record(
        baselineFps: Float? = null,
        baselineThermal: Int = 0,
        baselineOom: Int = 0,
        baselineDrops: Int = 0,
        outcomeFps: Float? = null,
        outcomeThermal: Int? = null,
        outcomeOom: Int? = null,
        outcomeDrops: Int? = null,
        outcomeTimestamp: Long? = null,
    ) = DeployRecord(
        id = "id",
        timestamp = 0,
        presetName = "balanced",
        baselineFps = baselineFps,
        baselineThermal = baselineThermal,
        baselineOom = baselineOom,
        baselineDrops = baselineDrops,
        outcomeFps = outcomeFps,
        outcomeThermal = outcomeThermal,
        outcomeOom = outcomeOom,
        outcomeDrops = outcomeDrops,
        outcomeTimestamp = outcomeTimestamp,
    )

    @Test
    fun `an unmeasured deploy has no outcome`() {
        assertFalse(record().hasOutcome)
    }

    @Test
    fun `any outcome timestamp counts as measured`() {
        assertTrue(record(outcomeTimestamp = 1L).hasOutcome)
        assertTrue(record(outcomeTimestamp = 0L).hasOutcome)
    }

    @Test
    fun `metrics without a timestamp do not count as an outcome`() {
        // hasOutcome keys off the timestamp alone. A partially-written record
        // (metrics landed, stamp did not) must read as unresolved.
        assertFalse(record(outcomeFps = 60f, outcomeThermal = 1).hasOutcome)
    }

    @Test
    fun `all four deltas compute when both sides are present`() {
        val cmp =
            record(
                baselineFps = 55f,
                baselineThermal = 1,
                baselineOom = 0,
                baselineDrops = 3,
                outcomeFps = 61f,
                outcomeThermal = 4,
                outcomeOom = 2,
                outcomeDrops = 1,
                outcomeTimestamp = 1L,
            ).comparison()
        assertEquals(6f, cmp.fpsDelta!!, 0.001f)
        assertEquals(3, cmp.thermalDelta)
        assertEquals(2, cmp.oomDelta)
        assertEquals(-2, cmp.dropFramesDelta)
    }

    @Test
    fun `a missing baseline leaves fpsDelta null rather than zero`() {
        val cmp = record(outcomeFps = 60f, outcomeTimestamp = 1L).comparison()
        assertNull("no baseline must not look like 'no change'", cmp.fpsDelta)
    }

    @Test
    fun `a missing outcome leaves fpsDelta null`() {
        assertNull(record(baselineFps = 55f).comparison().fpsDelta)
    }

    @Test
    fun `an absent outcome metric leaves only its own delta null`() {
        val cmp =
            record(
                baselineThermal = 2,
                baselineOom = 1,
                outcomeThermal = 5,
                outcomeOom = null,
                outcomeTimestamp = 1L,
            ).comparison()
        assertEquals(3, cmp.thermalDelta)
        assertNull(cmp.oomDelta)
    }

    @Test
    fun `a zero baseline is a real measurement, not a missing one`() {
        val cmp = record(baselineThermal = 0, outcomeThermal = 4, outcomeTimestamp = 1L).comparison()
        assertEquals(4, cmp.thermalDelta)
    }

    @Test
    fun `an equal outcome gives a zero delta, not null`() {
        val cmp = record(baselineFps = 60f, outcomeFps = 60f, outcomeTimestamp = 1L).comparison()
        assertEquals(0f, cmp.fpsDelta!!, 0.001f)
    }

    @Test
    fun `a regression is a negative delta`() {
        val cmp = record(baselineFps = 60f, outcomeFps = 45f, outcomeTimestamp = 1L).comparison()
        assertEquals(-15f, cmp.fpsDelta!!, 0.001f)
    }

    @Test
    fun `an entirely unresolved record yields four null deltas`() {
        val cmp = record().comparison()
        assertNull(cmp.fpsDelta)
        assertNull(cmp.thermalDelta)
        assertNull(cmp.oomDelta)
        assertNull(cmp.dropFramesDelta)
    }

    @Test
    fun `comparison is a pure read, so it can be called repeatedly`() {
        val r =
            record(
                baselineFps = 50f,
                outcomeFps = 55f,
                outcomeTimestamp = 1L,
            )
        assertEquals(r.comparison(), r.comparison())
    }

    @Test
    fun `defaults leave every delta null`() {
        val cmp = DeployRecord(id = "x", timestamp = 0, presetName = "p").comparison()
        assertNull(cmp.fpsDelta)
        assertNull(cmp.thermalDelta)
        assertNull(cmp.oomDelta)
        assertNull(cmp.dropFramesDelta)
    }
}
