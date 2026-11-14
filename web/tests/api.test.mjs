import test from 'node:test';
import assert from 'node:assert/strict';
import worker,{createWorker} from '../worker/index.js';
import {database} from './db.mjs';
import S from '../driving-simulator-3d/core.js';

export const sampleAnalysis={headline:'테스트 진단',diagnosis:'관측값에 따른 테스트 설명',driver_coaching:'다음 주행의 테스트 팁',
 next_check:'같은 구간에서 비교',preset_reason:'현재 후보 유지',recommended_preset:null,limitations:['시뮬레이션']};
const mockAI=async()=>({analysis:sampleAnalysis,provider:'openai',model:'test-model',response_id:'test-response'});
function harness(ai=mockAI){
 const db=database(),jobs=[],app=ai===null?worker:createWorker({ai});
 const request=(path,method='GET',data,token,extra={})=>app.fetch(new Request('https://test.local'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...extra},...(data!==undefined?{body:JSON.stringify(data)}:{})}),{DB:db},{waitUntil(p){jobs.push(p)}});
 const create=async()=>{const r=await request('/api/channels','POST',{});assert.equal(r.status,201);return r.json()};
 return {db,request,create,jobs};
}
function drive(steps=200){const s=new S.Session();s.start();for(let i=0;i<steps;i++)s.step({throttle:.6,steering:i>150?.1:0});s.pause();return s.exportLog();}

test('short zero-lap drive is analyzed, scoped to its own channel, and raw samples are not stored',async()=>{
 let sentPayload;
 const {request,create,db}=harness(async payload=>{sentPayload=payload;return mockAI();});
 const a=await create(),b=await create(),log=drive();
 assert.notEqual(a.code,b.code);assert.match(a.code,/^[A-HJ-NP-Z2-9]{10}$/);
 assert.equal((await request('/api/channels/'+a.code+'/analyze','POST',{log},b.write_token)).status,401);
 const response=await request('/api/channels/'+a.code+'/analyze','POST',{log},a.write_token);
 assert.equal(response.status,200);const result=await response.json();
 assert.equal(result.status,'analyzed');assert.equal(result.schema_version,'2.0.0');assert.equal(result.analysis_mode,'external_ai');
 assert.equal(result.evidence.selected_laps.length,0);assert.equal(result.analysis_window.duration_s,2);
 assert.equal(result.analysis_window.sample_count,log.samples.length);assert.equal(result.analysis_window.required_valid_laps,0);
 assert.equal(result.policy.external_ai,true);assert.equal(result.policy.raw_log_stored,false);assert.ok(!('samples' in result));
 assert.ok(result.evidence.observed_metrics.throttle_mean_pct>50);assert.ok(result.evidence.observed_metrics.lat_g_p95_abs>0);
 assert.equal(result.evidence.observed_metrics.lap_time_median_s,null);assert.equal(result.recommended_setup,null);
 for(const key of ['front_suspension_diff_p95_mm','rear_suspension_diff_p95_mm','steering_rate_p95_per_s'])assert.equal(typeof result.evidence.observed_metrics[key],'number');
 assert.equal(sentPayload.analysis_window.sample_count,log.samples.length);assert.ok(!('samples' in sentPayload));
 assert.equal((await(await request('/api/channels/'+a.code+'/latest')).json()).session_id,log.session_id);
 assert.equal((await request('/api/channels/'+b.code+'/latest')).status,404);
 db.raw.prepare('UPDATE channels SET expires=0 WHERE code=?').run(a.code);assert.equal((await request('/api/channels/'+a.code+'/latest')).status,404);
});

test('server storage is distinct from actual app receipt, and a new result needs a new receipt',async()=>{
 const {request,create}=harness(),a=await create(),b=await create(),url='/api/channels/'+a.code;
 assert.equal((await(await request(url+'/status')).json()).app_online,false);
 await request(url+'/latest','GET',undefined,undefined,{'X-Driving-Lab-Client':'pleos'});
 assert.equal((await(await request(url+'/status')).json()).app_online,true);
 const first=await(await request(url+'/analyze','POST',{log:drive()},a.write_token)).json();
 assert.equal((await(await request(url+'/status')).json()).received,false);
 assert.equal((await request('/api/channels/'+b.code+'/received','POST',{recommendation_id:first.recommendation_id})).status,409);
 assert.equal((await request(url+'/received','POST',{recommendation_id:first.recommendation_id})).status,200);
 let status=await(await request(url+'/status')).json();assert.equal(status.received,true);assert.equal(status.received_id,first.recommendation_id);
 const next=await(await request(url+'/analyze','POST',{log:drive(300)},a.write_token)).json();
 status=await(await request(url+'/status')).json();assert.equal(status.received,false);assert.equal(status.result_id,next.recommendation_id);
 assert.equal((await request(url+'/received','POST',{recommendation_id:first.recommendation_id})).status,409);
 assert.equal((await request(url+'/received','POST',{recommendation_id:next.recommendation_id})).status,200);
 assert.equal((await(await request(url+'/status')).json()).received,true);
});
test('AI failure preserves the previous result and never falls back to a synthetic model',async()=>{
 let failed=false;const {request,create}=harness(async()=>{if(failed)throw Object.assign(new Error('AI unavailable'),{status:503});return mockAI();});
 const a=await create(),url='/api/channels/'+a.code,log=drive();
 const first=await(await request(url+'/analyze','POST',{log},a.write_token)).json();failed=true;
 assert.equal((await request(url+'/analyze','POST',{log:drive(250)},a.write_token)).status,503);
 assert.equal((await(await request(url+'/latest')).json()).recommendation_id,first.recommendation_id);
 const missing=harness(null),c=await missing.create();
 assert.equal((await missing.request('/api/channels/'+c.code+'/analyze','POST',{log},c.write_token)).status,503);
});
test('a slower earlier AI request cannot overwrite a newer analysis',async()=>{
 let resolveFirst,started;const start=new Promise(r=>started=r);let count=0;
 const {request,create}=harness(async()=>{if(++count===1){started();return new Promise(r=>resolveFirst=r);}return mockAI();});
 const a=await create(),url='/api/channels/'+a.code;
 const earlier=request(url+'/analyze','POST',{log:drive()},a.write_token);await start;
 const latest=await(await request(url+'/analyze','POST',{log:drive(250)},a.write_token)).json();
 resolveFirst(await mockAI());assert.equal((await earlier).status,409);
 assert.equal((await(await request(url+'/latest')).json()).recommendation_id,latest.recommendation_id);
});
test('rejects an empty recording, real-vehicle labels, cross-origin writes, and oversized input before AI',async()=>{
 let calls=0;const {request,create}=harness(async()=>{calls++;return mockAI();});const a=await create(),url='/api/channels/'+a.code+'/analyze';
 assert.equal((await request(url,'POST',{log:drive(0)},a.write_token)).status,422);
 const log=drive();log.source='vehicle';assert.equal((await request(url,'POST',{log},a.write_token)).status,400);
 assert.equal((await request('/api/channels','POST',{},null,{Origin:'https://other.example'})).status,403);
 assert.equal((await request('/api/channels','POST',{padding:'x'.repeat(2000)})).status,413);
 assert.equal((await request(url,'POST',{},a.write_token,{'Content-Length':'999999999'})).status,413);
 assert.equal(calls,0);
});
