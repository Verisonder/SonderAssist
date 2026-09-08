package com.verisonder.sonderassist.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Rings that gather under a finger, and the way in to a model.
 *
 * The screen has nothing on it - no name, no button, nothing to read. What it
 * has instead is an answer for a touch: a set of turning rings that arrive where
 * the finger is and leave when it does. Two fingers tapped once arm it, two
 * fingers up carry it to the second stage, and two fingers left to right finish
 * it. Each leg has its own origin, taken where the fingers are when that leg
 * begins.
 *
 * Drawn rather than drawn from a file. A picture could not be tinted, could not
 * turn without softening at the edges, and could not brighten to say it had
 * understood the first half of a gesture.
 *
 * Carried over from BlackFriday unchanged apart from the package and this note.
 * The gesture rules in it were found by testing on a real phone and should not
 * be re-derived.
 */
class HudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** The whole gesture, completed. */
    var onLoad: (() -> Unit)? = null

    /**
     * Where the fingers are, after every change.
     *
     * The rings are only half the answer to a touch; the field underneath
     * brightens too, and it needs telling. The array is reused, so whoever
     * takes it must read it rather than keep it.
     */
    var onFingers: ((points: FloatArray, count: Int) -> Unit)? = null

    private val fingerBuffer = FloatArray(4)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()

    /**
     * One ring set, under one finger.
     *
     * [lifted] is when the finger went, or zero while it is still down. Kept
     * after the lift so the rings can fade rather than blink out.
     */
    private class Touch(
        val id: Int,
        var x: Float,
        var y: Float,
        val born: Long,
        var lifted: Long = 0L
    ) {
        /** Where this finger landed. Movement is measured from here, not from
         *  the middle of however many fingers happen to be down. */
        var startX = x
        var startY = y
    }

    private val touches = mutableListOf<Touch>()

    /**
     * A piece of the instrument: how far out it sits, how fast it turns, and
     * what it is.
     *
     * Speeds are two decimal places and no more. The phase wraps at a hundred
     * turns, so every one of these completes a whole number of turns at the
     * wrap and the moment it happens cannot be seen.
     */
    private class Part(
        val radius: Float,
        val speed: Float,
        val kind: Int,
        val width: Float = 1f,
        val alpha: Int = 255
    )

    private val parts = listOf(
        Part(radius = 1.00f, speed = 0.24f, kind = BEADS, width = 1.0f, alpha = 45),
        Part(radius = 0.87f, speed = -0.60f, kind = BROKEN, width = 2.4f, alpha = 130),
        Part(radius = 0.75f, speed = 0.35f, kind = TICKS, width = 1.0f, alpha = 85),
        Part(radius = 0.60f, speed = -0.80f, kind = THIN, width = 1.0f, alpha = 70),
        Part(radius = 0.50f, speed = 1.00f, kind = RING, width = 1.0f, alpha = 40),
        Part(radius = 0.40f, speed = -0.45f, kind = HEAVY, width = 4.4f, alpha = 210),
    )

    /** Degrees turned so far, counted forward and wrapped only at a whole turn. */
    private var phase = 0f
    private var lastFrame = 0L

    // The gesture, in two parts: two fingers tapped, then a swipe up
    private var pressStart = 0L
    private var drift = 0f
    private var sawTwo = false
    private var armedAt = 0L

    /** When the swipe up landed. The gesture is three parts now, not two. */
    private var stagedAt = 0L
    private var fired = false

    private val armed get() = armedAt != 0L
    private val staged get() = stagedAt != 0L

    private fun dp(value: Float) = value * resources.displayMetrics.density
    private val slop by lazy { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }

    // ----------------------------------------------------------------
    // The gesture
    // ----------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = System.nanoTime()

        // Decided here, acted on at the end. Loading hides this view, which
        // empties the list of fingers - and doing that in the middle of reading
        // that same list is what crashed it.
        var launch = false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                touches.add(
                    Touch(event.getPointerId(index), event.getX(index), event.getY(index), now)
                )

                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    // A press begins
                    pressStart = now
                    drift = 0f
                    sawTwo = false
                    fired = false
                }

                // The second finger is what the tap is timed from, so a
                // one-fingered press can never build towards it
                if (event.pointerCount == 2) {
                    sawTwo = true
                    pressStart = now
                    drift = 0f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    touches.firstOrNull { it.id == id && it.lifted == 0L }?.let {
                        it.x = event.getX(i)
                        it.y = event.getY(i)
                    }
                }

                // Each finger against where it landed, never against the middle
                // of the fingers still down. That midpoint jumps half the gap
                // between two fingers the moment one of them lifts, which read
                // as a drag every time and threw the tap away.
                // Read before anything can change it. The sideways leg must not be
                // satisfied by the same event that finished the upward one, or a single
                // diagonal drag completes both and the gesture is one swipe, not three.
                val wasStaged = staged

                var swiping = 0
                var crossing = 0
                for (index in touches.indices) {
                    val touch = touches[index]
                    if (touch.lifted != 0L) continue

                    drift = maxOf(drift, hypot(touch.x - touch.startX, touch.y - touch.startY))

                    // Up, and mostly up: a diagonal drag across the screen is
                    // not the same gesture and should not be taken for one
                    val up = touch.startY - touch.y
                    if (up > dp(SWIPE_DP) && abs(touch.x - touch.startX) < up) swiping++

                    // The same test turned on its side, and left to right only:
                    // a swipe that goes either way is half a gesture, and the
                    // one that matters here has a direction.
                    val across = touch.x - touch.startX
                    if (across > dp(SWIPE_DP) && abs(touch.y - touch.startY) < across) crossing++
                }

                // Two fingers arm it and two fingers carry it through each
                // stage. Counting one was enough before, which meant the swipe
                // could be done with a thumb after the tap - easy to do by
                // accident, and not the gesture that was asked for.
                if (armed && !staged && swiping >= 2) {
                    // Not the end any more, only the middle. Each finger is
                    // measured from where it landed, so the sideways swipe that
                    // follows needs its own origin - otherwise the distance it
                    // had already travelled upward would count against it, and
                    // fingers that never lift could never satisfy the second
                    // leg at all.
                    stagedAt = now
                    armedAt = 0L
                    for (index in touches.indices) {
                        val touch = touches[index]
                        if (touch.lifted != 0L) continue
                        touch.startX = touch.x
                        touch.startY = touch.y
                    }
                    drift = 0f
                }

                if (wasStaged && !fired && crossing >= 2) {
                    fired = true
                    stagedAt = 0L
                    launch = true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(event.actionIndex)
                touches.firstOrNull { it.id == id && it.lifted == 0L }?.lifted = now

                // Counted as the press ends rather than as each finger goes,
                // so lifting one of two does not read as a tap of its own
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    countTap(now)
                    touches.forEach { if (it.lifted == 0L) it.lifted = now }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                touches.forEach { if (it.lifted == 0L) it.lifted = now }
                sawTwo = false
            }
        }

        postInvalidateOnAnimation()
        reportFingers()

        // Last, and outside everything that was reading the fingers
        if (launch) onLoad?.invoke()
        return true
    }

    /** Hand the field the fingers that are still down, newest last. */
    private fun reportFingers() {
        var count = 0
        for (touch in touches) {
            if (touch.lifted != 0L || count >= 2) continue
            fingerBuffer[count * 2] = touch.x
            fingerBuffer[count * 2 + 1] = touch.y
            count++
        }
        onFingers?.invoke(fingerBuffer, count)
    }

    /**
     * Arm the screen if the press that has just ended was a two-fingered tap.
     *
     * One tap, not two. Two fingers landing together and leaving together
     * inside a moment is already an unlikely accident, and asking for it twice
     * turned a gesture into a password.
     */
    private fun countTap(now: Long) {
        val quick = (now - pressStart) < TAP_MS * 1_000_000L
        if (!sawTwo || !quick || drift > slop * 2.5f || fired) {
            sawTwo = false
            return
        }
        sawTwo = false
        armedAt = now
    }

    // ----------------------------------------------------------------
    // The rings
    // ----------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        advance(now)

        // Armed, the rings brighten and pick up. Nothing turns red: red in this
        // app means thinking and nothing else.
        val armedNow = armed && (now - armedAt) < ARM_MS * 1_000_000L
        if (armed && !armedNow) armedAt = 0L

        // The second stage forgets on the same timer. A gesture left half done
        // should not sit waiting to be finished by an accident minutes later.
        val stagedNow = staged && (now - stagedAt) < ARM_MS * 1_000_000L
        if (staged && !stagedNow) stagedAt = 0L

        val iterator = touches.iterator()
        var moving = false
        while (iterator.hasNext()) {
            val touch = iterator.next()
            val fade = fade(touch, now)
            if (fade <= 0f) {
                iterator.remove()
                continue
            }
            if (touch.lifted == 0L || fade < 1f) moving = true
            // Brighter again at the second stage, so the rings say which leg of
            // the gesture the screen thinks it is on rather than leaving it to
            // be guessed at.
            draw(canvas, touch.x, touch.y, fade * if (stagedNow) 1f else if (armedNow) 0.9f else 0.75f)
        }

        if (moving || touches.isNotEmpty()) postInvalidateOnAnimation()
    }

    /** In over a moment, out over a slightly longer one. */
    private fun fade(touch: Touch, now: Long): Float {
        val inward = ((now - touch.born) / 1_000_000f / IN_MS).coerceIn(0f, 1f)
        if (touch.lifted == 0L) return inward
        val outward = ((now - touch.lifted) / 1_000_000f / OUT_MS).coerceIn(0f, 1f)
        return inward * (1f - outward)
    }

    private fun draw(canvas: Canvas, cx: Float, cy: Float, fade: Float) {
        val reach = dp(REACH_DP)

        for (part in parts) {
            val radius = reach * part.radius
            val turn = phase * part.speed
            val alpha = (part.alpha * fade).toInt().coerceIn(0, 255)
            if (alpha == 0) continue

            paint.color = (alpha shl 24) or 0xFFFFFF
            paint.strokeWidth = dp(part.width)
            paint.style = Paint.Style.STROKE

            arc.set(cx - radius, cy - radius, cx + radius, cy + radius)

            when (part.kind) {
                // A plain circle looks the same whatever it is doing, so this
                // one is not turned at all
                RING -> canvas.drawCircle(cx, cy, radius, paint)

                BEADS -> {
                    // A faint circle with bright beads on it. The reference has
                    // holes punched through a solid ring, which is the same
                    // shape read the other way up - and the only one of the two
                    // that works over a background that has to show through.
                    canvas.drawCircle(cx, cy, radius, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = ((alpha * 4).coerceAtMost(255) shl 24) or 0xFFFFFF
                    for (i in 0 until BEAD_COUNT) {
                        val angle = Math.toRadians(turn.toDouble()).toFloat() +
                            (i * 2.0 * Math.PI / BEAD_COUNT).toFloat()
                        canvas.drawCircle(
                            cx + cos(angle) * radius,
                            cy + sin(angle) * radius,
                            dp(1.8f),
                            paint
                        )
                    }
                }

                // The two halves go opposite ways rather than turning as one.
                // A broken ring that keeps its gaps a fixed distance apart is a
                // solid thing with holes in it; halves that slide against each
                // other are an instrument doing something.
                BROKEN -> {
                    canvas.drawArc(arc, 8f + turn, 142f, false, paint)
                    canvas.drawArc(arc, 190f - turn, 142f, false, paint)
                }

                HEAVY -> {
                    canvas.drawArc(arc, 120f + turn, 130f, false, paint)
                    canvas.drawArc(arc, 280f - turn, 120f, false, paint)
                }

                THIN -> {
                    canvas.drawArc(arc, 200f + turn, 140f, false, paint)
                    // The terminator, which is what stops a thin arc reading as
                    // a smudge. It rides the end of the arc, so it has to take
                    // the same turn.
                    paint.style = Paint.Style.FILL
                    val end = Math.toRadians(340.0 + turn).toFloat()
                    canvas.drawCircle(
                        cx + cos(end) * radius,
                        cy + sin(end) * radius,
                        dp(2.0f),
                        paint
                    )
                }

                TICKS -> {
                    val inner = radius * 0.90f
                    val long = radius * 0.82f
                    for (i in 0 until TICK_COUNT) {
                        val angle = Math.toRadians(turn.toDouble()).toFloat() +
                            (i * 2.0 * Math.PI / TICK_COUNT).toFloat()
                        val far = if (i % 6 == 0) long else inner
                        canvas.drawLine(
                            cx + cos(angle) * radius,
                            cy + sin(angle) * radius,
                            cx + cos(angle) * far,
                            cy + sin(angle) * far,
                            paint
                        )
                    }
                }
            }
        }
    }

    /** Move the turn on by however long the last frame actually took. */
    private fun advance(now: Long) {
        if (lastFrame == 0L) {
            lastFrame = now
            return
        }
        val seconds = (now - lastFrame) / 1_000_000_000f
        lastFrame = now
        // A spell in the background should not spin the rings forward by a
        // minute of turning the moment the app comes back
        phase += RATE_DEG * seconds.coerceAtMost(0.1f)
        if (phase >= WRAP) phase -= WRAP
    }

    override fun onDetachedFromWindow() {
        touches.clear()
        lastFrame = 0L
        armedAt = 0L
        stagedAt = 0L
        onFingers?.invoke(fingerBuffer, 0)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Hidden mid-touch - the model started loading under a finger - would
        // otherwise leave a pool of light on the field with nothing over it
        if (visibility != VISIBLE) {
            touches.clear()
            armedAt = 0L
            stagedAt = 0L
            onFingers?.invoke(fingerBuffer, 0)
        }
    }

    companion object {
        private const val RING = 0
        private const val BEADS = 1
        private const val BROKEN = 2
        private const val HEAVY = 3
        private const val THIN = 4
        private const val TICKS = 5

        private const val BEAD_COUNT = 12
        private const val TICK_COUNT = 48

        /** Half the width of the whole instrument, in dp. */
        private const val REACH_DP = 76f

        /** Degrees per second for a part of speed 1. */
        private const val RATE_DEG = 76f

        /** A hundred whole turns, which every two-decimal speed divides into. */
        private const val WRAP = 36000f

        private const val IN_MS = 110f
        private const val OUT_MS = 260f

        /** How long two fingers may stay down and still count as a tap. */
        private const val TAP_MS = 340L

        /** How long an armed screen waits for the swipe before forgetting. */
        private const val ARM_MS = 4000L

        /** How far up, in dp, before the swipe counts. */
        private const val SWIPE_DP = 150f
    }
}
