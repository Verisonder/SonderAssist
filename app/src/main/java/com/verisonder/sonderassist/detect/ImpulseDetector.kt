package com.verisonder.sonderassist.detect

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The second detector. Measures the pull instead of its derivative.
 *
 * [SnatchDetector] triggers on the one-sample rate of change of axial acceleration, and
 * asks for that and a large acceleration to hold on the same sample. Replayed against a
 * smooth pull — a half-cosine ramp, which is what a hand does — that conjunction depends
 * on where the sampling clock happens to fall inside the rise, and it halves when the
 * sensor delivers 50 Hz instead of the 100 Hz the numbers were written for. At the
 * cautious end of the slider it never fired on a modelled pull at all, including a
 * violent one; only a step clears it, and a step is a knock.
 *
 * A thief does one thing a knock, a tap and a footstep do not: **he changes the phone's
 * velocity.** So this integrates linear acceleration over a short trailing window to get
 * the change in velocity as a vector, and triggers on its size and direction. Integration
 * averages the sensor noise the derivative amplified, and the result does not depend on
 * the sample rate or on clock phase. A knock is a large derivative with no impulse and
 * needs no second threshold to reject it.
 *
 * Direction is a cone about +Y rather than the sign of one axis, so the premise that the
 * grip opens at the top is one number here rather than the shape of the code.
 *
 * Everything already proven stays: the lagged held gate, the upright gate for the pocket,
 * free-fall rejection, the confirmation window. Gravity is filtered by time rather than by
 * sample, so the estimate behaves the same at any rate.
 *
 * **Every threshold is still reasoned from physics, not measured.** Record traces.
 */
class ImpulseDetector(private val tuning: Tuning = Tuning()) {

    /**
     * @param minDeltaV m/s of velocity change inside [impulseWindowMs] along the cone.
     * @param minDeltaVWithRotation the lower bar when the gyroscope also sees ≥ [rotationSupport].
     * @param coneCos cosine of the half-angle about +Y a pull must lie within. 0.5 is 60°.
     *   Zero is any direction with a positive Y component; −1 is any direction at all.
     * @param impulseWindowMs the trailing window the velocity change is integrated over.
     *   Longer than a grab's rise, shorter than a stride.
     * @param gravityTauS time constant of the gravity low-pass, seconds. Not per sample.
     * @param restMs how long the phone must be continuously settled inside the confirmation
     *   window to be judged put down. One settled sample is not rest; a moving phone
     *   produces one by chance.
     */
    data class Tuning(
        val minDeltaV: Float = 0.7f,
        val minDeltaVWithRotation: Float = 0.42f,
        val rotationSupport: Float = 1.5f,
        val coneCos: Float = 0.5f,
        val impulseWindowMs: Long = 150,
        val gravityTauS: Float = 0.5f,
        val confirmWindowMs: Long = 700,
        val settledAccelBand: Float = 0.6f,
        val settledRotation: Float = 0.35f,
        val restMs: Long = 150,
        val heldTremorMin: Float = 0.06f,
        val heldTremorMax: Float = 7.0f,
        val freeFallCeiling: Float = 3.0f,
        val freeFallMs: Long = 120,
        val windowMs: Long = 900,
        val heldLagMs: Long = 250,
        val uprightMinGravityY: Float = SnatchDetector.Tuning.UPSIDE_DOWN_Y,
    ) {
        companion object {
            /**
             * One slider, one number. 0 is the most cautious. The rotation bar is 60% of
             * the plain one, the same proportion the first detector uses.
             */
            fun forSensitivity(sensitivity: Float): Tuning {
                val s = sensitivity.coerceIn(0f, 1f)
                val v = 1.4f + (0.7f - 1.4f) * s
                return Tuning(minDeltaV = v, minDeltaVWithRotation = v * 0.6f)
            }
        }
    }

    private class Linear(val timestampNs: Long, val x: Float, val y: Float, val z: Float)

    private val window = ArrayDeque<Sample>()
    private val linear = ArrayDeque<Linear>()
    private var previous: Sample? = null

    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var gravitySeeded = false

    private var candidateAtNs = 0L
    private var candidateDeltaV = 0f
    private var candidateRotation = 0f
    private var inCandidate = false
    private var freeFallSinceNs = 0L
    private var restSinceNs = 0L

    var verdict: SnatchDetector.Verdict = SnatchDetector.Verdict.Idle
        private set

    /** The last velocity change measured, m/s, for the readout. */
    var lastDeltaV = 0f
        private set

    fun reset() {
        window.clear()
        linear.clear()
        previous = null
        gravitySeeded = false
        inCandidate = false
        freeFallSinceNs = 0L
        restSinceNs = 0L
        lastDeltaV = 0f
        verdict = SnatchDetector.Verdict.Idle
    }

