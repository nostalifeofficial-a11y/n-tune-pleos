package kr.nostalife.companion

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class GoalAdvice(val originId: String, val goal: String, val result: Recommendation) {
    fun matches(base: Recommendation?, selectedGoal: String): Boolean = base?.id == originId && selectedGoal == goal
}
object GoalAdviceProtocol {
    fun parse(body: String, originId: String, goal: String): GoalAdvice {
        val raw = JSONObject(body)
        require(raw.getString("origin_recommendation_id") == originId) { "주행 기록이 다른 추천입니다." }
        require(raw.getJSONObject("driving_preference").getString("goal") == goal) { "선택한 목표가 다른 추천입니다." }
        val result = (RecommendationContract.parse(200, body) as Latest.Found).result
        require(result.id != originId && result.ai?.targets != null && result.ai.brief != null) { "AI 추천값이 완성되지 않았습니다." }
        return GoalAdvice(originId, goal, result)
    }
}
object GoalAdviceClient {
    fun request(base: Recommendation, preference: DrivePreference, address: String): GoalAdvice {
        val deliveryCode = JSONObject(base.rawJson).optJSONObject("delivery")?.optString("code").orEmpty()
        val code = if (address.startsWith(Connection.PUBLIC_ORIGIN + "/api/channels/")) address.substringAfterLast('/') else deliveryCode
        val url = Connection.publicAddress(code) + "/advice"
        val data = JSONObject().put("recommendation_id", base.id).put("driving_preference", JSONObject()
            .put("goal", preference.goal).put("comfort", driveStep(preference.comfort).toInt())
            .put("steering_response", driveStep(preference.steering).toInt()))
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000; connection.readTimeout = 100000; connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "Nostalife-Pleos/5.3")
            connection.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status != 200) throw IllegalStateException(when(status) {
                404 -> "연결한 주행 기록 또는 추천 기능을 찾지 못했습니다. 주행 연결을 확인해 주세요."
                409 -> "최신 주행 기록을 받은 뒤 다시 추천받아 주세요."
                429 -> "추천 요청이 많습니다. 잠시 후 다시 시도해 주세요."
                else -> "AI 추천을 완료하지 못했습니다. 잠시 후 다시 눌러 주세요."
            })
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    require(output.size() + n <= 512 * 1024) { "AI 응답이 너무 큽니다." }
                    output.write(buffer, 0, n)
                }
            }
            return GoalAdviceProtocol.parse(output.toString("UTF-8"), base.id, preference.goal)
        } finally { connection.disconnect() }
    }
}
