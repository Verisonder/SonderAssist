package com.verisonder.sonderassist.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

/**
 * The same synthesised fixtures as [SnatchDetectorTest], plus the two things the second
 * detector exists for: a smooth pull, and the same pull at half the sample rate. Still no
 * recorded traces, so these prove the shape of the logic and nothing about the numbers.
 */
class ImpulseDetectorTest {

    private val stepNs = 10_000_000L
    private val restY = 8.5f
    private val restZ = 4.9f
    private val pocketY = -9.0f
    private val pocketZ = 2.0f
    private val start = 1_200_000_000L

    private fun replay(samples: List<Sample>, tuning: ImpulseDetector.Tuning = ImpulseDetector.Tuning()) =
        ImpulseDetector(tuning).let { d -> samples.map { d.accept(it) } }

    private fun fired(verdicts: List<SnatchDetector.Verdict>) =
        verdicts.any { it is SnatchDetector.Verdict.Snatch }

    private fun held(fromNs: Long, ms: Int, random: Random, y: Float = restY, z: Float = restZ) =
        (0 until ms / 10).map { i ->
            Sample(
                fromNs + i * stepNs,
                (random.nextFloat() - 0.5f) * 0.4f,
                y + (random.nextFloat() - 0.5f) * 0.4f,
                z + (random.nextFloat() - 0.5f) * 0.4f,
                (random.nextFloat() - 0.5f) * 0.15f,
                (random.nextFloat() - 0.5f) * 0.15f,
                (random.nextFloat() - 0.5f) * 0.15f,
            )
        }

    private fun onTable(fromNs: Long, ms: Int) =
        (0 until ms / 10).map { i -> Sample(fromNs + i * stepNs, 0f, 0f, Sample.GRAVITY) }

    private fun axialPull(fromNs: Long, y: Float = restY, z: Float = restZ) = (0 until 4).map { i ->
        Sample(fromNs + i * stepNs, 0f, y + minOf(18f, 4f + i * 7f), z, 0.1f, 0.1f, 0.1f)
    }

    private fun carriedAway(fromNs: Long, ms: Int, random: Random, upward: Boolean = true, y: Float = restY, z: Float = restZ) =
        (0 until ms / 10).map { i ->
            Sample(
                fromNs + i * stepNs,
                2f + (random.nextFloat() - 0.5f) * 3f,
                y + (if (upward) 6f else -6f) + (random.nextFloat() - 0.5f) * 4f,
                z + (random.nextFloat() - 0.5f) * 4f,
                0.5f, 0.4f, 0.3f,
            )
        }

    /**
     * A hand, not a step: a half-cosine ramp to [peak] over [riseMs], held 120 ms, then
     * carried. [hz] and [phase] are the two things the first detector was sensitive to.
     */
    private fun smoothPull(peak: Float, riseMs: Int, hz: Int, phase: Float, seed: Int): List<Sample> {
        val step = 1_000_000_000L / hz
        val random = Random(seed)
        val t0 = (phase * step).toLong()
        val before = (0 until hz * 12 / 10).map { i ->
            Sample(
                t0 + i * step,
                (random.nextFloat() - 0.5f) * 0.4f,
                restY + (random.nextFloat() - 0.5f) * 0.4f,
                restZ + (random.nextFloat() - 0.5f) * 0.4f,
                0.05f, 0.05f, 0.05f,
            )
        }
        val pullStart = before.last().timestampNs + step
        val pull = (0 until hz * 9 / 10).map { i ->
            val ms = i * 1000f / hz
            val a = when {
                ms < riseMs -> peak * 0.5f * (1f - cos(PI * ms / riseMs).toFloat())
                ms < riseMs + 120 -> peak
                else -> 6f + (random.nextFloat() - 0.5f) * 4f
            }
            val moving = ms > riseMs
            Sample(
                pullStart + i * step,
                if (moving) 2f + (random.nextFloat() - 0.5f) * 3f else 0f,
                restY + a,
                restZ + if (moving) (random.nextFloat() - 0.5f) * 4f else 0f,
                0.1f, 0.1f, 0.1f,
            )
        }
        return before + pull
    }

    @Test
    fun `a straight pull up the long axis fires`() {
        val r = Random(1)
        assertTrue(fired(replay(held(0, 1200, r) + axialPull(start) + carriedAway(start + 4 * stepNs, 900, r))))
    }

