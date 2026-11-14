package kr.nostalife.companion

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

enum class Phase { IDLE, CACHED, LOADING, SUCCESS, HELD, EMPTY, FAILURE }
data class ReceivedResult(val result: Recommendation, val receivedAt: Instant)
data class ScreenState(val baseUrl: String, val phase: Phase = Phase.IDLE, val received: ReceivedResult? = null, val error: String? = null) {
    val loading: Boolean get() = phase == Phase.LOADING
    val stale: Boolean get() = received != null && phase !in setOf(Phase.SUCCESS, Phase.HELD)
}
interface ResultCache {
    fun load(baseUrl: String): ReceivedResult?
    fun save(baseUrl: String, result: ReceivedResult)
    fun clear()
}
class RecommendationController(
    private var connection: Connection,
    private val reader: LatestReader,
    private val cache: ResultCache,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Instant = { Instant.now() }
) {
    private fun initial(base: String): ScreenState {
        val cached = runCatching { cache.load(base) }.getOrNull()
        return ScreenState(base, if (cached == null) Phase.IDLE else Phase.CACHED, cached)
    }
    private val mutable = MutableStateFlow(initial(connection.baseUrl))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    fun changeConnection(next: Connection) {
        generation++; job?.cancel(); job = null
        val changedServer = connection.baseUrl != next.baseUrl
        connection = next
        mutable.value = if (changedServer) initial(next.baseUrl) else
            mutable.value.copy(phase = if (mutable.value.received != null) Phase.CACHED else Phase.IDLE, error = null)
    }
    fun permissionDenied() {
        mutable.value = mutable.value.copy(phase = Phase.FAILURE, error = "PC 연결에는 로컬 네트워크 권한이 필요합니다. 다시 불러오기를 누르고 권한을 허용하세요.")
    }
    fun refresh(silent: Boolean = false) {
        if (job?.isActive == true) return
        val target = connection
        val ticket = generation
        if (!silent || mutable.value.phase in setOf(Phase.IDLE, Phase.CACHED))
            mutable.value = mutable.value.copy(phase = Phase.LOADING, error = null)
        job = scope.launch {
            try {
                val latest = reader.fetch(target)
                if (ticket != generation) return@launch
                when (latest) {
                    is Latest.Found -> {
                        val received = ReceivedResult(latest.result, now())
                        withContext(io) { cache.save(target.baseUrl, received) }
                        if (ticket == generation) mutable.value = ScreenState(target.baseUrl,
                            if (latest.result.status == "held") Phase.HELD else Phase.SUCCESS, received)
                    }
                    Latest.Empty -> {
                        withContext(io) { cache.clear() }
                        if (ticket == generation) mutable.value = ScreenState(target.baseUrl, Phase.EMPTY)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (ticket == generation) mutable.value = mutable.value.copy(phase = Phase.FAILURE,
                    error = when (error) {
                        is FetchException -> error.userMessage
                        is ContractException -> error.message
                        is java.io.IOException -> "결과를 기기에 저장하지 못했습니다. 저장 공간을 확인하세요."
                        else -> "결과를 처리하지 못했습니다. 다시 시도해 주세요."
                    })
            }
        }
    }
}
