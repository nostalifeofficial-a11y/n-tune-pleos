package kr.nostalife.companion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GoalAdviceTest {
    private val origin = "defaadfa-3b8d-4953-8d5b-28220adbad29"
    private val goal = DriveGoals.choices.first().sentence
    private fun fixture() = JSONObject(javaClass.classLoader!!.getResource("ai-window.json")!!.readText())
    private fun response(): JSONObject = fixture().apply {
        put("recommendation_id", "af84d339-c1f8-4a99-b570-312f43e482af")
        put("origin_recommendation_id", origin)
        put("driving_preference", JSONObject().put("goal", goal))
        getJSONObject("analysis").put("driving_targets", JSONObject()
            .put("comfort", 70).put("steering_response", 40).put("scope", "preference_trial")
            .put("reason", "계약 검증용 응답").put("next_check", "동일 구간을 비교하세요."))
        getJSONObject("analysis").put("brief", JSONObject()
            .put("summary", "계약 검증용 요약").put("next_action", "급격한 조향을 줄여 보세요."))
    }
    @Test fun multipleGoalsPreserveCustomTextAndCanBeDeselected() {
        val custom = "내가 직접 정한 목표"
        val both = DriveGoals.toggle(DriveGoals.toggle(custom, "commute"), "corner")
        assertEquals(setOf("commute", "corner"), DriveGoals.selected(both))
        assertEquals(custom, DriveGoals.custom(both))
        assertEquals(DriveGoals.compose(setOf("corner"), custom), DriveGoals.toggle(both, "commute"))
        assertEquals(6, DriveGoals.choices.map { it.id }.toSet().size)
    }
    @Test fun cachedAdviceOnlyMatchesItsSourceAndGoal() {
        val advice = GoalAdviceProtocol.parse(response().toString(), origin, goal)
        val base = (RecommendationContract.parse(200, fixture().toString()) as Latest.Found).result
        assertTrue(advice.matches(base, goal))
        assertFalse(advice.matches(base, DriveGoals.choices[1].sentence))
        assertFalse(advice.matches(advice.result, goal))
        assertFalse(advice.matches(null, goal))
        assertEquals(70, advice.result.ai!!.targets!!.comfort)
    }
    @Test fun responsesForAnotherRecordOrGoalAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { GoalAdviceProtocol.parse(response().toString(), "another-record", goal) }
        assertThrows(IllegalArgumentException::class.java) { GoalAdviceProtocol.parse(response().toString(), origin, "another-goal") }
    }
    @Test fun missingTargetsAreNeverReplacedWithDefaults() {
        val noNumbers = response().apply { getJSONObject("analysis").remove("driving_targets") }
        assertThrows(IllegalArgumentException::class.java) { GoalAdviceProtocol.parse(noNumbers.toString(), origin, goal) }
        val sameId = response().apply { put("recommendation_id", origin) }
        assertThrows(IllegalArgumentException::class.java) { GoalAdviceProtocol.parse(sameId.toString(), origin, goal) }
    }
}
