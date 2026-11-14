/* Nostalife browser simulator. Pure deterministic core; no network or platform IO. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.NostalifeSim = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const VERSION = '1.0.0';
  const STEP_MS = 20;
  const SAMPLE_MS = 100;
  const MAX_DURATION_MS = 30 * 60 * 1000;
  const clamp = (v, min, max) => Math.max(min, Math.min(max, v));
  const round = (v, n = 4) => Number(v.toFixed(n));
  const wrap = a => Math.atan2(Math.sin(a), Math.cos(a));
  const copy = v => JSON.parse(JSON.stringify(v));
  const PRESETS = Object.freeze({
    stable: Object.freeze({ id: 'stable', label: '안정형', steering_gain: 0.92, steering_response_hz: 3.8, yaw_damping_hz: 5.8 }),
    balanced: Object.freeze({ id: 'balanced', label: '균형형', steering_gain: 1.08, steering_response_hz: 6, yaw_damping_hz: 4.4 }),
    rotation: Object.freeze({ id: 'rotation', label: '회전형', steering_gain: 1.25, steering_response_hz: 8.5, yaw_damping_hz: 3.4 })
  });
  const R = 86, STRAIGHT = 210, ARC = Math.PI * R, LENGTH = 2 * STRAIGHT + 2 * ARC;
  const TRACK = Object.freeze({ id: 'nostalife-loop-v1', name: 'NOSTALIFE LOOP', length_m: LENGTH, width_m: 30, checkpoint_count: 8 });
  function pointAt(distance) {
    let s = ((distance % LENGTH) + LENGTH) % LENGTH;
    if (s < 105) return { x: 240 + s, y: 246, heading: 0 };
    s -= 105;
    if (s < ARC) { const a = Math.PI / 2 - s / R; return { x: 345 + R * Math.cos(a), y: 160 + R * Math.sin(a), heading: a - Math.PI / 2 }; }
    s -= ARC;
    if (s < STRAIGHT) return { x: 345 - s, y: 74, heading: -Math.PI };
    s -= STRAIGHT;
    if (s < ARC) { const a = -Math.PI / 2 - s / R; return { x: 135 + R * Math.cos(a), y: 160 + R * Math.sin(a), heading: a - Math.PI / 2 }; }
    s -= ARC;
    return { x: 135 + s, y: 246, heading: 0 };
  }
  function project(x, y) {
    const candidates = [];
    const add = s => { const p = pointAt(s); candidates.push({ ...p, s: ((s % LENGTH) + LENGTH) % LENGTH, distance: Math.hypot(x - p.x, y - p.y) }); };
    const bx = clamp(x, 135, 345);
    add(bx >= 240 ? bx - 240 : LENGTH - (240 - bx));
    add(105 + ARC + (345 - clamp(x, 135, 345)));
    const ra = clamp(Math.atan2(y - 160, x - 345), -Math.PI / 2, Math.PI / 2);
    add(105 + (Math.PI / 2 - ra) * R);
    let la = Math.atan2(y - 160, x - 135);
    if (la > 0) la -= 2 * Math.PI;
    la = clamp(la, -3 * Math.PI / 2, -Math.PI / 2);
    add(105 + ARC + STRAIGHT + (-Math.PI / 2 - la) * R);
    return candidates.reduce((a, b) => a.distance <= b.distance ? a : b);
  }
  const GATES = Object.freeze(Array.from({ length: 8 }, (_, i) => Object.freeze({ id: i, s: i * LENGTH / 8, ...pointAt(i * LENGTH / 8) })));
  class LapTracker {
    constructor() { this.laps = []; this.resetLap(0); }
    resetLap(time) { this.startTime = time; this.nextGate = 1; this.checkpoints = []; this.reasons = new Set(); }
    invalidate(reason) { this.reasons.add(reason); }
    close(time, completed, reason) {
      if (reason) this.invalidate(reason);
      if (time <= this.startTime) return null;
      const lap = { lap: this.laps.length + 1, start_t_ms: this.startTime, end_t_ms: time, lap_time_ms: time - this.startTime, completed, valid: completed && this.reasons.size === 0, invalid_reasons: [...this.reasons], checkpoints: [...this.checkpoints] };
      this.laps.push(lap); this.resetLap(time); return lap;
    }
    advance(prev, cur, time, offTrack) {
      if (offTrack) this.invalidate('off_track');
      const crossings = [];
      for (const g of GATES) {
        const tx = Math.cos(g.heading), ty = Math.sin(g.heading);
        const a = (prev.x - g.x) * tx + (prev.y - g.y) * ty;
        const b = (cur.x - g.x) * tx + (cur.y - g.y) * ty;
        const forward = a < -1e-8 && b >= -1e-8;
        const reverse = a > 1e-8 && b <= 1e-8;
        if (!forward && !reverse) continue;
        const f = a / (a - b);
        const x = prev.x + (cur.x - prev.x) * f, y = prev.y + (cur.y - prev.y) * f;
        if (Math.abs(-(x - g.x) * ty + (y - g.y) * tx) <= TRACK.width_m / 2) crossings.push({ id: g.id, f, forward });
      }
      crossings.sort((a, b) => a.f - b.f);
      for (const hit of crossings) {
        if (!hit.forward) { this.invalidate('wrong_direction'); continue; }
        if (hit.id === this.nextGate) {
          this.checkpoints.push(hit.id);
          if (hit.id === 0) this.close(time, true);
          else this.nextGate = (hit.id + 1) % 8;
        } else if (hit.id === 0) this.close(time, false, 'missing_checkpoints');
        else this.invalidate('checkpoint_order');
      }
    }
    get validCount() { return this.laps.filter(l => l.valid).length; }
  }
  function steeringRate(speed, setup) { return 1.55 * setup.steering_gain * speed / (12 + speed) / (1 + speed / 55); }
  function autoplay(state, setup) {
    const p = project(state.x, state.y);
    const lookAhead = 12 + state.speed * 0.5;
    const target = pointAt(p.s + lookAhead);
    const error = wrap(Math.atan2(target.y - state.y, target.x - state.x) - state.heading);
    const desiredYaw = 2 * state.speed * Math.sin(error) / lookAhead;
    const curve = Math.abs(wrap(pointAt(p.s + 30).heading - p.heading));
    const targetSpeed = curve > 0.1 ? 21 : 26;
    const drag = 0.15 + 0.02 * state.speed + 0.0022 * state.speed ** 2;
    return { steering: clamp(desiredYaw / Math.max(0.08, steeringRate(state.speed, setup)), -1, 1), throttle: clamp((targetSpeed - state.speed) * 0.48 + drag / 7, 0, 1), brake: clamp((state.speed - targetSpeed) * 0.22, 0, 1) };
  }
  function newId() {
    if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID();
    return 'sim-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
  }
  class Session {
    constructor({ preset = 'balanced', mode = 'manual', targetLaps = 4, sessionId, createdAt } = {}) {
      if (!PRESETS[preset] || !['manual', 'autoplay'].includes(mode)) throw new Error('Unknown preset or mode');
      if (!Number.isInteger(targetLaps) || targetLaps < 1 || targetLaps > 4) throw new Error('targetLaps must be 1..4');
      this.setup = Object.freeze({ ...PRESETS[preset] });
      this.mode = mode; this.targetLaps = targetLaps;
      this.sessionId = sessionId || newId(); this.createdAt = createdAt || new Date().toISOString();
      this.time = 0; this.status = 'ready'; this.finishReason = null;
      this.state = { ...pointAt(0), speed: 0, steering: 0, yaw: 0, throttle: 0, brake: 0, offTrack: false };
      this.tracker = new LapTracker(); this.samples = []; this.events = [];
      this.record();
    }
    start() { if (this.status === 'ready' || this.status === 'paused') this.status = 'running'; }
    pause() { if (this.status === 'running') this.status = 'paused'; }
    record() {
      if (this.samples.at(-1)?.t_ms === this.time) return;
      const p = project(this.state.x, this.state.y);
      this.samples.push({ t_ms: this.time, lap: this.tracker.laps.length + 1, sector: Math.min(4, Math.floor(p.s / LENGTH * 4) + 1), progress: round(p.s / LENGTH, 6), speed_kmh: round(this.state.speed * 3.6, 3), steering: round(this.state.steering), brake: round(this.state.brake), throttle: round(this.state.throttle), x_m: round(this.state.x), y_m: round(this.state.y), heading_rad: round(wrap(this.state.heading)), yaw_rate_rad_s: round(this.state.yaw), on_track: !this.state.offTrack, lap_valid_so_far: this.tracker.reasons.size === 0 });
    }
    step(input = {}) {
      if (this.status !== 'running') return;
      const controls = this.mode === 'autoplay' ? autoplay(this.state, this.setup) : input;
      const value = (k, lo, hi) => clamp(Number.isFinite(controls[k]) ? controls[k] : 0, lo, hi);
      const dt = STEP_MS / 1000, v = this.state, prev = { x: v.x, y: v.y };
      v.throttle = value('throttle', 0, 1); v.brake = value('brake', 0, 1);
      v.steering += (value('steering', -1, 1) - v.steering) * (1 - Math.exp(-this.setup.steering_response_hz * dt));
      const drag = 0.15 + 0.02 * v.speed + 0.0022 * v.speed ** 2 + (v.offTrack ? v.speed * 0.8 : 0);
      v.speed = clamp(v.speed + (v.throttle * 7 - v.brake * 13 - drag) * dt, 0, 130 / 3.6);
      const yawTarget = v.steering * steeringRate(v.speed, this.setup);
      v.yaw += (yawTarget - v.yaw) * (1 - Math.exp(-this.setup.yaw_damping_hz * dt));
      v.heading = wrap(v.heading + v.yaw * dt);
      v.x += Math.cos(v.heading) * v.speed * dt; v.y += Math.sin(v.heading) * v.speed * dt;
      v.offTrack = project(v.x, v.y).distance > TRACK.width_m / 2;
      this.time += STEP_MS;
      this.tracker.advance(prev, v, this.time, v.offTrack);
      if (this.time % SAMPLE_MS === 0) this.record();
      if (this.mode === 'autoplay' && this.tracker.validCount >= this.targetLaps) this.finish('target_laps');
      else if (this.time >= MAX_DURATION_MS) this.finish('duration_limit');
    }
    recover() {
      if (!['running', 'paused'].includes(this.status)) return;
      this.tracker.close(this.time, false, 'recovery');
      Object.assign(this.state, pointAt(0), { speed: 0, steering: 0, yaw: 0, throttle: 0, brake: 0, offTrack: false });
      this.events.push({ t_ms: this.time, type: 'recovery_to_start' });
      // A recovery can coincide with an already-recorded grid timestamp. Keep one
      // sample at that time and make it describe the new lap, as other boundaries do.
      if (this.samples.at(-1)?.t_ms === this.time) { this.samples.pop(); this.record(); }
    }
    finish(reason = 'user_stop') {
      if (this.status === 'finished') return;
      this.status = 'finished'; this.finishReason = reason;
      this.record();
      if (this.time > this.tracker.startTime) this.tracker.close(this.time, false, 'incomplete');
    }
    exportLog() {
      const laps = copy(this.tracker.laps);
      if (this.status !== 'finished' && this.time > this.tracker.startTime) laps.push({ lap: laps.length + 1, start_t_ms: this.tracker.startTime, end_t_ms: this.time, lap_time_ms: this.time - this.tracker.startTime, completed: false, valid: false, invalid_reasons: [...new Set([...this.tracker.reasons, 'incomplete'])], checkpoints: [...this.tracker.checkpoints] });
      const log = { schema_version: VERSION, session_id: this.sessionId, created_at: this.createdAt, source: 'simulation_' + this.mode, simulation: true, simulator: { name: 'nostalife-browser-simulator', version: VERSION, physics_step_ms: STEP_MS }, vehicle_id: 'nostalife-virtual-01', track_id: TRACK.id, setup: copy(this.setup), sampling: { interval_ms: SAMPLE_MS, time_basis: 'elapsed_simulation_time', terminal_sample_may_be_irregular: true }, units: { t_ms: 'ms', speed_kmh: 'km/h', steering: 'normalized [-1,1]; positive right', brake: 'fraction [0,1]', throttle: 'fraction [0,1]', position: 'm', heading_rad: 'rad; screen x-right/y-down', yaw_rate_rad_s: 'rad/s' }, status: this.status, finish_reason: this.finishReason, duration_ms: this.time, samples: copy(this.samples), laps, events: copy(this.events) };
      // Exports are snapshots: append the current terminal sample without mutating recording.
      if (log.samples.at(-1)?.t_ms !== this.time) { const count = this.samples.length; this.record(); log.samples = copy(this.samples); this.samples.length = count; }
      log.summary = { sample_count: log.samples.length, valid_laps: laps.filter(l => l.valid).length, completed_laps: laps.filter(l => l.completed).length };
      return log;
    }
  }
  // One animation owner. Starting/resuming never adds another requestAnimationFrame chain.
  class FixedClock {
    constructor(request, cancel, tick, paint) { Object.assign(this, { request, cancel, tick, paint }); this.handle = null; this.active = false; this.last = null; this.accumulator = 0; this.rate = 1; this.generation = 0; }
    schedule() { const generation = this.generation; this.handle = this.request(t => this.frame(t, generation)); }
    start() { if (this.active) return; this.active = true; this.last = null; this.generation++; this.schedule(); }
    frame(time, generation) {
      if (!this.active || generation !== this.generation) return;
      this.handle = null;
      if (this.last !== null) this.accumulator += Math.min(100, Math.max(0, time - this.last)) * this.rate;
      this.last = time;
      while (this.accumulator >= STEP_MS && this.active) { this.accumulator -= STEP_MS; this.tick(); }
      this.paint();
      if (this.active) this.schedule();
    }
    reset() { this.last = null; this.accumulator = 0; }
    stop() { this.active = false; this.generation++; if (this.handle !== null) this.cancel(this.handle); this.handle = null; this.reset(); }
  }
  return { VERSION, STEP_MS, SAMPLE_MS, MAX_DURATION_MS, PRESETS, TRACK, GATES, pointAt, project, LapTracker, Session, FixedClock, autoplay, clamp, round };
});
