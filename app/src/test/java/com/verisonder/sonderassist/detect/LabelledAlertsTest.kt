package com.verisonder.sonderassist.detect

import com.verisonder.sonderassist.trace.Trace
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Real alerts off the phone, each labelled by its owner as a theft or a false alarm.
 *
 * Every one of them fired v1 at the most cautious setting, so the first test pins that
 * the replay still does — if it does not, the detector changed, not the data. The second
 * pins what the shake gate does to the same set: v2 must keep every theft, and the false
 * alarms it lets through are counted so a change in either direction shows up.
 *
 * Each record is cut to two seconds before the trigger, which is enough for the gravity
 * estimate to settle and for the held window to fill.
 */
class LabelledAlertsTest {

    private class Labelled(val label: String, val triggerNs: Long, val samples: List<Sample>)

    private val alerts: List<Labelled> by lazy {
        val text = javaClass.getResource("/alerts/labelled-2026-10.txt")!!.readText()
        text.split("===== ALERT").filter { it.isNotBlank() }.map { block ->
            val at = block.indexOf(Trace.MAGIC)
            val header = block.substring(0, at).lineSequence().filter { it.isNotBlank() }
                .associate { it.substringBefore(' ') to it.substringAfter(' ') }
            Labelled(
                header.getValue("label"),
                header.getValue("trigger_ns").toLong(),
                Trace.parse(block.substring(at)).samples,
            )
        }
    }

    private fun fire(tuning: SnatchDetector.Tuning, alert: Labelled): SnatchDetector.Verdict.Snatch? {
        val detector = SnatchDetector(tuning)
        return alert.samples.map { detector.accept(it) }
            .filterIsInstance<SnatchDetector.Verdict.Snatch>()
            .firstOrNull()
    }

    // As the phone ran: most cautious setting, upright guard off.
    private val cautious = SnatchDetector.Tuning.forSensitivity(0f)
        .copy(uprightMinGravityY = Float.NEGATIVE_INFINITY)

    @Test
    fun `v1 still fires on every alert at the sample the phone did`() {
        assertEquals(30, alerts.size)
        for (alert in alerts) {
            assertEquals(alert.triggerNs, fire(cautious, alert)?.atNs)
        }
    }

    @Test
    fun `v2 keeps every theft and drops sixteen of nineteen false alarms`() {
        val v2 = cautious.copy(minShake = SnatchDetector.Tuning.V2_MIN_SHAKE)
        val thefts = alerts.filter { it.label == "theft" }
        val falses = alerts.filter { it.label == "false" }
        assertEquals(11, thefts.size)
        assertEquals(19, falses.size)
        assertEquals(11, thefts.count { fire(v2, it) != null })
        assertEquals(3, falses.count { fire(v2, it) != null })
    }
}
