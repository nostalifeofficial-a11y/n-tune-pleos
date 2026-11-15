package kr.nostalife.vehicleprobe

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.time.Instant

private object ProbeWorker {
    val handler = Handler(HandlerThread("NostalifeVehicleProbe").apply { start() }.looper)
}

class ProbeActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ProbeWorker.handler
    private lateinit var output: TextView
    private lateinit var capture: Capture
    private var car: CarSource? = null
    private var sensors: SensorSource? = null
    private var pleos: PleosSource? = null
    private var running = false
    private var exportText = ""
    private val keys = listOf("accelerator_pedal_pct", "brake_pedal_pct", "suspension_fl_mm",
        "suspension_fr_mm", "suspension_rl_mm", "suspension_rr_mm", "lateral_g", "accelerometer_x_mps2", "pleos_brake_pressed")
    private val refresh = object : Runnable {
        override fun run() {
            if (!isFinishing) output.text = capture.text()
            if (running) main.postDelayed(this, 500)
        }
    }
    private val autosave = object : Runnable {
        override fun run() {
            if (!running) return
            saveInternal(capture)
            main.postDelayed(this, 5000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 24) }
        root.addView(TextView(this).apply { text = "Nostalife · 차량 데이터 확인"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "페달 · 바퀴별 변위 · 횡G\n측정값과 읽기 실패 이유를 기록합니다. 단위와 데이터 출처는 저장한 JSON에서 확인할 수 있습니다."
            textSize = 16f
        })
        root.addView(Button(this).apply { text = "권한 허용 후 다시 읽기"; setOnClickListener { askPermissions() } })
        root.addView(Button(this).apply { text = "수집 중지"; setOnClickListener { stopCapture() } })
        root.addView(Button(this).apply { text = "측정 기록 JSON 저장"; setOnClickListener { export() } })
        output = TextView(this).apply { textSize = 16f; setTextIsSelectable(true) }
        root.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        begin()
    }
    private fun newCapture(): Capture {
        val store = Capture(SystemClock::elapsedRealtimeNanos)
        store.environment("targetVehicle", "Pleos development environment; actual trim/firmware unverified")
        store.define("speed_mps", "차량 속도", "m/s", "AAOS PERF_VEHICLE_SPEED")
        store.environment("startedAtUtc", Instant.now().toString())
        store.environment("deviceModel", Build.MODEL)
        store.environment("deviceManufacturer", Build.MANUFACTURER)
        store.environment("androidApi", Build.VERSION.SDK_INT)
        store.environment("buildFingerprint", Build.FINGERPRINT)
        store.environment("automotive", packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
        store.environment("pleosVehicleSdk", "2.3.2")
        store.environment("validation", "API probe; not proof of production vehicle compatibility")
        store.define(keys[0], "가속 페달", "%", "AAOS CarPropertyManager")
        store.define(keys[1], "브레이크 페달", "%", "AAOS CarPropertyManager")
        listOf("fl" to "앞 왼쪽", "fr" to "앞 오른쪽", "rl" to "뒤 왼쪽", "rr" to "뒤 오른쪽").forEach { (wheel, label) ->
            store.define("suspension_${wheel}_mm", "서스펜션 $label", "mm", "AAOS VEHICLE_PASSIVE_SUSPENSION_HEIGHT")
            store.metadata("suspension_${wheel}_mm", "definition", "neutral-relative displacement; negative compression; not calibrated damper stroke")
        }
        store.define("lateral_g", "횡G · 선형가속도 센서", "g", "AAOS SensorManager TYPE_LINEAR_ACCELERATION X / 9.80665")
        store.define("accelerometer_x_mps2", "가속도 원신호 X", "m/s²", "AAOS TYPE_ACCELEROMETER; includes gravity, not substituted for linear acceleration")
        store.define("pleos_brake_pressed", "Pleos 브레이크 작동", "0/1", "Pleos Vehicle SDK Boolean; not pedal percentage")
        return store
    }
    private fun begin() {
        if (running) stopCapture()
        capture = newCapture()
        val session = capture
        running = true
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            keys.forEach { session.status(it, "not_automotive_device", "차량용 Android 환경이 아닙니다. 휴대폰 센서값을 차량값으로 사용하지 않습니다.") }
            running = false
            saveInternal(session)
        } else worker.post {
            try {
                car = CarSource(applicationContext, worker, session).also { it.start() }
                sensors = SensorSource(applicationContext, worker, session).also { it.start() }
                pleos = PleosSource(applicationContext, worker, session).also { it.start() }
            } catch (e: LinkageError) {
                keys.forEach { session.status(it, "automotive_runtime_missing", describe(e)) }
            }
        }
        main.removeCallbacks(refresh)
        main.post(refresh)
        main.removeCallbacks(autosave)
        if (running) main.postDelayed(autosave, 9000)
    }
    private fun askPermissions() {
        val requested = listOf("android.car.permission.CAR_SPEED", "android.car.permission.READ_CAR_PEDALS", "pleos.car.permission.CAR_DRIVE").filter { name ->
            try {
                val info = packageManager.getPermissionInfo(name, 0)
                info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS &&
                    checkSelfPermission(name) != PackageManager.PERMISSION_GRANTED
            } catch (_: PackageManager.NameNotFoundException) { false }
        }
        if (requested.isEmpty()) begin() else requestPermissions(requested.toTypedArray(), 1)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) begin()
    }
    private fun stopCapture() {
        if (!::capture.isInitialized) return
        running = false
        main.removeCallbacks(refresh)
        main.removeCallbacks(autosave)
        val session = capture
        worker.post {
            car?.stop(); sensors?.stop(); pleos?.stop()
            car = null; sensors = null; pleos = null
            session.stopped()
            saveInternal(session)
            main.post { if (!isFinishing && ::output.isInitialized) output.text = session.text() }
        }
    }
    private fun saveInternal(session: Capture) {
        // App-private evidence; no network transmission or VIN collection.
        worker.post { File(filesDir, "vehicle-probe-latest.json").writeText(session.snapshot().toString(2)) }
    }
    private fun export() {
        exportText = capture.snapshot().toString(2)
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "nostalife-vehicle-probe.json")
        }, 2)
    }
    @Deprecated("Legacy Activity document result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2 && resultCode == RESULT_OK && data?.data != null) {
            try {
                contentResolver.openOutputStream(data.data!!)?.bufferedWriter()?.use { it.write(exportText) }
                Toast.makeText(this, "측정 기록을 저장했습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }
    override fun onStop() { stopCapture(); super.onStop() }
}
