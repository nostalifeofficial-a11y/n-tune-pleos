package kr.nostalife.companion

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.time.Instant

class LocalResultCache(context: Context) : ResultCache {
    private val file = AtomicFile(File(context.filesDir, "latest-simulation.json"))
    override fun load(baseUrl: String): ReceivedResult? {
        if (!file.baseFile.exists() || file.baseFile.length() > 1_048_576) return null
        val body = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        if (body.getString("base_url") != baseUrl) return null
        val parsed = RecommendationContract.parse(200, body.getJSONObject("result").toString()) as Latest.Found
        return ReceivedResult(parsed.result, Instant.parse(body.getString("received_at")))
    }
    override fun save(baseUrl: String, result: ReceivedResult) {
        val body = JSONObject().put("base_url", baseUrl).put("received_at", result.receivedAt.toString())
            .put("result", JSONObject(result.result.rawJson)).toString()
        val stream = file.startWrite()
        try { stream.write(body.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    override fun clear() = file.delete()
}
