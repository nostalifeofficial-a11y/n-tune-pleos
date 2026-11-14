'use strict';
const validate = require('../driving-simulator-3d/validate.cjs');
const { quantile, mean, deviation } = require('./metrics.cjs');
const R = value => value == null ? null : Number(value.toFixed(6));
const p95 = values => R(quantile(values, .95));
const abs = values => values.map(Math.abs);
const FEATURE_KEYS = ['lap_time_s', 'speed_mean_kmh', 'speed_max_kmh', 'brake_mean', 'throttle_mean',
  'front_slip_p95_abs', 'rear_slip_p95_abs', 'front_slip_max_p95_abs', 'rear_slip_max_p95_abs',
  'front_slip_imbalance_p95', 'rear_slip_imbalance_p95', 'front_suspension_diff_p95_abs',
  'rear_suspension_diff_p95_abs', 'yaw_steering_ratio_p95', 'lat_g_p95_abs', 'long_g_p95_abs'];
function regression(x, y) {
  if (x.length < 20) return null;
  const mx = mean(x), my = mean(y), xx = x.reduce((sum, v) => sum + (v - mx) ** 2, 0);
  if (xx < 1e-8) return null;
  const slope = x.reduce((sum, v, i) => sum + (v - mx) * (y[i] - my), 0) / xx, intercept = my - slope * mx;
  const residual = y.reduce((sum, v, i) => sum + (v - slope * x[i] - intercept) ** 2, 0), yy = y.reduce((sum, v) => sum + (v - my) ** 2, 0);
  return { slope: R(slope), intercept: R(intercept), r_squared: yy > 1e-8 ? R(1 - residual / yy) : null, samples: x.length };
}
function perLap(log, lap) {
  const rows = log.samples.filter(s => s.t_ms >= lap.start_t_ms && s.t_ms < lap.end_t_ms);
  const rates = [], releaseRates = []; let totalVariation = 0, corrections = 0, lastSign = 0, lastCorrection = -Infinity;
  for (let i = 1; i < rows.length; i++) {
    const a = rows[i - 1], b = rows[i], dt = (b.t_ms - a.t_ms) / 1000, difference = b.steering - a.steering;
    rates.push(Math.abs(difference) / dt); totalVariation += Math.abs(difference);
    if (Math.abs(difference) >= .008) { const sign = Math.sign(difference); if (lastSign && sign !== lastSign && b.t_ms - lastCorrection >= 250) { corrections++; lastCorrection = b.t_ms; } lastSign = sign; }
    if (a.brake >= .05 && a.brake - b.brake > .005) releaseRates.push((a.brake - b.brake) / dt);
  }
  const summary = { lap_no: lap.lap, is_valid: true, is_complete: true, is_partial: false,
    lap_time_s: R(lap.lap_time_ms / 1000), speed_mean_kmh: R(mean(rows.map(s => s.speed_kmh))),
    speed_max_kmh: R(Math.max(...rows.map(s => s.speed_kmh))),
    brake_mean: R(mean(rows.map(s => s.brake_pct))), throttle_mean: R(mean(rows.map(s => s.throttle_pct))),
    yaw_steering_ratio_p95: p95(rows.map(s => Math.abs(s.yaw_rate_deg_s) / (Math.abs(s.steering_wheel_deg) + 1e-6))),
    lat_g_p95_abs: p95(abs(rows.map(s => s.lat_g))), long_g_p95_abs: p95(abs(rows.map(s => s.long_g))) };
  const extra = {};
  for (const [axle, left, right] of [['front', 'fl', 'fr'], ['rear', 'rl', 'rr']]) {
    const pairs = rows.map(s => [Math.abs(s.wheels[left].slip_angle_rad), Math.abs(s.wheels[right].slip_angle_rad)]);
    summary[axle + '_slip_p95_abs'] = p95(pairs.map(([a, b]) => (a + b) / 2));
    summary[axle + '_slip_max_p95_abs'] = p95(pairs.map(([a, b]) => Math.max(a, b)));
    summary[axle + '_slip_imbalance_p95'] = p95(pairs.map(([a, b]) => Math.abs(a - b) / ((a + b) / 2 + 1e-6)));
    // Exact original pair_diff: abs(abs(left) - abs(right)), NOT abs(left-right).
    summary[axle + '_suspension_diff_p95_abs'] = p95(rows.map(s => Math.abs(Math.abs(s.wheels[left].suspension_travel_m) - Math.abs(s.wheels[right].suspension_travel_m))));
    extra[axle + '_signed_suspension_delta_p95_mm'] = p95(rows.map(s => Math.abs(s.wheels[left].suspension_travel_m - s.wheels[right].suspension_travel_m) * 1000));
  }
  return { rows, summary, rates, releaseRates, totalVariation, corrections, extra };
}
function analyzeMetrics3D(log) {
  const checked = validate(log);
  if (!checked.valid) throw Object.assign(new Error(checked.errors.slice(0, 8).join(' ')), { code: 'INVALID_LOG', details: checked.errors });
  const eligible = log.laps.filter(l => l.valid && l.completed), selected = eligible.slice(0, 4), laps = selected.map(l => perLap(log, l));
  const rows = laps.flatMap(l => l.rows), times = laps.map(l => l.summary.lap_time_s), duration = times.reduce((sum, n) => sum + n, 0);
  const release = laps.flatMap(l => l.releaseRates), rates = laps.flatMap(l => l.rates);
  const observed = { lap_time_median_s: R(quantile(times, .5)), lap_time_std_s: R(deviation(times)),
    lap_time_range_s: times.length ? R(Math.max(...times) - Math.min(...times)) : null,
    speed_mean_kmh: R(mean(rows.map(s => s.speed_kmh))), speed_max_kmh: rows.length ? R(Math.max(...rows.map(s => s.speed_kmh))) : null,
    steering_rate_p95_per_s: p95(rates), steering_abs_p95: p95(abs(rows.map(s => s.steering))),
    steering_total_variation_per_s: duration ? R(laps.reduce((sum, l) => sum + l.totalVariation, 0) / duration) : null,
    steering_corrections_per_min: duration ? R(laps.reduce((sum, l) => sum + l.corrections, 0) / duration * 60) : null,
    brake_release_p95_per_s: p95(release), brake_release_sample_count: release.length,
    valid_laps_available: eligible.length, selected_lap_count: selected.length, selected_duration_s: R(duration),
    selected_sample_count: rows.length, excluded_lap_count: log.laps.length - eligible.length,
    lat_g_p95_abs: p95(abs(rows.map(s => s.lat_g))), long_g_p95_abs: p95(abs(rows.map(s => s.long_g))),
    throttle_mean_pct: R(mean(rows.map(s => s.throttle_pct))), brake_mean_pct: R(mean(rows.map(s => s.brake_pct))) };
  for (const axle of ['front', 'rear']) observed[axle + '_suspension_diff_p95_mm'] = p95(rows.map(s => {
    const [left, right] = axle === 'front' ? ['fl', 'fr'] : ['rl', 'rr'];
    return Math.abs(Math.abs(s.wheels[left].suspension_travel_m) - Math.abs(s.wheels[right].suspension_travel_m)) * 1000;
  }));
  const corner = rows.filter(s => s.speed_kmh >= 30 && Math.abs(s.lat_g) >= .15 && Math.abs(s.long_g) < .15);
  const handling = { steering_vs_lateral_g: regression(corner.map(s => s.lat_g), corner.map(s => s.steering_wheel_deg)),
    steering_vs_yaw_rate: regression(corner.map(s => s.yaw_rate_deg_s), corner.map(s => s.steering_wheel_deg)),
    interpretation: 'Simulation-only linear fits, not a calibrated production understeer gradient.' };
  const payload = { game: 'SIMULATION', simulation: true, source: { input_type: log.source, simulator: 'nostalife-3d-v2' },
    session_id: log.session_id, car: log.vehicle_id, track: log.track_id, current_setup: { ...log.setup, family: 'simulation:' + log.setup.id },
    conditions: { weather: 'unknown', air_temp_c: null, track_temp_c: null, fuel_start_l: null },
    summary: { game: 'SIMULATION', lap_summaries: laps.map(l => l.summary) }, events: [],
    measurement_semantics: { tyre_slip: 'simulated slip angle radians; not calibrated to ACC wheelSlip',
      suspension: 'simulated spring compression metres', pedals: 'simulated input percentages', accelerations: 'simulated body forces / mass / standard gravity' },
    unsupported_measurements: ['ACC_handling_events', 'air_temp_c', 'track_temp_c', 'fuel_start_l'] };
  return { observed, payload, enough: laps.length >= 3, selected_laps: selected.map(l => l.lap), handling,
    missing_signals: [...payload.unsupported_measurements], feature_keys: FEATURE_KEYS,
    lap_metrics: laps.map(l => ({ lap: l.summary.lap_no, ...l.summary, ...l.extra, brake_release_p95_per_s: p95(l.releaseRates) })) };
}
module.exports = { analyzeMetrics3D, FEATURE_KEYS, regression };
