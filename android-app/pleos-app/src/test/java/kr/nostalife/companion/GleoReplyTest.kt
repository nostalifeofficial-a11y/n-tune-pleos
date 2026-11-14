package kr.nostalife.companion

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GleoReplyTest {
    @Test fun aiExplanationIncludesWholeWindowDiagnosisAndCoaching() {
        val result = (RecommendationContract.parse(200, body("ai-window.json")) as Latest.Found).result
        val reply = GleoReply.fromState("EXPLAIN_RECOMMENDATION", ScreenState("https://test.local", Phase.SUCCESS, ReceivedResult(result, Instant.now())))
        assertEquals("success", reply.getString("status"))
        val data = JSONArray(reply.getString("data")).getJSONObject(0)
        assertEquals(result.ai!!.coaching, data.getString("coaching"))
        assertEquals(0, data.getInt("validLaps"))
        assertEquals(2.0, data.getDouble("analysisDurationSeconds"), 1e-9)
    }
    private fun body(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun result() = (RecommendationContract.parse(200, body("recommended.json")) as Latest.Found).result
    private fun fresh() = ScreenState("http://10.0.2.2:4181", Phase.SUCCESS, ReceivedResult(result(), Instant.now()))
    @Test fun eachActionReturnsDocumentedCallbackAndSameSession() {
        for (action in GleoReply.actions) {
            val reply = GleoReply.fromState(action, fresh())
            assertEquals(action, reply.getString("functionName")); assertEquals("success", reply.getString("status"))
            assertEquals("voice", reply.getString("mode"))
            val data = JSONArray(reply.getString("data")).getJSONObject(0)
            assertEquals(result().sessionId, data.getString("sessionId")); assertTrue(data.getBoolean("simulation"))
            assertEquals(action == "EXPLAIN_RECOMMENDATION", data.has("rationale"))
            assertEquals(action == "SHOW_SESSION_METRICS", data.has("metrics"))
        }
    }
    @Test fun timeoutFailureAndCachedResultsCannotBeReportedAsFresh() {
        for (state in listOf(null, fresh().copy(phase = Phase.FAILURE), fresh().copy(phase = Phase.CACHED)))
            assertEquals("fail", GleoReply.fromState("SHOW_LATEST_RECOMMENDATION", state).getString("status"))
    }
    @Test fun emptyAndHeldNeverInventASetup() {
        val empty = GleoReply.fromState("SHOW_LATEST_RECOMMENDATION", ScreenState("local", Phase.EMPTY))
        assertFalse(empty.has("data"))
        val held = (RecommendationContract.parse(200, body("held.json")) as Latest.Found).result
        val reply = GleoReply.fromState("EXPLAIN_RECOMMENDATION", ScreenState("local", Phase.HELD, ReceivedResult(held, Instant.now())))
        assertTrue(JSONArray(reply.getString("data")).getJSONObject(0).isNull("recommendedPreset"))
    }
    @Test fun actual3dContractIncludesRequiredMeasurements() {
        val original = JSONObject(body("recommended-3d.json"))
        val result = (RecommendationContract.parse(200, original.toString()) as Latest.Found).result
        assertEquals("nostalife-virtual-3d-01", result.vehicleId)
        assertNotNull(result.observed["lat_g_p95_abs"])
        assertNotNull(result.observed["front_suspension_diff_p95_mm"])
        original.getJSONObject("evidence").getJSONObject("observed_metrics").remove("lat_g_p95_abs")
        assertThrows(ContractException::class.java) { RecommendationContract.parse(200, original.toString()) }
    }
}
