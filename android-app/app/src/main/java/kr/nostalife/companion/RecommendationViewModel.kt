package kr.nostalife.companion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

class RecommendationViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("connection", 0)
    // Tokens stay in this ViewModel only. No saved state, file, clipboard or log.
    private var token = ""
    private val defaultAddress = if (BuildConfig.DEBUG) BuildConfig.DEFAULT_SERVER_URL else BuildConfig.DEFAULT_SERVER_URL.replace("http://", "https://")
    private val savedAddress = preferences.getString("base_url", defaultAddress) ?: defaultAddress
    private val startingConnection = runCatching { Connection.create(savedAddress, "", BuildConfig.DEBUG, allowMissingToken = true) }.getOrElse {
        Connection.create(defaultAddress, "", BuildConfig.DEBUG)
    }
    private val controller = RecommendationController(startingConnection, HttpLatestReader(), LocalResultCache(application), viewModelScope)
    val state = controller.state
    val suggestedAddress: String get() = state.value.baseUrl
    val hasToken: Boolean get() = token.isNotEmpty()
    fun saveConnection(address: String, enteredToken: String): String? = try {
        val usedToken = if (enteredToken.isBlank() && address.trim().trimEnd('/') == state.value.baseUrl) token else enteredToken.trim()
        val next = Connection.create(address, usedToken, BuildConfig.DEBUG)
        token = usedToken
        preferences.edit().putString("base_url", next.baseUrl).apply()
        controller.changeConnection(next)
        null
    } catch (error: IllegalArgumentException) { error.message ?: "연결 설정을 확인해 주세요." }
    fun savePublicCode(code: String): String? = try {
        saveConnection(Connection.publicAddress(code), "")
    } catch (error: IllegalArgumentException) { error.message }
    fun refresh(silent: Boolean = false) = controller.refresh(silent)
    fun permissionDenied() = controller.permissionDenied()
}
