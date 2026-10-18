package com.verisonder.sonderassist.learn

import com.verisonder.sonderassist.detect.Sample
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertRecordTest {

    private fun record() = AlertRecord(
        id = 1729213425123L,
        time = "2026-10-18 01:23:45",
        by = "v2",
        value = 1.53f,
        rotation = 2.1f,
        triggerNs = 1_010_000_000L,
        detectors = "BOTH",
        sensitivity = 0f,
        uprightGuard = true,
        readout = "v1 watching · v2 locked",
        app = "5.9 (38)",
        samples = (0 until 101).map { i ->
            Sample(1_000_000_000L + i * 10_000_000L, 0.1f * i, 9.8f, 0.5f, 0f, 1.5f, 0f)
        },
    )

    @Test
    fun `round trips header and samples`() {
        val (header, samples) = AlertRecord.parse(record().serialise())
        assertEquals("v2", header["by"])
        assertEquals("m/s", header["unit"])
        assertEquals("BOTH", header["detectors"])
        assertEquals("on", header["upright_guard"])
        assertEquals("0.00", header["sensitivity"])
        assertEquals("1010000000", header["trigger_ns"])
        assertEquals("v1 watching · v2 locked", header["readout"])
        assertEquals(101, samples.size)
        assertEquals(record().samples[50], samples[50])
    }

    @Test
    fun `measures the sample rate from the samples`() {
        assertEquals("100.0", AlertRecord.parse(record().serialise()).first["rate_hz"])
    }
}
