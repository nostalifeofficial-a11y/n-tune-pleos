package kr.nostalife.vehicleprobe

import org.junit.Assert.*
import org.junit.Test

class CaptureTest {
    @Test fun missingAndStaleMeasurementsNeverBecomeZeroOrCurrentData() {
        var now = 1_000_000_000L
        val capture = Capture({ now })
        capture.define("brake", "brake", "%", "test")
        capture.status("brake", "permission_denied", "denied")
        assertTrue(capture.snapshot().getJSONArray("channels").getJSONObject(0).isNull("currentValue"))
        capture.sample("brake", 0.0, now)
        assertEquals(0.0, capture.snapshot().getJSONArray("channels").getJSONObject(0).getDouble("currentValue"), 0.0)
        now += 3_000_000_000L
        val row = capture.snapshot().getJSONArray("channels").getJSONObject(0)
        assertEquals("stale", row.getString("status"))
        assertTrue(row.isNull("currentValue"))
        assertEquals(0.0, row.getDouble("lastValue"), 0.0)
    }
    @Test fun rejectsBooleanPressureAndOutOfRangeAsPedalPercentage() {
        assertNull(pedalPercentage(true))
        assertNull(pedalPercentage(100))
        assertNull(pedalPercentage(101f))
        assertNull(pedalPercentage(Float.NaN))
        assertEquals(45.0, pedalPercentage(45f)!!, 0.0)
    }
    @Test fun convertsGravityUnitsAndKeepsIndependentWheelMapping() {
        assertEquals(1.0, lateralG(9.80665f)!!, 1e-6)
        assertEquals("rl", wheelNames[4])
        assertEquals("rr", wheelNames[8])
        assertNull(wheelNames[3])
    }
    @Test fun repeatedOutOfOrderAndInvalidSamplesDoNotInflateRate() {
        var now = 1_000_000_000L
        val capture = Capture({ now }, 2)
        capture.define("x", "x", "g", "test")
        capture.sample("x", 1.0, now)
        capture.sample("x", 2.0, now)
        capture.sample("x", 3.0, now - 100)
        now += 100_000_000L
        capture.sample("x", 2.0, now)
        now += 100_000_000L
        capture.sample("x", 3.0, now)
        val row = capture.snapshot().getJSONArray("channels").getJSONObject(0)
        assertEquals(3, row.getInt("sampleCount"))
        assertEquals(2, row.getInt("retainedSampleCount"))
        assertEquals(10.0, row.getDouble("observedHz"), 1e-9)
        capture.sample("x", Double.NaN, now + 1)
        assertEquals("invalid_sample", capture.snapshot().getJSONArray("channels").getJSONObject(0).getString("status"))
    }
}