    @Test
    fun `a yank downward does not fire`() {
        val r = Random(3)
        val push = (0 until 4).map { i ->
            Sample(start + i * stepNs, 0f, restY - minOf(18f, 4f + i * 7f), restZ, 0.1f, 0.1f, 0.1f)
        }
        assertFalse(fired(replay(held(0, 1200, r) + push + carriedAway(start + 4 * stepNs, 900, r, upward = false))))
    }

    @Test
    fun `putting the phone down does not fire`() {
        val r = Random(4)
        val lowering = (0 until 3).map { i -> Sample(start + i * stepNs, 0f, restY + 3f, restZ, 0.5f, 0.2f, 0.1f) }
        assertFalse(fired(replay(held(0, 1200, r) + lowering + onTable(start + 3 * stepNs, 1500))))
    }

    @Test
    fun `a knock does not fire because there is no impulse`() {
        val r = Random(5)
        val knock = listOf(
            Sample(start, 0f, restY + 20f, restZ, 0.1f, 0.1f, 0.1f),
            Sample(start + stepNs, 0f, restY, restZ, 0.1f, 0.1f, 0.1f),
        )
        assertFalse(fired(replay(held(0, 1200, r) + knock + held(start + 2 * stepNs, 800, r))))
    }

    @Test
    fun `dropping the phone does not fire`() {
        val r = Random(6)
        val falling = (0 until 40).map { i -> Sample(start + (4 + i) * stepNs, 0.2f, 0.1f, 0.3f, 2f, 1f, 1f) }
        assertFalse(fired(replay(held(0, 1200, r) + axialPull(start) + falling)))
    }

    @Test
    fun `a phone going into a pocket does not fire, and it is the gate doing it`() {
        val r = Random(11)
        val walking = (0 until 120).map { i ->
            Sample(
                i * stepNs,
                (r.nextFloat() - 0.5f) * 1.2f,
                pocketY + (r.nextFloat() - 0.5f) * 1.2f,
                pocketZ + (r.nextFloat() - 0.5f) * 1.2f,
                (r.nextFloat() - 0.5f) * 0.4f, (r.nextFloat() - 0.5f) * 0.4f, (r.nextFloat() - 0.5f) * 0.4f,
            )
        }
        val shove = (0 until 4).map { i ->
            Sample(start + i * stepNs, 0f, pocketY + minOf(20f, 5f + i * 8f), pocketZ, 0.8f, 0.5f, 0.3f)
        }
        val after = carriedAway(start + 4 * stepNs, 900, r, y = pocketY - 1f, z = pocketZ)
        assertFalse(fired(replay(walking + shove + after)))
        assertTrue(fired(replay(walking + shove + after, ImpulseDetector.Tuning(uprightMinGravityY = -100f))))
    }

    @Test
    fun `a grab with the phone flat in an open palm fires`() {
        val r = Random(12)
        val flat = held(0, 1200, r, y = 0.5f, z = 9.7f)
        assertTrue(fired(replay(flat + axialPull(start, 0.5f, 9.7f) + carriedAway(start + 4 * stepNs, 900, r, y = 0.5f, z = 9.7f))))
    }

    @Test
    fun `a smooth pull fires at every sample phase and at both rates, even at the cautious end`() {
        val cautious = ImpulseDetector.Tuning.forSensitivity(0f)
        for (hz in listOf(100, 50)) {
            for (k in 0 until 10) {
                val samples = smoothPull(peak = 20f, riseMs = 60, hz = hz, phase = k / 10f, seed = k)
                assertTrue("$hz Hz, phase $k", fired(replay(samples, cautious)))
            }
        }
    }

    @Test
    fun `duplicate timestamps are dropped`() {
        val r = Random(7)
        val batched = listOf(
            Sample(start, 0f, restY, restZ),
            Sample(start, 0f, restY + 25f, restZ, 6f, 4f, -3f),
        )
        assertFalse(fired(replay(held(0, 1200, r) + batched)))
    }

    @Test
    fun `the slider ends sit where they were written`() {
        assertEquals(0.7f, ImpulseDetector.Tuning.forSensitivity(1f).minDeltaV, 0.001f)
        assertEquals(1.4f, ImpulseDetector.Tuning.forSensitivity(0f).minDeltaV, 0.001f)
        assertEquals(0.84f, ImpulseDetector.Tuning.forSensitivity(0f).minDeltaVWithRotation, 0.001f)
    }
}
