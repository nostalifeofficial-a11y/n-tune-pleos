package kr.nostalife.vehicleprobe

import org.json.JSONArray
import org.json.JSONObject

data class Reading(val value: Double, val timestampNs: Long, val receivedNs: Long)
data class Channel(
    val key: String, val label: String, val unit: String, val source: String,
    var status: String = "not_started", var reason: String = "아직 확인하지 않았습니다.",
    var latest: Reading? = null, var count: Int = 0, var firstNs: Long? = null,
    val samples: ArrayDeque<Reading> = ArrayDeque(),
    val metadata: MutableMap<String, Any> = linkedMapOf()
)

/** Records evidence, never substitutes zero for a failed/missing measurement. */
class Capture(private val nowNs: () -> Long, private val maxSamples: Int = 6000) {
    private val channels = linkedMapOf<String, Channel>()
    private val environment = linkedMapOf<String, Any>()

    @Synchronized fun environment(key: String, value: Any) { environment[key] = value }
    @Synchronized fun define(key: String, label: String, unit: String, source: String) {
        channels.putIfAbsent(key, Channel(key, label, unit, source))
    }
    @Synchronized fun metadata(key: String, name: String, value: Any) {
        channels.getValue(key).metadata[name] = value
    }
    @Synchronized fun status(key: String, code: String, reason: String) {
        channels.getValue(key).apply { status = code; this.reason = reason }
    }
    @Synchronized fun stopped() {
        channels.values.filter { it.status == "received" || it.status == "waiting_for_sample" || it.status == "connecting" }
            .forEach { it.status = "stopped"; it.reason = "수집을 중지했습니다. 이전 표본은 JSON에 보존됩니다." }
    }
    @Synchronized fun sample(key: String, value: Double?, timestampNs: Long) {
        val row = channels.getValue(key)
        if (value == null || !value.isFinite() || timestampNs <= 0 || timestampNs > nowNs() + 1_000_000_000L) {
            status(key, "invalid_sample", "숫자 또는 측정 시각이 유효하지 않습니다.")
            return
        }
        val previous = row.latest
        if (previous != null && timestampNs <= previous.timestampNs) return
        val reading = Reading(value, timestampNs, nowNs())
        row.latest = reading
        if (row.firstNs == null) row.firstNs = timestampNs
        row.count++
        row.status = "received"
        row.reason = "데이터를 수신했습니다."
        row.samples.addLast(reading)
        while (row.samples.size > maxSamples) row.samples.removeFirst()
    }
    @Synchronized fun snapshot(): JSONObject {
        val now = nowNs()
        return JSONObject().put("schema", "nostalife.vehicle-probe.v1")
            .put("environment", JSONObject(environment as Map<*, *>))
            .put("exportElapsedRealtimeNs", now)
            .put("channels", JSONArray(channels.values.map { row ->
                val last = row.latest
                val stale = last != null && now - last.timestampNs > 2_000_000_000L
                val usable = row.status == "received" && !stale
                val interval = if (last != null && row.count > 1) last.timestampNs - row.firstNs!! else 0L
                JSONObject().put("key", row.key).put("label", row.label).put("unit", row.unit)
                    .put("source", row.source)
                    .put("status", if (stale && row.status == "received") "stale" else row.status)
                    .put("reason", if (stale && row.status == "received") "마지막 측정 후 2초 이상 지났습니다." else row.reason)
                    .put("currentValue", if (usable) last!!.value else JSONObject.NULL)
                    .put("lastValue", last?.value ?: JSONObject.NULL)
                    .put("sampleCount", row.count).put("retainedSampleCount", row.samples.size)
                    .put("ageMs", if (last == null) JSONObject.NULL else (now - last.timestampNs) / 1_000_000.0)
                    .put("observedHz", if (interval > 0) (row.count - 1) * 1e9 / interval else JSONObject.NULL)
                    .put("metadata", JSONObject(row.metadata as Map<*, *>))
                    .put("samples", JSONArray(row.samples.map { sample ->
                        JSONObject().put("value", sample.value).put("timestampNs", sample.timestampNs)
                            .put("receivedElapsedRealtimeNs", sample.receivedNs)
                    }))
            }))
    }
    @Synchronized fun text(): String {
        val now = nowNs()
        return channels.values.joinToString("\n\n") { row ->
            val last = row.latest
            val stale = last != null && now - last.timestampNs > 2_000_000_000L
            val value = if (row.status == "received" && !stale && last != null) "%.3f".format(last.value) else "—"
            val state = if (row.status == "received" && stale) "stale" else row.status
            val reason = if (row.status == "received" && stale) "마지막 측정 후 2초 이상 지났습니다." else row.reason
            "${row.label}: $value ${row.unit}\n$state · $reason"
        }
    }
}

internal fun pedalPercentage(raw: Any?): Double? =
    (raw as? Float)?.toDouble()?.takeIf { it.isFinite() && it in 0.0..100.0 }

internal fun lateralG(xMetersPerSecondSquared: Float): Double? =
    xMetersPerSecondSquared.toDouble().takeIf { it.isFinite() }?.div(9.80665)

internal val wheelNames = mapOf(1 to "fl", 2 to "fr", 4 to "rl", 8 to "rr")
