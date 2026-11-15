import test from 'node:test';import assert from 'node:assert/strict';
import {analyzeWithAI,validateAnalysis,drivingPreference} from '../lib/ai.js';
import {N_CUSTOM_PROFILES} from '../lib/n-custom.js';
const analysis={headline:'가속 구간 분석',diagnosis:'측정 설명',driver_coaching:'가속을 부드럽게',next_check:'같은 구간 비교',preset_reason:'현재 유지',recommended_preset:null,limitations:[],driving_targets:{comfort:70,steering_response:45,scope:'preference_trial',reason:'측정과 목표 근거',next_check:'다음 주행 비교'}};
const response=value=>Response.json({id:'resp_test',status:'completed',model:'model-returned',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify(value)}]}]});
analysis.brief={summary:'짧은 구간의 반응을 확인했어요.',next_action:'같은 구간에서 조향을 부드럽게 이어 보세요.'};
analysis.n_custom=N_CUSTOM_PROFILES.map(p=>({car:p.car,variant:p.variant,scope:'ui_preview',reason:'주행 목표에 맞춰 화면에서 비교할 후보입니다.',settings:[{key:'suspension',value:'Normal'}]}));

test('AI chooses explicit stepped targets; missing and invalid numbers never become a recommendation',()=>{
 assert.deepEqual(validateAnalysis(analysis).driving_targets,analysis.driving_targets);
 for(const value of [-5,103,67,75.5,'75',null])assert.throws(()=>validateAnalysis({...analysis,driving_targets:{...analysis.driving_targets,comfort:value}}),e=>e.code==='AI_RESPONSE_INVALID');
 assert.throws(()=>validateAnalysis({...analysis,driving_targets:undefined}),e=>e.code==='AI_RESPONSE_INVALID');
 assert.deepEqual(drivingPreference({goal:'편안하게',comfort:80,steering_response:35}),{goal:'편안하게',comfort:80,steering_response:35});
 assert.throws(()=>drivingPreference({goal:'x',comfort:67,steering_response:35}),e=>e.status===400);
});
test('uses server credential, no storage, bounded input, and strict Responses schema',async()=>{
 const value=await analyzeWithAI({simulation:true,measurements:{lat_g_p95_abs:.2}},{OPENAI_API_KEY:'unit-test-only'}, {fetcher:async(url,options)=>{
  assert.equal(url,'https://api.openai.com/v1/responses');assert.equal(options.headers.Authorization,'Bearer unit-test-only');
  assert.equal(options.redirect,'manual','Cloudflare supports manual redirects; credentials must not follow another host');
  const body=JSON.parse(options.body);assert.equal(body.store,false);assert.equal(body.text.format.strict,true);assert.equal(body.text.format.type,'json_schema');
  assert.match(body.instructions,/완주나 유효 3랩을 요구하지 않는다/);assert.equal(JSON.parse(body.input).measurements.lat_g_p95_abs,.2);
  assert.deepEqual(JSON.parse(body.input).n_custom_catalog,N_CUSTOM_PROFILES);
  assert.ok(body.text.format.schema.required.includes('brief'));assert.ok(body.text.format.schema.required.includes('n_custom'));
  return response(analysis);
 }});
 assert.equal(value.model,'model-returned');assert.deepEqual(value.analysis,analysis);assert.equal(value.response_id,'resp_test');
});
test('short answer and car-specific N candidates reject incompatible, duplicate, or invented values',()=>{
 assert.deepEqual(validateAnalysis(analysis).n_custom,analysis.n_custom);
 const valid={car:'avante_n',variant:'MT',scope:'ui_preview',reason:'UI 비교 후보',settings:[{key:'engine',value:'Sport'},{key:'rev',value:'Sport+'}]};
 assert.deepEqual(validateAnalysis({...analysis,n_custom:[valid]}).n_custom,[valid]);
 for(const invalid of [
  {...valid,variant:'EV'}, {...valid,scope:'vehicle_control'},
  {...valid,settings:[{key:'transmission',value:'Sport'}]},
  {...valid,settings:[{key:'esc',value:'Off'}]},
  {...valid,settings:[{key:'engine',value:'Race'}]},
  {...valid,settings:[{key:'__proto__',value:'Sport'}]},
  {...valid,settings:[{key:'engine',value:'Sport'},{key:'engine',value:'Normal'}]},
  {car:'ioniq5_n',variant:'EV',scope:'ui_preview',reason:'x',settings:[{key:'sound',value:'Lightspeed'}]},
  {car:'ioniq6_n',variant:'EV',scope:'ui_preview',reason:'x',settings:[{key:'sound',value:'Supersonic'}]}
 ])assert.throws(()=>validateAnalysis({...analysis,n_custom:[invalid]}),e=>e.code==='AI_RESPONSE_INVALID');
 assert.throws(()=>validateAnalysis({...analysis,n_custom:[valid,valid]}),e=>e.code==='AI_RESPONSE_INVALID');
 assert.throws(()=>validateAnalysis({...analysis,brief:{summary:'a'.repeat(121),next_action:'x'}}),e=>e.code==='AI_RESPONSE_INVALID');
 assert.throws(()=>validateAnalysis({...analysis,brief:undefined}),e=>e.code==='AI_RESPONSE_INVALID');
 assert.deepEqual(validateAnalysis({...analysis,n_custom:[{...valid,settings:[]}]}).n_custom[0].settings,[]);
});
test('missing or rejected keys, quota, and incomplete output are explicit failures',async()=>{
 await assert.rejects(analyzeWithAI({},{}),e=>e.code==='AI_NOT_CONFIGURED');
 for(const [status,code] of [[401,'AI_AUTH_FAILED'],[429,'AI_LIMIT'],[503,'AI_UNAVAILABLE']]){
  await assert.rejects(analyzeWithAI({},{OPENAI_API_KEY:'test'},{fetcher:async()=>Response.json({error:{code:'insufficient_quota'}},{status})}),e=>e.code===code);
 }
 await assert.rejects(analyzeWithAI({},{OPENAI_API_KEY:'test'},{fetcher:async()=>Response.json({status:'incomplete'})}),e=>e.code==='AI_INCOMPLETE');
 await assert.rejects(analyzeWithAI({},{OPENAI_API_KEY:'test'},{fetcher:async()=>response({...analysis,recommended_preset:'unsafe'})}),e=>e.code==='AI_RESPONSE_INVALID');
});
