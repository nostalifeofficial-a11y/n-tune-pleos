package kr.nostalife.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject

private val Cream = Color(0xFFFAF8F4)
private val Black = Color(0xFF090909)
private val Accent = Color(0xFFE60025)
private val Gray = Color(0xFF62615E)

@Composable private fun Copy(text: String, size: Int = 22, color: Color = Black, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, modifier, color, fontSize = size.sp, lineHeight = (size * 1.4).sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}
@Composable private fun Choice(text: String, selected: Boolean = false, recommended: Boolean = false, onClick: () -> Unit) {
    Button(onClick, shape = RoundedCornerShape(6.dp), modifier = Modifier.heightIn(min = 58.dp),
        border = if (recommended) BorderStroke(3.dp, Accent) else null,
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Accent else if (recommended) Color(0xFFFFEDF1) else Black,
            contentColor = if (recommended && !selected) Accent else Cream)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(text, fontSize = 21.sp)
            if (recommended) Text("AI 추천", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Copy(title, 25, bold = true); content()
    }
}
@Composable private fun NSettingChoice(text: String, selected: Boolean, recommended: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Copy(if (recommended) "AI 추천" else " ", 17, Accent, true)
        Box(Modifier.border(5.dp, if (recommended) Accent else Color.Transparent, RoundedCornerShape(3.dp)).padding(6.dp)) {
            Choice(text, selected, onClick = onClick)
        }
    }
}

