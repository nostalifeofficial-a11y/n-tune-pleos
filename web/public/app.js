import { sendAnalysis, refreshDelivery } from './pairing.js?v=4';
import { createScene } from './scene.js';
const S = window.Nostalife3D, $ = id => document.getElementById(id);
const viewport = $('viewport');
let scene;
try { scene = createScene(viewport); $('scene-message').hidden = true; }
catch (e) { $('scene-message').textContent = '3D 화면을 열 수 없습니다. 브라우저의 그래픽 가속을 확인해 주세요. ' + e.message; throw e; }
let session = new S.Session(), lastFrame = null, accumulator = 0, previousPaint = 0, result = null;
const held = new Set();
const sliders = { steering: $('steer-slider'), throttle: $('throttle-slider'), brake: $('brake-slider') };
const manual = { steering: 0, throttle: 0, brake: 0 };
let sliderControl = false, analyzing = false;
// Publish only current simulator values to this PC. This is separate from online AI analysis.
let publishing = false;
setInterval(async () => {
  if (publishing) return;
  publishing = true;
  try { await fetch('/local/sim', {method:'POST', headers:{'Content-Type':'application/json'},
    body:JSON.stringify({session_id:session.sessionId, values:session.sample(), running:session.status === 'running'})}); }
  catch {} finally { publishing = false; }
}, 250);
const formatTime = ms => `${Math.floor(ms / 60000)}:${(ms % 60000 / 1000).toFixed(2).padStart(5, '0')}`;
for (const id of ['suspension', 'slip']) for (const wheel of S.CORNERS) {
  const box = document.createElement('div'); box.className = 'wheel';
  box.innerHTML = `<span>${wheel.id.toUpperCase()}</span><b id="${id}-${wheel.id}">0.0</b><i id="${id}-bar-${wheel.id}"></i>`;
  $(id).appendChild(box);
}
function clearInputs() { held.clear(); sliderControl = false; Object.keys(manual).forEach(key => { manual[key] = 0; sliders[key].value = '0'; }); }
function newSession(mode) {
  if (analyzing) return;
  clearInputs(); result = null; $('result').hidden = true;
  session = new S.Session({ preset: $('preset').value, mode, targetLaps: 4 });
  session.start(); accumulator = 0; lastFrame = null;
  $('session').textContent = `${mode === 'manual' ? '직접 운전' : '자동 주행'} · ${session.sessionId}`;
  viewport.focus({ preventScroll: true });
}
$('start').onclick = () => newSession('manual');
$('autoplay').onclick = () => newSession('autoplay');
function pause() { if (analyzing) return; if (session.status === 'running') session.pause(); else session.start(); accumulator = 0; lastFrame = null; clearInputs(); }
$('pause').onclick = pause;
$('recover').onclick = () => { session.recover(); clearInputs(); viewport.focus({ preventScroll: true }); };
function cameraNext() { const modes = ['chase', 'hood', 'overview']; $('camera').value = modes[(modes.indexOf($('camera').value) + 1) % modes.length]; }
function editing(event) { return ['INPUT', 'SELECT', 'TEXTAREA', 'BUTTON'].includes(event.target.tagName); }
window.addEventListener('keydown', event => {
  if (editing(event)) return;
  if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Space', 'KeyW', 'KeyA', 'KeyS', 'KeyD'].includes(event.code)) event.preventDefault();
  if (!event.repeat && event.code === 'Space') pause();
  if (!event.repeat && event.code === 'KeyC') cameraNext();
  if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'KeyW', 'KeyA', 'KeyS', 'KeyD'].includes(event.code)) { sliderControl = false; held.add(event.code); }
});
window.addEventListener('keyup', event => held.delete(event.code));
window.addEventListener('blur', () => { if (session.status === 'running') session.pause(); clearInputs(); });
document.addEventListener('visibilitychange', () => { if (document.hidden) { session.pause(); clearInputs(); } });
Object.entries(sliders).forEach(([key, slider]) => slider.addEventListener('input', () => { sliderControl = true; manual[key] = Number(slider.value); }));
function inputs(dt) {
  const pad = navigator.getGamepads?.()?.find(p => p && p.connected);
  if (pad && (Math.abs(pad.axes[0] || 0) > .08 || (pad.buttons[6]?.value || 0) > .02 || (pad.buttons[7]?.value || 0) > .02)) {
    return { steering: Math.abs(pad.axes[0]) > .08 ? pad.axes[0] : 0, throttle: pad.buttons[7]?.value || 0, brake: pad.buttons[6]?.value || 0 };
  }
  if (!sliderControl) {
    const left = held.has('KeyA') || held.has('ArrowLeft'), right = held.has('KeyD') || held.has('ArrowRight');
    const target = Number(right) - Number(left), steerRate = target ? 1.5 : 2.4;
    manual.steering += Math.max(-steerRate * dt, Math.min(steerRate * dt, target - manual.steering));
    manual.throttle = held.has('KeyW') || held.has('ArrowUp') ? 1 : 0;
    manual.brake = held.has('KeyS') || held.has('ArrowDown') ? 1 : 0;
  }
  return manual;
}
function drawInstruments() {
  const state = session.state;
  $('speed').textContent = Math.round(state.speed * 3.6);
  $('laps').textContent = session.tracker.validCount;
  $('lap-time').textContent = formatTime(session.time - session.tracker.startTime);
  $('steer-value').textContent = (state.roadAngle * S.VEHICLE.steering_ratio * 180 / Math.PI).toFixed(1) + '°';
  $('throttle-value').textContent = (state.throttle * 100).toFixed(0) + '%';
  $('brake-value').textContent = (state.brake * 100).toFixed(0) + '%';
  $('steering-angle').textContent = $('steer-value').textContent;
  $('yaw').textContent = (state.yaw * 180 / Math.PI).toFixed(1) + ' °/s';
  $('lat-g').textContent = (state.ay / S.G).toFixed(2); $('long-g').textContent = (state.ax / S.G).toFixed(2);
  $('pause').textContent = session.status === 'paused' ? '주행 계속하기' : '일시 정지';
  $('status').textContent = ({ ready: '운전 준비', running: session.mode === 'manual' ? '직접 운전 · 기록 중' : '자동 주행 · 기록 중', paused: '일시 정지', finished: '주행 완료 · 분석할 수 있습니다' })[session.status];
  $('validity').textContent = session.tracker.reasons.size ? '이번 랩 무효 · ' + [...session.tracker.reasons].join(', ') : `현재 랩 정상 · 체크포인트 ${session.tracker.checkpoints.length}/8`;
  for (const wheel of state.wheels) {
    const travel = wheel.travel * 1000, slip = wheel.slipAngle * 180 / Math.PI;
    $('suspension-' + wheel.id).textContent = travel.toFixed(1);
    $('slip-' + wheel.id).textContent = slip.toFixed(2);
    $('suspension-bar-' + wheel.id).style.width = Math.min(100, Math.abs(travel) / 80 * 100) + '%';
    $('slip-bar-' + wheel.id).style.width = Math.min(100, Math.abs(slip) / 10 * 100) + '%';
  }
  const map = $('minimap').getContext('2d'); map.clearRect(0, 0, 220, 130); map.strokeStyle = '#91aaa8'; map.lineWidth = 8; map.beginPath();
  const mx = x => (x - 35) / 2, my = y => (y - 60) / 1.65 + 10;
  for (let i = 0; i <= 180; i++) { const p = S.pointAt(i / 180 * S.TRACK.length_m); i ? map.lineTo(mx(p.x), my(p.y)) : map.moveTo(mx(p.x), my(p.y)); }
  map.stroke(); map.fillStyle = '#d4f799'; map.beginPath(); map.arc(mx(state.x), my(state.y), 4, 0, Math.PI * 2); map.fill();
  const g = $('gplot').getContext('2d'); g.clearRect(0, 0, 200, 180); g.strokeStyle = '#35464b'; g.lineWidth = 1;
  for (const radius of [35, 70]) { g.beginPath(); g.arc(100, 90, radius, 0, Math.PI * 2); g.stroke(); }
  g.beginPath(); g.moveTo(20, 90); g.lineTo(180, 90); g.moveTo(100, 10); g.lineTo(100, 170); g.stroke();
  g.fillStyle = '#90a6a3'; g.font = '10px monospace'; g.fillText('1G', 168, 86); g.fillText('가속', 88, 15);
  g.fillStyle = '#d4f799'; g.beginPath(); g.arc(100 + Math.max(-1.2, Math.min(1.2, state.ay / S.G)) * 60, 90 - Math.max(-1.2, Math.min(1.2, state.ax / S.G)) * 60, 6, 0, Math.PI * 2); g.fill();
  $('lap-list').replaceChildren(...session.tracker.laps.slice(-5).map(lap => { const row = document.createElement('div'); row.textContent = `LAP ${lap.lap}  ${formatTime(lap.lap_time_ms)}  ${lap.valid ? '✓' : '무효'}`; return row; }));
}
function frame(timestamp) {
  const elapsed = lastFrame === null ? 0 : Math.min(100, timestamp - lastFrame); lastFrame = timestamp;
  if (session.status === 'running') {
    accumulator += elapsed * (session.mode === 'autoplay' && $('fast').checked ? 4 : 1);
    while (accumulator >= S.STEP_MS && session.status === 'running') { session.step(inputs(S.STEP_MS / 1000)); accumulator -= S.STEP_MS; }
  }
  scene.render(session.state, $('camera').value);
  if (timestamp - previousPaint > 80) { drawInstruments(); previousPaint = timestamp; }
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
function download(log) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(log)], { type: 'application/json' }));
  const a = document.createElement('a'); a.href = url; a.download = `nostalife-3d-${log.session_id}.json`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
