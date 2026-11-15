import {PRESETS} from '../analysis-demo/metrics.cjs';
const comparisons=[['lap_time_median_s','완성랩 시간 중앙값','s','lower'],['lap_time_std_s','완성랩 시간 표준편차','s','lower'],
 ['steering_total_variation_per_s','초당 조향 변화','1/s','observe'],['brake_release_p95_per_s','브레이크 해제 P95','1/s','observe']];
export function makeResult(log,measured,ai){
 const {metrics:m,window:w}=measured;
 return {schema_version:'2.0.0',recommendation_id:crypto.randomUUID(),session_id:log.session_id,created_at:new Date().toISOString(),
  source:log.source,simulation:true,analysis_mode:'external_ai',status:'analyzed',vehicle_id:log.vehicle_id,track_id:log.track_id,
  current_setup:measured.current,recommended_setup:ai.analysis.recommended_preset?{...PRESETS[ai.analysis.recommended_preset]}:null,
  hold_reason:null,analysis_window:w,analysis:ai.analysis,ai:{provider:ai.provider,model:ai.model,response_id:ai.response_id},
  capture_summary:{samples:w.sample_count,duration_s:w.duration_s,scope:w.scope,lat_g_peak_abs:m.lat_g_peak_abs,long_g_peak_abs:m.long_g_peak_abs,
   throttle_mean_pct:m.throttle_mean_pct,brake_mean_pct:m.brake_mean_pct,speed_max_kmh:m.speed_max_kmh},
  evidence:{selected_laps:measured.selectedLaps,observed_metrics:m,wheel_metrics:measured.wheels,handling_signature:measured.handling,
   rationale:ai.analysis.diagnosis,missing_signals:['real_vehicle_sensor_data','ACC_calibrated_wheel_slip','weather','fuel'],
   limitations:ai.analysis.limitations,execution_location:'hosted_worker',same_as_current:ai.analysis.recommended_preset===log.setup.id,
   model:null,telemetry_origin:'simulated_four_wheel_dynamics'},
  comparison_metrics:comparisons.map(([key,label,unit,desired_direction])=>({key,label,unit,desired_direction,baseline:m[key],
    method:key.startsWith('lap_')?'실제 완성한 유효 랩의 참고 통계; 분석의 필수 조건이 아님':'주행 시작부터 분석 버튼을 누른 시점까지의 전체 기록'})),
  comparison_conditions:{lap_selection:'start_to_analyze',minimum_valid_laps:0},
  policy:{simulation_only:true,external_ai:true,training_collection:false,production_upload:true,raw_log_stored:false,retention_days:7}};
}

// Reuse a stored measurement summary; never invent samples or overwrite the recorded drive.
export function advicePayload(record,preference){
 return {simulation:true,source:'pleos_contest_saved_summary',current_setup:record.current_setup,
  available_presets:Object.fromEntries(Object.entries(PRESETS).map(([id,p])=>[id,p.label])),
  units:{acceleration:'g',pedals:'percent (0..100)',speed:'km/h',suspension:'mm',slip_angle:'rad',steering_and_brake_rates:'normalized input per second'},
  analysis_window:record.analysis_window,measurements:record.evidence.observed_metrics,
  wheel_measurements:record.evidence.wheel_metrics,handling:record.evidence.handling_signature,
  driving_preference:preference,limitations:['저장된 주행 요약을 사용하며 원시 표본을 다시 측정하지 않습니다.']};
}
export function makeAdvice(record,ai,preference){
 return {...record,recommendation_id:crypto.randomUUID(),created_at:new Date().toISOString(),
  origin_recommendation_id:record.recommendation_id,driving_preference:preference,
  analysis:ai.analysis,ai:{provider:ai.provider,model:ai.model,response_id:ai.response_id},
  recommended_setup:ai.analysis.recommended_preset?{...PRESETS[ai.analysis.recommended_preset]}:null,
  evidence:{...record.evidence,rationale:ai.analysis.diagnosis,limitations:ai.analysis.limitations,
   same_as_current:ai.analysis.recommended_preset===record.current_setup.id}};
}
