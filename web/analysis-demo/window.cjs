'use strict';
const validate = require('../driving-simulator-3d/validate.cjs');
const { mean, quantile, deviation, PRESETS } = require('./metrics.cjs');
const { regression } = require('./metrics3d.cjs');
const round = n => n == null ? null : Number(n.toFixed(6));
const percentile = values => round(quantile(values, .95));
const absolute = values => values.map(Math.abs);
const maximum = values => values.length ? Math.max(...values) : null;

function measureWindow(log) {
  const checked = validate(log);
  if (!checked.valid) throw Object.assign(new Error(checked.errors.slice(0, 6).join(' ')), { code: 'INVALID_LOG' });
  const rows = log.samples;
  const duration = rows.length ? (rows.at(-1).t_ms - rows[0].t_ms) / 1000 : 0;
  if (rows.length < 2 || duration <= 0) throw Object.assign(new Error('아직 기록된 주행 시간이 없습니다. 주행을 시작한 뒤 분석해 주세요.'), { code: 'EMPTY_RECORDING', status: 422 });
  const rates = [], releases = [];
  let variation = 0, corrections = 0, sign = 0, lastCorrection = -Infinity, distance = 0;
  for (let i = 1; i < rows.length; i++) {
    const a = rows[i - 1], b = rows[i], dt = (b.t_ms - a.t_ms) / 1000, delta = b.steering - a.steering;
    variation += Math.abs(delta); rates.push(Math.abs(delta) / dt);
    distance += (a.speed_kmh + b.speed_kmh) / 2 / 3.6 * dt;
    if (Math.abs(delta) >= .008) {
      const next = Math.sign(delta);
      if (sign && next !== sign && b.t_ms - lastCorrection >= 250) { corrections++; lastCorrection = b.t_ms; }
      sign = next;
    }
    if (a.brake >= .05 && a.brake - b.brake > .005) releases.push((a.brake - b.brake) / dt);
  }
  const validLaps = log.laps.filter(l => l.valid && l.completed);
  const times = validLaps.map(l => l.lap_time_ms / 1000);
  const metrics = {
    duration_s: round(duration), sample_count: rows.length, distance_m: round(distance),
    speed_mean_kmh: round(mean(rows.map(s => s.speed_kmh))), speed_max_kmh: round(maximum(rows.map(s => s.speed_kmh))),
    throttle_mean_pct: round(mean(rows.map(s => s.throttle_pct))), brake_mean_pct: round(mean(rows.map(s => s.brake_pct))),
    lat_g_p95_abs: percentile(absolute(rows.map(s => s.lat_g))), long_g_p95_abs: percentile(absolute(rows.map(s => s.long_g))),
    lat_g_peak_abs: round(maximum(absolute(rows.map(s => s.lat_g)))), long_g_peak_abs: round(maximum(absolute(rows.map(s => s.long_g)))),
    steering_rate_p95_per_s: percentile(rates), steering_total_variation_per_s: round(variation / duration),
    steering_corrections_per_min: round(corrections / duration * 60), brake_release_p95_per_s: percentile(releases),
    brake_release_sample_count: releases.length,
    yaw_steering_ratio_p95: percentile(rows.map(s => Math.abs(s.yaw_rate_deg_s) / (Math.abs(s.steering_wheel_deg) + 1e-6))),
    valid_laps_available: validLaps.length, selected_lap_count: validLaps.length,
    excluded_lap_count: 0, invalid_or_partial_lap_count: log.laps.length - validLaps.length,
    selected_duration_s: round(duration), selected_sample_count: rows.length,
    lap_time_median_s: round(quantile(times, .5)), lap_time_std_s: round(deviation(times))
  };
  const wheels = {};
  for (const name of ['fl', 'fr', 'rl', 'rr']) {
    const travel = rows.map(s => s.wheels[name].suspension_travel_m * 1000);
    wheels[name] = { suspension_mean_mm: round(mean(travel)), suspension_p95_abs_mm: percentile(absolute(travel)),
      suspension_min_mm: round(Math.min(...travel)), suspension_max_mm: round(Math.max(...travel)),
      slip_angle_p95_abs_rad: percentile(rows.map(s => Math.abs(s.wheels[name].slip_angle_rad))) };
  }
  for (const [axle, left, right] of [['front', 'fl', 'fr'], ['rear', 'rl', 'rr']]) {
    const pairs = rows.map(s => [Math.abs(s.wheels[left].slip_angle_rad), Math.abs(s.wheels[right].slip_angle_rad)]);
    metrics[axle + '_slip_p95_abs'] = percentile(pairs.map(([a, b]) => (a + b) / 2));
    metrics[axle + '_slip_max_p95_abs'] = percentile(pairs.map(([a, b]) => Math.max(a, b)));
    metrics[axle + '_slip_imbalance_p95'] = percentile(pairs.map(([a, b]) => Math.abs(a - b) / ((a + b) / 2 + 1e-6)));
    metrics[axle + '_suspension_diff_p95_mm'] = percentile(rows.map(s => Math.abs(Math.abs(s.wheels[left].suspension_travel_m) - Math.abs(s.wheels[right].suspension_travel_m)) * 1000));
    metrics[axle + '_signed_suspension_delta_p95_mm'] = percentile(rows.map(s => Math.abs(s.wheels[left].suspension_travel_m - s.wheels[right].suspension_travel_m) * 1000));
  }
  const corners = rows.filter(s => s.speed_kmh >= 30 && Math.abs(s.lat_g) >= .15 && Math.abs(s.long_g) < .15);
  const handling = { steering_vs_lateral_g: regression(corners.map(s => s.lat_g), corners.map(s => s.steering_wheel_deg)),
    steering_vs_yaw_rate: regression(corners.map(s => s.yaw_rate_deg_s), corners.map(s => s.steering_wheel_deg)) };
  const window = { scope: 'start_to_analyze', start_t_ms: rows[0].t_ms, end_t_ms: rows.at(-1).t_ms,
    duration_s: round(duration), sample_count: rows.length, includes_incomplete_and_invalid_laps: true, required_valid_laps: 0 };
  return { window, metrics, wheels, handling, selectedLaps: validLaps.map(l => l.lap),
    current: { ...PRESETS[log.setup.id] },
    payload: { simulation: true, source: 'pleos_driving_lab_3d', current_setup: { ...PRESETS[log.setup.id] },
      available_presets: Object.fromEntries(Object.entries(PRESETS).map(([id, p]) => [id, p.label])),
      analysis_window: window, measurements: metrics, wheel_measurements: wheels, handling,
      units: { acceleration: 'g', pedals: 'percent (0..100)', speed: 'km/h', suspension: 'mm',
        slip_angle: 'rad', steering_and_brake_rates: 'normalized input per second', yaw_steering_ratio: 'deg/s divided by steering wheel deg; unstable near zero steering' },
      metric_definitions: {
        brake_release_p95_per_s: 'P95 rate of decreasing brake input, unit: normalized pedal input per second; NOT release event count or frequency. A value of 7.5 means 750 percentage points per second.',
        steering_rate_p95_per_s: 'P95 absolute normalized steering input change per second; NOT average or number of turns.',
        front_suspension_diff_p95_mm: 'P95 absolute difference of absolute left and right front travel, NOT a maximum, NOT a mechanical fault indicator.',
        rear_suspension_diff_p95_mm: 'P95 absolute difference of absolute left and right rear travel, NOT a maximum, NOT a mechanical fault indicator.',
        slip_p95: 'All slip fields named p95 are 95th percentiles, not temporal maxima. No calibrated normal/abnormal threshold is provided.'
      },
      limits: ['All signals are computed by a virtual vehicle model, not production car sensors.',
        'Slip angle is not calibrated to ACC wheelSlip or an Avante sensor.',
        'Lap completion is not required. Null values mean the relevant observation is absent.',
        'Suspension difference of absolute values and signed left-right delta are distinct.',
        'Time is the simulation recording clock; paused wall time is excluded.'] } };
}
module.exports = { measureWindow };
