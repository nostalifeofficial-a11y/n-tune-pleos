package kr.nostalife.companion

import kotlin.math.roundToInt

fun driveStep(value: Float): Float = if (value.isFinite()) ((value.coerceIn(0f, 100f) / 5f).roundToInt() * 5).toFloat() else 50f

data class NOption(val key: String, val title: String, val choices: List<String>, val detail: String)
object NCustom {
    val cars = listOf("아반떼 N", "아이오닉 5 N", "아이오닉 6 N")
    private val three = listOf("Normal", "Sport", "Sport+")
    fun options(car: String, variant: String): List<NOption> {
        require(car in cars && variant in listOf("DCT", "MT"))
        val chassis = listOf(
            NOption("suspension", "서스펜션", three, "승차감과 차체 반응을 선택합니다."),
            NOption("steering", "스티어링", three, "핸들의 무게감 설정입니다. 일반 화면의 조향응답성 지수와는 별개입니다."),
            NOption("esc", "ESC", listOf("Normal", "Sport", "Off"), "차체 자세 제어 · 이 앱에서는 화면의 저장값만 바뀝니다."))
        if (car == cars[0]) return listOf(
            NOption("engine", "엔진", three, "2.0 터보 엔진의 가속 반응"),
            if (variant == "DCT") NOption("transmission", "변속기 · 8 DCT", three, "자동 변속 반응")
            else NOption("rev", "레브 매칭 · 6 MT", three, "수동 변속 시 회전수 보정"),
            NOption("elsd", "e-LSD", listOf("Normal", "Sport"), "전자식 차동 제한")) + chassis +
            NOption("sound", "배기음", three, "내연기관 배기 사운드 · 음원 재생 없음")
        return listOf(NOption("motor", "모터", listOf("Eco") + three, "듀얼 모터의 가속 반응")) + chassis + listOf(
            NOption("elsd", "e-LSD", three, "후륜 전자식 차동 제한"),
            NOption("sound", "N Active Sound+", listOf("Off", "Ignition", "Evolution", if (car == cars[1]) "Supersonic" else "Lightspeed"), "전기차 가상 주행 사운드 · 음원 재생 없음"),
            NOption("eshift", "N e-Shift", listOf("Off", "On"), "가상 변속 기능 선택")) +
            if (car == cars[2]) listOf(
                NOption("ambient", "N Ambient Shift Light", listOf("Off", "On"), "아이오닉 6 N의 실내 변속 알림"),
                NOption("battery", "N Battery", listOf("Off", "Drag", "Sprint", "Endurance"), "아이오닉 6 N의 배터리 준비 목적 · 실제 온도 제어 없음")) else emptyList()
    }
    fun default(option: NOption) = if ("Normal" in option.choices) "Normal" else option.choices.first()
    fun storageKey(car: String, variant: String, slot: Int, field: String): String {
        require(slot in 1..2 && options(car, variant).any { it.key == field })
        return "ncustom:${cars.indexOf(car)}:${if (car == cars[0]) variant else "EV"}:$slot:$field"
    }
}
