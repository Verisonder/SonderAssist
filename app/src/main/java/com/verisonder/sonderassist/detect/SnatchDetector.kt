package com.verisonder.sonderassist.detect

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Decides whether the phone was just pulled out of a hand.
 *
 * Scope, narrowly: **a grab from the hand, with no getaway.** Android's own Theft
 * Detection Lock already covers snatch-then-run. What it does not cover is the thief who
 * simply walks off, or steps through a closing train door, because it waits for running,
 * biking or driving before it acts. That waiting is the gap; this closes it by acting on
 * the grab itself.
 *
 * **Being wrong is cheap and that shapes everything here.** A platform feature shipping to
 * a billion phones must tune for almost no false alarms. A false lock here costs one
 * fingerprint touch, so this fires on evidence that would be far too thin for Google, and
 * the tuning target is "never miss a real grab" rather than "never fire wrongly".
 *
 * ### The geometry, which is the whole idea
 *
 * The hand grips the **bottom** of the phone. The thief takes the **top** and pulls up and
 * away. That is not one possibility among many — it is how the grip works. A hand wrapped
 * around the lower half opens toward the top, so the only direction the phone can leave is
 * along its own long axis, out through the top edge. Real pulls spread perhaps forty
 * degrees either side of it and never reverse it.
 *
 * So the trigger is not the size of the jerk but **its direction in the phone's own
 * frame**: a sharp positive transient along +Y, the axis running from the bottom edge to
 * the top edge. That is a far narrower target than raw magnitude, and most of what a phone
 * does in an ordinary day — set down, pocketed, carried, handed over — does not produce it.
 *
 * ### Why rotation is evidence and not a requirement
 *
 * A grab off the centre of mass makes the phone pivot about the hand and the gyroscope
 * sees it. But a pull straight along the long axis runs directly through that pivot, so it
 * generates almost no torque and almost no rotation — and that is the middle case of
 * three. A detector that required rotation would sail past it. Rotation therefore lowers
 * the jerk needed rather than gating on it.
 *
 * ### Gravity
 *
 * Removed with a low-pass estimate rather than by using TYPE_LINEAR_ACCELERATION, whose
 * fusion smooths away the sharp transient this exists to find. The estimate also gives the
 * phone's tilt for free, which is what keeps "+Y in the phone's frame" meaningful while it
 * is held at any angle.
 *
 * ### Tuning
 *
 * **Every default in [Tuning] is a guess** from the physics, not from data. Record traces,
 * replay them here, and move the numbers until the grabs fire and the ordinary day does
 * not. Until then, treat every number in this file as fiction.
 */
class SnatchDetector(private val tuning: Tuning = Tuning()) {

