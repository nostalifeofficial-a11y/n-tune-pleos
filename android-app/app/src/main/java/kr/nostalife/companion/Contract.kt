package kr.nostalife.companion

import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

class ContractException(message: String = "서버 응답이 2단계 추천 계약과 다릅니다.") : Exception(message)
data class Preset(val id: String, val label: String, val gain: Double, val response: Double, val damping: Double)
data class Comparison(val key: String, val label: String, val unit: String, val baseline: Double?, val direction: String, val method: String)
data class AiAnalysis(val headline: String, val diagnosis: String, val coaching: String,
    val nextCheck: String, val presetReason: String, val model: String)
data class Recommendation(
    val id: String, val sessionId: String, val createdAt: Instant, val source: String,
    val analysisMode: String, val status: String, val vehicleId: String, val trackId: String,
    val current: Preset, val recommended: Preset?, val holdReason: String?,
    val rationale: String, val selectedLaps: List<Int>, val observed: Map<String, Double?>,
    val comparisons: List<Comparison>, val missingSignals: List<String>, val limitations: List<String>,
    val synthetic: Boolean, val rawJson: String,
    val capture: Map<String, Double?> = emptyMap(),
    val ai: AiAnalysis? = null
)
sealed interface Latest {
    data class Found(val result: Recommendation) : Latest
    data object Empty : Latest
}
private fun JSONObject.requiredString(key: String): String =
    (opt(key) as? String)?.takeIf { it.isNotBlank() } ?: throw ContractException()
