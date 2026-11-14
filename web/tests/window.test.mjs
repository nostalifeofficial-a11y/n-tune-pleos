import test from 'node:test';import assert from 'node:assert/strict';import S from '../driving-simulator-3d/core.js';
import metrics from '../analysis-demo/window.cjs';
test('the partial off-track tail contributes to the analysis and is never fabricated as a lap',()=>{
 const s=new S.Session();s.start();for(let i=0;i<1000;i++)s.step({throttle:1,steering:.7});
 const log=s.exportLog(),r=metrics.measureWindow(log);
 assert.equal(r.window.duration_s,10);assert.equal(r.window.sample_count,log.samples.length);assert.equal(r.selectedLaps.length,0);
 assert.equal(r.metrics.lap_time_median_s,null);assert.equal(r.metrics.excluded_lap_count,0);
 const expected=log.samples.reduce((a,b)=>a+b.throttle_pct,0)/log.samples.length;
 assert.ok(Math.abs(r.metrics.throttle_mean_pct-expected)<1e-6);
 assert.ok(r.metrics.lat_g_p95_abs>0);assert.ok(r.wheels.fl.suspension_p95_abs_mm>0);
 assert.ok(JSON.stringify(r.payload).length<12000);assert.ok(!('samples' in r.payload));
});
test('separate brake-release absence, standstill, and empty recording',()=>{
 const s=new S.Session();assert.throws(()=>metrics.measureWindow(s.exportLog()),e=>e.code==='EMPTY_RECORDING');
 s.start();for(let i=0;i<100;i++)s.step();const r=metrics.measureWindow(s.exportLog());
 assert.equal(r.window.required_valid_laps,0);assert.equal(r.metrics.brake_release_p95_per_s,null);
 assert.equal(r.metrics.brake_release_sample_count,0);assert.equal(r.metrics.throttle_mean_pct,0);
});
