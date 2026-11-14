package kr.nostalife.vehicleprobe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.SystemClock

/** SDK 2.3.2 exposes Boolean brake status; it is never converted to pedal percentage. */
class PleosSource(private val context: Context, private val handler: Handler, private val capture: Capture) {
    private var vehicle: Vehicle? = null
    private var pending = 0
    private var closed = false
    private var subscribed = false
    private val key = "pleos_brake_pressed"
    private val listener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(value: Boolean?) {
            handler.post { if (!closed) record(value) }
        }
        override fun onFailed(e: Exception) { handler.post { if (!closed) capture.status(key, errorCode(e), describe(e)) } }
    }
    fun start() {
        if (context.checkSelfPermission("pleos.car.permission.CAR_DRIVE") != PackageManager.PERMISSION_GRANTED) {
            capture.status(key, "pleos_permission_not_granted", "Pleos CAR_DRIVE 권한이 허용되지 않았습니다.")
            return
        }
        try {
            val sdk = Vehicle(context.applicationContext)
            vehicle = sdk
            sdk.initialize()
            capture.status(key, "waiting_for_sample", "Pleos 브레이크 작동 여부를 요청했습니다.")
            pending++
            sdk.getBrake().getCurrentBrakePedalStatus(
                onComplete = { value -> handler.post {
                    pending--
                    if (!closed) {
                        record(value)
                        try { sdk.getBrake().registerBrakePedalStatus(listener); subscribed = true }
                        catch (e: Exception) { capture.status(key, errorCode(e), describe(e)) }
                    }
                    releaseIfClosed()
                } },
                onFailed = { e -> handler.post {
                    pending--
                    if (!closed) capture.status(key, errorCode(e), describe(e))
                    releaseIfClosed()
                } }
            )
            handler.postDelayed({
                if (!closed && pending > 0) capture.status(key, "pleos_callback_timeout", "8초 안에 SDK 응답이 오지 않았습니다. 콜백 전에 release하지 않습니다.")
            }, 8000)
        } catch (e: Exception) {
            pending = 0
            capture.status(key, errorCode(e), describe(e))
            stop()
        } catch (e: LinkageError) {
            pending = 0
            capture.status(key, "pleos_runtime_missing", describe(e))
            stop()
        }
    }
    private fun record(value: Boolean?) {
        capture.metadata(key, "valueType", "Boolean; 1=pressed, 0=not pressed; NOT pedal percent")
        capture.metadata(key, "timestampKind", "callback receipt, not ECU measurement time")
        capture.sample(key, value?.let { if (it) 1.0 else 0.0 }, SystemClock.elapsedRealtimeNanos())
    }
    fun stop() {
        closed = true
        if (subscribed) {
            try { vehicle?.getBrake()?.unregisterBrakePedalStatus(listener) } catch (_: Exception) { }
            subscribed = false
        }
        releaseIfClosed()
    }
    private fun releaseIfClosed() {
        if (closed && pending == 0) {
            try { vehicle?.release() } catch (_: Exception) { }
            vehicle = null
        }
    }
}