private fun JSONObject.requiredBoolean(key: String): Boolean = opt(key) as? Boolean ?: throw ContractException()
private fun JSONObject.number(key: String, nullable: Boolean = false): Double? {
    if (!has(key)) throw ContractException()
    if (isNull(key) && nullable) return null
    return (get(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 } ?: throw ContractException()
}
private fun JSONObject.textList(key: String): List<String> {
    val list = getJSONArray(key)
    return (0 until list.length()).map { (list.get(it) as? String) ?: throw ContractException() }
}
object RecommendationContract {
    private val presets = mapOf(
        "stable" to Preset("stable", "안정형", .92, 3.8, 5.8),
        "balanced" to Preset("balanced", "균형형", 1.08, 6.0, 4.4),
        "rotation" to Preset("rotation", "회전형", 1.25, 8.5, 3.4)
    )
    private fun preset(obj: JSONObject): Preset {
        val expected = presets[obj.requiredString("id")] ?: throw ContractException()
        if (obj.requiredString("label") != expected.label ||
            abs(obj.number("steering_gain")!! - expected.gain) > 1e-9 ||
            abs(obj.number("steering_response_hz")!! - expected.response) > 1e-9 ||
            abs(obj.number("yaw_damping_hz")!! - expected.damping) > 1e-9) throw ContractException()
        return expected
    }
    fun parse(httpStatus: Int, body: String): Latest {
        try {
            val root = JSONObject(body)
            val version = root.requiredString("schema_version")
            if (version !in setOf("1.0.0", "2.0.0")) throw ContractException("지원하지 않는 분석 버전입니다.")
            val externalAi = version == "2.0.0"
            if (httpStatus == 404 && root.optString("status") == "empty" &&
                root.has("recommendation") && root.isNull("recommendation")) return Latest.Empty
            if (httpStatus != 200) throw ContractException()
            if (!root.requiredBoolean("simulation") ||
                root.requiredString("source") !in setOf("simulation_manual", "simulation_autoplay"))
                throw ContractException("시뮬레이션 출처의 결과만 표시할 수 있습니다.")
            val policy = root.getJSONObject("policy")
            if (!policy.requiredBoolean("simulation_only") || policy.requiredBoolean("external_ai") != externalAi ||
                policy.requiredBoolean("training_collection")) throw ContractException()
            if (policy.requiredBoolean("production_upload") &&
                (policy.opt("raw_log_stored") != false || policy.optInt("retention_days") != 7 ||
                    root.optJSONObject("evidence")?.optString("execution_location") != "hosted_worker")) throw ContractException()
            val vehicle = root.requiredString("vehicle_id")
            val track = root.requiredString("track_id")
            if ((vehicle to track) !in setOf("nostalife-virtual-01" to "nostalife-loop-v1",
                    "nostalife-virtual-3d-01" to "nostalife-circuit-3d-v1")) throw ContractException()
            if (externalAi && (vehicle != "nostalife-virtual-3d-01" || !policy.requiredBoolean("production_upload"))) throw ContractException()
            val id = root.requiredString("recommendation_id")
            UUID.fromString(id)
            val session = root.requiredString("session_id")
            if (session.length !in 8..128) throw ContractException()
            val created = root.requiredString("created_at")
            if (!created.endsWith("Z")) throw ContractException()
            val status = root.requiredString("status")
            val mode = root.requiredString("analysis_mode")
            if (externalAi) {
                if (status != "analyzed" || mode != "external_ai") throw ContractException()
            } else if (status !in setOf("recommended", "held") || mode !in setOf("local_model", "rule_based")) throw ContractException()
            val current = preset(root.getJSONObject("current_setup"))
            if (!root.has("recommended_setup")) throw ContractException()
            val next = if (root.isNull("recommended_setup")) null else preset(root.getJSONObject("recommended_setup"))
            val held = if (status == "held") {
                if (!root.has("recommended_setup") || next != null) throw ContractException()
                val reason = root.getJSONObject("hold_reason")
                if (reason.requiredString("code") != "INSUFFICIENT_VALID_LAPS" || reason.number("required_valid_laps") != 3.0 ||
                    reason.number("available_valid_laps")!! !in 0.0..2.0) throw ContractException()
                reason.requiredString("message")
            } else {
                if ((!externalAi && next == null) || !root.has("hold_reason") || !root.isNull("hold_reason")) throw ContractException()
                null
            }
            val evidence = root.getJSONObject("evidence")
            val lapsArray = evidence.getJSONArray("selected_laps")
            val laps = (0 until lapsArray.length()).map {
                val value = lapsArray.get(it) as? Number ?: throw ContractException()
                if (value.toDouble() != value.toInt().toDouble() || value.toInt() < 1) throw ContractException()
                value.toInt()
            }
            if (laps != laps.distinct().sorted() || (!externalAi && laps.size > 4) ||
                (status == "recommended" && laps.size < 3) || (status == "held" && laps.size > 2)) throw ContractException()
            val metrics = evidence.getJSONObject("observed_metrics")
            val keys = listOf("lap_time_median_s", "lap_time_std_s", "steering_rate_p95_per_s",
                "brake_release_p95_per_s", "steering_total_variation_per_s", "steering_corrections_per_min",
                "speed_mean_kmh", "selected_lap_count", "valid_laps_available", "excluded_lap_count")
            val extraKeys = if (vehicle == "nostalife-virtual-3d-01") listOf("lat_g_p95_abs", "long_g_p95_abs",
                "throttle_mean_pct", "brake_mean_pct", "front_suspension_diff_p95_mm", "rear_suspension_diff_p95_mm") else emptyList()
            // Older results may omit these measurements; preserve missing values as null.
            val displayKeys = listOf("distance_m", "speed_max_kmh", "front_slip_p95_abs", "rear_slip_p95_abs")
            val observed = (keys + extraKeys).associateWith { metrics.number(it, nullable = true) } +
                displayKeys.associateWith { if (metrics.has(it)) metrics.number(it, nullable = true) else null }
            if (observed["selected_lap_count"] != laps.size.toDouble()) throw ContractException()
            if ((observed["valid_laps_available"] ?: -1.0) < laps.size) throw ContractException()
            val capture = root.optJSONObject("capture_summary")?.let { summary ->
                listOf("samples", "duration_s", "lat_g_peak_abs", "long_g_peak_abs", "throttle_mean_pct", "brake_mean_pct", "speed_max_kmh").associateWith { key ->
                    (summary.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 }
                }
            } ?: emptyMap()
            val ai = if (externalAi) {
                val window = root.getJSONObject("analysis_window")
                val duration = window.number("duration_s")!!
                val samples = window.number("sample_count")!!
                if (window.requiredString("scope") != "start_to_analyze" || window.number("required_valid_laps") != 0.0 ||
                    !window.requiredBoolean("includes_incomplete_and_invalid_laps") || duration <= 0 || duration > 900 ||
                    samples < 2 || samples > 18002 || samples != samples.toInt().toDouble() ||
                    window.number("start_t_ms") != 0.0 || abs(window.number("end_t_ms")!! / 1000 - duration) > 1e-6 ||
                    capture["samples"] != samples || capture["duration_s"] != duration ||
                    metrics.number("selected_sample_count") != samples || metrics.number("selected_duration_s") != duration ||
                    metrics.number("excluded_lap_count") != 0.0) throw ContractException()
                val meta = root.getJSONObject("ai")
                if (meta.requiredString("provider") != "openai") throw ContractException()
                val analysis = root.getJSONObject("analysis")
                val recommendedId = if (analysis.has("recommended_preset") && analysis.isNull("recommended_preset")) null else analysis.requiredString("recommended_preset")
                if (recommendedId != next?.id) throw ContractException()
                fun text(key: String) = analysis.requiredString(key).also { if (it.length > 4000) throw ContractException() }
                val limits = analysis.textList("limitations")
                if (limits.size > 12 || limits.any { it.length > 1000 } || limits != evidence.textList("limitations")) throw ContractException()
                AiAnalysis(text("headline"), text("diagnosis"), text("driver_coaching"), text("next_check"), text("preset_reason"), meta.requiredString("model"))
            } else null
            val comparisonsArray = root.getJSONArray("comparison_metrics")
            val comparisonKeys = setOf("lap_time_median_s", "lap_time_std_s", "steering_total_variation_per_s", "brake_release_p95_per_s")
            val comparisons = (0 until comparisonsArray.length()).map {
                val row = comparisonsArray.getJSONObject(it)
                val key = row.requiredString("key")
                val unit = row.requiredString("unit")
                val direction = row.requiredString("desired_direction")
                if (key !in comparisonKeys || unit != (if (key.startsWith("lap_")) "s" else "1/s") ||
                    direction !in setOf("lower", "observe")) throw ContractException()
                val baseline = row.number("baseline", nullable = true)
                if (baseline != observed[key]) throw ContractException()
                Comparison(key, row.requiredString("label"), unit, baseline, direction, row.requiredString("method"))
            }
            if (comparisons.size != 4 || comparisons.map { it.key }.toSet() != comparisonKeys) throw ContractException()
            val model = evidence.optJSONObject("model")
            val synthetic = model?.opt("synthetic") == true
            if (mode == "local_model" && status == "recommended" &&
                (!synthetic || model?.opt("inference_executed") != true)) throw ContractException()
            return Latest.Found(Recommendation(id, session, Instant.parse(created), root.requiredString("source"), mode, status,
                vehicle, track, current, next, held, evidence.requiredString("rationale"), laps, observed, comparisons,
                evidence.textList("missing_signals"), evidence.textList("limitations"), synthetic, body,
                capture, ai))
        } catch (error: ContractException) { throw error }
        catch (_: Exception) { throw ContractException() }
    }
}
