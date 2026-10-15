// E2E for firm document templates: uploads a logo for the demo firm, generates every template
// from a matter through the UI and API, and runs axe (WCAG 2.1 A/AA) on the new pages and dialog.
// Usage: WEB=http://127.0.0.1:43391 AXE=/path/axe.min.js OUT=/tmp/shots node e2e-doc-templates.mjs
import { chromium } from 'playwright';
import fs from 'node:fs';
const W = process.env.WEB || 'http://127.0.0.1:43391';
const OUT = process.env.OUT || '/tmp/doc-templates-e2e';
const axeSrc = fs.readFileSync(process.env.AXE || 'node_modules/axe-core/axe.min.js', 'utf8');
fs.mkdirSync(OUT, { recursive: true });
let pass = 0, fail = 0; const axeTotals = {};
const ok = (c, m) => { if (c) { pass++; console.log('PASS', m); } else { fail++; console.log('FAIL', m); } };
const b = await chromium.launch();
const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
await ctx.addInitScript(() => { try { localStorage.setItem('ls_public_cookie', 'essential'); } catch {} });
const p = await ctx.newPage(); p.setDefaultTimeout(45000);
const errs = []; p.on('pageerror', e => errs.push(e.message));
async function api(method, path, body, raw) {
  return p.evaluate(async ({ method, path, body, raw }) => {
    const h = {}; const t = localStorage.getItem('ls_access'); if (t) h.Authorization = 'Bearer ' + t;
    let b2 = body;
    if (raw) { const fd = new FormData(); fd.append('file', new Blob([Uint8Array.from(atob(raw.b64), c => c.charCodeAt(0))], { type: raw.type }), raw.name); b2 = fd; }
    else if (body !== undefined) { h['Content-Type'] = 'application/json'; b2 = JSON.stringify(body); }
    const r = await fetch(path, { method, headers: h, body: b2 });
    let j = null; try { j = await r.json(); } catch {}
    return { status: r.status, body: j };
  }, { method, path, body, raw });
}
async function axe(name) {
  await p.addScriptTag({ content: axeSrc });
  const v = await p.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } })).violations.map(x => `${x.id}(${x.nodes.length}): ${x.nodes[0]?.target}`));
  axeTotals[name] = v.length; ok(v.length === 0, `axe ${name}: ${v.length} violations ${v.join('; ')}`);
}
// login as the Ndlovu demo owner
await p.goto(W + '/login'); const inp = p.locator('form input');
await inp.nth(0).fill('ndlovu-partners'); await inp.nth(1).fill('thabo@ndlovulaw.co.za'); await inp.nth(2).fill('password'); await inp.nth(2).press('Enter');
await p.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 60000 });
// logo upload
const logo = fs.readFileSync(process.env.LOGO || new URL('./demo-logo.png', import.meta.url));
const up = await api('POST', '/api/v1/firm/branding/logo', undefined, { b64: logo.toString('base64'), type: 'image/png', name: 'logo.png' });
ok(up.status === 200, 'logo upload ' + up.status);
const br = await api('GET', '/api/v1/firm/branding'); ok(br.body?.data?.hasLogo === true, 'branding has logo');
// settings page (letterhead) and templates library
await p.goto(W + '/settings'); await p.waitForLoadState('networkidle'); await p.getByText(/letterhead/i).first().waitFor();
await p.screenshot({ path: OUT + '/01-settings-letterhead.png', fullPage: true }); await axe('settings');
await p.goto(W + '/templates'); await p.waitForLoadState('networkidle'); await p.getByText(/Quick text templates/).first().waitFor();
await p.screenshot({ path: OUT + '/02-templates-library.png', fullPage: true }); await axe('templates');
// matter and dialog
const cases = (await api('GET', '/api/v1/cases')).body.data; const c = cases.find(x => /Khumalo/.test(x.title)) || cases[0];
const lib = (await api('GET', '/api/v1/doc-templates')).body.data; const sys = lib.filter(t => t.system);
ok(sys.length === 9, 'system templates listed: ' + sys.length);
await p.goto(W + '/cases/' + c.id); await p.waitForLoadState('networkidle');
await p.getByRole('button', { name: 'Generate document' }).click();
await p.locator('#gen-template').waitFor();
await axe('generate-dialog-empty');
let first = true;
for (const t of sys) {
  await p.locator('#gen-template').selectOption(t.id);
  await p.getByRole('button', { name: 'Generate and save to matter' }).waitFor();
  await p.waitForTimeout(600);
  if (first) { await p.screenshot({ path: OUT + '/03-generate-dialog.png' }); await axe('generate-dialog-form'); }
  const resp = p.waitForResponse(r => r.url().includes('/generate') && r.request().method() === 'POST', { timeout: 90000 });
  await p.getByRole('button', { name: 'Generate and save to matter' }).click();
  const r = await resp; const st = await p.getByRole('status').first().textContent().catch(() => '');
  ok(r.status() === 200 && /Saved to the matter/.test(st || ''), `generate ${t.id} -> ${r.status()}`);
  if (first) { await p.screenshot({ path: OUT + '/04-generated.png' }); await axe('generate-dialog-result'); first = false; }
}
// preview via API for each (from Node, to keep the browser light) and check PDF bytes
const tok = await p.evaluate(() => localStorage.getItem('ls_access'));
for (const t of sys) {
  const r = await fetch(`${W}/api/v1/doc-templates/${t.id}/preview`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + tok }, body: JSON.stringify({ caseId: c.id, values: {} }) });
  const a = Buffer.from(await r.arrayBuffer());
  ok(r.status === 200 && a.subarray(0, 5).toString() === '%PDF-' && a.length > 1000, `preview ${t.id} ${a.length} bytes`);
}
const files = (await api('GET', `/api/v1/documents?caseId=${c.id}`)).body?.data || [];
const gen = files.filter(f => f.category === 'generated'); ok(gen.length >= sys.length, `matter has ${gen.length} generated files`);
ok(errs.length === 0, 'no page errors ' + errs.join('; '));
console.log(JSON.stringify({ pass, fail, axe: axeTotals }));
await b.close(); process.exit(fail ? 1 : 0);
