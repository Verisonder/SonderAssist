package com.verisonder.sonderassist.detect

import com.verisonder.sonderassist.trace.Trace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * These fixtures are **synthesised, not recorded**, and that is a real limitation rather
 * than a detail. They prove the state machine does what it claims — that all three pull
 * directions fire, that a put-down and a drop and a knock do not — and they will catch a
 * rewrite that breaks the logic.
 *
 * They prove nothing about whether the thresholds are right. That needs traces off the
 * phone, replayed through [replay]. When those exist they go in
 * `src/test/resources/traces/` and get asserted here by name.
 */
class SnatchDetectorTest {

    private val hz = 100
    private val stepNs = 1_000_000_000L / hz

    // A phone being looked at is tilted, not flat, so gravity sits mostly along -Y with
    // some +Z in the device frame. Modelling it flat would make the axial channel read
    // zero at rest by accident rather than by construction, and hide a sign error.
    private val restY = -8.5f
    private val restZ = 4.9f

    private fun replay(samples: List<Sample>): List<SnatchDetector.Verdict> {
        val detector = SnatchDetector()
        return samples.map { detector.accept(it) }
    }

    private fun fired(verdicts: List<SnatchDetector.Verdict>) =
        verdicts.any { it is SnatchDetector.Verdict.Snatch }

    /** A hand at rest: gravity plus the small tremor a hand always has. */
    private fun held(fromNs: Long, ms: Int, random: Random): List<Sample> =
        (0 until ms * hz / 1000).map { i ->
            Sample(
                timestampNs = fromNs + i * stepNs,
                ax = (random.nextFloat() - 0.5f) * 0.4f,
                ay = restY + (random.nextFloat() - 0.5f) * 0.4f,
                az = restZ + (random.nextFloat() - 0.5f) * 0.4f,
                gx = (random.nextFloat() - 0.5f) * 0.15f,
                gy = (random.nextFloat() - 0.5f) * 0.15f,
                gz = (random.nextFloat() - 0.5f) * 0.15f,
            )
        }

    /** Dead still on a table: no tremor worth the name. */
    private fun onTable(fromNs: Long, ms: Int): List<Sample> =
        (0 until ms * hz / 1000).map { i ->
            Sample(fromNs + i * stepNs, 0f, 0f, Sample.GRAVITY)
        }

    /** The middle arrow: straight up the long axis, almost no rotation. */
    private fun axialPull(fromNs: Long) = (0 until 4).map { i ->
        Sample(fromNs + i * stepNs, 0f, restY + minOf(18f, 4f + i * 7f), restZ, 0.1f, 0.1f, 0.1f)
    }

    /** A diagonal arrow: off-axis, so the phone pivots about the hand as well. */
    private fun diagonalPull(fromNs: Long) = (0 until 4).map { i ->
        Sample(fromNs + i * stepNs, 3f + i * 2f, restY + minOf(12f, 3f + i * 5f), restZ, 2.2f, 1.4f, -1.1f)
    }

    /** Still moving afterwards, nowhere near settled. */
    private fun carriedAway(fromNs: Long, ms: Int, random: Random, upward: Boolean = true): List<Sample> {
        val offset = if (upward) 6f else -6f
        return (0 until ms * hz / 1000).map { i ->
            Sample(
                timestampNs = fromNs + i * stepNs,
                ax = 2f + (random.nextFloat() - 0.5f) * 3f,
                ay = restY + offset + (random.nextFloat() - 0.5f) * 4f,
                az = restZ + (random.nextFloat() - 0.5f) * 4f,
                gx = 0.5f, gy = 0.4f, gz = 0.3f,
            )
        }
    }

    private val start = 1_200_000_000L

    @Test
    fun `a straight pull up the long axis fires even with no rotation`() {
        val random = Random(1)
        val samples = held(0, 1200, random) + axialPull(start) +
            carriedAway(start + 4 * stepNs, 900, random)
        // The middle of the three directions. It runs through the pivot, so it produces
        // almost no torque — a detector that required rotation would miss it entirely,
        // and an earlier one did.
        assertTrue(fired(replay(samples)))
    }

    @Test
    fun `a diagonal pull fires on the lower bar because rotation agrees`() {
        val random = Random(2)
        val samples = held(0, 1200, random) + diagonalPull(start) +
            carriedAway(start + 4 * stepNs, 900, random)
        assertTrue(fired(replay(samples)))
    }

    @Test
    fun `a yank downward out of the hand does not fire`() {
        val random = Random(3)
        val push = (0 until 4).map { i ->
            Sample(start + i * stepNs, 0f, restY - minOf(18f, 4f + i * 7f), restZ, 0.1f, 0.1f, 0.1f)
        }
        val samples = held(0, 1200, random) + push +
            carriedAway(start + 4 * stepNs, 900, random, upward = false)
        // A hand gripping the bottom opens toward the top. The phone cannot leave
        // downward, so a downward transient is something else happening.
        assertFalse(fired(replay(samples)))
    }

    @Test
    fun `putting the phone down does not fire`() {
        val random = Random(4)
        val lowering = (0 until 3).map { i ->
            Sample(start + i * stepNs, 0f, restY + 3f, restZ, 0.5f, 0.2f, 0.1f)
        }
        val samples = held(0, 1200, random) + lowering + onTable(start + 3 * stepNs, 1500)
        assertFalse(fired(replay(samples)))
    }

    @Test
    fun `a knock does not fire because the phone does not actually move`() {
        val random = Random(5)
        val knock = listOf(
            Sample(start, 0f, restY + 20f, restZ, 0.1f, 0.1f, 0.1f),
            Sample(start + stepNs, 0f, restY, restZ, 0.1f, 0.1f, 0.1f),
        )
        val samples = held(0, 1200, random) + knock + held(start + 2 * stepNs, 800, random)
        assertFalse(fired(replay(samples)))
    }

    @Test
    fun `dropping the phone does not fire`() {
        val random = Random(6)
        val falling = (0 until 40).map { i ->
            Sample(start + (4 + i) * stepNs, 0.2f, 0.1f, 0.3f, 2f, 1f, 1f)
        }
        val samples = held(0, 1200, random) + axialPull(start) + falling
        assertFalse("a dropped phone is not a stolen phone", fired(replay(samples)))
    }

    @Test
    fun `a phone lying on a table is not watched at all`() {
        assertTrue(replay(onTable(0, 1500)).all { it is SnatchDetector.Verdict.Idle })
    }

    @Test
    fun `duplicate timestamps cannot produce an infinite jerk`() {
        val random = Random(7)
        val batched = listOf(
            Sample(start, 0f, restY, restZ),
            // Sensor batching can deliver two events stamped identically. Dividing by a
            // zero interval would give an infinite jerk and fire instantly.
            Sample(start, 0f, restY + 25f, restZ, 6f, 4f, -3f),
        )
        assertFalse(fired(replay(held(0, 1200, random) + batched)))
    }

    @Test
    fun `reset clears the state machine`() {
        val detector = SnatchDetector()
        held(0, 1200, Random(8)).forEach { detector.accept(it) }
        detector.reset()
        assertEquals(SnatchDetector.Verdict.Idle, detector.verdict)
    }

    @Test
    fun `a trace round trips`() {
        val original = Trace.Recording("grab from hand", held(0, 300, Random(9)))
        val back = Trace.parse(Trace.serialise(original))
        assertEquals("grab from hand", back.label)
        assertEquals(original.samples.size, back.samples.size)
        assertEquals(original.samples.first(), back.samples.first())
        assertEquals(original.samples.last(), back.samples.last())
    }
}
