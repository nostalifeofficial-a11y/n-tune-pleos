const key='nostalife-public-channel-v1';
let channel=null,pending=null,sending=false,checking=false,lastError='';
const nice=code=>code.slice(0,5)+'-'+code.slice(5);
function paint(){document.getElementById('pair-code').textContent=channel?nice(channel.code):'연결 준비 중';}
function showStatus(text,received=false){
 document.getElementById('pair-status').textContent=text;
 const detail=document.getElementById('delivery-status');if(detail)detail.textContent=text;
 document.querySelector('.online-dot')?.classList.toggle('received',received);
}
export async function refreshDelivery(){
 if(!channel||checking)return;
 checking=true;const code=channel.code;
 try{
  const response=await fetch('/api/channels/'+code+'/status',{cache:'no-store'});
  if(code!==channel?.code)return;
  if(response.status===404){showStatus('연결이 만료됐습니다. 새 연결을 만들어 양쪽에 입력해 주세요.');return;}
  if(!response.ok)throw new Error();
  const state=await response.json();
  if(sending||state.analyzing)showStatus('기록 전송 · AI 분석 중입니다. 완료되면 Pleos로 전달합니다.');
  else if(lastError)showStatus('이번 분석 실패 · Pleos에는 이전 결과가 남아 있습니다. 같은 기록으로 다시 분석해 주세요.');
  else if(state.received)showStatus(`최근 결과 Pleos 수신 확인 ✓ · ${state.result_id.slice(0,8)}${state.app_online?' · 앱 연결 중':''}`,true);
  else if(state.result_id)showStatus(`AI 분석 완료 · 이 PC의 Pleos 앱 수신을 기다립니다.`);
  else if(state.app_online)showStatus('Pleos 연결 확인 ✓ · 주행 후 분석을 누르면 결과가 도착합니다.',true);
  else showStatus(`Pleos 연결 대기 · PC 실행기에서 Pleos 시작을 눌러 주세요.`);
 }catch{showStatus('수신 상태를 확인하지 못했습니다. 인터넷 연결을 확인해 주세요.');}
 finally{checking=false;}
}
export async function ensureChannel(force=false){
 if(pending)return pending;
 // A local bridge restart starts a new, isolated channel. Never reuse stale credentials from disk.
 if(!force&&channel&&channel.expires_at>new Date().toISOString()&&/^[A-HJ-NP-Z2-9]{10}$/.test(channel.code)&&/^[a-f0-9]{64}$/.test(channel.write_token)){paint();return channel;}
 pending=(async()=>{const response=await fetch('/api/channels',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({force})});const value=await response.json();if(!response.ok)throw new Error(value.message||'연결을 만들 수 없습니다.');channel=value;try{localStorage.setItem(key,JSON.stringify(channel));}catch{}paint();return channel;})();
 try{return await pending;}finally{pending=null;}
}
export async function sendAnalysis(log){
 const saved=await ensureChannel();sending=true;lastError='';showStatus('기록 전송 · AI 분석 중입니다.');
 try{
  const driving_preference={goal:document.getElementById('drive-goal').value,comfort:Number(document.getElementById('comfort-target').value),steering_response:Number(document.getElementById('response-target').value)};
  const response=await fetch('/api/channels/'+saved.code+'/analyze',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+saved.write_token},body:JSON.stringify({log,driving_preference})});
  const result=await response.json();if(!response.ok)throw new Error(result.message||'분석에 실패했습니다.');
  channel.expires_at=result.delivery.expires_at;try{localStorage.setItem(key,JSON.stringify(channel));}catch{}
  showStatus('서버 저장 완료 · Pleos 수신을 확인하고 있습니다.');return result;
 }catch(error){lastError=error.message;showStatus('이번 분석 실패 · 같은 기록으로 다시 분석해 주세요.');throw error;}
 finally{sending=false;}
}
document.getElementById('copy-code').onclick=async()=>{try{await navigator.clipboard.writeText(nice((await ensureChannel()).code));document.getElementById('copy-code').textContent='복사됨';}catch{document.getElementById('pair-status').textContent='표시된 연결 코드를 직접 입력해 주세요.';}};
document.getElementById('renew-code').onclick=async()=>{if(!confirm('새 연결을 만들면 Pleos에도 새 코드를 입력해야 합니다. 새로 만들까요?'))return;try{await ensureChannel(true);await refreshDelivery();}catch(e){showStatus(e.message);}};
ensureChannel().then(refreshDelivery).catch(e=>{document.getElementById('pair-code').textContent='연결 준비 실패';showStatus(e.message);});
setInterval(()=>{if(!document.hidden)refreshDelivery();},5000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshDelivery();});