    /**
     * @param axialJerk m/s³ along +Y. The trigger, reached alone only by a hard pull.
     * @param axialJerkWithRotation the lower bar that applies when the phone is also
     *   pivoting, because two signals agreeing beat one strong one.
     * @param rotationSupport rad/s above which rotation counts as agreeing.
     * @param minAxialAccel m/s² of actual pull along +Y. Rejects a knock, which spikes the
     *   derivative without the phone going anywhere.
     * @param confirmWindowMs how long the phone must keep moving after the transient.
     * @param settledAccelBand m/s² either side of gravity, inside which it is still.
     * @param settledRotation rad/s below which there is no meaningful rotation.
     * @param heldTremorMin m/s² of variation. Below it the phone rests on something.
     * @param heldTremorMax above it the phone is already being waved about, where a grab
     *   cannot be told from the motion already happening.
     * @param freeFallCeiling m/s². Below it the device is falling: a drop, not a theft.
     * @param freeFallMs how long that must hold to be a fall and not a sampling artefact.
     * @param windowMs the trailing window the held statistics are computed over.
     * @param heldLagMs how far back that window ends. Samples newer than this are left
     *   out of the held check entirely, so the event being judged cannot decide whether
     *   the phone was in a hand before it.
     * @param gravityAlpha low-pass coefficient for the gravity estimate. The time constant
     *   is roughly `sampleInterval / (1 - alpha)`, so at 100 Hz this is about half a
     *   second — slow enough to ignore a pull, fast enough to follow the phone being
     *   tilted. **This is not a free parameter.** At 0.85 the constant is 67 ms, which is
     *   the same order as the transient itself: the filter then tracks the pull, subtracts
     *   it as though it were gravity, and the straight axial grab silently stops firing.
     *   That was the behaviour before it was measured.
     */
    data class Tuning(
        val axialJerk: Float = 600f,
        val axialJerkWithRotation: Float = 350f,
        val rotationSupport: Float = 1.5f,
        val minAxialAccel: Float = 6f,
        val confirmWindowMs: Long = 700,
        val settledAccelBand: Float = 0.6f,
        val settledRotation: Float = 0.35f,
        val heldTremorMin: Float = 0.06f,
        // Raised from 4.5 after measuring: three volume presses inside one window reach
        // about 4.3, which is close enough to the old ceiling to blind the detector at
        // the exact moment someone is handling the phone. The lag below is the real fix;
        // this is the margin.
        val heldTremorMax: Float = 7.0f,
        val freeFallCeiling: Float = 3.0f,
        val freeFallMs: Long = 120,
        val windowMs: Long = 900,
        val heldLagMs: Long = 250,
        val gravityAlpha: Float = 0.98f,
    ) {
        companion object {
            /**
             * Turn one slider into a set of thresholds.
             *
             * The three numbers that decide whether a grab fires move together, because
             * they describe one physical event from three angles and moving one alone
             * just makes the detector incoherent. The endpoints are deliberately wide:
             * at 0 it should take a genuine yank, at 1 it should be twitchy enough to be
             * annoying. Nobody can tell what the middle should be without traces, which
             * is what the slider is for in the meantime.
             *
             * @param sensitivity 0 is the most cautious, 1 the most eager.
             */
            fun forSensitivity(sensitivity: Float): Tuning {
                val s = sensitivity.coerceIn(0f, 1f)
                fun between(cautious: Float, eager: Float) = cautious + (eager - cautious) * s
                return Tuning(
                    axialJerk = between(900f, 300f),
                    axialJerkWithRotation = between(550f, 180f),
                    minAxialAccel = between(9f, 3.5f),
                )
            }
        }
    }

    sealed interface Verdict {
        /** Nothing of interest, including the phone not being in a hand. */
        data object Idle : Verdict

        /** Held, and being watched. */
        data object Watching : Verdict

        /** A transient has been seen and the confirmation window is running. */
        data class Candidate(val axialJerk: Float, val rotation: Float) : Verdict

        /** Fire. Lock the screen. */
        data class Snatch(val axialJerk: Float, val rotation: Float, val atNs: Long) : Verdict

        /** A candidate that did not survive its window, kept for the trace viewer. */
        data class Rejected(val reason: String) : Verdict
    }

    private val window = ArrayDeque<Sample>()
    private var previous: Sample? = null
    private var previousAxial = 0f

    // Gravity in the device frame, so +Y stays meaningful at any holding angle.
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var gravitySeeded = false

    private var candidateAtNs = 0L
    private var candidateJerk = 0f
    private var candidateRotation = 0f
    private var inCandidate = false
    private var freeFallSinceNs = 0L

    var verdict: Verdict = Verdict.Idle
        private set

    fun reset() {
        window.clear()
        previous = null
        previousAxial = 0f
        gravitySeeded = false
        inCandidate = false
        freeFallSinceNs = 0L
        verdict = Verdict.Idle
    }

