package com.verisonder.sonderassist.detect

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Decides whether the phone was just pulled out of a hand.
 *
 * Scope, narrowly: **a grab from the hand, with no getaway.** Android's own Theft
 * Detection Lock already covers snatch-then-run, and it covers it with a model trained on
 * more data than this will ever see. What it does not cover is the case where the thief
 * simply walks off, or steps through a closing train door, because it waits for running,
 * biking or driving before it acts. That waiting is the gap, and this closes it by acting
 * on the grab itself.
 *
 * **Being wrong is cheap and that shapes everything here.** Google ships to a billion
 * phones, so a false lock is a support burden and they must tune for near-zero. A false
 * lock here costs one fingerprint touch. So this deliberately fires on evidence that
 * would be far too thin for a platform feature, and the tuning target is "never miss a
 * real grab", not "never fire wrongly".
 *
 * ### The signature
 *
 * A phone leaving a hand against resistance is not the same shape as a phone being put
 * down, handed over, or pocketed:
 *
 * 1. **A jerk transient.** Jerk is the derivative of acceleration, and it is the
 *    discriminator — not acceleration itself, which a brisk normal movement matches
 *    easily. Fingers grip, the phone is pulled, the grip fails, and the acceleration
 *    changes almost discontinuously.
 * 2. **A rotation burst.** A grab is never on the centre of mass. The phone pivots out of
 *    the grip, so the gyroscope sees a spike alongside the jerk. Setting a phone down
 *    flat produces jerk with very little rotation.
 * 3. **Motion that does not settle.** Whatever happens next, the phone does not come to
 *    rest. A put-down does, a bump on a desk does, a phone tossed onto a sofa does within
 *    about a second.
 *
 * ### What is deliberately rejected
 *
 * - **A drop.** Free fall reads near zero g, which nothing in a hand-to-hand grab does.
 *   A dropped phone is not a stolen phone and locking on it would train the owner to
 *   distrust the app.
 * - **Anything that settles** inside the confirmation window.
 * - **Anything while not in a hand.** A phone lying on a table has almost no variance;
 *   a held phone always carries tremor. Grabbing from a table is a real case, but it is a
 *   different signature and belongs in its own detector rather than being bolted on here.
 *
 * ### Tuning
 *
 * Every threshold lives in [Tuning] and none of the defaults are trustworthy yet — they
 * are starting points from the physics, not from data. The way to set them is to record
 * real traces with the capture screen, replay them here, and move the numbers until the
 * grabs fire and the put-downs do not. Until that has happened, treat every default in
 * this file as a guess.
 */
class SnatchDetector(private val tuning: Tuning = Tuning()) {

    /**
     * @param jerkThreshold m/s³. Normal handling — pocketing, passing, gesturing — sits
     *   well under 100. A grab against a closed grip is an order of magnitude above it.
     * @param rotationThreshold rad/s. Roughly a third of a turn per second.
     * @param confirmWindowMs how long after the spike the phone has to keep moving. Long
     *   enough to rule out a put-down, short enough that the lock still beats the thief's
     *   thumb.
     * @param settledAccelBand m/s² either side of gravity. Inside it, the phone is still.
     * @param settledRotation rad/s below which there is no meaningful rotation.
     * @param heldTremorMin m/s² of variation over the recent window. Below this the
     *   phone is resting on something, not held.
     * @param heldTremorMax above this the phone is already being moved about, and a
     *   "grab" cannot be told from the movement already happening.
     * @param freeFallCeiling m/s². Below it the device is falling and this is a drop.
     * @param freeFallMs how long that has to hold to count as a real fall rather than a
     *   sampling artefact.
     * @param windowMs the trailing window the held/tremor statistics are computed over.
     */
    data class Tuning(
        val jerkThreshold: Float = 450f,
        val rotationThreshold: Float = 2.0f,
        val confirmWindowMs: Long = 700,
        val settledAccelBand: Float = 0.6f,
        val settledRotation: Float = 0.35f,
        val heldTremorMin: Float = 0.06f,
        val heldTremorMax: Float = 4.5f,
        val freeFallCeiling: Float = 3.0f,
        val freeFallMs: Long = 120,
        val windowMs: Long = 900,
        /**
         * Require the gyroscope to agree. Turned off automatically for a device with no
         * gyroscope, where jerk alone has to carry the decision — worse, but better than
         * refusing to run.
         */
        val requireRotation: Boolean = true,
    )

    sealed interface Verdict {
        /** Nothing of interest. Includes the phone not being in a hand at all. */
        data object Idle : Verdict

        /** Held, and being watched. */
        data object Watching : Verdict

        /** A spike has been seen and the confirmation window is running. */
        data class Candidate(val jerk: Float, val rotation: Float) : Verdict

        /** Fire. Lock the screen. */
        data class Snatch(val jerk: Float, val rotation: Float, val atNs: Long) : Verdict

        /** A candidate that did not survive the window, kept for the trace viewer. */
        data class Rejected(val reason: String) : Verdict
    }

