package com.verisonder.sonderassist.learn

import com.verisonder.sonderassist.detect.Sample
import com.verisonder.sonderassist.trace.Trace

/**
 * One grab alert, written down the moment it fired.
 *
 * Pure Kotlin like the detectors, so the format is pinned by a unit test and can be read
 * back off the phone by the same parser the trace fixtures use.
 *
 * **The raw samples are the point.** The numbers in the header say what the detector
 * thought; the samples say what actually happened, and they are what let a false alarm be
 * replayed through either detector at any setting to find what set it off - and a fix be
 * tested against every recorded alert before it ships. A header alone could only ever
 * support guessing.
 *
 * The samples cover the few seconds leading up to the lock, which includes the trigger
 * and the whole confirmation window after it.
 *
 * ```
 * id 1729213425123
 * time 2026-10-18 01:23:45
 * by v1
 * value 812.4
 * unit jerk
 * rotation 2.130
 * trigger_ns 123456789000
 * detectors BOTH
 * sensitivity 0.00
 * upright_guard off
 * rate_hz 98.7
 * readout v1 locked · v2 watching
 * app 5.9 (38)
 * SATRACE1 alert-1729213425123
 * <timestampNs> <ax> <ay> <az> <gx> <gy> <gz>
 * ...
 * ```
 */
data class AlertRecord(
    val id: Long,
    val time: String,
    val by: String,
    val value: Float,
    val rotation: Float,
    val triggerNs: Long,
    val detectors: String,
    val sensitivity: Float,
    val uprightGuard: Boolean,
    val readout: String,
    val app: String,
    val samples: List<Sample>,
) {
    /** Measured, not assumed. v1 behaves differently at 50 Hz and 100 Hz. */
    val rateHz: Float
        get() {
            if (samples.size < 2) return 0f
            val spanNs = samples.last().timestampNs - samples.first().timestampNs
            return if (spanNs <= 0) 0f else (samples.size - 1) * 1e9f / spanNs
        }

    fun serialise(): String = buildString {
        appendLine("id $id")
        appendLine("time $time")
        appendLine("by $by")
        appendLine("value %.3f".format(java.util.Locale.US, value))
        appendLine("unit jerk")
        appendLine("rotation %.3f".format(java.util.Locale.US, rotation))
        appendLine("trigger_ns $triggerNs")
        appendLine("detectors $detectors")
        appendLine("sensitivity %.2f".format(java.util.Locale.US, sensitivity))
        appendLine("upright_guard " + if (uprightGuard) "on" else "off")
        appendLine("rate_hz %.1f".format(java.util.Locale.US, rateHz))
        appendLine("readout " + readout.replace('\n', ' '))
        appendLine("app $app")
        append(Trace.serialise(Trace.Recording("alert-$id", samples)))
    }

    companion object {
        /** Where the samples begin. Everything above it is the header. */
        private const val SAMPLES_MARK = Trace.MAGIC

        /** The header as key/value pairs, and the samples. */
        fun parse(text: String): Pair<Map<String, String>, List<Sample>> {
            val at = text.indexOf(SAMPLES_MARK)
            require(at >= 0) { "no samples in alert record" }
            val header = text.substring(0, at).lineSequence()
                .filter { it.isNotBlank() }
                .associate { line ->
                    val space = line.indexOf(' ')
                    if (space < 0) line to "" else line.substring(0, space) to line.substring(space + 1)
                }
            return header to Trace.parse(text.substring(at)).samples
        }
    }
}