    fun accept(sample: Sample): SnatchDetector.Verdict {
        val last = previous
        if (last != null && sample.timestampNs <= last.timestampNs) return verdict

        window.addLast(sample)
        val cutoff = sample.timestampNs - (tuning.windowMs + tuning.heldLagMs) * 1_000_000
        while (window.isNotEmpty() && window.first().timestampNs < cutoff) window.removeFirst()

        updateGravity(sample, last)
        linear.addLast(
            Linear(
                sample.timestampNs,
                sample.ax - gravityX,
                sample.ay - gravityY,
                sample.az - gravityZ,
            )
        )
        val impulseCutoff = sample.timestampNs - tuning.impulseWindowMs * 1_000_000
        while (linear.isNotEmpty() && linear.first().timestampNs < impulseCutoff) linear.removeFirst()

        // Trapezoid integral of linear acceleration over the window: the change in
        // velocity, as a vector in the phone's own frame.
        var dvx = 0.0
        var dvy = 0.0
        var dvz = 0.0
        var prior: Linear? = null
        for (l in linear) {
            val p = prior
            if (p != null) {
                val dt = (l.timestampNs - p.timestampNs) / 1_000_000_000.0
                dvx += 0.5 * (p.x + l.x) * dt
                dvy += 0.5 * (p.y + l.y) * dt
                dvz += 0.5 * (p.z + l.z) * dt
            }
            prior = l
        }
        val magnitude = sqrt(dvx * dvx + dvy * dvy + dvz * dvz).toFloat()
        lastDeltaV = magnitude
        previous = sample

        trackFreeFall(sample)
        trackRest(sample)

        if (inCandidate) {
            verdict = judgeCandidate(sample)
            return verdict
        }

        if (!isHeld(sample.timestampNs)) {
            verdict = SnatchDetector.Verdict.Idle
            return verdict
        }

        val rotation = sample.rotationMagnitude
        val bar = if (rotation >= tuning.rotationSupport) {
            tuning.minDeltaVWithRotation
        } else {
            tuning.minDeltaV
        }

        if (magnitude >= bar && dvy / magnitude >= tuning.coneCos) {
            if (gravityY < tuning.uprightMinGravityY) {
                verdict = SnatchDetector.Verdict.Rejected("upside down — going into a pocket")
                return verdict
            }
            inCandidate = true
            candidateAtNs = sample.timestampNs
            candidateDeltaV = magnitude
            candidateRotation = rotation
            verdict = SnatchDetector.Verdict.Candidate(magnitude, rotation)
            return verdict
        }

        verdict = SnatchDetector.Verdict.Watching
        return verdict
    }

    // ------------------------------------------------------------------- internals

    private fun judgeCandidate(sample: Sample): SnatchDetector.Verdict {
        val elapsedMs = (sample.timestampNs - candidateAtNs) / 1_000_000

        if (freeFallSinceNs != 0L &&
            (sample.timestampNs - freeFallSinceNs) / 1_000_000 >= tuning.freeFallMs
        ) {
            inCandidate = false
            return SnatchDetector.Verdict.Rejected("free fall — dropped, not taken")
        }

        if (restSinceNs != 0L &&
            (sample.timestampNs - restSinceNs) / 1_000_000 >= tuning.restMs
        ) {
            inCandidate = false
            return SnatchDetector.Verdict.Rejected("came to rest inside the window")
        }

        if (elapsedMs >= tuning.confirmWindowMs) {
            inCandidate = false
            return SnatchDetector.Verdict.Snatch(candidateDeltaV, candidateRotation, candidateAtNs)
        }

        return SnatchDetector.Verdict.Candidate(candidateDeltaV, candidateRotation)
    }

    /**
     * Low-pass by elapsed time, seeded from the first sample. The coefficient is computed
     * from the real interval on every sample, so the time constant is [Tuning.gravityTauS]
     * whatever rate the sensor actually delivers.
     */
    private fun updateGravity(sample: Sample, last: Sample?) {
        if (!gravitySeeded || last == null) {
            gravityX = sample.ax
            gravityY = sample.ay
            gravityZ = sample.az
            gravitySeeded = true
            return
        }
        val dt = (sample.timestampNs - last.timestampNs) / 1_000_000_000.0
        val a = exp(-dt / tuning.gravityTauS).toFloat()
        gravityX = a * gravityX + (1 - a) * sample.ax
        gravityY = a * gravityY + (1 - a) * sample.ay
        gravityZ = a * gravityZ + (1 - a) * sample.az
    }

    private fun trackFreeFall(sample: Sample) {
        if (sample.accelMagnitude < tuning.freeFallCeiling) {
            if (freeFallSinceNs == 0L) freeFallSinceNs = sample.timestampNs
        } else {
            freeFallSinceNs = 0L
        }
    }

    private fun trackRest(sample: Sample) {
        val settled = abs(sample.accelMagnitude - Sample.GRAVITY) <= tuning.settledAccelBand &&
            sample.rotationMagnitude <= tuning.settledRotation
        if (settled) {
            if (restSinceNs == 0L) restSinceNs = sample.timestampNs
        } else {
            restSinceNs = 0L
        }
    }

    /** Same test as [SnatchDetector.isHeld], lag included. */
    private fun isHeld(now: Long): Boolean {
        val cutoff = now - tuning.heldLagMs * 1_000_000
        var count = 0
        var sum = 0.0
        for (s in window) {
            if (s.timestampNs > cutoff) continue
            sum += s.accelMagnitude
            count++
        }
        if (count < MIN_WINDOW_SAMPLES) return false
        val mean = sum / count
        var variance = 0.0
        for (s in window) {
            if (s.timestampNs > cutoff) continue
            val d = s.accelMagnitude - mean
            variance += d * d
        }
        val deviation = sqrt(variance / count).toFloat()
        return deviation in tuning.heldTremorMin..tuning.heldTremorMax
    }

    private companion object {
        const val MIN_WINDOW_SAMPLES = 8
    }
}
