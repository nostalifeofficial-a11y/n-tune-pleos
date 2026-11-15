// Candidate menu values for the contest UI. These are not vehicle control commands.
const three=['Normal','Sport','Sport+'];
const chassis={suspension:three,steering:three};
const electric=lastSound=>({...chassis,motor:['Eco',...three],elsd:three,
 sound:['Off','Ignition','Evolution',lastSound],eshift:['Off','On']});
export const N_CUSTOM_PROFILES=[
 {car:'avante_n',variant:'DCT',settings:{...chassis,engine:three,transmission:three,elsd:['Normal','Sport'],sound:three}},
 {car:'avante_n',variant:'MT',settings:{...chassis,engine:three,rev:three,elsd:['Normal','Sport'],sound:three}},
 {car:'ioniq5_n',variant:'EV',settings:electric('Supersonic')},
 {car:'ioniq6_n',variant:'EV',settings:{...electric('Lightspeed'),ambient:['Off','On'],battery:['Off','Drag','Sprint','Endurance']}}
];
export const N_CUSTOM_SCHEMA={type:'array',items:{type:'object',additionalProperties:false,properties:{
 car:{type:'string',enum:['avante_n','ioniq5_n','ioniq6_n']},variant:{type:'string',enum:['DCT','MT','EV']},
 scope:{type:'string',enum:['ui_preview']},reason:{type:'string'},
 settings:{type:'array',items:{type:'object',additionalProperties:false,properties:{
  key:{type:'string',enum:[...new Set(N_CUSTOM_PROFILES.flatMap(p=>Object.keys(p.settings)))]},
  value:{type:'string',enum:[...new Set(N_CUSTOM_PROFILES.flatMap(p=>Object.values(p.settings).flat()))]}},required:['key','value']}}},
 required:['car','variant','scope','reason','settings']}};
export function validNCustom(rows){
 if(!Array.isArray(rows)||rows.length>4)return false;
 const profiles=new Set();
 return rows.every(row=>{
  if(!row||typeof row!=='object')return false;
  const profile=N_CUSTOM_PROFILES.find(p=>p.car===row.car&&p.variant===row.variant),id=row.car+':'+row.variant;
  if(!profile||profiles.has(id)||row.scope!=='ui_preview'||typeof row.reason!=='string'||!row.reason.trim()||row.reason.length>200||
   !Array.isArray(row.settings)||row.settings.length>Object.keys(profile.settings).length)return false;
  profiles.add(id);const keys=new Set();
  return row.settings.every(s=>{
   const choices=s&&Object.hasOwn(profile.settings,s.key)?profile.settings[s.key]:null;
   if(!s||keys.has(s.key)||!Array.isArray(choices)||!choices.includes(s.value))return false;
   keys.add(s.key);return true;
  });
 });
}