    /**
     * Feed one sample, in order. Out-of-order and duplicate timestamps are dropped rather
     * than dividing by a zero interval, which would give an infinite jerk and fire
     * instantly — sensor batching really does deliver two events stamped the same.
     */
    fun accept(sample: Sample): Verdict {
        val last = previous
        if (last != null && sample.timestampNs <= last.timestampNs) return verdict

        window.addLast(sample)
        // Long enough to hold the lag as well as the span being measured, or the lagged
        // window would be permanently empty and nothing would ever be judged held.
        val cutoff = sample.timestampNs - (tuning.windowMs + tuning.heldLagMs) * 1_000_000
        while (window.isNotEmpty() && window.first().timestampNs < cutoff) window.removeFirst()

        updateGravity(sample)
        // Linear acceleration along the long axis. Positive is "toward the top edge",
        // which is the one direction the phone can leave a hand gripping the bottom.
        val axial = sample.ay - gravityY

        val jerk = if (last == null) {
            0f
        } else {
            val seconds = (sample.timestampNs - last.timestampNs) / 1_000_000_000.0
            if (seconds <= 0.0) 0f else ((axial - previousAxial) / seconds).toFloat()
        }
        previousAxial = axial
        previous = sample

        trackFreeFall(sample)

        if (inCandidate) {
            verdict = judgeCandidate(sample)
            return verdict
        }

        if (!isHeld(sample.timestampNs)) {
            verdict = Verdict.Idle
            return verdict
        }

        val rotation = sample.rotationMagnitude
        val bar = if (rotation >= tuning.rotationSupport) {
            tuning.axialJerkWithRotation
        } else {
            tuning.axialJerk
        }

        if (jerk >= bar && axial >= tuning.minAxialAccel) {
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

    /**
     * Low-pass toward the current reading, seeded from the first sample rather than from
     * zero. A filter starting at zero spends its first second climbing to gravity and
     * reports a metre per second squared of pull that is not there.
     */
    private fun updateGravity(sample: Sample) {
        if (!gravitySeeded) {
            gravityX = sample.ax
            gravityY = sample.ay
            gravityZ = sample.az
            gravitySeeded = true
            return
        }
        val a = tuning.gravityAlpha
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

    private fun isSettled(sample: Sample): Boolean =
        abs(sample.accelMagnitude - Sample.GRAVITY) <= tuning.settledAccelBand &&
            sample.rotationMagnitude <= tuning.settledRotation

    /**
     * Whether the phone looks like it was in a hand, **just before now**.
     *
     * A hand is never still: on a table the deviation of the acceleration magnitude is
     * near zero, held it is small but always present. The upper bound matters as much as
     * the lower, because a phone already being waved about gives a grab nothing to stand
     * out against.
     *
     * **The window ends `heldLagMs` ago, and that is the fix for a real bug.** Measured up
     * to the present, a single sharp impulse — a firm tap on the screen, a volume button
     * press — pushed the deviation over the ceiling, so the detector answered "not in a
     * hand" and ignored everything for the rest of the window. Touch the phone, get it
     * snatched a moment later, and nothing fired. The event being judged must not get a
     * vote on whether the phone was held before it happened.
     */
    private fun isHeld(now: Long): Boolean {
        val cutoff = now - tuning.heldLagMs * 1_000_000
        val settled = window.filter { it.timestampNs <= cutoff }
        if (settled.size < MIN_WINDOW_SAMPLES) return false
        return magnitudeDeviation(settled) in tuning.heldTremorMin..tuning.heldTremorMax
    }

    private fun magnitudeDeviation(samples: List<Sample>): Float {
        var sum = 0.0
        for (s in samples) sum += s.accelMagnitude
        val mean = sum / samples.size
        var variance = 0.0
        for (s in samples) {
            val d = s.accelMagnitude - mean
            variance += d * d
        }
        return sqrt(variance / samples.size).toFloat()
    }

    private companion object {
        /** Below this the window is too short for the deviation to mean anything. */
        const val MIN_WINDOW_SAMPLES = 8
    }
}
