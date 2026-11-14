import test from 'node:test';import assert from 'node:assert/strict';
import {analyzeWithAI} from '../lib/ai.js';
const analysis={headline:'가속 구간 분석',diagnosis:'측정 설명',driver_coaching:'가속을 부드럽게',next_check:'같은 구간 비교',preset_reason:'현재 유지',recommended_preset:null,limitations:[]};
const response=value=>Response.json({id:'resp_test',status:'completed',model:'model-returned',output:[{type:'message',content:[{type:'output_text',text:JSON.stringify(value)}]}]});
test('uses server credential, no storage, bounded input, and strict Responses schema',async()=>{
 const value=await analyzeWithAI({simulation:true,measurements:{lat_g_p95_abs:.2}},{OPENAI_API_KEY:'unit-test-only'}, {fetcher:async(url,options)=>{
  assert.equal(url,'https://api.openai.com/v1/responses');assert.equal(options.headers.Authorization,'Bearer unit-test-only');
  assert.equal(options.redirect,'manual','Cloudflare supports manual redirects; credentials must not follow another host');
  const body=JSON.parse(options.body);assert.equal(body.store,false);assert.equal(body.text.format.strict,true);assert.equal(body.text.format.type,'json_schema');
  assert.match(body.instructions,/완주나 유효 3랩을 요구하지 않는다/);assert.equal(JSON.parse(body.input).measurements.lat_g_p95_abs,.2);
  return response(analysis);
 }});
 assert.equal(value.model,'model-returned');assert.deepEqual(value.analysis,analysis);assert.equal(value.response_id,'resp_test');
});
test('missing or rejected keys, quota, and incomplete output are explicit failures',async()=>{
 await assert.rejects(analyzeWithAI({},{}),e=>e.code==='AI_NOT_CONFIGURED');
 for(const [status,code] of [[401,'AI_AUTH_FAILED'],[429,'AI_LIMIT'],[503,'AI_UNAVAILABLE']]){
  await assert.rejects(analyzeWithAI({},{OPENAI_API_KEY:'test'},{fetcher:async()=>Response.json({error:{code:'insufficient_quota'}},{status})}),e=>e.code===code);
 }
 await assert.rejects(analyzeWithAI({},{OPENAI_API_KEY:'test'},{fetcher:async()=>Response.json({status:'incomplete'})}),e=>e.code==='AI_INCOMPLETE');
 await assert.rejects(analyzeWithAI({},{OPENAI_API_KEY:'test'},{fetcher:async()=>response({...analysis,recommended_preset:'unsafe'})}),e=>e.code==='AI_RESPONSE_INVALID');
});
