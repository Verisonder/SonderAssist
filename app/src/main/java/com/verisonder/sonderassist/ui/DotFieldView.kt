package com.verisonder.sonderassist.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A field of dots that brightens toward a soft centre.
 *
 * Sits behind the conversation. Black on its own is flat, and a picture would
 * fight the text; a dot matrix is the same shape as everything else here - the
 * launcher icon, the loading dots, the labels.
 *
 * Still while nothing is happening, and drawn once into a bitmap so it costs
 * nothing. It only breathes while Friday is answering, which is the one moment
 * where a little movement says something true.
 */
class DotFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val spacing = dp(15f)
    private val baseRadius = dp(1.3f)

    /**
     * How far the glow reaches, against the smaller side of the view.
     *
     * Her waiting screen spreads further, because the dots are the only thing
     * on it. Everywhere else this sits behind a conversation and stays where it
     * was.
     */
    private val reach get() = if (quiet) 0.80f else 0.62f

    /** Brightest dot, out of 255. Texture rather than decoration, but visible. */
    private val peak = 78

    /**
     * Floor under the falloff, so the field does not vanish at the edges.
     *
     * This is what decides how much of the screen has dots on it at all: the
     * edges sit at this fraction of the middle. Behind a conversation it stays
     * at the sixth it always was, which is texture under text. On her waiting
     * screen it goes to two thirds, so the field reads as a surface with a glow
     * on it rather than a pool of dots in the middle of a black screen.
     */
    private val floor get() = if (quiet) 0.60f else 0.16f



    /**
     * Counted forward, never wrapped. The drift and the swell run at different
     * rates, so a phase that looped would snap both back at the loop point.
     */
    private var phase = 0f
    private var lastFrame = 0L
    private var running = false

    /** How hard the field is working. */
    enum class Motion { STILL, TALKING, THINKING }

    var motion = Motion.STILL
        set(value) {
            if (field == value) return
            field = value
            if (value == Motion.STILL) stop() else start()
            invalidate()
        }

    /** How far the glow wanders, and how fast, for each state. */
    private val wanderDp: Float
        get() = when (motion) {
            Motion.STILL -> 0f
            Motion.TALKING -> 26f
            Motion.THINKING -> 62f
        }

    /** Radians per second. Thinking turns roughly twice as fast. */
    private val rate: Float
        get() = when (motion) {
            Motion.STILL -> 0f
            Motion.TALKING -> 0.70f
            Motion.THINKING -> 1.50f
        }

    private val swell: Float
        get() = when (motion) {
            Motion.STILL -> 0f
            Motion.TALKING -> 0.14f
            Motion.THINKING -> 0.30f
        }

    /**
     * How strongly light travels through the field.
     *
     * A ring of brightness runs outward from the middle, so the dots take their
     * turn rather than the whole field brightening at once.
     */
    private val ripple: Float
        get() = when (motion) {
            Motion.STILL -> 0f
            Motion.TALKING -> 0.40f
            Motion.THINKING -> 0.62f
        }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    /**
     * Where fingers are, and how far each one has come up or gone down.
     *
     * A second glow, tighter and brighter than the drifting one, so touching the
     * field does something rather than nothing. Two slots because arming her
     * screen takes two fingers, and one lighting up while the other did not
     * would read as a fault.
     */
    /**
     * Whether the field is holding back.
     *
     * On her waiting screen the dots are the only thing on show, and the glow
     * they already carry sits near the middle - which is where a thumb lands.
     * Left at full strength the pool of light has nothing to stand out from.
     * Turned down, the difference is the point rather than a guess.
     *
     * Only the field itself is dimmed. What a finger adds is not, so the light
     * under it is as bright as it ever was.
     */
    var quiet = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * How far down the field goes while it is holding back.
     *
     * Measured rather than guessed: at 0.58 the brightest dot lands on 45 out
     * of 255, which is where the field already sits elsewhere in the app. A
     * little above that leaves the untouched screen reading as a field rather
     * than as black, and still leaves the pool under a finger four times
     * brighter than anything around it.
     */
    private val quietly = 0.68f

    /**
     * How far through the opening the field is, from nothing to all of it.
     *
     * The field arrives rather than being there already: a coarse grid first,
     * then the rest filling in between. Quick enough to be the app opening and
     * not an animation being sat through.
     */
    private var opening = 1f

    /**
     * The single swell once the grid is all there, from nothing to nothing.
     *
     * The field arrives and then takes a breath. One breath - anything that
     * keeps beating turns a way in to a screen into a thing that needs
     * watching.
     */
    private var pulse = 1f

    /** Play the opening. Ignored if one is already running. */
    fun open() {
        if (opening < 1f || pulse < 1f) return
        opening = 0f
        pulse = 0f
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    private val fingers = FloatArray(4)
    private var fingerCount = 0
    private val lit = FloatArray(2)

    /** How wide the pool of light is, and how much it adds at its centre. */
    private val poolSpan get() = dp(120f)
    private val poolPeak = 1.6f

    /**
     * Say where the fingers are, or pass a count of zero for none.
     *
     * [points] is x and y in pairs. The array is read, not kept, so the caller
     * can hand over the same one every time.
     */
    fun lightUnder(points: FloatArray, count: Int) {
        fingerCount = count.coerceIn(0, 2)
        for (i in 0 until fingerCount * 2) fingers[i] = points[i]
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        advance()

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // The glow drifts, so the field is never quite the same twice - a
        // fixed spot reads as a smudge on the screen. Thinking wanders further
        // and faster than talking, because more is going on.
        val wander = dp(wanderDp)
        val cx = w / 2f + cos(phase) * wander
        val cy = h * 0.42f + sin(phase * 0.7f) * wander * 0.6f

        val span = min(w, h) * reach
        val breath = 1f + swell * sin(phase * 1.6f)

        var row = 0
        var y = spacing / 2f
        while (y < h) {
            var column = 0
            var x = spacing / 2f
            while (x < w) {
                val dx = (x - cx) / span
                val dy = (y - cy) / span

                // Gaussian falloff. Squared distance keeps it round and avoids
                // a square root for every dot on every frame.
                val squared = dx * dx + dy * dy
                val fall = exp(-squared * 2.4f)
                var strength = (floor + (1f - floor) * fall) * breath
                if (quiet) strength *= quietly
                // A ring rather than a swell of the whole middle: the light
                // starts at the centre and goes outward past the edges, so
                // what is seen travels instead of simply getting brighter.
                if (pulse < 1f) {
                    val front = pulse * PULSE_REACH
                    val band = (sqrt(squared) - front) / PULSE_WIDTH
                    // Thinning as it goes, the way a ring of anything does
                    strength *= 1f + PULSE_PEAK * exp(-band * band) * (1f - pulse * 0.55f)
                }


                if (ripple > 0f) {
                    // Distance, not distance squared: the wave has to keep its
                    // spacing as it travels, and squaring bunches the rings up
                    // toward the middle
                    val distance = sqrt(squared)
                    strength *= 1f + ripple * sin(phase * 2.4f - distance * 7f)
                }

                // Added on top rather than multiplied in, so the light under a
                // finger arrives at the same brightness wherever it lands -
                // multiplying would make it bright in the middle of the screen
                // and almost nothing at the edges, where the falloff is already
                // near the floor.
                for (slot in 0 until 2) {
                    if (lit[slot] <= 0.01f) continue
                    val fx = (x - fingers[slot * 2]) / poolSpan
                    val fy = (y - fingers[slot * 2 + 1]) / poolSpan
                    strength += lit[slot] * poolPeak * exp(-(fx * fx + fy * fy) * 2.4f)
                }

                // Every third dot both ways is the field's frame and lands
                // first; the ones sharing a line with it follow; the rest fill
                // the gaps. Three stages rather than one fade, so what is seen
                // is a grid getting finer instead of a flat thing brightening.
                if (opening < 1f) {
                    val onColumn = column % 3 == 0
                    val onRow = row % 3 == 0
                    val due = when {
                        onColumn && onRow -> 0f
                        onColumn || onRow -> 0.30f
                        else -> 0.55f
                    }
                    strength *= ((opening - due) / 0.45f).coerceIn(0f, 1f)

                    // The whole field lands bright and cools to normal as the
                    // grid completes, so the dots arrive rather than fade up.
                    // It is gone by the time the opening ends, which is where
                    // the ring takes over.
                    strength *= 1f + OPEN_LIFT * (1f - opening)
                }

                if (strength > 0.02f) {
                    // Capped, or a dot at the centre of the pool swells into a
                    // blob and the field stops looking like a matrix
                    val shown = strength.coerceAtMost(2.4f)
                    paint.color = colour(shown)
                    canvas.drawCircle(x, y, baseRadius * (0.7f + 0.5f * shown), paint)
                }
                x += spacing
                column++
            }
            y += spacing
            row++
        }

        // A finger that has just landed has nothing lit yet, so it has to be
        // asked for by itself - otherwise the first frame is also the last and
        // the glow never gets off the ground
        if (running || opening < 1f || pulse < 1f ||
            fingerCount > 0 || lit[0] > 0.01f || lit[1] > 0.01f
        ) {
            postInvalidateOnAnimation()
        }
    }

    /**
     * Move the phase on by however long the last frame took.
     *
     * Time-based rather than frame-based, so changing pace mid-flight speeds
     * the drift up from where it is instead of jumping it somewhere new.
     */
    private fun advance() {
        val now = System.nanoTime()
        if (lastFrame == 0L) {
            lastFrame = now
            return
        }
        val seconds = (now - lastFrame) / 1_000_000_000f
        lastFrame = now
        phase += rate * seconds.coerceAtMost(0.1f)

        // In quickly, out more slowly. A light that leaves as fast as it
        // arrives reads as a flicker rather than something being lifted away.
        val step = seconds.coerceAtMost(0.1f)

        // In order: the grid fills in, and only then does it swell
        if (opening < 1f) {
            opening = (opening + step / OPEN_SECONDS).coerceAtMost(1f)
        } else if (pulse < 1f) {
            pulse = (pulse + step / PULSE_SECONDS).coerceAtMost(1f)
        }

        for (slot in 0 until 2) {
            val target = if (slot < fingerCount) 1f else 0f
            val pace = if (target > lit[slot]) 6f else 3f
            lit[slot] = if (target > lit[slot]) {
                (lit[slot] + pace * step).coerceAtMost(target)
            } else {
                (lit[slot] - pace * step).coerceAtLeast(target)
            }
        }

        // Wrapped only where every frequency here completes a whole number of
        // turns - the drift at 1 and 0.7, the breath at 1.6, the ripple at 2.4
        // all land together at 20 pi. Wrapping anywhere else would show as a
        // jump; not wrapping at all would eventually cost float precision and
        // the motion would start stepping.
        if (phase >= WRAP) phase -= WRAP
    }

    companion object {
        private const val WRAP = (20 * Math.PI).toFloat()

        /** How long the field takes to arrive. Short on purpose. */
        private const val OPEN_SECONDS = 0.62f

        /** How much brighter the field starts before settling to itself. */
        private const val OPEN_LIFT = 1.1f

        /**
         * The ring afterwards: how long it takes, how bright it is, how thick
         * it is, and how far out it runs before it is gone.
         *
         * Reach is in the same units as the glow's own falloff, where 1.0 is
         * the span. Past 1.6 is off the corners of a tall screen, so the ring
         * leaves rather than stopping.
         */
        private const val PULSE_SECONDS = 0.62f
        private const val PULSE_PEAK = 1.5f
        private const val PULSE_WIDTH = 0.20f
        private const val PULSE_REACH = 1.9f
    }

    /**
     * White, warming toward red only while thinking.
     *
     * The colour is the signal. Red everywhere all the time makes it decoration
     * and says nothing; kept for the one state it means something, it tells you
     * which mode you are in without a word on screen.
     */
    private fun colour(strength: Float): Int {
        val alpha = (peak * strength).toInt().coerceIn(0, 255)
        if (motion != Motion.THINKING) {
            return (alpha shl 24) or 0xFFFFFF
        }

        val warm = ((strength - 0.55f) / 0.45f).coerceIn(0f, 1f)
        val g = (255 - 190 * warm).toInt()
        val b = (255 - 200 * warm).toInt()
        return (alpha shl 24) or (255 shl 16) or (g shl 8) or b
    }

    private fun start() {
        if (running) return
        running = true
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    private fun stop() {
        running = false
        lastFrame = 0L
        // The phase is kept, so the next reply picks the drift up where it was
        // rather than snapping the glow back to the middle
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
