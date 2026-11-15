package kr.nostalife.companion

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DrivePresentation {
    // Existing AI records remain intact; only their first sentence is shown in the folded view.
    fun excerpt(text: String, limit: Int = 100): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        val sentence = Regex("[.!?](?:\\s|$)").find(clean)?.let { clean.take(it.range.first + 1) } ?: clean
        return if (sentence.length <= limit) sentence else sentence.take(limit - 1).trimEnd() + "…"
    }
    fun driveTitle(time: Instant, zone: ZoneId = ZoneId.of("Asia/Seoul")): String =
        DateTimeFormatter.ofPattern("MM.dd · HH:mm 주행", Locale.KOREAN).withZone(zone).format(time)
    fun carId(car: String): String = listOf("avante_n", "ioniq5_n", "ioniq6_n")[NCustom.cars.indexOf(car)]
    fun advice(ai: AiAnalysis?, car: String, variant: String): NCustomAdvice? =
        ai?.nCustom?.firstOrNull { it.car == carId(car) && it.variant == if (car == NCustom.cars[0]) variant else "EV" }
}
