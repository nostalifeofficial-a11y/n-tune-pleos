'use strict';
const { PRESETS, VEHICLE, TRACK, VERSION } = require('./core.js');
const object = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const number = (v, low, high) => typeof v === 'number' && Number.isFinite(v) && v >= low && v <= high;
const knownReasons = new Set(['off_track', 'wrong_direction', 'checkpoint_order', 'missing_checkpoints', 'recovery', 'incomplete']);
module.exports = function validate(log) {
  const errors = [], check = (pass, message) => { if (!pass && errors.length < 40) errors.push(message); };
  if (!object(log)) return { valid: false, errors: ['3D 주행 로그 객체가 필요합니다.'] };
  check(log.schema_version === VERSION && log.simulator?.name === 'nostalife-3d-simulator' && log.simulator?.version === VERSION, '3D 로그 버전이 다릅니다.');
  check(log.vehicle_id === VEHICLE.id && log.track_id === TRACK.id, '3D 가상 차량·코스가 다릅니다.');
  check(log.simulation === true && ['simulation_manual', 'simulation_autoplay'].includes(log.source), '시뮬레이션 출처가 필요합니다.');
  check(typeof log.session_id === 'string' && log.session_id.length >= 8 && log.session_id.length <= 128, '세션 ID가 유효하지 않습니다.');
  check(typeof log.created_at === 'string' && log.created_at.endsWith('Z') && Number.isFinite(Date.parse(log.created_at)), 'UTC 생성 시각이 필요합니다.');
  check(log.sampling?.interval_ms === 50 && log.sampling?.time_basis === 'elapsed_simulation_time' && log.simulator?.physics_step_ms === 5, '샘플링 계약이 다릅니다.');
  check(object(log.setup) && Object.hasOwn(PRESETS, log.setup.id) && Object.entries(PRESETS[log.setup.id]).every(([k, v]) => log.setup[k] === v), '프리셋 계약이 다릅니다.');
  const units = { speed_kmh: 'km/h', steering_wheel_deg: 'deg', yaw_rate_deg_s: 'deg/s', lat_g: 'g', long_g: 'g', throttle_pct: '%', brake_pct: '%', suspension_travel_m: 'm; positive compression', slip_angle_rad: 'rad', longitudinal_slip_ratio: 'ratio' };
  check(Object.entries(units).every(([key, unit]) => log.units?.[key] === unit), '측정 단위가 다릅니다.');
  check(Number.isInteger(log.duration_ms) && number(log.duration_ms, 0, 900000) && log.duration_ms % 10 === 0, '주행 시간이 유효하지 않습니다.');
  const samples = Array.isArray(log.samples) ? log.samples : [];
  check(samples.length >= 1 && samples.length <= 18002, '샘플 수는 1~18002여야 합니다.');
  for (let i = 0; i < samples.length && errors.length < 40; i++) {
    const s = samples[i], previous = samples[i - 1];
    if (!object(s)) { check(false, `samples[${i}] 객체가 필요합니다.`); continue; }
    check(Number.isInteger(s.t_ms) && s.t_ms >= 0 && s.t_ms <= log.duration_ms && s.t_ms % 10 === 0 &&
      (i === 0 ? s.t_ms === 0 : s.t_ms > previous?.t_ms && s.t_ms - previous?.t_ms <= 50) &&
      (i === samples.length - 1 || s.t_ms % 50 === 0), `samples[${i}] 시각이 유효하지 않습니다.`);
    const fields = { speed_kmh: [0, 250], steering: [-1, 1], steering_wheel_deg: [-650, 650], road_wheel_angle_deg: [-45, 45],
      throttle: [0, 1], brake: [0, 1], throttle_pct: [0, 100], brake_pct: [0, 100], lat_g: [-5, 5], long_g: [-5, 5],
      yaw_rate_deg_s: [-300, 300], x_m: [-100000, 100000], y_m: [-100000, 100000], z_m: [-.2, .2],
      heading_rad: [-Math.PI - .000001, Math.PI + .000001], roll_rad: [-.2, .2], pitch_rad: [-.2, .2], progress: [0, 1] };
    check(Object.entries(fields).every(([key, bounds]) => number(s[key], ...bounds)), `samples[${i}] 값 또는 필수 채널이 유효하지 않습니다.`);
    check(Math.abs(s.throttle * 100 - s.throttle_pct) <= .0001 && Math.abs(s.brake * 100 - s.brake_pct) <= .0001, `samples[${i}] 페달 단위 변환이 다릅니다.`);
    check(typeof s.on_track === 'boolean' && typeof s.lap_valid_so_far === 'boolean' && Number.isInteger(s.lap) && s.lap >= 1 && Number.isInteger(s.sector) && s.sector >= 1 && s.sector <= 4, `samples[${i}] 랩 상태가 유효하지 않습니다.`);
    for (const wheel of ['fl', 'fr', 'rl', 'rr']) {
      const w = s.wheels?.[wheel];
      check(object(w) && number(w.suspension_travel_m, -.121, .121) && number(w.slip_angle_rad, -Math.PI, Math.PI) && number(w.longitudinal_slip_ratio, -.601, .601) && number(w.speed_kmh, 0, 400) && number(w.normal_force_n, 0, 12000), `samples[${i}].wheels.${wheel} 측정값이 유효하지 않습니다.`);
    }
  }
  check(samples.at(-1)?.t_ms === log.duration_ms, '마지막 샘플 시각과 주행 시간이 다릅니다.');
  const laps = Array.isArray(log.laps) ? log.laps : [];
  check(Array.isArray(log.laps), 'laps 배열이 필요합니다.');
  let end = 0;
  for (let i = 0; i < laps.length && errors.length < 40; i++) {
    const l = laps[i];
    check(object(l) && l.lap === i + 1 && l.start_t_ms === end && Number.isInteger(l.end_t_ms) && l.end_t_ms > end && l.end_t_ms <= log.duration_ms && l.end_t_ms % 10 === 0 && l.lap_time_ms === l.end_t_ms - l.start_t_ms, `laps[${i}] 시간·순서가 유효하지 않습니다.`);
    if (!object(l)) continue;
    check(typeof l.valid === 'boolean' && typeof l.completed === 'boolean' && Array.isArray(l.invalid_reasons) && l.invalid_reasons.every(r => knownReasons.has(r)) && Array.isArray(l.checkpoints), `laps[${i}] 상태가 유효하지 않습니다.`);
    if (l.valid) {
      check(l.completed && l.invalid_reasons?.length === 0 && JSON.stringify(l.checkpoints) === '[1,2,3,4,5,6,7,0]', `laps[${i}] 유효 랩 체크포인트가 다릅니다.`);
      const row = samples.filter(s => object(s) && s.t_ms >= l.start_t_ms && s.t_ms < l.end_t_ms);
      check(row.length >= 2 && row.every(s => s.on_track && s.lap_valid_so_far && s.lap === l.lap), `laps[${i}] 샘플이 유효 랩과 모순됩니다.`);
    }
    end = l.end_t_ms;
  }
  check(log.duration_ms === 0 || end === log.duration_ms, '랩 기록이 세션 전체 시간을 포함해야 합니다.');
  check(log.summary?.sample_count === samples.length && log.summary?.valid_laps === laps.filter(l => l?.valid).length && log.summary?.completed_laps === laps.filter(l => l?.completed).length, '요약과 원본 기록이 다릅니다.');
  return { valid: errors.length === 0, errors };
};
