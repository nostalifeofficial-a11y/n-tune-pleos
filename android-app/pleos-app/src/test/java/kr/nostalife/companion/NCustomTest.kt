package kr.nostalife.companion

import org.junit.Assert.*
import org.junit.Test

class NCustomTest {
    @Test fun recommendationsMatchOnlyTheirCarAndGearboxWithoutChangingSelections() {
        NCustom.cars.forEach { car ->
            listOf("DCT", "MT").forEach { variant ->
                val allowed = NAdviceCatalog.options(DrivePresentation.carId(car), if (car == NCustom.cars[0]) variant else "EV")!!
                assertEquals(NCustom.options(car, variant).filter { it.key != "esc" }.associate { it.key to it.choices }, allowed)
            }
        }
        val advice = NCustomAdvice("avante_n", "MT", "UI 후보", mapOf("engine" to "Sport", "rev" to "Sport+"))
        val ai = AiAnalysis("", "", "", "", "", "", nCustom = listOf(advice))
        assertEquals(advice, DrivePresentation.advice(ai, NCustom.cars[0], "MT"))
        assertNull(DrivePresentation.advice(ai, NCustom.cars[0], "DCT"))
        assertNull(DrivePresentation.advice(ai, NCustom.cars[1], "MT"))
        assertNull(DrivePresentation.advice(null, NCustom.cars[0], "MT"))
    }
    @Test fun oldTextUsesWholeSentenceWithoutSplittingDecimalValues() {
        assertEquals("속도는 68.8km/h입니다.", DrivePresentation.excerpt("속도는 68.8km/h입니다. 더 긴 내용입니다."))
        assertEquals(100, DrivePresentation.excerpt("가".repeat(200)).length)
        assertEquals("11.15 · 10:28 주행", DrivePresentation.driveTitle(java.time.Instant.parse("2026-11-15T01:28:21Z")))
    }
    @Test fun stepsAreBoundedAndPreserveEndpoints() {
        assertEquals(0f, driveStep(-10f)); assertEquals(100f, driveStep(110f))
        assertEquals(65f, driveStep(66f)); assertEquals(70f, driveStep(68f))
        assertEquals(50f, driveStep(Float.NaN))
        (0..100).forEach { assertEquals(0f, driveStep(it.toFloat()) % 5f) }
    }
    @Test fun carsVariantsAndSlotsStayIndependent() {
        val keys = NCustom.cars.flatMap { car -> listOf("DCT", "MT").filter { car == NCustom.cars.first() || it == "DCT" }.flatMap { variant ->
            (1..2).flatMap { slot -> NCustom.options(car, variant).map { NCustom.storageKey(car, variant, slot, it.key) } }
        } }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(NCustom.options(NCustom.cars[0], "MT").any { it.key == "rev" })
        assertFalse(NCustom.options(NCustom.cars[0], "MT").any { it.key == "transmission" })
        assertTrue(NCustom.options(NCustom.cars[1], "DCT").first { it.key == "sound" }.choices.contains("Supersonic"))
        assertTrue(NCustom.options(NCustom.cars[2], "DCT").first { it.key == "sound" }.choices.contains("Lightspeed"))
        assertTrue(NCustom.options(NCustom.cars[2], "DCT").any { it.key == "ambient" })
        assertFalse(NCustom.options(NCustom.cars[1], "DCT").any { it.key == "ambient" })
    }
}