    private val window = ArrayDeque<Sample>()
    private var previous: Sample? = null

    private var candidateAtNs = 0L
    private var candidateJerk = 0f
    private var candidateRotation = 0f
    private var inCandidate = false
    private var freeFallSinceNs = 0L

    /** Latest verdict, so a UI can show what the detector currently thinks. */
    var verdict: Verdict = Verdict.Idle
        private set

    fun reset() {
        window.clear()
        previous = null
        inCandidate = false
        freeFallSinceNs = 0L
        verdict = Verdict.Idle
    }

    /**
     * Feed one sample. Returns the verdict for this sample.
     *
     * Samples must arrive in order. Out-of-order or duplicate timestamps are dropped
     * rather than producing a divide by zero in the jerk — sensor batching can deliver
     * two events with the same timestamp and the resulting infinity would fire instantly.
     */
    fun accept(sample: Sample): Verdict {
        val last = previous
        if (last != null && sample.timestampNs <= last.timestampNs) return verdict

        window.addLast(sample)
        val cutoff = sample.timestampNs - tuning.windowMs * 1_000_000
        while (window.isNotEmpty() && window.first().timestampNs < cutoff) window.removeFirst()

        val jerk = if (last == null) 0f else jerkBetween(last, sample)
        previous = sample

        trackFreeFall(sample)

        if (inCandidate) {
            verdict = judgeCandidate(sample)
            return verdict
        }

        if (!isHeld()) {
            verdict = Verdict.Idle
            return verdict
        }

        val rotation = sample.rotationMagnitude
        val rotationAgrees = !tuning.requireRotation || rotation >= tuning.rotationThreshold
        if (jerk >= tuning.jerkThreshold && rotationAgrees) {
            inCandidate = true
            candidateAtNs = sample.timestampNs
            candidateJerk = jerk
            candidateRotation = rotation
            verdict = Verdict.Candidate(jerk, rotation)
            return verdict
        }

        verdict = Verdict.Watching
        return verdict
    }

    // ------------------------------------------------------------------- internals

    private fun judgeCandidate(sample: Sample): Verdict {
        val elapsedMs = (sample.timestampNs - candidateAtNs) / 1_000_000

        // A fall that began around the spike means the phone was dropped, not taken.
        if (freeFallSinceNs != 0L &&
            (sample.timestampNs - freeFallSinceNs) / 1_000_000 >= tuning.freeFallMs
        ) {
            inCandidate = false
            return Verdict.Rejected("free fall — dropped, not taken")
        }

        if (isSettled(sample)) {
            inCandidate = false
            return Verdict.Rejected("came to rest inside the window")
        }

        if (elapsedMs >= tuning.confirmWindowMs) {
            inCandidate = false
            return Verdict.Snatch(candidateJerk, candidateRotation, candidateAtNs)
        }

        return Verdict.Candidate(candidateJerk, candidateRotation)
    }

    private fun trackFreeFall(sample: Sample) {
        if (sample.accelMagnitude < tuning.freeFallCeiling) {
            if (freeFallSinceNs == 0L) freeFallSinceNs = sample.timestampNs
        } else {
            freeFallSinceNs = 0L
        }
    }

    private fun isSettled(sample: Sample): Boolean =
        abs(sample.accelMagnitude - Sample.GRAVITY) <= tuning.settledAccelBand &&
            sample.rotationMagnitude <= tuning.settledRotation

    /**
     * Whether the phone looks like it is in a hand.
     *
     * A hand is never still. Resting on a table the standard deviation of the
     * acceleration magnitude is near zero; held, it is small but always present. The
     * upper bound matters as much as the lower: if the phone is already being swung
     * about, a grab is not distinguishable from what is already happening, and firing
     * there is how an app earns a reputation for locking at random.
     */
    private fun isHeld(): Boolean {
        if (window.size < MIN_WINDOW_SAMPLES) return false
        val deviation = magnitudeDeviation()
        return deviation in tuning.heldTremorMin..tuning.heldTremorMax
    }

    private fun magnitudeDeviation(): Float {
        var sum = 0.0
        for (s in window) sum += s.accelMagnitude
        val mean = sum / window.size
        var variance = 0.0
        for (s in window) {
            val d = s.accelMagnitude - mean
            variance += d * d
        }
        return sqrt(variance / window.size).toFloat()
    }

    private fun jerkBetween(a: Sample, b: Sample): Float {
        val seconds = (b.timestampNs - a.timestampNs) / 1_000_000_000.0
        if (seconds <= 0.0) return 0f
        val dx = b.ax - a.ax
        val dy = b.ay - a.ay
        val dz = b.az - a.az
        return (sqrt((dx * dx + dy * dy + dz * dz).toDouble()) / seconds).toFloat()
    }

    private companion object {
        /** Below this the window is too short for the deviation to mean anything. */
        const val MIN_WINDOW_SAMPLES = 8
    }
}
