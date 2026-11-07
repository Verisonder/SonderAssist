package com.verisonder.sonderassist.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import com.verisonder.sonderassist.media.Friday
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The F.R.I.D.A.Y countdown, drawn on the alert screen once a countdown is asked for.
 *
 * Same language as [HudView]: white only, concentric rings at different alphas, some
 * turning. The heavy arc and the lit ticks run down with the time left; the number in the
 * middle is the seconds. When the siren starts it reads SIREN and pulses.
 *
 * It reads [Friday] directly and redraws itself only while there is something to show.
 * Touches pass straight through to the rings above.
 */
class CountdownView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    }
    private val arc = RectF()
    private var shown = 0f
    private val born = SystemClock.elapsedRealtime()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.elapsedRealtime()
        val ends = Friday.countdownEndsAt
        val total = Friday.countdownTotalMs
        val siren = Friday.sirenOn
        val active = siren || (ends > 0L && total > 0L)

        // Fade in and out rather than appear.
        shown = if (active) min(1f, shown + 0.06f) else (shown - 0.06f).coerceAtLeast(0f)
        if (shown > 0f) drawWheel(canvas, now, ends, total, siren)
        // Keep checking: a countdown can be started at any moment by voice or text. Idle,
        // a few times a second is enough; drawing, every frame.
        if (shown > 0f) postInvalidateOnAnimation() else postInvalidateDelayed(250)
    }

    private fun drawWheel(canvas: Canvas, now: Long, ends: Long, total: Long, siren: Boolean) {
        val cx = width / 2f
        val cy = height * 0.30f
        val r = min(width, height) * 0.30f
        val t = (now - born) / 1000f

        val leftMs = if (siren) 0L else (ends - now).coerceAtLeast(0L)
        val frac = if (siren || total <= 0L) 0f else leftMs.toFloat() / total
        val pulse = if (siren) 0.55f + 0.45f * sin(t * 8f) else 1f
        fun a(alpha: Int) = ((alpha * shown * pulse).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF

        // Outer beads, turning slowly.
        paint.style = Paint.Style.FILL
        paint.color = a(70)
        for (i in 0 until 72) {
            val ang = Math.toRadians((i * 5f + t * 6f).toDouble())
            canvas.drawCircle(cx + r * cos(ang).toFloat(), cy + r * sin(ang).toFloat(), dp(1.1f), paint)
        }

        // Tick ring: lit for the time left, a long tick every five.
        paint.style = Paint.Style.STROKE
        val lit = ceil(frac * 60f).toInt()
        for (i in 0 until 60) {
            val long = i % 5 == 0
            paint.strokeWidth = dp(if (long) 2f else 1f)
            paint.color = a(if (i < lit || siren) 210 else 40)
            val ang = Math.toRadians((i * 6f - 90f).toDouble())
            val r1 = r * 0.84f
            val r2 = r * (if (long) 0.92f else 0.89f)
            canvas.drawLine(
                cx + r1 * cos(ang).toFloat(), cy + r1 * sin(ang).toFloat(),
                cx + r2 * cos(ang).toFloat(), cy + r2 * sin(ang).toFloat(),
                paint,
            )
        }

        // The heavy arc: what is left, running anticlockwise down to the top.
        val rh = r * 0.74f
        arc.set(cx - rh, cy - rh, cx + rh, cy + rh)
        paint.strokeWidth = dp(1f)
        paint.color = a(40)
        canvas.drawCircle(cx, cy, rh, paint)
        paint.strokeWidth = dp(4.4f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = a(220)
        canvas.drawArc(arc, -90f, if (siren) 360f else 360f * frac, false, paint)
        paint.strokeCap = Paint.Cap.BUTT

        // Two broken arcs turning against each other.
        val rb = r * 0.62f
        arc.set(cx - rb, cy - rb, cx + rb, cy + rb)
        paint.strokeWidth = dp(2.2f)
        paint.color = a(120)
        canvas.drawArc(arc, 10f - t * 40f, 120f, false, paint)
        canvas.drawArc(arc, 190f - t * 40f, 120f, false, paint)
        val rt = r * 0.54f
        arc.set(cx - rt, cy - rt, cx + rt, cy + rt)
        paint.strokeWidth = dp(1f)
        paint.color = a(80)
        canvas.drawArc(arc, 60f + t * 25f, 200f, false, paint)

        // The number, and what it counts to.
        text.color = a(255)
        text.textSize = r * (if (siren) 0.30f else 0.48f)
        val big = if (siren) "SIREN" else ceil(leftMs / 1000.0).toInt().toString()
        canvas.drawText(big, cx, cy + text.textSize * 0.35f, text)
        if (!siren) {
            text.color = a(150)
            text.textSize = r * 0.09f
            text.letterSpacing = 0.3f
            canvas.drawText("SIREN IN", cx, cy - r * 0.30f, text)
            text.letterSpacing = 0f
        }
    }
}
