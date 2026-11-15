import windowMetrics from '../analysis-demo/window.cjs';
import {analyzeWithAI,drivingPreference} from '../lib/ai.js';
import {makeResult,advicePayload,makeAdvice} from '../lib/result.js';
const CODE=/^[A-HJ-NP-Z2-9]{10}$/;
const alphabet='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const TTL=7*24*60*60*1000, MAX_BODY=32*1024*1024;
const empty={schema_version:'1.0.0',status:'empty',recommendation:null};
const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'}});
async function hash(value){return [...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)))].map(x=>x.toString(16).padStart(2,'0')).join('');}
function randomCode(){return [...crypto.getRandomValues(new Uint8Array(10))].map(x=>alphabet[x%32]).join('');}
async function limit(env,request,type,count,period,identity=null){
 const now=Date.now(),window=Math.floor(now/period),ip=identity||request.headers.get('CF-Connecting-IP')||'local';
 const key=type+':'+await hash(window+':'+ip);
 const row=await env.DB.prepare('INSERT INTO request_limits (key,count,expires) VALUES (?,1,?) ON CONFLICT(key) DO UPDATE SET count=count+1 RETURNING count').bind(key,(window+1)*period).first();
 return row.count<=count;
}
async function body(request,max=MAX_BODY){
 if(!request.headers.get('Content-Type')?.startsWith('application/json'))throw Object.assign(new Error('JSON 요청이 필요합니다.'),{status:415});
 if(Number(request.headers.get('Content-Length')||0)>max)throw Object.assign(new Error('주행 기록이 너무 큽니다. 새 주행에서 다시 시도해 주세요.'),{status:413});
 const reader=request.body?.getReader();if(!reader)throw Object.assign(new Error('주행 기록이 없습니다.'),{status:400});
 const chunks=[];let size=0;while(true){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>max){await reader.cancel();throw Object.assign(new Error('주행 기록이 너무 큽니다.'),{status:413});}chunks.push(value);}
 const buffer=new Uint8Array(size);let offset=0;for(const c of chunks){buffer.set(c,offset);offset+=c.length;}
 try{return JSON.parse(new TextDecoder().decode(buffer));}catch{throw Object.assign(new Error('주행 기록 형식을 읽을 수 없습니다.'),{status:400});}
}
export function createWorker({ai=analyzeWithAI}={}){return {async fetch(request,env,ctx){
 const url=new URL(request.url),path=url.pathname,now=Date.now();
 if(!path.startsWith('/api/'))return env.ASSETS.fetch(request);
 try{
  if(path==='/api/health')return json({ok:true,version:'5.3.0',simulation:true,analysis_scope:'start_to_analyze',required_valid_laps:0,ai_configured:!!env.OPENAI_API_KEY,driving_targets:true,brief:true,n_custom:true,goal_advice:true});
  if(request.method!=='GET'){
   const origin=request.headers.get('Origin');if(origin&&origin!==url.origin)return json({message:'허용되지 않은 요청입니다.'},403);
   if(request.headers.get('Sec-Fetch-Site')==='cross-site')return json({message:'허용되지 않은 요청입니다.'},403);
  }
  if(path==='/api/channels'&&request.method==='POST'){
   if(!await limit(env,request,'create',24,3600000))return json({message:'연결을 너무 자주 만들었습니다. 잠시 후 다시 시도해 주세요.'},429);
   await body(request,1024);
   const secret=[...crypto.getRandomValues(new Uint8Array(32))].map(x=>x.toString(16).padStart(2,'0')).join(''),writeHash=await hash(secret);
   for(let attempt=0;attempt<4;attempt++){
    const code=randomCode();const row=await env.DB.prepare('INSERT OR IGNORE INTO channels (code,write_hash,created,expires) VALUES (?,?,?,?) RETURNING code').bind(code,writeHash,now,now+TTL).first();
    if(row){ctx.waitUntil(Promise.all([env.DB.prepare('DELETE FROM channels WHERE expires < ?').bind(now).run(),env.DB.prepare('DELETE FROM request_limits WHERE expires < ?').bind(now).run()]));return json({code,write_token:secret,expires_at:new Date(now+TTL).toISOString()},201);}
   }
   return json({message:'연결 생성에 실패했습니다. 다시 시도해 주세요.'},503);
  }
  const matched=/^\/api\/channels\/([^/]+)\/(latest|analyze|status|received|advice)$/.exec(path);
  if(!matched||!CODE.test(matched[1]))return json(empty,404);
  const [,code,action]=matched;
  if(action==='advice'&&request.method==='POST'){
   const input=await body(request,4096);
   if(typeof input?.recommendation_id!=='string'||! /^[a-f0-9-]{36}$/.test(input.recommendation_id))return json({message:'추천에 사용할 주행 기록을 먼저 연결해 주세요.'},400);
   const preference=drivingPreference(input.driving_preference);
   const row=await env.DB.prepare('SELECT result FROM channels WHERE code=? AND expires>?').bind(code,now).first();
   if(!row?.result)return json({message:'연결된 주행 기록이 없습니다. 홈페이지에서 분석을 보내 주세요.'},404);
   const record=JSON.parse(row.result);
   if(record.recommendation_id!==input.recommendation_id)return json({message:'새 주행 기록이 도착했습니다. 최신 기록으로 다시 추천받아 주세요.'},409);
   // The existing read code grants access to this summary only. This operation returns a
   // derived suggestion; it never changes channel results, receipts, expiry or write access.
   if(!await limit(env,request,'advice',30,3600000)||!await limit(env,request,'advice-channel',12,3600000,code))return json({message:'추천 요청이 많습니다. 잠시 후 다시 시도해 주세요.'},429);
   const response=await ai(advicePayload(record,preference),env);
   const latest=await env.DB.prepare('SELECT result FROM channels WHERE code=? AND expires>?').bind(code,Date.now()).first();
   if(latest?.result!==row.result)return json({message:'주행 기록이 바뀌었습니다. 최신 기록으로 다시 추천받아 주세요.'},409);
   return json(makeAdvice(record,response,preference));
  }
  if(action==='status'&&request.method==='GET'){
   if(!await limit(env,request,'status',60,60000))return json({message:'잠시 후 다시 확인합니다.'},429);
   const row=await env.DB.prepare('SELECT result,active_request,app_seen_at,received_id,received_at FROM channels WHERE code=? AND expires>?').bind(code,now).first();
   if(!row)return json({message:'연결이 만료됐습니다. 새 연결을 만들어 주세요.'},404);
   const current=row.result?JSON.parse(row.result).recommendation_id:null;
   return json({code,analyzing:!!row.active_request,app_online:!!row.app_seen_at&&now-row.app_seen_at<15000,
    result_id:current,received_id:row.received_id||null,received_at:row.received_at?new Date(row.received_at).toISOString():null,
    received:!!current&&current===row.received_id});
  }
  if(action==='received'&&request.method==='POST'){
   if(!await limit(env,request,'receipt',60,60000))return json({message:'잠시 후 다시 확인합니다.'},429);
   const input=await body(request,256);
   if(typeof input?.recommendation_id!=='string'||! /^[a-f0-9-]{36}$/.test(input.recommendation_id))return json({message:'수신한 기록 번호가 필요합니다.'},400);
   const row=await env.DB.prepare('SELECT result FROM channels WHERE code=? AND expires>?').bind(code,now).first();
   if(!row?.result||JSON.parse(row.result).recommendation_id!==input.recommendation_id)return json({message:'최신 기록과 다릅니다.'},409);
   await env.DB.prepare('UPDATE channels SET received_id=?,received_at=?,app_seen_at=? WHERE code=? AND result=? AND expires>?').bind(input.recommendation_id,now,now,code,row.result,now).run();
   return json({received:true});
  }
  if(action==='latest'&&request.method==='GET'){
   if(!await limit(env,request,'read',120,60000))return json({message:'요청이 많습니다. 잠시 후 다시 시도해 주세요.'},429);
   const row=await env.DB.prepare('SELECT result FROM channels WHERE code=? AND expires>?').bind(code,now).first();
   if(request.headers.get('X-Driving-Lab-Client')==='pleos')await env.DB.prepare('UPDATE channels SET app_seen_at=? WHERE code=? AND expires>?').bind(now,code,now).run();
   return row?.result?new Response(row.result,{headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer'}}):json(empty,404);
  }
  if(action==='analyze'&&request.method==='POST'){
   const token=request.headers.get('Authorization')?.replace(/^Bearer /,'')||'';
   if(!/^[a-f0-9]{64}$/.test(token))return json({message:'이 주행의 연결 정보가 없습니다. 새 연결을 만들어 주세요.'},401);
   const row=await env.DB.prepare('SELECT code FROM channels WHERE code=? AND write_hash=? AND expires>?').bind(code,await hash(token),now).first();
   if(!row)return json({message:'연결이 만료됐습니다. 새 연결을 만들어 주세요.'},401);
   if(!await limit(env,request,'analyze',30,3600000))return json({message:'분석 요청이 많습니다. 잠시 후 다시 시도해 주세요.'},429);
   const input=await body(request);
   if(input?.log?.simulator?.name!=='nostalife-3d-simulator'||!Array.isArray(input.log.samples)||input.log.samples.length>18002)return json({message:'3D 주행 기록은 한 번에 최대 15분까지 분석할 수 있습니다.'},400);
   const measured=windowMetrics.measureWindow(input.log),job=crypto.randomUUID();
   measured.payload.driving_preference=drivingPreference(input.driving_preference);
   await env.DB.prepare('UPDATE channels SET active_request=? WHERE code=?').bind(job,code).run();
   try{
    const response=await ai(measured.payload,env),result=makeResult(input.log,measured,response),finished=Date.now();
    result.driving_preference=measured.payload.driving_preference;
    result.delivery={code,expires_at:new Date(finished+TTL).toISOString()};
    const saved=await env.DB.prepare('UPDATE channels SET result=?,updated=?,expires=?,active_request=NULL WHERE code=? AND active_request=? AND expires>? RETURNING code').bind(JSON.stringify(result),finished,finished+TTL,code,job,finished).first();
    if(!saved)return json({message:'더 최근에 요청한 분석이 있습니다. 최신 분석 결과를 확인해 주세요.'},409);
    return json(result);
   }finally{await env.DB.prepare('UPDATE channels SET active_request=NULL WHERE code=? AND active_request=?').bind(code,job).run();}
  }
  return json({message:'지원하지 않는 요청입니다.'},405);
 }catch(error){return json({message:error.code==='INVALID_LOG'?'주행 기록이 올바르지 않습니다. '+error.message:error.status?error.message:'분석 서버에서 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'},error.status||(error.code==='INVALID_LOG'?400:500));}
}};}
export default createWorker();
