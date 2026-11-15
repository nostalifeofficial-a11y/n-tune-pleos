package kr.nostalife.companion

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.nostalife.vehicleprobe.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

data class DrivePreference(val vehicle: String = "아반떼 N", val goal: String = "출퇴근길을 편안하게 달리고 싶어요.",
    val comfort: Float = 65f, val steering: Float = 45f, val regen: Float = 35f)

class ContestModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("contest-drive", 0)
    val preference = MutableStateFlow(DrivePreference(prefs.getString("vehicle", "아반떼 N")!!,
        prefs.getString("goal", "출퇴근길을 편안하게 달리고 싶어요.")!!,
        driveStep(prefs.getFloat("comfort", 65f)), driveStep(prefs.getFloat("steering", 45f)), prefs.getFloat("regen", 35f)))
    val source = MutableStateFlow(if (application.getSharedPreferences("connection", 0).getString("base_url", "")!!.contains("/api/channels/")) "web" else "sim")
    private val mutableLive = MutableStateFlow(JSONObject())
    val live = mutableLive.asStateFlow()
    val history = MutableStateFlow(loadHistory())
    val preferenceSync = MutableStateFlow("주행 창에 목표를 연결합니다.")
    val customRevision = MutableStateFlow(0)
    val goalAdvice = MutableStateFlow(loadGoalAdvice())
    val adviceBusy = MutableStateFlow(false)
    val adviceMessage = MutableStateFlow("")
    private var lastPreferenceSync = 0L
    private val thread = HandlerThread("ContestVehicle").apply { start() }
    private val handler = Handler(thread.looper)
    private val capture = Capture(SystemClock::elapsedRealtimeNanos, 100)
    private var carSource: CarSource? = null
    private var pleosSource: PleosSource? = null
    private var sensorSource: SensorSource? = null
    private var polling = false
    init {
        capture.environment("pleosVehicleSdk", "2.3.2")
        capture.environment("scope", "development emulator; production car unverified")
        capture.define("speed_mps", "차량 속도", "m/s", "AAOS PERF_VEHICLE_SPEED")
        capture.define("accelerator_pedal_pct", "가속 페달", "%", "AAOS CarPropertyManager")
        capture.define("brake_pedal_pct", "브레이크 페달", "%", "AAOS CarPropertyManager")
        listOf("fl", "fr", "rl", "rr").forEach { capture.define("suspension_${it}_mm", "서스펜션 $it", "mm", "AAOS") }
        capture.define("lateral_g", "횡G", "g", "AAOS linear acceleration X")
        capture.define("accelerometer_x_mps2", "가속도 원신호", "m/s²", "AAOS accelerometer")
        capture.define("pleos_brake_pressed", "브레이크 작동", "0/1", "Pleos Vehicle SDK Boolean")
        handler.post {
            carSource = CarSource(application, handler, capture).also { it.start() }
            pleosSource = PleosSource(application, handler, capture).also { it.start() }
            sensorSource = SensorSource(application, handler, capture).also { it.start() }
        }
    }
    fun savePreference(value: DrivePreference) {
        val normalized = value.copy(goal = value.goal.take(500), comfort = driveStep(value.comfort), steering = driveStep(value.steering))
        if (normalized.goal != preference.value.goal) adviceMessage.value = ""
        preference.value = normalized
        prefs.edit().putString("goal", normalized.goal)
            .putFloat("comfort", normalized.comfort).putFloat("steering", normalized.steering).apply()
        lastPreferenceSync = 0
        syncPreference()
    }
    fun recommend(base: Recommendation?, value: DrivePreference, address: String) {
        if (adviceBusy.value) return
        if (base?.ai == null) { adviceMessage.value = "먼저 주행 기록을 연결해 주세요."; return }
        if (value.goal.isBlank()) { adviceMessage.value = "주행 목표를 하나 이상 골라 주세요."; return }
        savePreference(value)
        val selected = preference.value
        adviceBusy.value = true; adviceMessage.value = "AI가 주행 기록과 선택한 목표를 함께 살펴보고 있습니다."
        viewModelScope.launch {
            try {
                val answer = withContext(Dispatchers.IO) { GoalAdviceClient.request(base, selected, address) }
                if (preference.value.goal != selected.goal) {
                    adviceMessage.value = "목표가 바뀌었습니다. 새 목표로 다시 추천받아 주세요."
                } else {
                    goalAdvice.value = answer
                    prefs.edit().putString("goal-advice", answer.result.rawJson).apply()
                    storeHistory(answer.result)
                    adviceMessage.value = "AI 추천 완료 · 선택한 목표를 반영했습니다."
                }
            } catch (e: Exception) {
                adviceMessage.value = if (e is IllegalStateException || e is IllegalArgumentException) e.message.orEmpty()
                    else "AI에 연결하지 못했습니다. 주행 연결을 확인한 뒤 다시 눌러 주세요."
            } finally { adviceBusy.value = false }
        }
    }
    private fun loadGoalAdvice(): GoalAdvice? = runCatching {
        val body = prefs.getString("goal-advice", null) ?: return null
        val raw = JSONObject(body)
        GoalAdviceProtocol.parse(body, raw.getString("origin_recommendation_id"), raw.getJSONObject("driving_preference").getString("goal"))
    }.getOrNull()
    fun customValue(car: String, variant: String, slot: Int, option: NOption): String =
        prefs.getString(NCustom.storageKey(car, variant, slot, option.key), null)?.takeIf { it in option.choices } ?: NCustom.default(option)
    fun saveCustom(car: String, variant: String, slot: Int, option: NOption, value: String) {
        require(value in option.choices)
        prefs.edit().putString(NCustom.storageKey(car, variant, slot, option.key), value).apply()
        customRevision.value++
    }
    fun resetCustom(car: String, variant: String, slot: Int) {
        NCustom.options(car, variant).forEach { saveCustom(car, variant, slot, it, NCustom.default(it)) }
    }
    private fun syncPreference() {
        val now = SystemClock.elapsedRealtime()
        if (lastPreferenceSync != 0L && now - lastPreferenceSync < 5000) return
        lastPreferenceSync = now
        val current = preference.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { request("/local/preference", JSONObject().put("goal", current.goal)
                .put("comfort", driveStep(current.comfort).toInt()).put("steering_response", driveStep(current.steering).toInt())) }
                .onSuccess { preferenceSync.value = "PC 주행 창에 목표 연결됨 · 다음 분석에 사용" }
                .onFailure { preferenceSync.value = "PC 연결 대기 · 목표는 앱에 저장됨" }
        }
    }
    fun selectSource(value: String) { if (value in setOf("sim", "acc", "pleos", "web")) { source.value = value; mutableLive.value = JSONObject() } }
    fun poll() {
        syncPreference()
        if (polling) return
        polling = true
        val selected = source.value
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (selected == "pleos") {
                        val snapshot = capture.snapshot()
                        if (BuildConfig.DEBUG) File(getApplication<Application>().filesDir, "contest-api-evidence.json").writeText(snapshot.toString(2))
                        val channels = snapshot.getJSONArray("channels")
                        val rows = (0 until channels.length()).map { channels.getJSONObject(it) }.associateBy { it.getString("key") }
                        fun reading(key: String): Double? = rows[key]?.opt("currentValue")?.let { (it as? Number)?.toDouble() }
                        val values = JSONObject().put("speed_kmh", reading("speed_mps")?.let { kotlin.math.abs(it) * 3.6 } ?: JSONObject.NULL)
                            .put("throttle_pct", reading("accelerator_pedal_pct") ?: JSONObject.NULL)
                            .put("brake_pct", reading("brake_pedal_pct") ?: JSONObject.NULL)
                            .put("lat_g", reading("lateral_g") ?: JSONObject.NULL)
                            .put("brake_pressed", reading("pleos_brake_pressed") ?: JSONObject.NULL)
                        JSONObject().put("source", "pleos").put("fresh", rows.values.any { it.optString("status") == "received" })
                            .put("values", values).put("message", "Pleos Vehicle SDK · Android Car API")
                            .put("detail", rows.values.joinToString("\n") { "${it.getString("label")}: ${it.getString("status")}" })
                    } else if (selected == "web") JSONObject().put("message", "홈페이지의 분석 완료 결과를 확인하세요.") else request("/local/live?source=$selected")
                }
                if (source.value == selected) mutableLive.value = result
            } catch (_: Exception) {
                if (source.value == selected) mutableLive.value = JSONObject().put("fresh", false)
                    .put("message", "PC 실행기에서 로컬 연결을 시작해 주세요.")
            } finally { polling = false }
        }
    }
    fun remember(result: Recommendation) {
        if (result.ai == null) return
        storeHistory(result)
        viewModelScope.launch(Dispatchers.IO) { runCatching { request("/local/received", JSONObject().put("id", result.id)) } }
    }
    private fun storeHistory(result: Recommendation) {
        if (history.value.none { it.id == result.id }) {
            history.value = (listOf(result) + history.value).take(20)
            prefs.edit().putString("history", JSONArray(history.value.map { JSONObject(it.rawJson) }).toString()).apply()
        }
    }
    private fun loadHistory(): List<Recommendation> = runCatching {
        val array = JSONArray(prefs.getString("history", "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching { (RecommendationContract.parse(200, array.getJSONObject(index).toString()) as? Latest.Found)?.result }.getOrNull()
        }.take(20)
    }.getOrDefault(emptyList())
    private fun request(path: String, body: JSONObject? = null): JSONObject {
        // Development bridge is fixed loopback, reached using adb reverse. Never arbitrary URLs.
        val connection = URL("http://127.0.0.1:4182$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 1800; connection.readTimeout = 1800; connection.instanceFollowRedirects = false
            connection.setRequestProperty("X-Nostalife-App", "pleos")
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            check(connection.responseCode == 200)
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    override fun onCleared() {
        handler.post { carSource?.stop(); sensorSource?.stop(); pleosSource?.stop() }
        // Pleos async callbacks own their release; keep looper alive long enough to finish them.
        handler.postDelayed({ thread.quitSafely() }, 15000)
        super.onCleared()
    }
}
