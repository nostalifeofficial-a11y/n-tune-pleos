package kr.nostalife.companion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

private class MemoryCache : ResultCache {
    var base: String? = null
    var saved: ReceivedResult? = null
    override fun load(baseUrl: String) = saved.takeIf { base == baseUrl }
    override fun save(baseUrl: String, result: ReceivedResult) { base = baseUrl; saved = result }
    override fun clear() { saved = null; base = null }
}
@OptIn(ExperimentalCoroutinesApi::class)
class ControllerTest {
    private val connection = Connection.create(Connection.EMULATOR_URL, "", true)
    private val fixedTime = Instant.parse("2026-11-10T03:00:00Z")
    private fun found(name: String = "recommended.json") = RecommendationContract.parse(200, fixture(name))
    @Test fun loadingSuccessFailureRetryPreservesLastReceivedTime() = runTest {
        var fail = false; var calls = 0
        val cache = MemoryCache()
        val controller = RecommendationController(connection, LatestReader { calls++; if (fail) throw FetchException("offline"); found() },
            cache, this, StandardTestDispatcher(testScheduler)) { fixedTime }
        assertEquals(0, calls); controller.refresh()
        assertEquals(Phase.LOADING, controller.state.value.phase)
        advanceUntilIdle(); assertEquals(Phase.SUCCESS, controller.state.value.phase)
        val original = controller.state.value.received
        fail = true; controller.refresh(); advanceUntilIdle()
        assertEquals(Phase.FAILURE, controller.state.value.phase); assertTrue(controller.state.value.stale)
        assertEquals(original, controller.state.value.received); assertEquals(fixedTime, controller.state.value.received!!.receivedAt)
        fail = false; controller.refresh(); advanceUntilIdle()
        assertEquals(Phase.SUCCESS, controller.state.value.phase); assertFalse(controller.state.value.stale)
        assertEquals(3, calls)
    }
    @Test fun heldReplacesRecommendationAndEmptyClearsCache() = runTest {
        var next: Latest = found(); val cache = MemoryCache()
        val controller = RecommendationController(connection, LatestReader { next }, cache, this, StandardTestDispatcher(testScheduler))
        controller.refresh(); advanceUntilIdle()
        next = found("held.json"); controller.refresh(); advanceUntilIdle()
        assertEquals(Phase.HELD, controller.state.value.phase); assertNull(controller.state.value.received!!.result.recommended)
        next = Latest.Empty; controller.refresh(); advanceUntilIdle()
        assertEquals(Phase.EMPTY, controller.state.value.phase); assertNull(controller.state.value.received); assertNull(cache.saved)
    }
    @Test fun startupCacheIsExplicitlyStaleAndDoesNotFetchAutomatically() = runTest {
        val cache = MemoryCache()
        cache.save(connection.baseUrl, ReceivedResult((found() as Latest.Found).result, fixedTime))
        var calls = 0
        val controller = RecommendationController(connection, LatestReader { calls++; found() }, cache, this)
        assertEquals(Phase.CACHED, controller.state.value.phase); assertTrue(controller.state.value.stale); assertEquals(0, calls)
    }
    @Test fun duplicateRefreshAndChangingServerDoNotMixResults() = runTest {
        val gate = CompletableDeferred<Latest>(); var calls = 0
        val controller = RecommendationController(connection, LatestReader { calls++; gate.await() }, MemoryCache(),
            this, StandardTestDispatcher(testScheduler))
        controller.refresh(); controller.refresh(); runCurrent(); assertEquals(1, calls)
        controller.changeConnection(Connection.create("http://127.0.0.1:4181", "", true))
        gate.complete(found()); advanceUntilIdle()
        assertEquals("http://127.0.0.1:4181", controller.state.value.baseUrl)
        assertEquals(Phase.IDLE, controller.state.value.phase); assertNull(controller.state.value.received)
    }
}
