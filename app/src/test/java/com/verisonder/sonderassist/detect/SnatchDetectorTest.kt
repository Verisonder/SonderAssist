package com.verisonder.sonderassist.detect

import com.verisonder.sonderassist.trace.Trace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * These fixtures are **synthesised, not recorded**, and that is a real limitation rather
 * than a detail. They prove the state machine does what it says — a spike with rotation
 * that keeps moving fires, a spike that settles does not, a fall does not — and they will
 * catch a rewrite that breaks the logic.
 *
 * They prove nothing at all about whether the thresholds are right. That needs traces off
 * the phone, replayed through [replay]. The moment those exist, they go in
 * `src/test/resources/traces/` and get asserted here by name.
 */
class SnatchDetectorTest {

    private val hz = 100
    private val stepNs = 1_000_000_000L / hz

    private fun replay(samples: List<Sample>, tuning: SnatchDetector.Tuning = SnatchDetector.Tuning()): List<SnatchDetector.Verdict> {
        val detector = SnatchDetector(tuning)
        return samples.map { detector.accept(it) }
    }

    private fun fired(verdicts: List<SnatchDetector.Verdict>) =
        verdicts.any { it is SnatchDetector.Verdict.Snatch }

    /** A hand at rest: gravity plus small tremor, and a slow drift of rotation. */
    private fun held(fromNs: Long, ms: Int, random: Random): List<Sample> {
        val count = ms * hz / 1000
        return (0 until count).map { i ->
            Sample(
                timestampNs = fromNs + i * stepNs,
                ax = (random.nextFloat() - 0.5f) * 0.5f,
                ay = (random.nextFloat() - 0.5f) * 0.5f,
                az = Sample.GRAVITY + (random.nextFloat() - 0.5f) * 0.5f,
                gx = (random.nextFloat() - 0.5f) * 0.15f,
                gy = (random.nextFloat() - 0.5f) * 0.15f,
                gz = (random.nextFloat() - 0.5f) * 0.15f,
            )
        }
    }

    /** Dead still on a table: no tremor worth the name. */
    private fun onTable(fromNs: Long, ms: Int): List<Sample> {
        val count = ms * hz / 1000
        return (0 until count).map { i ->
            Sample(fromNs + i * stepNs, 0f, 0f, Sample.GRAVITY)
        }
    }

    /** One sample of very large acceleration change, with the phone pivoting. */
    private fun spike(atNs: Long) = Sample(atNs, 22f, -14f, 4f, gx = 5.5f, gy = 3.1f, gz = -2.4f)

    /** Still moving, nowhere near settled. */
    private fun carriedAway(fromNs: Long, ms: Int, random: Random): List<Sample> {
        val count = ms * hz / 1000
        return (0 until count).map { i ->
            Sample(
                timestampNs = fromNs + i * stepNs,
                ax = 3f + (random.nextFloat() - 0.5f) * 4f,
                ay = -2f + (random.nextFloat() - 0.5f) * 4f,
                az = Sample.GRAVITY + (random.nextFloat() - 0.5f) * 5f,
                gx = 1.4f, gy = -1.1f, gz = 0.8f,
            )
        }
    }

    @Test
    fun `a grab that keeps moving fires`() {
        val random = Random(1)
        val samples = held(0, 1200, random) +
            spike(1200 * 1_000_000L) +
            carriedAway(1200 * 1_000_000L + stepNs, 1200, random)
        assertTrue("a spike with rotation that never settles is the case this exists for", fired(replay(samples)))
    }

    @Test
    fun `putting the phone down does not fire`() {
        val random = Random(2)
        val start = 1200 * 1_000_000L
        val samples = held(0, 1200, random) +
            spike(start) +
            onTable(start + stepNs, 1500)
        assertFalse("coming to rest is a put-down, not a theft", fired(replay(samples)))
    }

    @Test
    fun `dropping the phone does not fire`() {
        val random = Random(3)
        val start = 1200 * 1_000_000L
        val count = 40
        val falling = (0 until count).map { i ->
            Sample(start + stepNs + i * stepNs, 0.2f, 0.1f, 0.3f, gx = 2f, gy = 1f, gz = 1f)
        }
        val samples = held(0, 1200, random) + spike(start) + falling
        assertFalse("a dropped phone is not a stolen phone", fired(replay(samples)))
    }

    @Test
    fun `a phone lying on a table is not watched at all`() {
        val verdicts = replay(onTable(0, 1500))
        assertTrue(verdicts.all { it is SnatchDetector.Verdict.Idle })
    }

    @Test
    fun `a jerk with no rotation does not fire while rotation is required`() {
        val random = Random(4)
        val start = 1200 * 1_000_000L
        val straightShove = Sample(start, 24f, 0f, Sample.GRAVITY)
        val samples = held(0, 1200, random) + straightShove +
            carriedAway(start + stepNs, 1000, random)
        assertFalse("a grab pivots; a straight shove is something else", fired(replay(samples)))
    }

    @Test
    fun `rotation can be waived for a device with no gyroscope`() {
        val random = Random(5)
        val start = 1200 * 1_000_000L
        val straightShove = Sample(start, 24f, 0f, Sample.GRAVITY)
        val samples = held(0, 1200, random) + straightShove +
            carriedAway(start + stepNs, 1000, random)
        val verdicts = replay(samples, SnatchDetector.Tuning(requireRotation = false))
        assertTrue(fired(verdicts))
    }

    @Test
    fun `duplicate timestamps cannot produce an infinite jerk`() {
        val random = Random(6)
        val start = 1200 * 1_000_000L
        val batched = listOf(
            Sample(start, 0f, 0f, Sample.GRAVITY),
            // Sensor batching can deliver two events stamped identically. Dividing by a
            // zero interval would give an infinite jerk and fire instantly.
            Sample(start, 25f, -18f, 3f, gx = 6f, gy = 4f, gz = -3f),
        )
        val samples = held(0, 1200, random) + batched
        assertFalse(fired(replay(samples)))
    }

    @Test
    fun `reset clears the state machine`() {
        val random = Random(7)
        val detector = SnatchDetector()
        held(0, 1200, random).forEach { detector.accept(it) }
        detector.reset()
        assertEquals(SnatchDetector.Verdict.Idle, detector.verdict)
    }

    @Test
    fun `a trace round trips`() {
        val random = Random(8)
        val original = Trace.Recording("grab from hand", held(0, 300, random))
        val back = Trace.parse(Trace.serialise(original))
        assertEquals("grab from hand", back.label)
        assertEquals(original.samples.size, back.samples.size)
        assertEquals(original.samples.first(), back.samples.first())
        assertEquals(original.samples.last(), back.samples.last())
    }
}
