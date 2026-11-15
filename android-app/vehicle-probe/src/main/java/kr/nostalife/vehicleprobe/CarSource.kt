package kr.nostalife.vehicleprobe

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler

/** Calls public android.car APIs only; never writes a vehicle property. */
@Suppress("DEPRECATION")
class CarSource(private val context: Context, private val handler: Handler, private val capture: Capture) {
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var closed = false
    private var ready = false
    private val subscribed = mutableSetOf<Int>()
    private val properties = listOf(
        Property("speed_mps", "PERF_VEHICLE_SPEED", VehiclePropertyIds.PERF_VEHICLE_SPEED,
            "android.car.permission.CAR_SPEED"),
        Property("accelerator_pedal_pct", "ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE", 291504911,
            "android.car.permission.READ_CAR_PEDALS"),
        Property("brake_pedal_pct", "BRAKE_PEDAL_COMPRESSION_PERCENTAGE", 291504912,
            "android.car.permission.READ_CAR_PEDALS"),
        Property("suspension", "VEHICLE_PASSIVE_SUSPENSION_HEIGHT", 390071059,
            "android.car.permission.CAR_DYNAMICS_STATE")
    )
    private data class Property(val key: String, val name: String, val id: Int, val permission: String) {
        val keys: List<String> get() = if (key == "suspension") wheelNames.values.map { "suspension_${it}_mm" } else listOf(key)
    }
    private fun fail(property: Property, code: String, message: String) {
        property.keys.forEach { capture.status(it, code, message) }
    }
    private fun failAll(code: String, message: String) = properties.forEach { fail(it, code, message) }

    fun start() {
        if (closed) return
        failAll("connecting", "차량 속성 서비스 연결 중입니다.")
        try {
            car = Car.createCar(context, handler, 0L) { service, isReady ->
                handler.post {
                    if (closed) return@post
                    if (!isReady) {
                        ready = false
                        manager = null
                        subscribed.clear()
                        failAll("car_service_disconnected", "차량 속성 서비스와 연결되지 않았습니다.")
                    } else if (!ready) {
                        ready = true
                        try {
                            manager = service.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                            if (manager == null) failAll("car_property_service_missing", "차량 속성 서비스를 얻지 못했습니다.")
                            else properties.forEach(::subscribe)
                        } catch (e: Exception) { failAll("car_service_error", describe(e)) }
                    }
                }
            }
            handler.postDelayed({
                if (!closed && !ready) failAll("car_service_timeout", "8초 안에 차량 서비스가 연결되지 않았습니다.")
            }, 8000)
        } catch (e: Exception) { failAll("car_service_error", describe(e)) }
        catch (e: LinkageError) { failAll("runtime_api_missing", describe(e)) }
    }

