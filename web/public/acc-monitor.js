const status = document.getElementById('acc-state');
const values = document.getElementById('acc-values');
let pending = false;
setInterval(async () => {
  if (document.hidden || pending) return;
  pending = true;
  try {
    const response = await fetch('/local/live?source=acc', {cache:'no-store'});
    const result = await response.json();
    status.textContent = result.message;
    values.textContent = ['speed_kmh','throttle_pct','brake_pct'].map((key,i) =>
      ['속도','가속','제동'][i]+': '+(result.fresh && Number.isFinite(result.values[key]) ? result.values[key].toFixed(1) : '—')+[' km/h',' %',' %'][i]).join('   /   ');
  } catch { status.textContent = '로컬 연결 대기'; values.textContent = '—'; }
  finally { pending = false; }
}, 1000);
