package kr.nostalife.companion

import org.json.JSONArray
import org.json.JSONObject

object GleoReply {
    val actions = setOf("SHOW_LATEST_RECOMMENDATION", "EXPLAIN_RECOMMENDATION", "SHOW_SESSION_METRICS")
    fun fromState(action: String, state: ScreenState?): JSONObject {
        require(action in actions)
        val response = JSONObject().put("functionName", action).put("mode", "voice")
        if (state == null || state.phase == Phase.FAILURE) return response.put("status", "fail")
            .put("message", "The local analysis server could not be reached in time. No fresh result is available.")
        if (state.phase == Phase.EMPTY) return response.put("status", "success")
            .put("message", "아직 분석이 없습니다. 웹에서 주행한 뒤 분석하고 앱으로 보내기를 눌러 주세요. 완주 조건은 없습니다.")
        val r = state.received?.result ?: return response.put("status", "fail").put("message", "There is no current analysis result.")
        if (state.stale) return response.put("status", "fail").put("message", "Only a cached analysis is available; its freshness is unverified.")
        val data = JSONObject().put("simulation", true).put("status", r.status).put("sessionId", r.sessionId)
            .put("analysisMode", r.analysisMode).put("modelIsSynthetic", r.synthetic).put("validLaps", r.selectedLaps.size)
            .put("currentPreset", r.current.label).put("recommendedPreset", r.recommended?.label ?: JSONObject.NULL)
        if (action == "EXPLAIN_RECOMMENDATION") data.put("rationale", r.rationale)
        r.ai?.let { ai ->
            data.put("headline", ai.headline).put("analysisDurationSeconds", r.capture["duration_s"])
            if (action == "EXPLAIN_RECOMMENDATION") data.put("coaching", ai.coaching).put("nextCheck", ai.nextCheck)
        }
        if (action == "SHOW_SESSION_METRICS") data.put("metrics", JSONObject(r.observed as Map<*, *>))
        return response.put("status", "success").put("data", JSONArray().put(data).toString())
            .put("message", if (r.ai != null) r.ai.headline + ". " + r.ai.diagnosis
            else if (r.status == "held") "이전 방식으로 분석한 기록입니다. 웹에서 새 분석을 실행해 주세요."
            else "A local simulation analysis is available. This is a candidate for comparison in the simulator, not a validated real-vehicle setup.")
    }
}