$('download').onclick = () => { session.pause(); clearInputs(); download(session.exportLog()); };
$('analyze').onclick = async () => {
  if (analyzing) return;
  session.pause(); clearInputs(); analyzing = true;
  const locked = ['analyze','start','autoplay','pause','recover','renew-code'];
  locked.forEach(id => { $(id).disabled = true; });
  const capturedLog = session.exportLog(), panel = $('result'); panel.hidden = false; panel.textContent = '기록을 여기까지 멈췄습니다. AI가 이번 주행 전체를 분석하고 있습니다…';
  try {
    result = await sendAnalysis(capturedLog);
    panel.replaceChildren();
    const title = document.createElement('h2'); title.textContent = result.analysis.headline; panel.append(title);
    const scope = document.createElement('p'); scope.className='analysis-scope'; scope.textContent = `시작 → 분석 버튼 · ${result.analysis_window.duration_s.toFixed(2)}초 · ${result.analysis_window.sample_count.toLocaleString()}개 표본`; panel.append(scope);
    const conclusion=document.createElement('section'),target=result.analysis.driving_targets;
    const conclusionTitle=document.createElement('h3');conclusionTitle.textContent='AI 주행 결론';conclusion.append(conclusionTitle);
    const conclusionText=document.createElement('p');conclusionText.textContent=target?`승차감 ${target.comfort} / 조향응답성 ${target.steering_response} · ${target.reason}`:'분석은 도착했습니다. 현재 서버는 수치 결론을 반환하지 않아 추천값은 대기 중입니다.';conclusion.append(conclusionText);panel.append(conclusion);
    for(const [key,label] of [['diagnosis','주행 진단'],['driver_coaching','운전 팁'],['preset_reason','프리셋 비교'],['next_check','다음 주행에서 확인']]){
      const section=document.createElement('section'),heading=document.createElement('h3'),text=document.createElement('p');heading.textContent=label;text.textContent=result.analysis[key];section.append(heading,text);panel.append(section);
    }
    if(result.analysis.limitations.length){const detail=document.createElement('p');detail.className='analysis-limits';detail.textContent=result.analysis.limitations.join(' ');panel.append(detail);}
    const delivery = document.createElement('p'); delivery.id='delivery-status'; delivery.textContent = '서버 저장 완료 · Pleos 수신을 확인하고 있습니다.'; panel.append(delivery);
    refreshDelivery();
    const id = document.createElement('code'); id.textContent = `SESSION ${result.session_id} / RESULT ${result.recommendation_id}`; panel.append(id);
    if (result.recommended_setup) { const apply = document.createElement('button'); apply.textContent = `${result.recommended_setup.label}으로 비교 주행 시작`; apply.onclick = () => { $('preset').value = result.recommended_setup.id; newSession('manual'); }; panel.append(document.createElement('br'), apply); }
    panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  } catch (error) { panel.textContent = '분석 실패: ' + error.message; }
  finally { analyzing = false; locked.forEach(id => { $(id).disabled = false; }); }
};
// Readable diagnostics for local verification; no synthetic input bypass or network side effects.
window.nostalife3dDiagnostics = () => ({ status: session.status, source: session.mode, sessionId: session.sessionId,
  timeMs: session.time, samples: session.samples.length, validLaps: session.tracker.validCount, telemetry: session.sample(),
  rendering: scene.stats(), recommendationId: result?.recommendation_id ?? null });

$('fullscreen').onclick=()=>viewport.requestFullscreen?.();
