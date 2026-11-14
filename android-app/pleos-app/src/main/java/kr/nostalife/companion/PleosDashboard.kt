package kr.nostalife.companion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Paper = Color(0xFFFAF8F4)
private val Ink = Color(0xFF090909)
private val Muted = Color(0xFF62615E)
private val Red = Color(0xFFE60025)
private val Green = Color(0xFF009D55)
private val Rule = Color(0xFFBDBAB4)
private val PitFigures = FontFamily.SansSerif
private val LocalScale = compositionLocalOf { 1f }
private enum class Page { SUMMARY, MEASUREMENTS, ANALYSIS }
private fun fmt(n: Double?, digits: Int = 2) = n?.let { String.format(Locale.US, "%.$digits" + "f", it) } ?: "—"
private fun firstSentence(text: String) = text.trim().split(Regex("(?<=[.!?。])\\s+|\\n")).firstOrNull().orEmpty()

@Composable
private fun Label(text: String, size: Float, modifier: Modifier = Modifier, color: Color = Ink,
                  weight: FontWeight = FontWeight.Normal, italic: Boolean = false, lines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color = color, fontSize = (size * LocalScale.current).sp,
        lineHeight = (size * 1.3f * LocalScale.current).sp, fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        maxLines = lines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Figure(text: String, size: Float, modifier: Modifier = Modifier, color: Color = Ink) {
    Text(text, modifier, color = color, fontSize = (size * LocalScale.current).sp,
        lineHeight = (size * 1.08f * LocalScale.current).sp, fontFamily = PitFigures,
        fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Slash(height: Float = 42f) {
    val s = LocalScale.current
    Canvas(Modifier.size((height * .34f * s).dp, (height * s).dp)) {
        drawPath(Path().apply {
            moveTo(size.width * .5f, 0f); lineTo(size.width, 0f)
            lineTo(size.width * .5f, size.height); lineTo(0f, size.height); close()
        }, Red)
    }
}

@Composable
private fun MetricHeading(text: String, size: Float = 40f) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((14 * LocalScale.current).dp)) {
        Slash(size * 1.2f)
        Label(text, size, weight = FontWeight.Black, italic = true, lines = 1)
    }
}

@Composable
private fun PitHeader(dark: Boolean, state: ScreenState, connect: () -> Unit) {
    val s = LocalScale.current
    val foreground = if (dark) Paper else Ink
    Row(Modifier.fillMaxWidth().height((82 * s).dp).background(if (dark) Ink else Paper)
        .padding(horizontal = (18 * s).dp), verticalAlignment = Alignment.CenterVertically) {
        if (!dark) {
            Label("Pleos", 43f, weight = FontWeight.Bold)
            Box(Modifier.padding(horizontal = (22 * s).dp).width((2 * s).dp).height((45 * s).dp).background(Ink))
        }
        Box(Modifier.width((80 * s).dp).fillMaxHeight().background(if (dark) Red else Paper), contentAlignment = Alignment.Center) {
            Figure("D", 68f, color = if (dark) Paper else Red)
        }
        Label("DRIVING LAB", 43f, Modifier.weight(1f).padding(start = (18 * s).dp),
            color = foreground, weight = FontWeight.Black, italic = true, lines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy((5 * s).dp)) { repeat(if (dark) 1 else 3) { Slash(52f) } }
        if (dark) Label("Pleos", 33f, Modifier.padding(horizontal = (22 * s).dp), color = Paper, italic = true)
        val connected = state.phase in setOf(Phase.SUCCESS, Phase.HELD, Phase.EMPTY) && !state.stale
        TextButton(onClick = connect) {
            Label(if (connected) "● 연결됨" else if (state.loading) "연결 중" else "○ 웹 연결", 27f,
                color = if (connected) Green else foreground, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Action(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = false) {
    val s = LocalScale.current
    Button(onClick, modifier.heightIn(min = (78 * s).dp), shape = RectangleShape,
        border = if (outlined) BorderStroke((2 * s).dp, Paper) else null,
        colors = ButtonDefaults.buttonColors(containerColor = if (outlined) Ink else Red, contentColor = Paper),
        contentPadding = PaddingValues(horizontal = (26 * s).dp, vertical = (14 * s).dp)) {
        Label(text, 35f, color = Paper, weight = FontWeight.Bold, lines = 1)
    }
}

@Composable
fun PleosDashboard(model: RecommendationViewModel, restricted: Boolean, diagnostics: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(Page.SUMMARY) }
    var connecting by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != Page.SUMMARY && !connecting && !restricted) { page = Page.SUMMARY }
    val pairedCode = state.baseUrl.takeIf { it.startsWith(Connection.PUBLIC_ORIGIN + "/api/channels/") }?.substringAfterLast('/')
    fun connect() { code = pairedCode.orEmpty(); error = null; connecting = true }
    MaterialTheme(colorScheme = lightColorScheme(primary = Red, onPrimary = Color.White,
        background = Paper, surface = Paper, onSurface = Ink, onBackground = Ink, outline = Rule)) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val scale = minOf(maxWidth.value / 1500f, maxHeight.value / 1000f).coerceIn(.4f, 1.6f)
                CompositionLocalProvider(LocalScale provides scale) {
                    val s = LocalScale.current
                    if (restricted) {
                        Column(Modifier.fillMaxSize().background(Ink).padding((64 * s).dp), verticalArrangement = Arrangement.Center) {
                            Label("DRIVING LAB", 36f, color = Paper, weight = FontWeight.Black, italic = true)
                            Spacer(Modifier.height((40 * s).dp))
                            Slash(64f)
                            Label("분석은 정차 후에 확인하세요", 56f, color = Paper, weight = FontWeight.Bold)
                            Label("주행 중에는 화면 조작이 제한됩니다.", 29f, color = Color.LightGray)
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(horizontal = (26 * s).dp, vertical = (12 * s).dp)) {
                            PitHeader(page != Page.SUMMARY, state) { connect() }
                            HorizontalDivider(thickness = (3 * s).dp, color = Ink)
                            if (state.phase == Phase.FAILURE || state.stale) {
                                Row(Modifier.fillMaxWidth().background(Color(0xFFFFE9D9)).padding((12 * s).dp), verticalAlignment = Alignment.CenterVertically) {
                                    val stamp = state.received?.let { time(it.receivedAt) }
                                    Label(state.error ?: "이전 결과${stamp?.let { " · $it 수신" }.orEmpty()}를 표시 중입니다.", 24f,
                                        Modifier.weight(1f), color = Color(0xFF843A17), lines = 2)
                                    TextButton(onClick = { model.refresh() }, enabled = !state.loading) { Label("다시 연결", 24f, color = Red, weight = FontWeight.Bold) }
                                }
                            }
                            val received = state.received
                            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = (16 * s).dp)) {
                                if (received == null) EmptyScreen(pairedCode, state, { connect() }, { model.refresh() })
                                else when (page) {
                                    Page.SUMMARY -> Summary(received.result, { page = Page.MEASUREMENTS }, { page = Page.ANALYSIS })
                                    Page.MEASUREMENTS -> Measurements(received, { page = Page.SUMMARY })
                                    Page.ANALYSIS -> Analysis(received, { page = Page.SUMMARY })
                                }
                            }
                            HorizontalDivider(thickness = (2 * s).dp, color = Ink)
                            Row(Modifier.fillMaxWidth().height((49 * s).dp), verticalAlignment = Alignment.CenterVertically) {
                                Label("PIT WALL · 4.2", 21f, weight = FontWeight.Black, italic = true)
                                Spacer(Modifier.width((25 * s).dp))
                                Label(received?.let { "가상 주행 · 최근 수신 ${time(it.receivedAt)}" } ?: "웹에서 주행 후 분석을 보내세요", 21f, Modifier.weight(1f), color = Muted, lines = 1)
                                TextButton(onClick = { model.refresh() }, enabled = !state.loading) { Label("새로고침", 22f, color = Muted) }
                            }
                        }
                    }
                    if (connecting && !restricted) AlertDialog(onDismissRequest = { connecting = false }, shape = RectangleShape,
                        title = { Label("웹과 연결하기", 38f, weight = FontWeight.Black) },
                        text = { Column(verticalArrangement = Arrangement.spacedBy((20 * s).dp)) {
                            Label("웹과 이 앱의 코드가 같아야 결과가 도착합니다.", 27f)
                            OutlinedTextField(value = code, onValueChange = { code = it.uppercase(); error = null }, singleLine = true,
                                label = { Text("웹 연결 코드 10자리") },
                                textStyle = LocalTextStyle.current.copy(fontSize = (34 * s).sp, fontFamily = FontFamily.Monospace))
                            error?.let { Label(it, 25f, color = Red) }
                            TextButton(onClick = { connecting = false; diagnostics() }) { Label("차량 데이터 진단", 23f, color = Muted) }
                        } },
                        confirmButton = { Button(onClick = { error = model.savePublicCode(code); if (error == null) { connecting = false; page = Page.SUMMARY; model.refresh() } }, shape = RectangleShape) { Label("연결", 28f, color = Paper, weight = FontWeight.Bold) } },
                        dismissButton = { TextButton(onClick = { connecting = false }) { Label("닫기", 26f, color = Muted) } })
                }
            }
        }
    }
}

