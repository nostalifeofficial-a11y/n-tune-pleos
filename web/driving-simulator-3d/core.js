/* Simplified four-wheel vehicle dynamics. All telemetry is simulated, never real-car data. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(require('../driving-simulator/core.js'));
  else root.Nostalife3D = factory(root.NostalifeSim);
})(typeof globalThis !== 'undefined' ? globalThis : this, function (Base) {
  'use strict';
  const VERSION = '2.0.0', STEP_MS = 10, SAMPLE_MS = 50, G = 9.80665;
  const { clamp, pointAt, project, LapTracker, PRESETS } = Base;
  const TRACK = { ...Base.TRACK, id: 'nostalife-circuit-3d-v1', name: 'Nostalife proving ground' };
  const VEHICLE = { id: 'nostalife-virtual-3d-01', mass_kg: 1420, wheelbase_m: 2.65,
    track_width_m: 1.58, cg_height_m: .51, steering_ratio: 15, tyre_radius_m: .32 };
  const CORNERS = [{ id: 'fl', x: 1.25, y: -.79 }, { id: 'fr', x: 1.25, y: .79 },
    { id: 'rl', x: -1.4, y: -.79 }, { id: 'rr', x: -1.4, y: .79 }];
  const wrap = value => Math.atan2(Math.sin(value), Math.cos(value));
  const copy = value => JSON.parse(JSON.stringify(value));
  const round = value => Number(value.toFixed(6));
  function roadHeight(x, y) {
    const p = project(x, y);
    const fine = .0025 * Math.sin(x * .65) * Math.cos(y * .8);
    const testBump = .025 * Math.exp(-(((x - 292) / 2.3) ** 2)) * Math.exp(-(((y - 246) / 18) ** 2));
    return fine + testBump + (p.distance > TRACK.width_m / 2 ? .018 * Math.sin(x * 1.1) * Math.cos(y * .9) : 0);
  }
  function maxRoadAngle(speed, setup) { return .48 * setup.steering_gain / (1 + Math.max(0, speed) / 45); }
  function autopilot(state, setup) {
    const p = project(state.x, state.y), lookAhead = 11 + state.u * .7;
    const target = pointAt(p.s + lookAhead);
    const error = wrap(Math.atan2(target.y - state.y, target.x - state.x) - state.heading);
    const desired = Math.atan(VEHICLE.wheelbase_m * 2 * Math.sin(error) / lookAhead);
    const curve = Math.abs(wrap(pointAt(p.s + 45).heading - p.heading));
    const targetSpeed = curve > .13 ? 22 : 29;
    return { steering: clamp(desired * 1.13 / maxRoadAngle(state.u, setup), -1, 1),
      throttle: clamp(.18 + (targetSpeed - state.u) * .25, 0, 1), brake: clamp((state.u - targetSpeed) * .16, 0, 1) };
  }
  class Session {
    constructor({ preset = 'balanced', mode = 'manual', targetLaps = 4, sessionId, createdAt } = {}) {
      if (!PRESETS[preset] || !['manual', 'autoplay'].includes(mode)) throw new Error('Unknown setup/mode');
      if (!Number.isInteger(targetLaps) || targetLaps < 1 || targetLaps > 8) throw new Error('targetLaps must be 1..8');
      this.setup = Object.freeze({ ...PRESETS[preset] }); this.mode = mode; this.targetLaps = targetLaps;
      this.sessionId = sessionId || globalThis.crypto?.randomUUID?.() || `sim3d-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      this.createdAt = createdAt || new Date().toISOString();
      this.time = 0; this.status = 'ready'; this.finishReason = null;
      this.tracker = new LapTracker(); this.samples = []; this.events = [];
      this.resetState(); this.record();
    }
    resetState() {
      this.state = { ...pointAt(0), u: 0, v: 0, speed: 0, steering: 0, steeringInput: 0,
        roadAngle: 0, yaw: 0, throttle: 0, brake: 0, ax: 0, ay: 0, offTrack: false,
        heave: 0, heaveRate: 0, roll: 0, rollRate: 0, pitch: 0, pitchRate: 0,
        wheels: CORNERS.map(c => ({ ...c, travel: 0, travelRate: 0, slipAngle: 0, slipRatio: 0,
          speed: 0, normalForce: VEHICLE.mass_kg * G * (c.x > 0 ? 1.4 : 1.25) / VEHICLE.wheelbase_m / 2,
          rotation: 0, road: roadHeight(pointAt(0).x + c.x, pointAt(0).y + c.y) })) };
    }
    start() { if (this.status === 'ready' || this.status === 'paused') this.status = 'running'; }
    pause() { if (this.status === 'running') this.status = 'paused'; }
    step(input = {}) {
      if (this.status !== 'running') return;
      const controls = this.mode === 'autoplay' ? autopilot(this.state, this.setup) : input;
      const clean = (key, min, max) => clamp(Number.isFinite(controls[key]) ? controls[key] : 0, min, max);
      const command = { throttle: clean('throttle', 0, 1), brake: clean('brake', 0, 1), steering: clean('steering', -1, 1) };
      const previous = { x: this.state.x, y: this.state.y };
      this.integrate(command, .005); this.integrate(command, .005);
      this.time += STEP_MS;
      this.state.offTrack = project(this.state.x, this.state.y).distance > TRACK.width_m / 2;
      this.tracker.advance(previous, this.state, this.time, this.state.offTrack);
      if (this.time % SAMPLE_MS === 0) this.record();
      if (this.mode === 'autoplay' && this.tracker.validCount >= this.targetLaps) this.finish('target_laps');
      if (this.time >= 15 * 60 * 1000) this.finish('duration_limit');
    }
    integrate(command, dt) {
      const s = this.state, mass = VEHICLE.mass_kg, L = VEHICLE.wheelbase_m;
      s.throttle += (command.throttle - s.throttle) * (1 - Math.exp(-12 * dt));
      s.brake += (command.brake - s.brake) * (1 - Math.exp(-16 * dt));
      s.steeringInput = command.steering;
      s.steering += (command.steering - s.steering) * (1 - Math.exp(-this.setup.steering_response_hz * dt));
      s.roadAngle = s.steering * maxRoadAngle(s.u, this.setup);
      const frontStiffness = this.setup.id === 'rotation' ? 39000 : 35000;
      const rearStiffness = this.setup.id === 'stable' ? 31000 : this.setup.id === 'rotation' ? 41000 : 35000;
      const damper = this.setup.id === 'stable' ? 3600 : this.setup.id === 'rotation' ? 2400 : 3000;
      let vertical = 0, rollMoment = 0, pitchMoment = 0;
      for (const w of s.wheels) {
        const wx = s.x + Math.cos(s.heading) * w.x - Math.sin(s.heading) * w.y;
        const wy = s.y + Math.sin(s.heading) * w.x + Math.cos(s.heading) * w.y;
        const road = roadHeight(wx, wy), roadVelocity = (road - w.road) / dt;
        w.road = road;
        w.travel = clamp(road - s.heave - s.roll * w.y - s.pitch * w.x, -.12, .12);
        w.travelRate = clamp(roadVelocity - s.heaveRate - s.rollRate * w.y - s.pitchRate * w.x, -2, 2);
        const staticForce = mass * G * (w.x > 0 ? 1.4 : 1.25) / L / 2;
        w.normalForce = clamp(staticForce + (w.x > 0 ? frontStiffness : rearStiffness) * w.travel + damper * w.travelRate, 0, 12000);
        vertical += w.normalForce; rollMoment += w.y * w.normalForce; pitchMoment += w.x * w.normalForce;
      }
      const springAccel = (vertical - mass * G) / mass;
      s.heaveRate += springAccel * dt; s.heave = clamp(s.heave + s.heaveRate * dt, -.14, .14);
      s.rollRate += (mass * s.ay * .40 + rollMoment) / 720 * dt;
      s.pitchRate += (mass * s.ax * .40 + pitchMoment) / 2100 * dt;
      s.roll = clamp(s.roll + s.rollRate * dt, -.18, .18);
      s.pitch = clamp(s.pitch + s.pitchRate * dt, -.18, .18);
      let fx = 0, fy = 0, yawMoment = 0;
      for (const w of s.wheels) {
        const front = w.x > 0, delta = front ? s.roadAngle : 0;
        const vx = s.u - s.yaw * w.y, vy = s.v + s.yaw * w.x;
        const wheelVx = vx * Math.cos(delta) + vy * Math.sin(delta);
        const wheelVy = -vx * Math.sin(delta) + vy * Math.cos(delta);
        w.slipAngle = Math.atan2(wheelVy, Math.max(2, Math.abs(wheelVx)));
        const mu = s.offTrack ? .58 : 1.08, capacity = Math.max(1, w.normalForce * mu);
        const engineForce = front ? s.throttle * 3900 / (1 + s.u / 45) : 0;
        const brakeForce = s.brake * (front ? 4800 : 3000);
        const demand = engineForce - brakeForce;
        // Invert a smooth tyre force law; relax slip over 60 ms, independently per wheel.
        const ratioTarget = clamp(demand / Math.max(800, w.normalForce * 8), -.6, .6);
        w.slipRatio += (ratioTarget - w.slipRatio) * (1 - Math.exp(-dt / .06));
        let contactFx = clamp(w.slipRatio * w.normalForce * 8, -capacity, capacity);
        if (s.u < .05 && demand <= 0) contactFx = 0;
        const lateralCapacity = Math.sqrt(Math.max(0, capacity * capacity - contactFx * contactFx));
        const corneringStiffness = 70000 * w.normalForce / (mass * G / 4);
        const contactFy = -lateralCapacity * Math.tanh(corneringStiffness * w.slipAngle / Math.max(1, lateralCapacity));
        const bodyFx = contactFx * Math.cos(delta) - contactFy * Math.sin(delta);
        const bodyFy = contactFx * Math.sin(delta) + contactFy * Math.cos(delta);
        fx += bodyFx; fy += bodyFy; yawMoment += w.x * bodyFy - w.y * bodyFx;
        w.speed = Math.max(0, wheelVx * (1 + w.slipRatio));
        w.rotation += w.speed / VEHICLE.tyre_radius_m * dt;
      }
      const resistance = s.u > .02 ? 180 + .42 * s.u * s.u + (s.offTrack ? s.u * 110 : 0) : 0;
      s.ax = (fx - resistance) / mass; s.ay = fy / mass;
      const du = s.ax + s.v * s.yaw, dv = s.ay - s.u * s.yaw;
      s.u = clamp(s.u + du * dt, 0, 55);
      s.v = clamp(s.v + dv * dt, -18, 18);
      s.yaw += (yawMoment / 2500 - s.yaw * this.setup.yaw_damping_hz * .035) * dt;
      if (s.u < .5) { s.v *= Math.exp(-12 * dt); s.yaw *= Math.exp(-12 * dt); }
      s.heading = wrap(s.heading + s.yaw * dt);
      s.x += (s.u * Math.cos(s.heading) - s.v * Math.sin(s.heading)) * dt;
      s.y += (s.u * Math.sin(s.heading) + s.v * Math.cos(s.heading)) * dt;
      s.speed = Math.hypot(s.u, s.v);
    }
    sample() {
      const s = this.state, p = project(s.x, s.y);
      return { t_ms: this.time, lap: this.tracker.laps.length + 1, sector: Math.min(4, Math.floor(p.s / TRACK.length_m * 4) + 1),
        progress: round(p.s / TRACK.length_m), speed_kmh: round(s.speed * 3.6), steering: round(s.steering),
        steering_wheel_deg: round(s.roadAngle * VEHICLE.steering_ratio * 180 / Math.PI), road_wheel_angle_deg: round(s.roadAngle * 180 / Math.PI),
        throttle: round(s.throttle), brake: round(s.brake), throttle_pct: round(s.throttle * 100), brake_pct: round(s.brake * 100),
        lat_g: round(s.ay / G), long_g: round(s.ax / G), yaw_rate_deg_s: round(s.yaw * 180 / Math.PI),
        x_m: round(s.x), y_m: round(s.y), z_m: round(s.heave), heading_rad: round(s.heading),
        roll_rad: round(s.roll), pitch_rad: round(s.pitch), on_track: !s.offTrack, lap_valid_so_far: this.tracker.reasons.size === 0,
        wheels: Object.fromEntries(s.wheels.map(w => [w.id, { suspension_travel_m: round(w.travel),
          slip_angle_rad: round(w.slipAngle), longitudinal_slip_ratio: round(w.slipRatio),
          speed_kmh: round(w.speed * 3.6), normal_force_n: round(w.normalForce) }])) };
    }
    record() { if (this.samples.at(-1)?.t_ms !== this.time) this.samples.push(this.sample()); }
    recover() {
      if (!['running', 'paused'].includes(this.status)) return;
      this.tracker.close(this.time, false, 'recovery'); this.resetState();
      this.events.push({ t_ms: this.time, type: 'recovery_to_start' });
      if (this.samples.at(-1)?.t_ms === this.time) this.samples.pop();
      if (this.time % SAMPLE_MS === 0) this.record();
    }
    finish(reason = 'user_stop') {
      if (this.status === 'finished') return;
      this.status = 'finished'; this.finishReason = reason; this.record();
      if (this.time > this.tracker.startTime) this.tracker.close(this.time, false, 'incomplete');
    }
    exportLog() {
      const laps = copy(this.tracker.laps);
      if (this.status !== 'finished' && this.time > this.tracker.startTime) laps.push({
        lap: laps.length + 1, start_t_ms: this.tracker.startTime, end_t_ms: this.time,
        lap_time_ms: this.time - this.tracker.startTime, completed: false, valid: false,
        invalid_reasons: [...new Set([...this.tracker.reasons, 'incomplete'])], checkpoints: [...this.tracker.checkpoints] });
      const samples = copy(this.samples);
      if (samples.at(-1)?.t_ms !== this.time) samples.push(this.sample());
      return { schema_version: VERSION, session_id: this.sessionId, created_at: this.createdAt,
        source: 'simulation_' + this.mode, simulation: true, vehicle_id: VEHICLE.id, track_id: TRACK.id,
        simulator: { name: 'nostalife-3d-simulator', version: VERSION, physics_step_ms: 5,
          limitations: ['Simplified four-wheel/three-body-mode model; not an Avante digital twin.',
            'Tyre slip angle and longitudinal slip ratio are separate; neither is calibrated to ACC wheelSlip.',
            'Suspension travel is model-relative spring compression; not a production sensor reading.'] },
        setup: copy(this.setup), vehicle_model: copy(VEHICLE), sampling: { interval_ms: SAMPLE_MS, time_basis: 'elapsed_simulation_time' },
        units: { speed_kmh: 'km/h', steering_wheel_deg: 'deg', yaw_rate_deg_s: 'deg/s', lat_g: 'g', long_g: 'g',
          throttle_pct: '%', brake_pct: '%', suspension_travel_m: 'm; positive compression', slip_angle_rad: 'rad', longitudinal_slip_ratio: 'ratio' },
        status: this.status, finish_reason: this.finishReason, duration_ms: this.time, samples, laps, events: copy(this.events),
        summary: { sample_count: samples.length, valid_laps: laps.filter(l => l.valid).length, completed_laps: laps.filter(l => l.completed).length } };
    }
  }
  return { VERSION, STEP_MS, SAMPLE_MS, G, VEHICLE, TRACK, CORNERS, PRESETS, pointAt, project, roadHeight, Session, autopilot };
});
