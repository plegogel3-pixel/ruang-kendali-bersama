import { createRemoteJWKSet, jwtVerify } from "jose";
interface Env { PAIR_DB:D1Database; FIREBASE_PROJECT_ID:string; PAIR_HMAC_KEY:string; }
const cors={"access-control-allow-origin":"*","access-control-allow-headers":"authorization,content-type","access-control-allow-methods":"POST,OPTIONS"};
const reply=(v:unknown,s=200)=>new Response(JSON.stringify(v),{status:s,headers:{...cors,"content-type":"application/json; charset=utf-8","cache-control":"no-store"}});
async function auth(req:Request,env:Env):Promise<string|null>{
 const token=req.headers.get("authorization")?.replace(/^Bearer\\s+/i,""); if(!token)return null;
 try {const jwks=createRemoteJWKSet(new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"));
 const {payload}=await jwtVerify(token,jwks,{issuer:"https://securetoken.google.com/"+env.FIREBASE_PROJECT_ID,audience:env.FIREBASE_PROJECT_ID});
 return typeof payload.sub==="string"?payload.sub:null;}catch{return null;}
}
async function digest(code:string,secret:string){const k=await crypto.subtle.importKey("raw",new TextEncoder().encode(secret),{name:"HMAC",hash:"SHA-256"},false,["sign"]);const b=await crypto.subtle.sign("HMAC",k,new TextEncoder().encode(code));return [...new Uint8Array(b)].map(x=>x.toString(16).padStart(2,"0")).join("");}
function code6(){const out:string[]=[];const lim=250;while(out.length<6){for(const b of crypto.getRandomValues(new Uint8Array(16)))if(b<lim&&out.length<6)out.push(String(b%10));}return out.join("");}
export default {async fetch(req:Request,env:Env){
 if(req.method==="OPTIONS")return new Response(null,{headers:cors});if(req.method!=="POST")return reply({error:"not_found"},404);
 const uid=await auth(req,env);if(!uid)return reply({error:"unauthorized"},401);
 let body:any;try{body=await req.json();}catch{return reply({error:"invalid_request"},400);}
 const now=Math.floor(Date.now()/1000),path=new URL(req.url).pathname;
 if(path==="/pair/create"){
  const deviceId=typeof body.deviceId==="string"?body.deviceId:"";if(!/^[A-Za-z0-9_-]{8,128}$/.test(deviceId))return reply({error:"invalid_request"},400);
  const code=code6(),h=await digest(code,env.PAIR_HMAC_KEY);
  await env.PAIR_DB.prepare("DELETE FROM pairing_challenges WHERE child_uid=? AND device_id=?").bind(uid,deviceId).run();
  await env.PAIR_DB.prepare("INSERT INTO pairing_challenges(code_hash,child_uid,device_id,created_at,expires_at) VALUES(?,?,?,?,?)").bind(h,uid,deviceId,now,now+300).run();
  return reply({code,expiresIn:300});
 }
 if(path==="/pair/redeem"){
  const code=typeof body.code==="string"?body.code:"";if(!/^\\d{6}$/.test(code))return reply({error:"pairing_failed"},400);
  const h=await digest(code,env.PAIR_HMAC_KEY);
  const claim=await env.PAIR_DB.prepare("UPDATE pairing_challenges SET consumed_at=? WHERE code_hash=? AND expires_at>? AND consumed_at IS NULL AND attempts<5").bind(now,h,now).run();
  if(!claim.meta.changes){await env.PAIR_DB.prepare("UPDATE pairing_challenges SET attempts=attempts+1 WHERE code_hash=? AND consumed_at IS NULL AND expires_at>?").bind(h,now).run();return reply({error:"pairing_failed"},400);}
  const row=await env.PAIR_DB.prepare("SELECT child_uid,device_id FROM pairing_challenges WHERE code_hash=?").bind(h).first<{child_uid:string,device_id:string}>();
  if(!row||row.child_uid===uid)return reply({error:"pairing_failed"},400);
  const active=await env.PAIR_DB.prepare("SELECT device_id FROM device_links WHERE device_id=? AND revoked_at IS NULL").bind(row.device_id).first();
  if(active)return reply({error:"device_already_linked"},409);
  await env.PAIR_DB.prepare("INSERT INTO device_links(device_id,child_uid,parent_uid,linked_at) VALUES(?,?,?,?)").bind(row.device_id,row.child_uid,uid,now).run();
  return reply({deviceId:row.device_id,childUid:row.child_uid});
 }
 if(path==="/pair/revoke"){
  const id=typeof body.deviceId==="string"?body.deviceId:"";
  const r=await env.PAIR_DB.prepare("UPDATE device_links SET revoked_at=? WHERE device_id=? AND parent_uid=? AND revoked_at IS NULL").bind(now,id,uid).run();
  return r.meta.changes?reply({revoked:true}):reply({error:"not_found"},404);
 }
 return reply({error:"not_found"},404);
}};