private fun time(stamp: java.time.Instant): String = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(stamp)

@Composable
private fun Summary(r: Recommendation, measurements: () -> Unit, analysis: () -> Unit) {
    val s = LocalScale.current
    val m = r.observed
    val held = r.status == "held"
    val lateral = if (held) r.capture["lat_g_peak_abs"] else m["lat_g_p95_abs"]
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(.52f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((32 * s).dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                MetricHeading(if (held) "횡G 최대" else "횡G P95", 54f)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val number = fmt(lateral)
                    val figureSize = minOf(278f, (maxWidth.value / s - 98f) / (number.length * .58f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Figure(number, figureSize)
                        Figure("g", 64f, Modifier.padding(bottom = (28 * s).dp, start = (12 * s).dp))
                    }
                }
            }
            Box(Modifier.fillMaxHeight().width((2 * s).dp).background(Ink))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                CompactMetric(if (held) "종G 최대" else "종G P95",
                    if (held) r.capture["long_g_peak_abs"] else m["long_g_p95_abs"], "g", Modifier.weight(1f), 2)
                HorizontalDivider(thickness = (2 * s).dp, color = Ink)
                CompactMetric("가속 평균", m["throttle_mean_pct"] ?: r.capture["throttle_mean_pct"], "%", Modifier.weight(1f), 1)
                HorizontalDivider(thickness = (2 * s).dp, color = Ink)
                CompactMetric("제동 평균", m["brake_mean_pct"] ?: r.capture["brake_mean_pct"], "%", Modifier.weight(1f), 1)
            }
        }
        HorizontalDivider(Modifier.padding(top = (18 * s).dp), thickness = (3 * s).dp, color = Ink)
        Row(Modifier.weight(.22f).fillMaxWidth().padding(vertical = (12 * s).dp), verticalAlignment = Alignment.CenterVertically) {
            Stat("기록 시간", r.capture["duration_s"] ?: m["duration_s"], "초", Modifier.weight(1f), 2)
            Box(Modifier.width((1 * s).dp).fillMaxHeight(.8f).background(Ink))
            Stat("주행 거리", m["distance_m"], "m", Modifier.weight(1f), 0)
            Box(Modifier.width((1 * s).dp).fillMaxHeight(.8f).background(Ink))
            Stat("최고 속도", m["speed_max_kmh"] ?: r.capture["speed_max_kmh"], "km/h", Modifier.weight(1.25f), 1)
        }
        Row(Modifier.weight(.26f).fillMaxWidth().background(Ink).padding((24 * s).dp), horizontalArrangement = Arrangement.spacedBy((24 * s).dp)) {
            Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((14 * s).dp)) {
                    Slash(58f)
                    Label(if (r.ai == null) "새 분석이 필요해요" else r.recommended?.let { "${it.label} 비교하기" } ?: "현재 설정 유지",
                        48f, color = Paper, weight = FontWeight.Black, italic = true, lines = 2)
                }
                Spacer(Modifier.height((12 * s).dp))
                Label(r.ai?.presetReason?.let(::firstSentence) ?: "웹에서 다시 분석을 보내주세요.", 24f,
                    Modifier.padding(start = (34 * s).dp), color = Color(0xFFD2D0CC), lines = 2)
            }
            Box(Modifier.width((1 * s).dp).fillMaxHeight().background(Color(0xFF777570)))
            Column(Modifier.weight(1.05f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Label("다음 주행 팁", 23f, color = Color(0xFFD2D0CC), weight = FontWeight.Bold)
                    Label(r.ai?.coaching?.let(::firstSentence) ?: "새 주행 기록을 보내면 운전 팁을 확인할 수 있어요.", 29f,
                        Modifier.padding(top = (6 * s).dp), color = Paper, weight = FontWeight.Bold, italic = true, lines = 3)
            }
            Column(Modifier.weight(.9f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Action("세부 측정값", measurements, Modifier.fillMaxWidth())
                TextButton(onClick = analysis, modifier = Modifier.fillMaxWidth()) {
                    Label("분석 보기  ›", 32f, color = Paper, weight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CompactMetric(title: String, value: Double?, unit: String, modifier: Modifier, digits: Int) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { MetricHeading(title, 34f) }
        Figure(fmt(value, digits), 106f)
        Figure(unit, 42f, Modifier.padding(top = (28 * LocalScale.current).dp, start = (5 * LocalScale.current).dp))
    }
}

@Composable
private fun Stat(title: String, value: Double?, unit: String, modifier: Modifier, digits: Int) {
    val s = LocalScale.current
    Column(modifier.padding(horizontal = (20 * s).dp), verticalArrangement = Arrangement.spacedBy((7 * s).dp)) {
        MetricHeading(title, 30f)
        Row(verticalAlignment = Alignment.Bottom) {
            Figure(fmt(value, digits), 94f)
            Figure(unit, if (unit == "km/h") 34f else 44f, Modifier.padding(start = (7 * s).dp, bottom = (10 * s).dp))
        }
    }
}

@Composable
private fun EmptyScreen(code: String?, state: ScreenState, connect: () -> Unit, refresh: () -> Unit) {
    val s = LocalScale.current
    Column(Modifier.fillMaxSize().padding((35 * s).dp), verticalArrangement = Arrangement.Center) {
        Slash(72f)
        Label(if (state.loading) "주행 결과를 불러오는 중" else "첫 주행을 기다리고 있어요", 66f, weight = FontWeight.Black)
        Spacer(Modifier.height((28 * s).dp))
        Label("웹에서 주행한 뒤 ‘분석하고 앱으로 보내기’를 누르세요.", 34f, color = Muted)
        Label(code?.let { "연결 코드  ${it.take(5)}-${it.drop(5)}" } ?: "웹에 표시된 연결 코드를 먼저 입력하세요.", 34f,
            Modifier.padding(vertical = (26 * s).dp), weight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy((16 * s).dp)) {
            Action("웹 연결", connect)
            Button(onClick = refresh, shape = RectangleShape, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Label("다시 불러오기", 31f, color = Paper) }
        }
    }
}

@Composable
private fun DetailHeader(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = (12 * LocalScale.current).dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { MetricHeading(title, 58f) }
        Action("← 요약", back)
    }
}

private data class GridMetric(val title: String, val value: String, val unit: String = "", val missing: Boolean = false)

@Composable
private fun Measurements(received: ReceivedResult, back: () -> Unit) {
    val r = received.result
    val s = LocalScale.current
    val m = r.observed
    fun metric(title: String, number: Double?, unit: String, digits: Int = 2) =
        GridMetric(title, fmt(number, digits), unit, number == null)
    val metrics = listOf(
        metric("횡G P95", m["lat_g_p95_abs"], "g"),
        metric("종G P95", m["long_g_p95_abs"], "g"),
        metric("가속 평균", m["throttle_mean_pct"] ?: r.capture["throttle_mean_pct"], "%", 1),
        metric("제동 평균", m["brake_mean_pct"] ?: r.capture["brake_mean_pct"], "%", 1),
        metric("전륜 변위 차 P95", m["front_suspension_diff_p95_mm"], "mm"),
        metric("후륜 변위 차 P95", m["rear_suspension_diff_p95_mm"], "mm"),
        metric("조향 변화 P95", m["steering_rate_p95_per_s"], "/s"),
        metric("브레이크 해제 P95", m["brake_release_p95_per_s"], "/s"),
        metric("기록 시간", r.capture["duration_s"], "초"),
        metric("주행 거리", m["distance_m"], "m", 0),
        metric("최고 속도", m["speed_max_kmh"] ?: r.capture["speed_max_kmh"], "km/h", 1),
        GridMetric("분석 구간", if (r.ai != null) "시작 → 버튼" else "이전 기록")
    )
    Column(Modifier.fillMaxSize()) {
        DetailHeader("세부 측정값", back)
        Label(if (r.ai != null) "주행 시작 → 분석 버튼 · ${fmt(r.capture["duration_s"])}초" else "이전 방식으로 분석된 기록",
            27f, Modifier.padding(bottom = (18 * s).dp), weight = FontWeight.Bold, italic = true)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Keep all twelve cells visible in the available parked-app panel.
            val cellScale = minOf(s, maxWidth.value / 1448f, maxHeight.value / 640f)
            CompositionLocalProvider(LocalScale provides cellScale) {
                Column(Modifier.fillMaxSize()) {
                    metrics.chunked(3).forEach { row ->
                        HorizontalDivider(thickness = (2 * cellScale).dp, color = Ink)
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            row.forEachIndexed { index, item ->
                                if (index > 0) Box(Modifier.width((1.5f * cellScale).dp).fillMaxHeight().background(Ink))
                                MeasurementCell(item, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                    HorizontalDivider(thickness = (2 * cellScale).dp, color = Ink)
                }
            }
        }
        Label("P95: 95백분위 · —: 관측 없음 · 조향·페달 변화율: 정규화 입력/초",
            21f, Modifier.padding(top = (12 * s).dp), color = Muted, lines = 1)
    }
}

@Composable
private fun MeasurementCell(item: GridMetric, modifier: Modifier) {
    val s = LocalScale.current
    Column(modifier.padding(horizontal = (18 * s).dp, vertical = (10 * s).dp)) {
        MetricHeading(item.title, 28f)
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (item.unit.isEmpty()) {
                Label(item.value, 46f, weight = FontWeight.Black, italic = true, lines = 1)
            } else {
                Figure(item.value, 82f)
                Figure(item.unit, 31f, Modifier.padding(start = (12 * s).dp, top = (26 * s).dp))
                if (item.missing) Label("관측 없음", 21f, Modifier.padding(start = (18 * s).dp, top = (26 * s).dp), color = Muted, lines = 1)
            }
        }
    }
}

@Composable
private fun Analysis(received: ReceivedResult, back: () -> Unit) {
    val r = received.result
    val s = LocalScale.current
    Column(Modifier.fillMaxSize()) {
        DetailHeader("주행 분석", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy((24 * s).dp)) {
            Label(r.ai?.headline ?: "이전 분석 기록", 43f, weight = FontWeight.Black)
            val sections = listOf("주행 진단" to (r.ai?.diagnosis ?: r.rationale), "운전 팁" to r.ai?.coaching,
                "현재 설정 · ${r.current.label}" to r.ai?.presetReason, "다음 주행에서 확인" to r.ai?.nextCheck)
            sections.forEach { (title, body) -> if (!body.isNullOrBlank()) {
                HorizontalDivider(color = Rule)
                Label(title, 27f, color = Red, weight = FontWeight.Bold)
                Label(body, 29f)
            } }
            HorizontalDivider(color = Ink)
            Label("추가 측정값", 27f, color = Red, weight = FontWeight.Bold)
            Label("전륜 슬립각 P95  ${fmt(r.observed["front_slip_p95_abs"], 3)} rad   ·   후륜 슬립각 P95  ${fmt(r.observed["rear_slip_p95_abs"], 3)} rad", 27f)
            Label(r.limitations.joinToString("\n"), 23f, color = Muted)
            Label("수신 ${time(received.receivedAt)} · 주행 ${r.sessionId.take(8)} · 결과 ${r.id.take(8)}", 23f, color = Muted)
        }
    }
}
