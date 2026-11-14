(function (root, factory) {
  const validate = factory();
  if (typeof module === 'object' && module.exports) module.exports = validate;
  else root.validateSimLog = validate;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const presets = { stable: [0.92, 3.8, 5.8], balanced: [1.08, 6, 4.4], rotation: [1.25, 8.5, 3.4] };
  const object = v => v !== null && typeof v === 'object' && !Array.isArray(v);
  const finite = (v, lo, hi) => typeof v === 'number' && Number.isFinite(v) && v >= lo && v <= hi;
  const integer = (v, lo, hi) => Number.isInteger(v) && finite(v, lo, hi);
  const reasons = ['off_track', 'wrong_direction', 'checkpoint_order', 'missing_checkpoints', 'recovery', 'incomplete'];
  return function validate(log) {
    const errors = [];
    const check = (ok, message) => { if (!ok && errors.length < 60) errors.push(message); };
    if (!object(log)) return { valid: false, errors: ['최상위 값은 JSON 객체여야 합니다.'] };
    check(log.schema_version === '1.0.0', 'schema_version: 지원 버전은 1.0.0입니다.');
    check(typeof log.session_id === 'string' && log.session_id.length >= 8 && log.session_id.length <= 128, 'session_id: 8~128자 문자열이어야 합니다.');
    check(typeof log.created_at === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{3})?Z$/.test(log.created_at) && Number.isFinite(Date.parse(log.created_at)), 'created_at: UTC ISO 8601 시각이어야 합니다.');
    check(['simulation_manual', 'simulation_autoplay'].includes(log.source) && log.simulation === true, 'source/simulation: 시뮬레이션 출처가 필요합니다.');
    check(log.vehicle_id === 'nostalife-virtual-01' && log.track_id === 'nostalife-loop-v1', 'vehicle_id/track_id: 알려진 데모 차량·코스가 아닙니다.');
    check(log.simulator?.name === 'nostalife-browser-simulator' && log.simulator?.version === '1.0.0' && log.simulator?.physics_step_ms === 20, 'simulator: 엔진 버전/주기가 다릅니다.');
    check(log.sampling?.interval_ms === 100 && log.sampling?.time_basis === 'elapsed_simulation_time' && log.sampling?.terminal_sample_may_be_irregular === true, 'sampling: 100ms 시뮬레이션 시간 계약이 필요합니다.');
    const expectedUnits = { t_ms: 'ms', speed_kmh: 'km/h', steering: 'normalized [-1,1]; positive right', brake: 'fraction [0,1]', throttle: 'fraction [0,1]', position: 'm', heading_rad: 'rad; screen x-right/y-down', yaw_rate_rad_s: 'rad/s' };
    check(Object.entries(expectedUnits).every(([key, value]) => log.units?.[key] === value), 'units: 단위 계약이 다릅니다.');
    const setup = log.setup;
    check(object(setup) && Object.hasOwn(presets, setup.id), 'setup.id: 알려진 프리셋이어야 합니다.');
    if (object(setup) && Object.hasOwn(presets, setup.id)) {
      check(['steering_gain', 'steering_response_hz', 'yaw_damping_hz'].every((k, i) => setup[k] === presets[setup.id][i]), 'setup: 프리셋 실제 파라미터와 일치하지 않습니다.');
      check(setup.label === { stable: '안정형', balanced: '균형형', rotation: '회전형' }[setup.id], 'setup.label: 프리셋 이름이 다릅니다.');
    }
    check(['ready', 'running', 'paused', 'finished'].includes(log.status), 'status: 지원하지 않는 상태입니다.');
    check(log.status === 'finished' ? ['target_laps', 'duration_limit', 'user_stop', 'new_session', 'fixture_end'].includes(log.finish_reason) : log.finish_reason === null, 'finish_reason: 상태와 종료 사유가 맞지 않습니다.');
    check(integer(log.duration_ms, 0, 1800000) && log.duration_ms % 20 === 0, 'duration_ms: 0~1800000, 20ms 배수여야 합니다.');
    const samples = Array.isArray(log.samples) ? log.samples : [];
    const laps = Array.isArray(log.laps) ? log.laps : [];
    check(Array.isArray(log.samples) && samples.length > 0 && samples.length <= 18002, 'samples: 1~18002개 배열이어야 합니다.');
    check(Array.isArray(log.laps), 'laps: 배열이어야 합니다.');
    let previous = -1;
    samples.forEach((s, i) => {
      if (!object(s)) { check(false, `samples[${i}]: 객체가 아닙니다.`); return; }
      const tag = `samples[${i}]`;
      check(integer(s.t_ms, 0, log.duration_ms) && s.t_ms > previous && s.t_ms % 20 === 0, `${tag}.t_ms: 증가하는 20ms 배수여야 합니다.`);
      check(i === samples.length - 1 || s.t_ms === i * 100, `${tag}.t_ms: 중간 샘플 간격은 100ms여야 합니다.`);
      if (i === samples.length - 1 && i > 0) check(s.t_ms - previous <= 100, `${tag}.t_ms: 끝 샘플 간격이 100ms를 넘었습니다.`);
      previous = s.t_ms;
      check(integer(s.lap, 1, laps.length + 1) && integer(s.sector, 1, 4), `${tag}: 랩/구간 범위가 잘못되었습니다.`);
      check(finite(s.speed_kmh, 0, 130) && finite(s.steering, -1, 1) && finite(s.brake, 0, 1) && finite(s.throttle, 0, 1), `${tag}: 속도·입력 범위가 잘못되었습니다.`);
      check(finite(s.progress, 0, 1) && finite(s.x_m, -1e7, 1e7) && finite(s.y_m, -1e7, 1e7) && finite(s.heading_rad, -3.1416, 3.1416) && finite(s.yaw_rate_rad_s, -10, 10), `${tag}: 위치·회전 수치가 잘못되었습니다.`);
      check(typeof s.on_track === 'boolean' && typeof s.lap_valid_so_far === 'boolean', `${tag}: 유효성 값은 boolean이어야 합니다.`);
    });
    check(samples[0]?.t_ms === 0 && samples.at(-1)?.t_ms === log.duration_ms, 'samples: 시작(0)과 종료 시각 샘플이 필요합니다.');
    let lastEnd = 0;
    laps.forEach((l, i) => {
      if (!object(l)) { check(false, `laps[${i}]: 객체가 아닙니다.`); return; }
      const tag = `laps[${i}]`;
      check(l.lap === i + 1 && l.start_t_ms === lastEnd && integer(l.end_t_ms, l.start_t_ms + 1, log.duration_ms), `${tag}: 랩 번호/시간 연결이 잘못되었습니다.`);
      check(l.start_t_ms % 20 === 0 && l.end_t_ms % 20 === 0 && l.lap_time_ms === l.end_t_ms - l.start_t_ms, `${tag}.lap_time_ms: 랩 시간 계산이 맞지 않습니다.`);
      lastEnd = l.end_t_ms;
      check(typeof l.valid === 'boolean' && typeof l.completed === 'boolean', `${tag}: valid/completed는 boolean이어야 합니다.`);
      check(Array.isArray(l.invalid_reasons) && l.invalid_reasons.every(r => reasons.includes(r)) && new Set(l.invalid_reasons).size === l.invalid_reasons.length, `${tag}: 무효 사유가 잘못되었습니다.`);
      check(Array.isArray(l.checkpoints) && l.checkpoints.length <= 8 && l.checkpoints.every((g, n) => g === (n + 1) % 8), `${tag}: 체크포인트 순서가 잘못되었습니다.`);
      if (l.completed) check(JSON.stringify(l.checkpoints) === '[1,2,3,4,5,6,7,0]', `${tag}: 완주에는 모든 체크포인트가 필요합니다.`);
      if (l.valid) check(l.completed === true && l.invalid_reasons?.length === 0, `${tag}: 유효 랩 조건을 만족하지 않습니다.`);
      else check(Array.isArray(l.invalid_reasons) && l.invalid_reasons.length > 0, `${tag}: 무효/미완주 사유가 필요합니다.`);
    });
    let lapIndex = 0;
    samples.forEach((s, i) => {
      if (!object(s) || s.t_ms === log.duration_ms) return;
      while (lapIndex < laps.length && s.t_ms >= laps[lapIndex]?.end_t_ms) lapIndex++;
      const lap = laps[lapIndex];
      check(s.lap === lapIndex + 1, `samples[${i}].lap: 랩 시간 구간과 번호가 다릅니다.`);
      if (lap?.valid) check(s.on_track === true && s.lap_valid_so_far === true, `samples[${i}]: 유효 랩에 무효 상태가 포함되어 있습니다.`);
    });
    check(lastEnd === log.duration_ms, 'laps: 전체 세션 시간에 대한 랩/미완주 기록이 필요합니다.');
    check(log.summary?.sample_count === samples.length && log.summary?.valid_laps === laps.filter(l => l?.valid === true).length && log.summary?.completed_laps === laps.filter(l => l?.completed === true).length, 'summary: 샘플/랩 집계가 맞지 않습니다.');
    check(Array.isArray(log.events), 'events: 배열이어야 합니다.');
    if (Array.isArray(log.events)) log.events.forEach((e, i) => check(object(e) && integer(e.t_ms, 0, log.duration_ms) && e.type === 'recovery_to_start' && (i === 0 || e.t_ms >= log.events[i - 1]?.t_ms), `events[${i}]: 복귀 이벤트 형식/순서가 잘못되었습니다.`));
    return { valid: errors.length === 0, errors };
  };
});
