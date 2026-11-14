package kr.nostalife.companion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Background = Color(0xFF101416)
private val Panel = Color(0xFF191F21)
private val Line = Color(0xFF303A36)
private val Lime = Color(0xFFD7F986)
private val TextColor = Color(0xFFF0F2E9)
private val Muted = Color(0xFF9EACA2)
private val Amber = Color(0xFFF1B680)
private val shape = RoundedCornerShape(18.dp)
private fun number(value: Double?, digits: Int = 3): String =
    value?.let { String.format(Locale.US, "%."+digits+"f", it) } ?: "—"
private fun time(value: Instant, utc: Boolean = false): String =
    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss z", Locale.KOREAN)
        .withZone(if (utc) ZoneOffset.UTC else ZoneId.systemDefault()).format(value)

@Composable
fun NostalifeApp(model: RecommendationViewModel, refresh: () -> Unit, restricted: Boolean = false, diagnostics: (() -> Unit)? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Color(0xFF1D2819),
        background = Background, surface = Panel, onSurface = TextColor, onBackground = TextColor,
        outline = Line, secondary = Muted, error = Amber)) {
        Surface(Modifier.fillMaxSize(), color = Background) {
            if (restricted) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), verticalArrangement = Arrangement.Center) {
                    Text("NOSTALIFE", color = Lime, fontSize = 24.sp, letterSpacing = 3.sp)
                    Spacer(Modifier.height(20.dp))
                    Text("정차 후 주행 분석을 확인하세요.", fontSize = 24.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("차량의 주행 중 화면 제한을 적용하고 있습니다.", color = Muted, fontSize = 16.sp)
                }
            } else if (settings) {
                BackHandler { settings = false }
                ConnectionScreen(model.suggestedAddress, model.hasToken, close = { settings = false }) { address, token ->
                    val error = model.saveConnection(address, token)
                    if (error == null) settings = false
                    error
                }
            } else {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    BrandHeader(state.loading) { settings = true }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Spacer(Modifier.height(4.dp))
                        Text("03 / SETUP COMPANION", color = Lime, fontSize = 10.sp, letterSpacing = 1.8.sp, fontFamily = FontFamily.Monospace)
                        Text("주행의 다음 선택", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                        Text("PC에서 분석한 주행 기록을 확인하세요.", color = Muted, fontSize = 12.sp)
                        if (diagnostics != null) OutlinedButton(onClick = diagnostics) { Text("차량 데이터 연결 확인") }
                        StatusMessage(state)
                        if (state.received == null) EmptyCard(state.phase)
                        else ResultContent(state.received!!, state.stale)
                        InfoCard("사용 안내", "PC와 분석 서버가 켜져 있어야 새 결과를 받을 수 있습니다. 프리셋은 시뮬레이터에서 직접 바꾼 뒤 같은 조건으로 비교하세요.")
                        Text(state.baseUrl, fontFamily = FontFamily.Monospace, color = Muted, fontSize = 10.sp)
                        Text("로컬 데모 · 시뮬레이션 결과 조회 전용", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    HorizontalDivider(color = Line)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
                        Button(onClick = refresh, enabled = !state.loading, shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                            if (state.loading) {
                                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Lime)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(when {
                                state.loading -> "최신 결과를 확인하고 있어요"
                                state.phase == Phase.FAILURE -> "다시 시도 · 최신 추천 불러오기"
                                else -> "최신 추천 불러오기"
                            }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun BrandHeader(busy: Boolean, settings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Lime), contentAlignment = Alignment.Center) {
            Text("n", color = Background, fontSize = 29.sp, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("NOSTALIFE", fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text("DRIVING LAB", fontSize = 8.sp, color = Muted, letterSpacing = 2.sp)
        }
        TextButton(onClick = settings, enabled = !busy, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text("연결 설정", fontSize = 12.sp)
        }
    }
}
@Composable
private fun Tag(text: String, warning: Boolean = false) {
    Surface(color = if (warning) Color(0xFF3B2D22) else Color(0xFF304028),
        shape = RoundedCornerShape(5.dp)) {
        Text(text, color = if (warning) Amber else Lime, fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}
@Composable
private fun StatusMessage(state: ScreenState) {
    val title: String
    val message: String
    val warning: Boolean
    when (state.phase) {
        Phase.FAILURE -> {
            title = if (state.received != null) "연결 실패 · 이전 결과 표시 중" else "연결 실패"
            message = state.error ?: "다시 시도해 주세요."
            warning = true
        }
        Phase.CACHED -> { title = "이전 결과 · 최신 여부 미확인"; message = "저장된 결과입니다. 최신 추천 불러오기로 PC의 현재 결과를 확인하세요."; warning = true }
        Phase.LOADING -> { title = "PC 서버에 연결 중"; message = if (state.received != null) "새 결과를 받기 전까지 이전 결과를 표시합니다." else "저장된 최신 결과를 요청하고 있습니다."; warning = false }
        Phase.EMPTY -> { title = "저장된 결과 없음"; message = "PC에서 주행 로그를 분석한 뒤 다시 불러오세요."; warning = false }
        Phase.HELD -> { title = "최신 보류 결과를 받았습니다"; message = "유효 랩이 부족해 추천 후보를 만들지 않았습니다."; warning = true }
        Phase.SUCCESS -> { title = "최신 결과 수신 완료"; message = "PC에 저장된 분석 결과와 연결되었습니다."; warning = false }
        else -> return
    }
    Surface(shape = RoundedCornerShape(12.dp), color = if (warning) Color(0xFF30271F) else Color(0xFF233020),
        border = BorderStroke(1.dp, if (warning) Color(0xFF6A513B) else Color(0xFF435737))) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontSize = 12.sp, color = if (warning) Amber else Lime, fontWeight = FontWeight.SemiBold)
            Text(message, fontSize = 11.sp, lineHeight = 18.sp, color = Muted)
            if (state.stale) state.received?.let { Text("마지막 수신  "+time(it.receivedAt), color = Amber, fontSize = 10.sp) }
        }
    }
}
@Composable
private fun EmptyCard(phase: Phase) {
    Surface(color = Panel, shape = shape, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(88.dp)) {
                val w = size.width; val h = size.height
                drawRoundRect(color = Color(0xFF354432), topLeft = androidx.compose.ui.geometry.Offset(w*.1f, h*.1f),
                    size = androidx.compose.ui.geometry.Size(w*.8f, h*.8f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h*.4f), style = Stroke(3.dp.toPx()))
                val path = Path().apply { moveTo(w*.30f,h*.50f); lineTo(w*.42f,h*.50f); lineTo(w*.48f,h*.28f); lineTo(w*.57f,h*.69f); lineTo(w*.64f,h*.50f); lineTo(w*.72f,h*.50f) }
                drawPath(path, Lime, style = Stroke(2.dp.toPx()))
            }
            Text(if (phase == Phase.EMPTY) "분석 기록을 먼저 만들어주세요" else "PC의 분석 결과를 기다립니다",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("주행 로그를 분석한 뒤\n아래 버튼으로 최신 결과를 불러오세요.",
                fontSize = 12.sp, lineHeight = 21.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("주행  →  로컬 분석  →  결과 조회", color = Lime, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
@Composable
private fun ResultContent(received: ReceivedResult, stale: Boolean) {
    val r = received.result
    Surface(color = Color(0xFF24301F), shape = shape, border = BorderStroke(1.dp, Color(0xFF4F643E))) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Tag(if (r.analysisMode == "local_model") "로컬 모델" else "규칙 기반")
                Tag("시뮬레이션")
            }
            Text(if (r.status == "held") "MORE VALID LAPS NEEDED" else "NEXT SETUP CANDIDATE",
                fontSize = 9.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(r.recommended?.label ?: "추천 보류", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                if (r.recommended?.id == r.current.id) Tag("현재 셋업 유지")
            }
            Text(r.holdReason ?: r.rationale, fontSize = 12.sp, lineHeight = 21.sp, color = Color(0xFFC0D0B5))
            HorizontalDivider(color = Color(0xFF43563A))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SmallLabel("현재 셋업"); Text(r.current.label, fontSize = 15.sp) }
                Text("→", color = Muted, modifier = Modifier.padding(horizontal = 20.dp))
                Column(Modifier.weight(1f)) { SmallLabel("다음 비교 후보"); Text(r.recommended?.label ?: "후보 없음", fontSize = 15.sp) }
            }
            if (r.synthetic) Text("합성 모델 검증용 · 실제 차량 효과를 뜻하지 않습니다.", color = Muted, fontSize = 10.sp, lineHeight = 17.sp)
            if (stale) Tag("이전 결과", warning = true)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("랩타임 중앙값", r.observed["lap_time_median_s"], "s", Modifier.weight(1f))
        MetricCard("랩타임 표준편차", r.observed["lap_time_std_s"], "s", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("조향 변화 P95", r.observed["steering_rate_p95_per_s"], "/s", Modifier.weight(1f))
        MetricCard("브레이크 해제 P95", r.observed["brake_release_p95_per_s"], "/s", Modifier.weight(1f))
    }
    if (r.vehicleId == "nostalife-virtual-3d-01") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("횡G P95", r.observed["lat_g_p95_abs"], "g", Modifier.weight(1f))
            MetricCard("종G P95", r.observed["long_g_p95_abs"], "g", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("가속 페달 평균", r.observed["throttle_mean_pct"], "%", Modifier.weight(1f))
            MetricCard("브레이크 평균", r.observed["brake_mean_pct"], "%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("전륜 변위 차 P95", r.observed["front_suspension_diff_p95_mm"], "mm", Modifier.weight(1f))
            MetricCard("후륜 변위 차 P95", r.observed["rear_suspension_diff_p95_mm"], "mm", Modifier.weight(1f))
        }
    }
    Surface(color = Panel, shape = shape, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeading("세션 기록", "SESSION")
            Detail("차종", (if (r.vehicleId == "nostalife-virtual-3d-01") "Nostalife Virtual 3D\n" else "Nostalife Virtual 01\n")+r.vehicleId)
            Detail("코스", (if (r.vehicleId == "nostalife-virtual-3d-01") "Nostalife Proving Ground\n" else "Nostalife Loop\n")+r.trackId)
            Detail("주행 출처", if (r.source == "simulation_autoplay") "시뮬레이션 · 자동 주행" else "시뮬레이션 · 수동 입력")
            Detail("선택한 유효 랩", if (r.selectedLaps.isEmpty()) "없음" else r.selectedLaps.joinToString(" · ") { "LAP $it" })
            Detail("분석 시각", time(r.createdAt, utc = true))
            Detail(if (stale) "이전 결과 마지막 수신" else "마지막 수신", time(received.receivedAt))
            HorizontalDivider(color = Line)
            SmallLabel("세션 ID")
            SelectionContainer { Text(r.sessionId, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 18.sp, color = Lime) }
            SmallLabel("추천 결과 ID")
            SelectionContainer { Text(r.id, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 17.sp, color = Muted) }
        }
    }
    Surface(color = Panel, shape = shape, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeading("다음 주행에서 비교", "COMPARE")
            Text("같은 코스·출발·입력 방식으로 프리셋만 바꿔 3–4개 유효 랩을 기록하세요.",
                fontSize = 11.sp, lineHeight = 19.sp, color = Muted)
            r.comparisons.forEach { metric ->
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(metric.label, fontSize = 12.sp)
                        Text(if (metric.direction == "lower") "감소 여부 비교" else "패턴을 함께 비교", fontSize = 9.sp, color = Muted)
                    }
                    Text(number(metric.baseline)+" "+metric.unit, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Lime)
                }
            }
        }
    }
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Line)) { Text(if (expanded) "분석 근거 접기 −" else "분석 근거와 결측 신호 ＋", fontSize = 12.sp) }
    if (expanded) Surface(color = Panel, shape = shape) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(r.rationale, fontSize = 11.sp, lineHeight = 20.sp, color = TextColor)
            r.limitations.forEach { Text("• "+it, fontSize = 10.sp, lineHeight = 19.sp, color = Muted) }
            SmallLabel("수집되지 않은 신호")
            Text(r.missingSignals.joinToString(", "), fontSize = 10.sp, lineHeight = 17.sp, color = Muted)
            SmallLabel("다음 후보의 실제 시뮬레이터 파라미터")
            val preset = r.recommended
            Text(if (preset == null) "보류 결과에는 프리셋이 없습니다."
                else "조향 이득 "+preset.gain+" · 조향 응답 "+preset.response+" · yaw 감쇠 "+preset.damping,
                fontSize = 10.sp, lineHeight = 18.sp, color = Muted)
        }
    }
}
@Composable
private fun SmallLabel(text: String) { Text(text, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp)) }
@Composable
private fun SectionHeading(title: String, tag: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(tag, color = Muted, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
    }
}
@Composable
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SmallLabel(label); Text(value, fontSize = 12.sp, lineHeight = 19.sp)
    }
}
@Composable
private fun MetricCard(label: String, value: Double?, unit: String, modifier: Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, color = Muted, fontSize = 10.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(number(value), fontSize = 24.sp, fontFamily = FontFamily.Monospace, letterSpacing = (-1).sp)
                Text(unit, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}
@Composable
private fun InfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 11.sp, color = Lime)
        Text(body, fontSize = 11.sp, lineHeight = 20.sp, color = Muted)
    }
}
@Composable
private fun ConnectionScreen(initialAddress: String, hasToken: Boolean, close: () -> Unit,
    save: (String, String) -> String?) {
    var address by remember { mutableStateOf(initialAddress) }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TextButton(onClick = close, contentPadding = PaddingValues(0.dp)) { Text("← 결과 화면") }
        Text("CONNECT YOUR PC", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text("분석 서버 연결", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("PC의 2단계 분석 서버 주소를 입력하세요. 앱에서는 결과만 조회합니다.",
            color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
        OutlinedTextField(value = address, onValueChange = { address = it; error = null },
            label = { Text("서버 기본 주소") }, placeholder = { Text("http://192.168.0.10:4180") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        OutlinedButton(onClick = { address = Connection.EMULATOR_URL; token = ""; error = null },
            shape = RoundedCornerShape(10.dp)) { Text("에뮬레이터 주소 사용", fontSize = 12.sp) }
        OutlinedTextField(value = token, onValueChange = { token = it; error = null },
            label = { Text("데모 토큰 · LAN 연결 시 필수") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            supportingText = { Text(if (hasToken) "현재 주소를 유지하면 입력 없이 기존 토큰을 유지합니다."
                else "토큰은 저장하지 않으며 앱을 다시 열면 입력해야 합니다.", fontSize = 10.sp) })
        if (error != null) Text(error!!, color = Amber, fontSize = 12.sp, lineHeight = 20.sp)
        Button(onClick = { error = save(address, token) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp)) { Text("연결 설정 저장", fontWeight = FontWeight.SemiBold) }
        HorizontalDivider(color = Line)
        InfoCard("에뮬레이터", "10.0.2.2는 이 노트북의 로컬 서버를 가리킵니다. 이 앱의 기본 주소는 ${BuildConfig.DEFAULT_SERVER_URL}입니다.")
        InfoCard("실제 휴대폰", "PC와 휴대폰을 같은 사설 Wi-Fi에 연결하고 PC 내부 IPv4 주소와 무작위 데모 토큰을 입력하세요. PC 서버는 LAN 조회 모드로 실행되어 있어야 합니다.")
        InfoCard("연결 범위", "로컬 HTTP는 디버그 APK에서만 허용됩니다. Android 17 이상에서 요청하는 로컬 네트워크 권한은 PC 결과 조회에 사용합니다.")
        Text("외부망 동기화·푸시 알림·차량 제어는 포함하지 않습니다.", color = Muted, fontSize = 10.sp, lineHeight = 18.sp)
    }
}
