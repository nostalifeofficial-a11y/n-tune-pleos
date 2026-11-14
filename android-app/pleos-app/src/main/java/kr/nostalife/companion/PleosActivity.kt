package kr.nostalife.companion

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kr.nostalife.vehicleprobe.ProbeActivity
import org.json.JSONObject
import java.io.File

class PleosActivity : ComponentActivity() {
    private val model: RecommendationViewModel by viewModels()
    private val restricted = mutableStateOf(true)
    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null
    private var gleoJob: Job? = null
    override fun onStart() {
        super.onStart()
        if (BuildConfig.DEBUG) File(filesDir, "ui-status.json").writeText("{\"visible\":true}")
    }
    override fun onStop() {
        if (BuildConfig.DEBUG) File(filesDir, "ui-status.json").writeText("{\"visible\":false}")
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PleosDashboard(model, restricted = restricted.value,
            diagnostics = { if (!restricted.value) startActivity(Intent(this, ProbeActivity::class.java)) }) }
        connectUx()
        handlePairing(intent)
        handleGleo(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { if (!restricted.value) model.refresh(silent = true); delay(3000) }
            }
        }
    }
    private fun connectUx() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
        try {
            car = Car.createCar(this, Handler(Looper.getMainLooper()), 0L) { service, ready ->
                if (!ready) { restricted.value = true; return@createCar }
                try {
                    ux = service.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    fun applyRestriction(requires: Boolean, flags: Int) {
                        restricted.value = requires || flags != 0
                        if (BuildConfig.DEBUG) File(filesDir, "ux-status.json").writeText(JSONObject()
                            .put("requiresDistractionOptimization", requires).put("activeRestrictions", flags)
                            .put("detailsHidden", restricted.value).toString())
                    }
                    ux!!.registerListener { state -> applyRestriction(state.isRequiresDistractionOptimization, state.activeRestrictions) }
                    val current = ux!!.currentCarUxRestrictions
                    applyRestriction(current.isRequiresDistractionOptimization, current.activeRestrictions)
                } catch (_: Exception) { restricted.value = true }
            }
        } catch (_: Exception) { restricted.value = true }
        catch (_: LinkageError) { restricted.value = true }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handlePairing(intent); handleGleo(intent) }
    private fun handlePairing(incoming: Intent?) {
        incoming?.getStringExtra("nostalife_pair_code")?.let { code ->
            if (model.savePublicCode(code) == null) model.refresh()
        }
    }
    private fun handleGleo(actionIntent: Intent?) {
        val action = actionIntent?.action?.takeIf { it in GleoReply.actions } ?: return
        gleoJob?.cancel()
        gleoJob = lifecycleScope.launch {
            model.refresh()
            val fresh = withTimeoutOrNull(5500) {
                model.state.first { it.phase in setOf(Phase.SUCCESS, Phase.HELD, Phase.EMPTY, Phase.FAILURE) }
            }
            val response = GleoReply.fromState(action, fresh)
            sendBroadcast(Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
                .putExtra("packageName", packageName).putExtra("response", response.toString()))
            if (BuildConfig.DEBUG) File(filesDir, "gleo-last-response.json").writeText(response.toString(2))
        }
    }
    override fun onDestroy() {
        gleoJob?.cancel()
        try { ux?.unregisterListener() } catch (_: Exception) { }
        try { car?.disconnect() } catch (_: Exception) { }
        super.onDestroy()
    }
}
