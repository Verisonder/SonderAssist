package com.verisonder.sonderassist.trace

import com.verisonder.sonderassist.detect.Sample

/**
 * A recorded run of motion, written on the phone and replayed in a unit test.
 *
 * This is the part that makes the detector tunable instead of guessable. Thresholds
 * chosen by reasoning about the physics are a starting point and nothing more; the only
 * way to know whether 450 m/s³ separates a grab from a pocket is to record both and look.
 *
 * One record per line, space separated, because there are seven fixed numeric fields that
 * will never nest and a format small enough to read in one sitting is worth more than a
 * familiar one. A header line carries the label so a file is self-describing when it turns
 * up in a folder a year later.
 *
 * ```
 * SATRACE1 <label>
 * <timestampNs> <ax> <ay> <az> <gx> <gy> <gz>
 * ...
 * ```
 *
 * Timestamps are the sensor's own monotonic clock, kept raw rather than rebased to zero,
 * so a trace can be lined up against anything else recorded in the same session.
 */
object Trace {

    const val MAGIC = "SATRACE1"

    class Recording(val label: String, val samples: List<Sample>)

    fun serialise(recording: Recording): String = buildString {
        append(MAGIC)
        append(' ')
        appendLine(recording.label.replace('\n', ' '))
        for (s in recording.samples) {
            append(s.timestampNs); append(' ')
            append(s.ax); append(' ')
            append(s.ay); append(' ')
            append(s.az); append(' ')
            append(s.gx); append(' ')
            append(s.gy); append(' ')
            append(s.gz)
            append('\n')
        }
    }

    fun parse(text: String): Recording {
        val lines = text.split('\n')
        require(lines.isNotEmpty() && lines[0].startsWith(MAGIC)) { "not a trace" }
        val label = lines[0].removePrefix(MAGIC).trim()
        val samples = ArrayList<Sample>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val f = line.trim().split(' ')
            // At least seven, so a later version that records another axis stays readable
            // by an earlier one. A trace is a recording of something that happened once
            // and cannot be made again; refusing to read it over an unknown column would
            // be throwing away the only copy.
            require(f.size >= 7) { "trace record has ${f.size} fields, expected at least 7" }
            samples.add(
                Sample(
                    timestampNs = f[0].toLong(),
                    ax = f[1].toFloat(),
                    ay = f[2].toFloat(),
                    az = f[3].toFloat(),
                    gx = f[4].toFloat(),
                    gy = f[5].toFloat(),
                    gz = f[6].toFloat(),
                )
            )
        }
        return Recording(label, samples)
    }

    /** A filename that sorts by time and says what it holds. */
    fun fileName(label: String, atMs: Long): String {
        val safe = label.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("").trim('-').ifEmpty { "trace" }
        return "$atMs-$safe.satrace"
    }
}
