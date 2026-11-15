const goal=document.getElementById('drive-goal'),comfort=document.getElementById('comfort-target'),response=document.getElementById('response-target');
function paint(){document.getElementById('comfort-number').textContent=comfort.value;document.getElementById('response-number').textContent=response.value;}
comfort.addEventListener('input',paint);response.addEventListener('input',paint);paint();
async function refresh(){
 try{
  const reply=await fetch('/local/preference',{cache:'no-store'});if(!reply.ok)return;
  const value=await reply.json();goal.value=value.goal;comfort.value=value.comfort;response.value=value.steering_response;
  goal.readOnly=true;comfort.disabled=true;response.disabled=true;paint();
  document.getElementById('preference-note').textContent='Pleos 앱에서 저장한 목표·선호입니다. 수치 결론을 지원하는 서버에서 다음 분석에 반영됩니다.';
 }catch{}
}
refresh();setInterval(()=>{if(!document.hidden)refresh();},3000);
