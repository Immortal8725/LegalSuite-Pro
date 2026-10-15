import { chromium } from 'playwright'; import fs from 'fs';
const W=process.env.WEB||'http://127.0.0.1:43291', OUT=process.env.OUT||'./e2e-out/axe', R=[];
const RESULTS=process.env.RESULTS||OUT+'/results-axe-perf.json';
fs.mkdirSync(OUT,{recursive:true});
const axeSrc=fs.readFileSync(process.env.AXE||'node_modules/axe-core/axe.min.js','utf8');
const b=await chromium.launch();
async function audit(p,label,path){
  const t0=Date.now(); let status=0;
  try{ const r=await p.goto(W+path,{waitUntil:'networkidle',timeout:45000}); status=r?.status()||0; }catch(e){ status='timeout'; }
  await p.waitForTimeout(600);
  const wall=Date.now()-t0;
  const perf=await p.evaluate(()=>{const n=performance.getEntriesByType('navigation')[0]||{};const res=performance.getEntriesByType('resource');return {ttfb:Math.round(n.responseStart||0),dcl:Math.round(n.domContentLoadedEventEnd||0),load:Math.round(n.loadEventEnd||0),kb:Math.round((res.reduce((a,r)=>a+(r.transferSize||0),0)+(n.transferSize||0))/1024),req:res.length}});
  await p.addScriptTag({content:axeSrc});
  const v=await p.evaluate(async()=>(await window.axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21a','wcag21aa']}})).violations.map(x=>({id:x.id,impact:x.impact,n:x.nodes.length,t:String(x.nodes[0]?.target)})));
  const ov=await p.evaluate(()=>document.documentElement.scrollWidth-window.innerWidth);
  const vp=p.viewportSize().width;
  const name=(label+path).replace(/[^a-z0-9]+/gi,'_').slice(0,80);
  if(v.length||ov>2) await p.screenshot({path:`${OUT}/${vp}-${name}.png`});
  R.push({label,path,vp,status,wall,...perf,axe:v,overflowPx:ov,url:p.url()});
  console.log(vp,label,path,status,'axe',v.length,v.map(x=>x.id).join(','),'overflow',ov,'dcl',perf.dcl,'kb',perf.kb);
}
async function loginStaff(p){ await p.goto(W+'/login'); const i=p.locator('form input'); await i.nth(0).fill('ndlovu-partners'); await i.nth(1).fill('thabo@ndlovulaw.co.za'); await i.nth(2).fill('password'); await i.nth(2).press('Enter'); await p.waitForURL(u=>!u.pathname.startsWith('/login'),{timeout:60000}); }
const app=['/dashboard','/ai','/audit','/billing','/calendar','/cases','/clients','/conflicts','/contacts','/documents','/esign','/expenses','/fitness','/integrations','/leads','/messages','/modules','/product-billing','/reports','/settings','/tasks','/team','/templates','/time','/trust','/usage','/voice','/website','/change-password'];
const pub=['/login','/register','/portal/login','/legal','/set-password','/firm/ndlovu-partners','/firm/ndlovu-partners/contact','/firm/ndlovu-partners/expertise','/firm/ndlovu-partners/fees','/firm/ndlovu-partners/how-we-help','/firm/ndlovu-partners/insights','/firm/ndlovu-partners/people','/firm/ndlovu-partners/recognition','/firm/ndlovu-partners/subscribe','/this-page-does-not-exist'];
for (const vp of [1366,390]) {
  const c=await b.newContext({viewport:{width:vp,height:vp>1000?800:844}}); const p=await c.newPage();
  for(const x of pub) await audit(p,'public',x);
  const legal=await p.evaluate(()=>[...document.querySelectorAll('a[href^="/legal/"]')].map(a=>a.getAttribute('href')));
  await p.goto(W+'/legal'); const ll=await p.evaluate(()=>[...document.querySelectorAll('a[href^="/legal/"]')].map(a=>a.getAttribute('href')));
  for(const x of [...new Set(ll)].slice(0,6)) await audit(p,'public',x);
  await loginStaff(p);
  for(const x of app) await audit(p,'owner',x);
  const cases=await p.evaluate(async()=>{const t=localStorage.getItem('accessToken')||localStorage.getItem('token');const r=await fetch('/api/v1/cases',{headers:t?{Authorization:'Bearer '+t}:{}});return r.ok?(await r.json()).data.map(c=>c.id):[]});
  if(cases[0]) await audit(p,'owner','/cases/'+cases[0]);
  const c2=await b.newContext({viewport:{width:vp,height:844}}); const pt=await c2.newPage();
  await pt.goto(W+'/portal/login'); const ins=pt.locator('form input'); await ins.nth(0).fill('ndlovu-partners'); await ins.nth(1).fill('nomsa@example.com'); await ins.nth(2).fill('portal123'); await ins.nth(2).press('Enter'); await pt.waitForTimeout(4000);
  await audit(pt,'portal','/portal');
  await c.close(); await c2.close();
}
fs.writeFileSync(RESULTS,JSON.stringify(R,null,1));
const bad=R.filter(r=>r.axe.length||r.overflowPx>2);console.log('AXE+OVERFLOW ISSUES',bad.length,'of',R.length);if(bad.length)process.exitCode=1; await b.close();
