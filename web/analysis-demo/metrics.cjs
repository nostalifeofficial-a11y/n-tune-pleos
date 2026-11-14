'use strict';
const validateLog = require('../driving-simulator/validate.js');
const { PRESETS } = require('../driving-simulator/core.js');
const round = (v,n=6) => v === null ? null : Number(v.toFixed(n));
function quantile(values, q) {
  if (!values.length) return null;
  const s=[...values].sort((a,b)=>a-b), p=(s.length-1)*q, i=Math.floor(p);
  return s[i]+(s[Math.min(i+1,s.length-1)]-s[i])*(p-i);
}
const mean = a => a.length ? a.reduce((s,x)=>s+x,0)/a.length : null;
const deviation = a => { const m=mean(a); return m===null?null:Math.sqrt(mean(a.map(x=>(x-m)**2))); };
const MISSING_SIGNALS = ['front_slip_p95_abs','rear_slip_p95_abs','front_slip_max_p95_abs','rear_slip_max_p95_abs','front_slip_imbalance_p95','rear_slip_imbalance_p95','front_suspension_diff_p95_abs','rear_suspension_diff_p95_abs','yaw_steering_ratio_p95','lat_g_p95_abs','long_g_p95_abs'];
function assertLog(log) {
  const checked=validateLog(log);
  if (!checked.valid) { const e=new Error(checked.errors.slice(0,8).join(' ')); e.code='INVALID_LOG'; e.details=checked.errors; throw e; }
  return log;
}
function perLap(log, lap) {
  const samples=log.samples.filter(s=>s.t_ms>=lap.start_t_ms && s.t_ms<lap.end_t_ms);
  const segments=[];
  if(samples.length<2)throw Object.assign(new Error('선택한 유효 랩에 계산 가능한 표본이 부족합니다.'),{code:'INVALID_LOG'});
  // Use only this lap's samples. Hold each value until the next; extend the
  // first/last value to the lap boundary (at most 100 ms). Never borrow a
  // sample from a preceding invalid lap or recovery interval.
  for(let i=0;i<samples.length;i++){
    const start=i===0?lap.start_t_ms:samples[i].t_ms;
    const end=i+1<samples.length?samples[i+1].t_ms:lap.end_t_ms;
    segments.push({sample:samples[i],seconds:(end-start)/1000});
  }
  const seconds=(lap.end_t_ms-lap.start_t_ms)/1000;
  const weighted=key=>segments.reduce((s,p)=>s+p.sample[key]*p.seconds,0)/seconds;
  const rates=[],releaseRates=[],absSteering=samples.map(s=>Math.abs(s.steering));
  let totalVariation=0,corrections=0,lastSign=0,lastCorrection=-Infinity;
  for(let i=1;i<samples.length;i++) {
    const a=samples[i-1],b=samples[i],dt=(b.t_ms-a.t_ms)/1000,d=b.steering-a.steering;
    totalVariation+=Math.abs(d);rates.push(Math.abs(d)/dt);
    if(Math.abs(d)>=0.008) { const sign=Math.sign(d); if(lastSign && sign!==lastSign && b.t_ms-lastCorrection>=250){corrections++;lastCorrection=b.t_ms;}lastSign=sign; }
    if(a.brake>=0.05 && a.brake-b.brake>0.005) releaseRates.push((a.brake-b.brake)/dt);
  }
  const measurements={lap_no:lap.lap,lap_time_s:seconds,speed_mean_kmh:weighted('speed_kmh'),speed_max_kmh:Math.max(...samples.map(s=>s.speed_kmh)),brake_mean:weighted('brake')*100,throttle_mean:weighted('throttle')*100};
  for(const key of Object.keys(measurements)) measurements[key]=round(measurements[key]);
  return { summary:{...measurements,is_valid:true,is_complete:true,is_partial:false,...Object.fromEntries(MISSING_SIGNALS.map(k=>[k,null]))},samples,rates,releaseRates,absSteering,corrections,totalVariation,seconds,metrics:{steering_rate_p95_per_s:round(quantile(rates,.95)),steering_total_variation_per_s:round(totalVariation/seconds),steering_corrections_per_min:round(corrections/seconds*60),brake_release_p95_per_s:round(quantile(releaseRates,.95)),brake_release_sample_count:releaseRates.length} };
}
function analyzeMetrics(log) {
  assertLog(log);
  const eligible=log.laps.filter(l=>l.valid===true && l.completed===true);
  const selected=eligible.slice(0,4), rows=selected.map(l=>perLap(log,l));
  const times=rows.map(r=>r.seconds),rates=rows.flatMap(r=>r.rates),releaseRates=rows.flatMap(r=>r.releaseRates),absSteering=rows.flatMap(r=>r.absSteering);
  const total=times.reduce((s,v)=>s+v,0),corrections=rows.reduce((s,r)=>s+r.corrections,0);
  const observed={
    lap_time_median_s:round(quantile(times,.5)),lap_time_std_s:round(deviation(times)),lap_time_range_s:times.length?round(Math.max(...times)-Math.min(...times)):null,
    speed_mean_kmh:total?round(rows.reduce((s,r)=>s+r.summary.speed_mean_kmh*r.seconds,0)/total):null,
    speed_max_kmh:rows.length?Math.max(...rows.map(r=>r.summary.speed_max_kmh)):null,
    steering_rate_p95_per_s:round(quantile(rates,.95)),steering_abs_p95:round(quantile(absSteering,.95)),
    steering_total_variation_per_s:total?round(rows.reduce((s,r)=>s+r.totalVariation,0)/total):null,
    steering_corrections_per_min:total?round(corrections/total*60):null,
    brake_release_p95_per_s:round(quantile(releaseRates,.95)),brake_release_sample_count:releaseRates.length,
    valid_laps_available:eligible.length,selected_lap_count:rows.length,selected_duration_s:round(total),selected_sample_count:rows.reduce((s,r)=>s+r.samples.length,0),
    excluded_lap_count:log.laps.filter(l=>!(l.valid&&l.completed)).length
  };
  const payload={game:'SIMULATION',simulation:true,source:{input_type:log.source},session_id:log.session_id,car:log.vehicle_id,track:log.track_id,current_setup:{...log.setup,family:'simulation:'+log.setup.id},conditions:{weather:'unknown',air_temp_c:null,track_temp_c:null,fuel_start_l:null},summary:{game:'SIMULATION',lap_summaries:rows.map(r=>r.summary)},events:[],unsupported_measurements:[...MISSING_SIGNALS,'ACC_handling_events']};
  return {observed,payload,selected_laps:selected.map(l=>l.lap),lap_metrics:rows.map(r=>({lap:r.summary.lap_no,lap_time_s:r.seconds,...r.metrics})),enough:rows.length>=3};
}
function ruleCandidate(metrics) {
  const m=metrics.observed;
  if ((m.steering_corrections_per_min??0)>8 || (m.steering_rate_p95_per_s??0)>1.25 || (m.brake_release_p95_per_s??0)>4) return {id:'stable',rule:'input_changes_high',explanation:'빠른 입력 변화가 관측되어 조향 이득이 낮은 안정형을 다음 비교 후보로 선택했습니다.'};
  if((m.steering_abs_p95??0)>.65) return {id:'rotation',rule:'steering_input_high',explanation:'정규화 조향 입력의 상위 95% 값이 커서 회전형을 비교 후보로 선택했습니다. 언더스티어 진단은 아닙니다.'};
  return {id:'balanced',rule:'balanced_baseline',explanation:'급격한 입력 변화 조건에 해당하지 않아 균형형을 비교 기준으로 선택했습니다.'};
}
module.exports={assertLog,analyzeMetrics,ruleCandidate,MISSING_SIGNALS,PRESETS,quantile,mean,deviation};