    private fun subscribe(property: Property) {
        val api = manager ?: return
        try {
            // A newer standard property may be backported without a Java constant.
            // Query only the documented public ID through CarPropertyManager; its service
            // still enforces permissions. Never infer sensor absence from a missing field.
            val runtimeId = try { VehiclePropertyIds::class.java.getField(property.name).getInt(null) }
                catch (_: NoSuchFieldException) { null }
            val granted = context.checkSelfPermission(property.permission) == PackageManager.PERMISSION_GRANTED
            property.keys.forEach {
                capture.metadata(it, "propertyName", property.name)
                capture.metadata(it, "propertyId", property.id)
                capture.metadata(it, "runtimeConstantPresent", runtimeId != null)
                capture.metadata(it, "requiredPermission", property.permission)
                capture.metadata(it, "permissionGranted", granted)
                capture.metadata(it, "publicConfigQueryAttempted", true)
            }
            if (runtimeId != null && runtimeId != property.id) {
                fail(property, "runtime_contract_mismatch", "실행 환경의 속성 ID가 공개 명세와 다릅니다.")
                return
            }
            val config = api.getCarPropertyConfig(property.id)
            property.keys.forEach { capture.metadata(it, "publicConfigExposed", config != null) }
            if (config == null) {
                fail(property, if (!granted) "property_or_permission_unavailable" else "property_not_exposed",
                    if (!granted) "공개 속성 조회 결과가 없고 ${property.permission} 권한도 없습니다. 미지원과 권한 차단을 이 결과만으로 구분할 수 없습니다."
                    else "현재 차량·펌웨어가 이 속성을 앱에 공개하지 않습니다. 센서 부재를 뜻하지는 않습니다.")
                return
            }
            val expectedType = if (property.key == "suspension") Int::class.javaObjectType else Float::class.javaObjectType
            if (config.propertyType != expectedType) {
                fail(property, "unexpected_value_type", "공개 명세의 값 타입과 다릅니다: ${config.propertyType}")
                return
            }
            val areas = config.areaIds.toList()
            if (property.key == "suspension") {
                wheelNames.forEach { (area, wheel) ->
                    if (area !in areas) capture.status("suspension_${wheel}_mm", "wheel_area_not_exposed", "해당 바퀴의 독립 측정 구역이 없습니다.")
                }
            } else if (0 !in areas) {
                fail(property, "global_area_missing", "전역 구역 0이 없습니다.")
                return
            }
            property.keys.forEach {
                capture.metadata(it, "areaIds", areas)
                capture.metadata(it, "minSampleRateHz", config.minSampleRate)
                capture.metadata(it, "maxSampleRateHz", config.maxSampleRate)
                capture.metadata(it, "requestedSampleRateHz", 10.0)
            }
            val relevantAreas = if (property.key == "suspension") areas.filter { it in wheelNames } else listOf(0)
            relevantAreas.forEach { area ->
                val key = keyFor(property, area) ?: return@forEach
                capture.status(key, "waiting_for_sample", "측정값을 요청했습니다.")
                try { onValue(api.getProperty<Any>(property.id, area)) }
                catch (e: Exception) { capture.status(key, errorCode(e), describe(e)) }
            }
            if (relevantAreas.isNotEmpty()) {
                if (api.registerCallback(callback, property.id, 10f)) subscribed += property.id
                else relevantAreas.forEach { area ->
                    keyFor(property, area)?.let { capture.status(it, "subscription_failed", "연속 수신 등록이 거절됐습니다. 마지막 조회값은 기록에 보존합니다.") }
                }
            }
        } catch (e: Exception) { fail(property, errorCode(e), describe(e)) }
        catch (e: LinkageError) { fail(property, "runtime_api_missing", describe(e)) }
    }

    private fun keyFor(property: Property, area: Int): String? =
        if (property.key == "suspension") wheelNames[area]?.let { "suspension_${it}_mm" }
        else property.key.takeIf { area == 0 }

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) { handler.post { if (!closed) onValue(value) } }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            handler.post {
                if (!closed) properties.find { it.id == propertyId }?.let { property ->
                    keyFor(property, areaId)?.let { capture.status(it, "vehicle_property_error", "차량 서비스가 오류를 보고했습니다.") }
                }
            }
        }
    }
    private fun onValue(value: CarPropertyValue<*>?) {
        if (value == null) return
        val property = properties.find { it.id == value.propertyId } ?: return
        val key = keyFor(property, value.areaId) ?: return
        if (value.status != CarPropertyValue.STATUS_AVAILABLE) {
            capture.status(key, "property_temporarily_unavailable", "차량 값 상태=${value.status}; 측정값으로 사용하지 않습니다.")
            return
        }
        val numeric = when (property.key) {
            "suspension" -> (value.value as? Int)?.toDouble()
            "speed_mps" -> (value.value as? Float)?.toDouble()?.takeIf { it.isFinite() }
            else -> pedalPercentage(value.value)
        }
        capture.sample(key, numeric, value.timestamp)
    }
    fun stop() {
        closed = true
        try { manager?.unregisterCallback(callback) } catch (_: Exception) { }
        subscribed.clear()
        try { car?.disconnect() } catch (_: Exception) { }
        car = null
    }
}

internal fun describe(error: Throwable) = "${error.javaClass.simpleName}: ${error.message.orEmpty().take(240)}"
internal fun errorCode(error: Exception) = when {
    error is SecurityException -> "permission_denied"
    error.javaClass.simpleName.contains("NotAvailable") -> "property_temporarily_unavailable"
    error is IllegalArgumentException -> "property_not_supported"
    else -> "read_error"
}