@Composable fun ContestDashboard(model: RecommendationViewModel, drive: ContestModel, restricted: Boolean, diagnostics: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val live by drive.live.collectAsStateWithLifecycle()
    val source by drive.source.collectAsStateWithLifecycle()
    val stored by drive.preference.collectAsStateWithLifecycle()
    val history by drive.history.collectAsStateWithLifecycle()
    val preferenceSync by drive.preferenceSync.collectAsStateWithLifecycle()
    val customRevision by drive.customRevision.collectAsStateWithLifecycle()
    val goalAdvice by drive.goalAdvice.collectAsStateWithLifecycle()
    val adviceBusy by drive.adviceBusy.collectAsStateWithLifecycle()
    val adviceMessage by drive.adviceMessage.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selectedHistory by remember { mutableStateOf<Recommendation?>(null) }
    var draft by remember(stored) { mutableStateOf(stored) }
    var showCustomGoal by rememberSaveable { mutableStateOf(DriveGoals.custom(stored.goal).isNotBlank()) }
    var saved by remember { mutableStateOf(false) }
    var webCode by rememberSaveable { mutableStateOf(if (model.suggestedAddress.contains("/api/channels/")) model.suggestedAddress.substringAfterLast('/') else "") }
    var connectionMessage by remember { mutableStateOf("") }
    var nCar by rememberSaveable { mutableStateOf(NCustom.cars.first()) }
    var nVariant by rememberSaveable { mutableStateOf("DCT") }
    var nSlot by rememberSaveable { mutableIntStateOf(1) }
    var nDetail by remember { mutableStateOf("") }
    val contentScroll = rememberScrollState()
    LaunchedEffect(page) { contentScroll.scrollTo(0) }
    val baseResult = state.received?.result
    val activeAdvice = goalAdvice?.takeIf { it.matches(baseResult, draft.goal) }
    val result = activeAdvice?.result ?: baseResult
    val matchesGoal = activeAdvice != null || (result != null && JSONObject(result.rawJson).optJSONObject("driving_preference")?.optString("goal") == draft.goal)
    val recommendationAi = result?.ai?.takeIf { matchesGoal }
    LaunchedEffect(state.received?.result?.id) { state.received?.result?.let(drive::remember) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Cream, surface = Color.White, onSurface = Black)) {
        Row(Modifier.fillMaxSize().background(Cream).systemBarsPadding()) {
            Column(Modifier.width(240.dp).fillMaxHeight().background(Black).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Copy("NOSTALIFE", 27, Cream, true)
                Copy("DRIVE\nSTUDIO", 32, Cream, true)
                Copy("PLEOS EDITION", 14, Accent, true)
                Spacer(Modifier.height(22.dp))
                listOf("01  일상 드라이브", "02  AI 주행 분석", "03  보관함").forEachIndexed { i, name ->
                    Button({ page = i; selectedHistory = null }, enabled = !restricted,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp), shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (page == i) Accent else Black, contentColor = Cream)) {
                        Text(name, fontSize = 19.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Copy("나의 감각을 기록하고,\n다음 주행을 이해하다.", 17, Cream)
                Copy("5.3 / CONTEST", 13, Color.LightGray)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Copy("Pleos  /  나의 드라이브", 26, bold = true, modifier = Modifier.weight(1f))
                    if (!restricted) Choice(if (page == 3) "N 모드 OFF · 일상으로" else "N 모드 ON →", page == 3) { page = if (page == 3) 0 else 3 }
                    Spacer(Modifier.width(20.dp))
                    Copy(if (restricted) "주차 후 이용" else "주차 중 · 이용 가능", 18, Gray)
                }
                HorizontalDivider(color = Color(0xFFBDBAB4))
                if (restricted) {
                    Spacer(Modifier.height(55.dp))
                    Copy("안전하게 정차한 뒤\n드라이브를 돌아보세요.", 40, bold = true)
                    Copy("차량의 주행 중 화면 제한을 따릅니다. 연결 상태가 확인되면 화면이 열립니다.", 22, Gray)
                } else Column(Modifier.weight(1f).verticalScroll(contentScroll), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    when (page) {
                        0 -> {
                            Copy("오늘은, 어떻게 달리고 싶나요?", 39, bold = true)
                            Copy("N 모드 OFF  ·  목표를 정하고, 주행 후 다음 설정을 선택하세요.", 22, Gray)
                            Panel("나의 주행 목표") {
                                Copy("원하는 목표를 골라 주세요 · 여러 개 선택 가능", 19, Gray)
                                DriveGoals.choices.chunked(3).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        row.forEach { goal ->
                                            val chosen = goal.id in DriveGoals.selected(draft.goal)
                                            Button({
                                                val next = DriveGoals.toggle(draft.goal, goal.id)
                                                if (next.length <= 500) { draft = draft.copy(goal = next); drive.savePreference(draft); saved = true }
                                            }, enabled = !adviceBusy, modifier = Modifier.weight(1f).heightIn(min = 60.dp), shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = if (chosen) Accent else Black, contentColor = Cream)) {
                                                Text((if (chosen) "✓ " else "") + goal.label, fontSize = 21.sp)
                                            }
                                        }
                                    }
                                }
                                TextButton({ showCustomGoal = !showCustomGoal }) { Text(if (showCustomGoal) "직접 입력 접기 ↑" else "목표 직접 입력 +", color = Gray, fontSize = 18.sp) }
                                if (showCustomGoal) OutlinedTextField(DriveGoals.custom(draft.goal), {
                                    val next = DriveGoals.compose(DriveGoals.selected(draft.goal), it)
                                    if (next.length <= 500) { draft = draft.copy(goal = next); saved = false }
                                }, label = { Text("추가하고 싶은 목표") }, modifier = Modifier.fillMaxWidth(), minLines = 1,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 24.sp))
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Choice(if (adviceBusy) "AI가 추천하는 중…" else "이 목표로 AI 추천받기", true) { drive.recommend(baseResult, draft, model.suggestedAddress); saved = true }
                                    if (showCustomGoal && !saved) TextButton({ drive.savePreference(draft); saved = true }) { Text("목표만 저장", fontSize = 19.sp) }
                                }
                                if (adviceMessage.isNotBlank()) Copy(adviceMessage, 18, Gray)
                            }
                            Panel("다음 주행 설정") {
                                val targets = recommendationAi?.targets
                                Copy("0~100 · 5 단위", 18, Gray)
                                PreferenceSlider("승차감", "0 · 탄탄하게", "100 · 부드럽게", draft.comfort, targets?.comfort) { draft = draft.copy(comfort = it); drive.savePreference(draft) }
                                PreferenceSlider("조향응답성", "0 · 완만하게", "100 · 빠르게", draft.steering, targets?.steeringResponse) { draft = draft.copy(steering = it); drive.savePreference(draft) }
                                if (targets != null) Choice("추천값으로 맞추기", true) {
                                    draft = draft.copy(comfort = targets.comfort.toFloat(), steering = targets.steeringResponse.toFloat())
                                    drive.savePreference(draft)
                                }
                                if (state.stale) Copy("이전 분석의 추천입니다.", 18, Accent)
                                Copy(if (targets == null) "추천값을 받으면 각 슬라이더 옆에 표시됩니다." else "추천값과 현재 선택값을 비교해 보세요.", 17, Gray)
                                if (targets != null) {
                                    Copy("추천 활동", 22, Accent, true)
                                    Copy(recommendationAi?.brief?.nextAction ?: DrivePresentation.excerpt(targets.nextCheck), 22)
                                }
                                Copy("개인 선호 기록 · 차량 설정은 변경되지 않습니다.", 17, Gray)
                            }
                            Panel("주행 데이터 연결") {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf("web" to "홈페이지 결과", "sim" to "PC 주행 창", "pleos" to "Pleos API", "acc" to "로컬 ACC").forEach { (key, label) ->
                                        Choice(label, source == key) {
                                            drive.selectSource(key)
                                            if (key == "sim") { model.saveConnection("http://127.0.0.1:4182", ""); model.refresh() }
                                            drive.poll()
                                        }
                                    }
                                }
                                if (source == "web") {
                                    Copy("인터넷 홈페이지 상단의 연결 코드를 입력하세요. 분석 완료된 결과를 가져옵니다.", 20, Gray)
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(webCode, { webCode = it.take(16) }, label = { Text("홈페이지 연결 코드") }, singleLine = true,
                                            modifier = Modifier.weight(1f), textStyle = LocalTextStyle.current.copy(fontSize = 25.sp))
                                        Choice("결과 연결", true) {
                                            val error = model.savePublicCode(webCode)
                                            connectionMessage = error ?: "연결 코드를 저장했습니다. 결과를 확인합니다."
                                            if (error == null) model.refresh()
                                        }
                                    }
                                    if (connectionMessage.isNotBlank()) Copy(connectionMessage, 18, Gray)
                                    Copy(state.error ?: if (result != null) "홈페이지 결과 수신됨 · ${result.createdAt}" else "홈페이지에서 주행 후 분석을 눌러 주세요.", 19, Gray)
                                    result?.let { SummaryReadings(it) }
                                } else {
                                Copy(live.optString("message", "연결 확인 중"), 20, if (live.optBoolean("fresh")) Color(0xFF007C45) else Gray)
                                val values = live.optJSONObject("values") ?: JSONObject()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf(Triple("속도", "speed_kmh", "km/h"), Triple("가속", "throttle_pct", "%"), Triple("제동", "brake_pct", "%"), Triple("횡가속도", "lat_g", "g")).forEach { (label, key, unit) ->
                                        Column(Modifier.weight(1f).background(Cream).padding(14.dp)) {
                                            Copy(label, 17, Gray)
                                            val number = if (live.optBoolean("fresh")) values.opt(key) as? Number else null
                                            Copy(number?.let { "%.1f".format(it.toDouble()) } ?: "—", 32, bold = true)
                                            Copy(unit, 16, Gray)
                                        }
                                    }
                                }
                                if (source == "pleos") {
                                    val pressed = values.opt("brake_pressed") as? Number
                                    Copy("Pleos 브레이크: " + when (pressed?.toInt()) { 1 -> "작동"; 0 -> "해제"; else -> "수신 대기" }, 20)
                                    Choice("API 수신 상세 확인") { diagnostics() }
                                    Copy(live.optString("detail"), 16, Gray)
                                } else Copy(if (source == "sim") "PC 실행기 → 별도 주행 창 열기 → 직접 운전 시작. W/A/S/D·방향키·게임패드를 지원합니다." else "ACC를 실행하고 트랙에 진입하면 현재 PC의 공유 메모리를 읽습니다. 정지·리플레이·다른 게임의 값은 주행값으로 사용하지 않습니다.", 18, Gray)
                                if (source == "sim" && result != null) SummaryReadings(result)
                                }
                            }
                        }
                        1 -> {
                            Copy("주행을 이해하는 AI", 39, bold = true)
                            Copy("이번 주행의 핵심과 다음 행동을 확인하세요.", 22, Gray)
                            Choice("최신 분석 가져오기", true) { model.refresh() }
                            if (state.stale) Copy("이전 저장 결과입니다. 현재 연결 상태: ${state.error ?: "확인 중"}", 19, Accent)
                            result?.let { Analysis(it) } ?: Panel("아직 받은 분석이 없습니다") {
                                Copy(state.error ?: "PC 주행 창에서 원하는 만큼 달린 뒤 분석해 주세요. 완주 조건은 없습니다.", 22, Gray)
                            }
                            Copy("테스트 주행을 바탕으로 한 AI 분석입니다.", 18, Gray)
                        }
                        2 -> {
                            Copy("나의 드라이브 보관함", 39, bold = true)
                            Copy("최근 기록 ${history.size}개 · 한국시간", 22, Gray)
                            if (history.isEmpty()) Panel("첫 주행을 기다리고 있어요") { Copy("주행 창에서 분석을 보내면 여기에 저장됩니다.", 22, Gray) }
                            selectedHistory?.let { Analysis(it) }
                            history.forEach { result ->
                                OutlinedButton({ selectedHistory = result }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                    border = BorderStroke(1.dp, Color(0xFFBDBAB4)), shape = RoundedCornerShape(6.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(DrivePresentation.driveTitle(result.createdAt).replace(" 주행", if (JSONObject(result.rawJson).has("origin_recommendation_id")) " 추천" else " 주행"), color = Black, fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Copy(result.capture["duration_s"]?.let { "${it.toInt()}초 · AI 분석" } ?: "AI 분석", 17, Gray)
                                    }
                                }
                            }
                        }
                        3 -> {
                            Copy("N CUSTOM", 43, bold = true)
                            Copy("N 모드 ON · 차종별 설정 화면", 22, Accent, true)
                            Copy("공모전용 UI 체험 · 정품 N Custom 앱이 아니며 차량 설정을 변경하지 않습니다.", 18, Gray)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                NCustom.cars.forEach { car -> Choice(car, nCar == car) { nCar = car; nDetail = "" } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                (1..2).forEach { slot -> Choice("CUSTOM $slot", nSlot == slot) { nSlot = slot; nDetail = "" } }
                                if (nCar == NCustom.cars[0]) listOf("DCT", "MT").forEach { variant ->
                                    Choice(if (variant == "DCT") "8 DCT" else "6 MT", nVariant == variant) { nVariant = variant; nDetail = "" }
                                }
                            }
                            val nRecommendation = DrivePresentation.advice(recommendationAi, nCar, nVariant)
                            Panel("AI 추천 항목과 활동") {
                                if (nRecommendation?.settings?.isNotEmpty() == true) {
                                    Copy(nRecommendation.settings.entries.joinToString(" · ") { (key, value) ->
                                        "${NCustom.options(nCar, nVariant).first { it.key == key }.title} $value"
                                    }, 23, Accent, true)
                                    Copy(nRecommendation.reason, 20)
                                    Copy("추천 활동 · " + (recommendationAi?.brief?.nextAction ?: recommendationAi?.targets?.nextCheck.orEmpty()), 21)
                                } else Copy(nRecommendation?.reason ?: "일상 화면에서 목표를 고르고 AI 추천을 받아 주세요.", 21, Gray)
                                Choice(if (adviceBusy) "AI가 추천하는 중…" else "이 목표로 다시 추천받기") { drive.recommend(baseResult, draft, model.suggestedAddress) }
                                if (adviceMessage.isNotBlank()) Copy(adviceMessage, 17, Gray)
                            }
                            Panel("$nCar  /  CUSTOM $nSlot") {
                                val advice = nRecommendation
                                Copy(if (nCar == NCustom.cars[0]) "엔진 · 변속기 · 배기음" else if (nCar == NCustom.cars[1]) "모터 · Supersonic 사운드 · N e-Shift" else "모터 · Lightspeed 사운드 · 앰비언트 변속 알림", 21, Gray)
                                Copy(if (advice?.settings?.isNotEmpty() == true) "빨간 네모 · AI 추천   /   빨간 채움 · 현재 선택" else "AI 추천 대기 · 도착하면 빨간 네모로 표시됩니다.", 18, Gray)
                                if (advice?.settings?.isNotEmpty() == true && state.stale) Copy("이전 분석의 추천입니다.", 18, Accent)
                                if (nCar == NCustom.cars[2]) Copy("공개 기능 소개를 바탕으로 구성한 UI이며 실제 연식·지역별 메뉴는 확인이 필요합니다.", 17, Gray)
                                NCustom.options(nCar, nVariant).forEach { option ->
                                    val selected = remember(nCar, nVariant, nSlot, customRevision, option.key) { drive.customValue(nCar, nVariant, nSlot, option) }
                                    val recommendation = advice?.settings?.get(option.key)?.takeIf { it in option.choices }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Copy(option.title, 24, bold = true, modifier = Modifier.weight(1f))
                                        Copy(if (option.key == "esc") "직접 선택" else recommendation?.let { "추천 · $it" } ?: if (advice != null) "취향대로 선택" else "추천 대기", 20, if (recommendation != null) Accent else Gray, recommendation != null)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        option.choices.forEach { choice -> NSettingChoice(choice, choice == selected, choice == recommendation) {
                                            drive.saveCustom(nCar, nVariant, nSlot, option, choice)
                                            nDetail = "${option.title} · $choice 저장됨 — ${option.detail}"
                                        } }
                                    }
                                    Copy(option.detail, 17, Gray)
                                    HorizontalDivider()
                                }
                                if (nDetail.isNotBlank()) Copy(nDetail, 20, Accent)
                                Choice("이 CUSTOM 기본값으로") { drive.resetCustom(nCar, nVariant, nSlot); nDetail = "CUSTOM $nSlot 기본값 저장됨" }
                                Copy("차종·변속기·CUSTOM 1/2를 각각 저장합니다.", 18, Gray)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
@Composable private fun PreferenceSlider(title: String, left: String, right: String, value: Float, recommendation: Int?, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Copy(title, 24, bold = true, modifier = Modifier.weight(1f))
            Copy("추천 ${recommendation ?: "—"}", 25, if (recommendation != null) Accent else Gray, true)
            Spacer(Modifier.width(24.dp))
            Copy("선택 ${value.toInt()}", 20, Gray)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton({ onChange(driveStep(value - 5)) }, enabled = value > 0) { Text("−5", fontSize = 22.sp) }
            Slider(driveStep(value), { onChange(driveStep(it)) }, modifier = Modifier.weight(1f), valueRange = 0f..100f, steps = 19)
            OutlinedButton({ onChange(driveStep(value + 5)) }, enabled = value < 100) { Text("+5", fontSize = 22.sp) }
        }
        Row(Modifier.fillMaxWidth()) { Copy(left, 17, Gray, modifier = Modifier.weight(1f)); Copy(right, 17, Gray) }
    }
}
@Composable private fun SummaryReadings(result: Recommendation) {
    Copy("최근 분석 기록 · 실시간 계기값과 구분됩니다.", 18, Gray)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("주행 시간" to "${result.capture["duration_s"] ?: "—"} s", "평균 속도" to "${result.observed["speed_mean_kmh"] ?: "—"} km/h",
            "표본" to "${result.capture["samples"]?.toInt() ?: "—"}").forEach { (label, value) ->
            Column(Modifier.weight(1f).background(Cream).padding(16.dp)) { Copy(label, 18, Gray); Copy(value, 26, bold = true) }
        }
    }
}
@Composable private fun Analysis(result: Recommendation) {
    val ai = result.ai
    if (ai == null) { Panel("AI 결과 확인 필요") { Copy("기존 형식의 결과는 AI 분석으로 표시하지 않습니다.") }; return }
    var expanded by remember(result.id) { mutableStateOf(false) }
    Panel("이번 주행 한눈에") {
        Copy(ai.brief?.summary ?: DrivePresentation.excerpt(ai.diagnosis), 26)
        HorizontalDivider()
        Copy("다음 주행에서", 23, bold = true)
        Copy(ai.brief?.nextAction ?: DrivePresentation.excerpt(ai.coaching, 85), 24)
        // A compact answer is supplied by new AI responses; old records use their first sentence.
        TextButton({ expanded = !expanded }) { Text(if (expanded) "접기 ↑" else "자세히 보기 ↓", color = Accent, fontSize = 21.sp) }
        if (expanded) {
            HorizontalDivider()
            Copy(ai.headline, 23, bold = true)
            Copy(ai.diagnosis, 22)
            Copy(ai.coaching, 22)
            Copy(ai.nextCheck, 21)
            ai.targets?.let { Copy(it.reason, 21); Copy(it.nextCheck, 20, Gray) }
            result.recommended?.let { Copy("테스트 주행 비교 후보: ${it.label}", 22, Accent, true) }
            Copy(ai.presetReason, 19, Gray)
            Copy(result.limitations.joinToString("\n"), 17, Gray)
            Copy("${DrivePresentation.driveTitle(result.createdAt)} · ${ai.model}", 16, Gray)
        }
    }
}
