package kr.nostalife.companion

data class DriveGoal(val id: String, val label: String, val sentence: String)
object DriveGoals {
    val choices = listOf(
        DriveGoal("commute", "편안한 출퇴근", "출퇴근길을 편안하게 달리고 싶어요."),
        DriveGoal("passenger", "동승자 편안함", "동승자가 편안하도록 흔들림을 줄이는 주행을 원해요."),
        DriveGoal("corner", "부드러운 코너링", "코너에서 조향과 차체 움직임을 부드럽게 이어가고 싶어요."),
        DriveGoal("response", "빠른 조향 반응", "핸들을 돌렸을 때 빠르고 또렷한 조향 반응을 원해요."),
        DriveGoal("braking", "안정적인 감속", "브레이크를 부드럽게 풀며 안정적으로 감속하고 싶어요."),
        DriveGoal("balance", "균형 잡힌 주행", "편안한 승차감과 자연스러운 조향의 균형을 원해요.")
    )
    fun selected(goal: String): Set<String> = choices.filter { it.sentence in goal.lines() }.map { it.id }.toSet()
    fun custom(goal: String): String = goal.lines().filter { line -> choices.none { it.sentence == line } }.joinToString("\n").trim()
    fun compose(ids: Set<String>, custom: String): String = (choices.filter { it.id in ids }.map { it.sentence } + custom.trim().takeIf { it.isNotBlank() }.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
    fun toggle(goal: String, id: String): String {
        require(choices.any { it.id == id })
        val current = selected(goal)
        return compose(if (id in current) current - id else current + id, custom(goal))
    }
}
