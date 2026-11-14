package kr.nostalife.vehicleprobe

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler

/** AAOS axes: X right, Y forward, Z up. Only started on an automotive device. */
class SensorSource(context: Context, private val handler: Handler, private val capture: Capture) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private var closed = false
    fun start() {
        val linear = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val raw = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        capture.environment("sensorInventory", manager?.getSensorList(Sensor.TYPE_ALL)?.map {
            "${it.stringType} | ${it.name} | ${it.vendor} | minDelayUs=${it.minDelay}"
        }.orEmpty())
        listOf("lateral_g" to linear, "accelerometer_x_mps2" to raw).forEach { (key, sensor) ->
            if (sensor == null) capture.status(key, "sensor_not_exposed", "Android 센서 서비스에서 해당 센서를 찾지 못했습니다.")
            else try {
                capture.metadata(key, "sensorName", sensor.name)
                capture.metadata(key, "sensorVendor", sensor.vendor)
                capture.metadata(key, "requestedPeriodUs", 20_000)
                capture.metadata(key, "frame", "Android Automotive: X right, Y forward, Z up")
                capture.status(key, "waiting_for_sample", "센서 측정값을 기다립니다.")
                if (!manager!!.registerListener(this, sensor, 20_000, handler))
                    capture.status(key, "sensor_registration_failed", "센서 수신 등록이 거절됐습니다.")
            } catch (e: Exception) { capture.status(key, errorCode(e), describe(e)) }
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (closed || event.values.size < 3) return
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            val key = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) "lateral_g" else "accelerometer_x_mps2"
            capture.status(key, "sensor_unreliable", "센서가 신뢰할 수 없는 정확도 상태를 보고했습니다.")
            return
        }
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> capture.sample("lateral_g", lateralG(event.values[0]), event.timestamp)
            Sensor.TYPE_ACCELEROMETER -> capture.sample("accelerometer_x_mps2", event.values[0].toDouble(), event.timestamp)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
    fun stop() { closed = true; manager?.unregisterListener(this) }
}
