package kr.nostalife.companion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

fun fixture(name: String): String = object {}.javaClass.classLoader!!.getResource(name)!!.readText()
class ContractTest {
    @Test fun wholeWindowAiAnalysisNeedsNoCompletedLaps() {
        val result = (RecommendationContract.parse(200, fixture("ai-window.json")) as Latest.Found).result
        assertEquals("analyzed", result.status)
        assertEquals("external_ai", result.analysisMode)
        assertEquals(0, result.selectedLaps.size)
        assertEquals(2.0, result.capture["duration_s"]!!, 1e-9)
        assertNotNull(result.ai)
        assertNull(result.recommended)
        assertFalse(result.synthetic)
        assertNotNull(result.observed["lat_g_p95_abs"])
    }
    @Test fun preservesOptionalDashboardMeasurementsAndRealZero() {
        val raw = JSONObject(fixture("ai-window.json"))
        raw.getJSONObject("evidence").getJSONObject("observed_metrics")
            .put("distance_m", 258.99).put("speed_max_kmh", 100.93)
            .put("front_slip_p95_abs", 0.0).put("rear_slip_p95_abs", 0.1536)
        val result = (RecommendationContract.parse(200, raw.toString()) as Latest.Found).result
        assertEquals(258.99, result.observed["distance_m"]!!, 1e-9)
        assertEquals(100.93, result.observed["speed_max_kmh"]!!, 1e-9)
        assertEquals(0.0, result.observed["front_slip_p95_abs"]!!, 1e-9)
        assertEquals(0.1536, result.observed["rear_slip_p95_abs"]!!, 1e-9)
        raw.getJSONObject("evidence").getJSONObject("observed_metrics").put("distance_m", -1)
        assertThrows(ContractException::class.java) { RecommendationContract.parse(200, raw.toString()) }
    }
    @Test fun olderResultsDoNotInventOptionalMeasurements() {
        val raw = JSONObject(fixture("recommended.json"))
        val metrics = raw.getJSONObject("evidence").getJSONObject("observed_metrics")
        listOf("distance_m", "speed_max_kmh", "front_slip_p95_abs", "rear_slip_p95_abs").forEach { metrics.remove(it) }
        val result = (RecommendationContract.parse(200, raw.toString()) as Latest.Found).result
        assertNull(result.observed["distance_m"])
        assertNull(result.observed["speed_max_kmh"])
        assertNull(result.observed["front_slip_p95_abs"])
    }
    @Test fun rejectsInconsistentAiWindowsAndPresetChoices() {
        for (mutate in listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("analysis_window").put("required_valid_laps", 3) },
            { it.getJSONObject("analysis_window").put("duration_s", 100) },
            { it.getJSONObject("analysis_window").put("includes_incomplete_and_invalid_laps", false) },
            { it.getJSONObject("analysis").put("recommended_preset", "stable") },
            { it.getJSONObject("ai").put("provider", "fake") },
            { it.getJSONObject("policy").put("external_ai", false) }
        )) {
            val raw = JSONObject(fixture("ai-window.json")); mutate(raw)
            assertThrows(ContractException::class.java) { RecommendationContract.parse(200, raw.toString()) }
        }
    }
    @Test fun actualStageTwoResultMapsWithoutChangingSessionOrPreset() {
        val body = fixture("recommended.json")
        val raw = JSONObject(body)
        val result = (RecommendationContract.parse(200, body) as Latest.Found).result
        assertEquals(raw.getString("session_id"), result.sessionId)
        assertEquals(raw.getJSONObject("recommended_setup").getString("id"), result.recommended!!.id)
        assertEquals(42.2, result.observed["lap_time_median_s"]!!, 1e-9)
        assertEquals(listOf(1,2,3,4), result.selectedLaps)
        assertEquals("local_model", result.analysisMode)
        assertTrue(result.synthetic)
    }
    @Test fun heldAndEmptyHaveNoInventedRecommendation() {
        val held = (RecommendationContract.parse(200, fixture("held.json")) as Latest.Found).result
        assertEquals("held", held.status); assertNull(held.recommended); assertNotNull(held.holdReason)
        assertEquals(Latest.Empty, RecommendationContract.parse(404, fixture("empty.json")))
    }
    @Test fun rejectsUnsupportedSourcesVersionsAndSetupParameters() {
        for (mutate in listOf<(JSONObject) -> Unit>(
            { it.put("simulation", false) }, { it.put("source", "ACC") },
            { it.put("schema_version", "2.0") }, { it.getJSONObject("recommended_setup").put("steering_gain", 99) },
            { it.put("created_at", "2026-11-10T10:00:00+09:00") },
            { it.getJSONObject("policy").put("production_upload", true) }
        )) {
            val raw = JSONObject(fixture("recommended.json")); mutate(raw)
            assertThrows(ContractException::class.java) { RecommendationContract.parse(200, raw.toString()) }
        }
    }
    @Test fun rejectsMalformedAndContradictoryContracts() {
        assertThrows(ContractException::class.java) { RecommendationContract.parse(200, "{broken") }
        assertThrows(ContractException::class.java) { RecommendationContract.parse(200, fixture("empty.json")) }
        val held = JSONObject(fixture("held.json"))
        held.put("recommended_setup", JSONObject(fixture("recommended.json")).getJSONObject("current_setup"))
        assertThrows(ContractException::class.java) { RecommendationContract.parse(200, held.toString()) }
        val mismatched = JSONObject(fixture("recommended.json"))
        mismatched.getJSONArray("comparison_metrics").getJSONObject(0).put("baseline", 999)
        assertThrows(ContractException::class.java) { RecommendationContract.parse(200, mismatched.toString()) }
    }
}
