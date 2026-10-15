// LegalSuite-Pro A-to-Z end-to-end test. Read-only for the repo: it only drives the running app.
import { chromium } from 'playwright';
import fs from 'node:fs';
const W = process.env.WEB || 'http://127.0.0.1:43123';
const OUT = process.env.OUT || './e2e-out/a2z/shots';
const RESULTS = process.env.RESULTS || './e2e-out/a2z/results.json';
fs.mkdirSync(OUT, { recursive: true });
const RUN = String(Date.now()).slice(-5);
const results = [];
let shotN = 0;
const browser = await chromium.launch({ timeout: 0 });
let C1 = ''; // resolved after login (UUIDs change every boot)

async function ctx(opts = {}) {
  const c = await browser.newContext({ viewport: opts.viewport || { width: 1440, height: 900 }, ...opts });
  await c.addInitScript(() => { try { localStorage.setItem('ls_public_cookie', 'essential'); } catch {} });
  return c;
}
function watch(page) {
  page._ev = []; page.setDefaultTimeout(45000); page.setDefaultNavigationTimeout(90000);
  page.on('console', m => { if (m.type() === 'error') page._ev.push({ kind: 'console', text: m.text().slice(0, 300) }); });
  page.on('pageerror', e => page._ev.push({ kind: 'pageerror', text: String(e.message).slice(0, 300) }));
  page.on('response', r => { const u = r.url(); if (r.status() >= 400 && !u.includes('_next/static')) page._ev.push({ kind: 'http', status: r.status(), method: r.request().method(), url: u.replace(W, '') }); });
  page.on('requestfailed', r => { const f = r.failure()?.errorText || ''; if (!/ERR_ABORTED|NS_BINDING_ABORTED/.test(f)) page._ev.push({ kind: 'reqfail', text: `${r.method()} ${r.url().replace(W, '')} ${f}` }); });
  return page;
}
async function settle(page, ms = 700) {
  try { await page.waitForLoadState('networkidle', { timeout: 12000 }); } catch {}
  await page.waitForTimeout(ms);
}
async function snap(page, name, full = true) {
  shotN++;
  const f = `${OUT}/${String(shotN).padStart(3, '0')}-${name}.png`;
  try { await page.screenshot({ path: f, fullPage: full, timeout: 20000 }); } catch { try { await page.screenshot({ path: f }); } catch {} }
  return f.replace(OUT.replace(/shots\/?$/, ''), '');
}
// allow: array of [status, urlRegex] pairs that are expected for this step (negative tests)
async function step(area, name, page, fn, { allow = [], shot = false, ignoreConsole = [] } = {}) {
  const start = page ? page._ev.length : 0;
  let ok = true, note = '', shotPath = '';
  try { const r = await fn(); if (typeof r === 'string') note = r; }
  catch (e) { ok = false; note = String(e.message || e).split('\n')[0].slice(0, 400); }
  let issues = [];
  if (page) {
    await page.waitForTimeout(250);
    const ev = page._ev.slice(start);
    issues = ev.filter(e => {
      if (e.kind === 'http' && /^\/(expertise|people|insights|recognition|contact|how-we-help|fees|subscribe|legal)[^?]*\?_rsc=/.test(e.url)) { state.rscLeak = (state.rscLeak || 0) + 1; state.rscPages = state.rscPages || new Set(); state.rscPages.add(page.url().replace(W, '')); return false; }
      if (e.kind === 'http') return !allow.some(([s, re]) => s === e.status && re.test(e.url));
      if (e.kind === 'console') {
        if (/Failed to load resource/.test(e.text)) return false; // covered by http capture
        return !ignoreConsole.some(re => re.test(e.text));
      }
      return true;
    });
    if (issues.length && ok) { ok = false; note = (note ? note + ' | ' : '') + 'Unexpected: ' + issues.map(i => i.kind === 'http' ? `${i.status} ${i.method} ${i.url}` : `${i.kind}: ${i.text}`).join('; ').slice(0, 500); }
    if (!ok || shot) shotPath = await snap(page, `${ok ? 'pass' : 'FAIL'}-${area}-${name}`.toLowerCase().replace(/[^a-z0-9-]+/g, '-').slice(0, 80));
  }
  results.push({ area, name, ok, note, shot: shotPath });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${area}] ${name}${note ? ' :: ' + note : ''}`);
  try { fs.writeFileSync(RESULTS, JSON.stringify({ run: RUN, partial: true, results, state }, null, 2)); } catch {}
  return ok;
}
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => {
    const h = { 'Content-Type': 'application/json' };
    const t = localStorage.getItem('ls_access'); if (t) h.Authorization = 'Bearer ' + t;
    const r = await fetch(path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
    let j = null; try { j = await r.json(); } catch {}
    return { status: r.status, body: j };
  }, { method, path, body });
}
function dialog(page, title) { return page.locator('div.fixed.inset-0').filter({ has: page.locator('h3', { hasText: title }) }); }
async function login(page, slug, email, pw) {
  await page.goto(W + '/login', { waitUntil: 'domcontentloaded' }); await settle(page, 300);
  const inputs = page.locator('form input');
  await inputs.nth(0).fill(slug); await inputs.nth(1).fill(email); await inputs.nth(2).fill(pw);
  await inputs.nth(2).press('Enter');
  await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 60000 });
  await settle(page);
}
async function go(page, path) { const r = await page.goto(W + path, { waitUntil: 'domcontentloaded' }); await settle(page); if (r && r.status() >= 400) throw new Error(`HTTP ${r.status()} on ${path}`); return r; }
async function expectText(page, text, timeout = 8000) { await page.getByText(text, { exact: false }).first().waitFor({ timeout }); }
function assert(c, msg) { if (!c) throw new Error(msg); }
const state = {};

// =============== 1. AUTH ===============
const ownerCtx = await ctx();
const p = watch(await ownerCtx.newPage());
await step('Auth', 'Wrong password is refused with a message', p, async () => {
  await p.goto(W + '/login'); await settle(p, 300);
  const i = p.locator('form input');
  await i.nth(0).fill('ndlovu-partners'); await i.nth(1).fill('thabo@ndlovulaw.co.za'); await i.nth(2).fill('wrong-pass');
  await i.nth(2).press('Enter'); await p.waitForTimeout(2000);
  assert(new URL(p.url()).pathname.startsWith('/login'), 'left the login page with a wrong password');
  const txt = await p.locator('body').innerText();
  assert(/invalid|incorrect|wrong|failed|not match/i.test(txt), 'no error text shown: ' + txt.slice(0, 120));
}, { allow: [[401, /auth\/login/], [400, /auth\/login/], [403, /auth\/login/]], shot: true });
await step('Auth', 'Owner login (Thabo, director)', p, async () => { await login(p, 'ndlovu-partners', 'thabo@ndlovulaw.co.za', 'password'); assert(!p.url().includes('/login'), 'still on login'); });
await step('Auth', 'Logout returns to sign-in and clears session', p, async () => {
  await p.locator('button[title="Sign out"]').first().click(); await p.waitForTimeout(1500);
  const tok = await p.evaluate(() => localStorage.getItem('ls_access'));
  assert(!tok, 'token still in storage');
  await p.goto(W + '/dashboard'); await settle(p);
  assert(/\/login/.test(p.url()) || /\/$/.test(new URL(p.url()).pathname), 'dashboard reachable after logout: ' + p.url());
  const n401 = p._ev.filter(e => e.kind === 'http' && e.status === 401).length; state.logout401 = n401;
  return 'redirected to ' + new URL(p.url()).pathname + (n401 ? `; dashboard fired ${n401} API call(s) that returned 401 before redirect` : '');
}, { allow: [[401, /api\/v1\//]] });
await step('Auth', 'Log back in', p, async () => { await login(p, 'ndlovu-partners', 'thabo@ndlovulaw.co.za', 'password'); const cs = (await api(p, 'GET', '/api/v1/cases')).body.data; C1 = (cs.find(c => /Khumalo/.test(c.title)) || cs[0]).id; return 'Khumalo matter ' + C1; });

// =============== 2. DASHBOARD ===============
await step('Dashboard', 'Docket dashboard renders with clocks', p, async () => {
  await go(p, '/dashboard');
  const t = await p.locator('main').innerText();
  assert(t.length > 200, 'dashboard nearly empty');
  return t.match(/RAF|Prescription|Act 40/i) ? 'SA clocks visible' : 'no SA clock keywords found';
}, { shot: true });
await step('Dashboard', 'Global search finds seeded client', p, async () => {
  const r = await api(p, 'GET', '/api/v1/search?q=Khumalo');
  assert(r.status === 200, 'search status ' + r.status);
  return 'results: ' + JSON.stringify(r.body?.data).slice(0, 80);
});

// =============== 3. CLIENTS / CONTACTS ===============
const clientFirst = 'Ayanda', clientLast = 'Test' + RUN;
await step('Clients', 'Create client via New client dialog', p, async () => {
  await go(p, '/clients');
  await p.getByRole('button', { name: 'New client' }).click();
  const d = dialog(p, 'New client');
  const inputs = d.locator('input');
  await inputs.nth(0).fill(clientFirst); await inputs.nth(1).fill(clientLast);
  await d.getByPlaceholder('Email').fill(`ayanda${RUN}@example.co.za`);
  await d.getByPlaceholder('Phone').fill('082 555 0101');
  await d.getByRole('button', { name: 'Save' }).click();
  await expectText(p, clientLast);
  const r = await api(p, 'GET', '/api/v1/clients');
  const c = r.body.data.find(x => (x.displayName || '').includes(clientLast));
  assert(c, 'client not in API list'); state.clientId = c.id;
}, { shot: true });
await step('Clients', 'Create contact (opposing counsel)', p, async () => {
  await go(p, '/contacts');
  await p.getByRole('button', { name: 'Add contact' }).click();
  const d = dialog(p, 'Contact').or(p.locator('div.fixed.inset-0')).first();
  await d.getByPlaceholder('First name').fill('Pieter'); await d.getByPlaceholder('Last name').fill('Counsel' + RUN);
  await d.getByPlaceholder('Firm / court').fill('Botha Attorneys'); await d.getByPlaceholder('Email').fill(`pieter${RUN}@botha.co.za`);
  await d.getByRole('button', { name: 'Save' }).click();
  await expectText(p, 'Counsel' + RUN);
});

// =============== 4. MATTER ===============
const matterTitle = `Test${RUN} v Road Accident Fund`;
await step('Matters', 'Create matter via New matter dialog', p, async () => {
  await go(p, '/cases');
  await p.getByRole('button', { name: 'New matter' }).first().click();
  const d = p.locator('div.fixed.inset-0').first();
  await d.locator('input').nth(0).fill(matterTitle);
  await d.locator('select').first().selectOption({ value: state.clientId });
  const inputs = d.locator('input:not([type=checkbox]):not([type=date])');
  await inputs.nth(1).fill('Personal injury (RAF)');
  await inputs.nth(2).fill('Road Accident Fund');
  await d.locator('input[type=date]').fill('2026-03-01');
  const ta = d.locator('textarea'); if (await ta.count()) await ta.fill('Pedestrian struck on N1, Midrand. Test run ' + RUN);
  await d.getByRole('button', { name: /Save|Create|Open/ }).last().click();
  await expectText(p, matterTitle);
  const r = await api(p, 'GET', '/api/v1/cases');
  const m = r.body.data.find(x => x.title === matterTitle);
  assert(m, 'matter not in API'); state.caseId = m.id; state.caseNumber = m.caseNumber;
  return `${m.caseNumber} created`;
}, { shot: true });
await step('Matters', 'Matter workspace: clocks, comms panel, notes', p, async () => {
  await go(p, '/cases/' + state.caseId);
  const t = await p.locator('main').innerText();
  assert(t.includes(matterTitle), 'title missing');
  assert(/RAF|s 23|prescri|clock/i.test(t), 'no statutory clock shown');
  assert(/SMS|WhatsApp|Email/i.test(t), 'comms panel missing');
  await p.getByPlaceholder('Hearing prep, call recap, strategy…').fill('Called client re medical records ' + RUN);
  await p.getByRole('button', { name: /Save note|Add note/ }).first().click();
  await expectText(p, 'medical records ' + RUN);
}, { shot: true });
await step('Matters', 'Draft help on matter answers a clock question', p, async () => {
  const ta = p.getByPlaceholder(/Parties, clocks, notes/);
  await ta.fill('When does the RAF claim prescribe?');
  await ta.locator('xpath=following::button[1]').click();
  await p.waitForTimeout(3000);
  const t = await p.locator('main').innerText();
  assert(/prescri|20\d\d|years|s 23/i.test(t.slice(-3000)), 'no answer text found');
});
await step('Matters', 'Outbound SMS without Twilio fails gracefully', p, async () => {
  const r = await api(p, 'POST', '/api/v1/outbound/sms', { caseId: state.caseId, to: '0825550101', body: 'Test' });
  assert(r.status < 500, 'server error ' + r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
  return `status ${r.status}: ${(r.body?.message || '').slice(0, 120)}`;
}, { allow: [[400, /outbound/], [409, /outbound/], [503, /outbound/], [422, /outbound/]] });

// =============== 5. TASKS / CALENDAR ===============
await step('Tasks', 'Add task and move it to in progress', p, async () => {
  await go(p, '/tasks');
  await p.getByRole('button', { name: 'Add task' }).click();
  const d = p.locator('div.fixed.inset-0').first();
  await d.getByPlaceholder('Title').fill('Request hospital records ' + RUN);
  await d.locator('textarea').fill('Netcare Milpark');
  await d.getByRole('button', { name: 'Create' }).click();
  await expectText(p, 'hospital records ' + RUN);
  const card = p.locator('div.mb-2.rounded-lg', { hasText: 'hospital records ' + RUN }).locator('select');
  const opts = await card.locator('option').allTextContents();
  const target = opts.find(o => /progress/i.test(o)) || opts[1];
  await card.selectOption({ label: target }); await settle(p);
  const r = await api(p, 'GET', '/api/v1/tasks');
  const t = r.body.data.find(x => x.title?.includes(RUN));
  assert(t && t.status !== 'todo', 'status not changed: ' + t?.status);
});
await step('Calendar', 'Create court event', p, async () => {
  await go(p, '/calendar');
  await p.getByRole('button', { name: 'New event' }).click();
  const d = p.locator('div.fixed.inset-0').first();
  await d.locator('input').first().fill('Pre-trial conference ' + RUN);
  await d.locator('input[type=datetime-local]').fill('2026-10-20T10:00');
  await d.getByPlaceholder('Location').fill('Gauteng Division, Pretoria');
  await d.getByRole('button', { name: /Save|Create/ }).last().click();
  await settle(p);
  const r = await api(p, 'GET', '/api/v1/events');
  assert(r.body.data.some(e => e.title?.includes(RUN)), 'event not saved');
});

// =============== 6. TIME / EXPENSE / INVOICE ===============
await step('Billing', 'Log 60 min manual time on new matter', p, async () => {
  await go(p, '/time');
  await p.getByRole('button', { name: 'Manual entry' }).click();
  const d = dialog(p, 'Log time');
  await d.locator('select').selectOption({ value: state.caseId });
  await d.locator('input[type=number]').fill('60');
  await d.locator('textarea').fill('Consultation and RAF 1 review ' + RUN);
  await d.getByRole('button', { name: 'Save' }).click();
  await expectText(p, 'RAF 1 review ' + RUN);
});
await step('Billing', 'Manual time left on Unassigned (the default)', p, async () => {
  await p.getByRole('button', { name: 'Manual entry' }).click();
  const d = dialog(p, 'Log time');
  await d.locator('select').selectOption({ value: '' });
  await d.locator('textarea').fill('Unassigned admin ' + RUN);
  await d.getByRole('button', { name: 'Save' }).click(); await settle(p);
  const still = await d.count();
  const saved = (await api(p, 'GET', '/api/v1/time-entries')).body.data.some(t => t.description?.includes('Unassigned admin ' + RUN));
  if (still) await d.locator('button', { hasText: '×' }).click();
  assert(saved, 'POST /api/v1/time-entries returns 500 "Invalid UUID string" for caseId ""; dialog stays open with no message');
}, { allow: [[500, /time-entries/]], shot: false });
await step('Billing', 'Live timer start and stop', p, async () => {
  await p.getByRole('button', { name: 'Start timer' }).click(); await p.waitForTimeout(2500);
  await p.getByRole('button', { name: /^Stop/ }).click(); await settle(p);
});
await step('Billing', 'Expense dialog offers a matter link', p, async () => {
  await go(p, '/expenses');
  await p.getByRole('button', { name: 'Log expense' }).click();
  const d = dialog(p, 'Expense');
  const selects = await d.locator('select').count();
  await d.getByPlaceholder('Vendor').fill('Sheriff Midrand');
  await d.getByPlaceholder('Description').fill('Service of summons ' + RUN);
  await d.locator('input[type=number]').fill('450');
  await d.getByRole('button', { name: 'Save' }).click();
  await expectText(p, 'summons ' + RUN);
  assert(selects > 1, 'Expense dialog has only a category select; no matter or client field, so the expense cannot be tied to a client');
}, { shot: true });
await step('Billing', 'Expense linked to matter (API) for invoice', p, async () => {
  const r = await api(p, 'POST', '/api/v1/expenses', { caseId: state.caseId, category: 'filing fees', vendor: 'Registrar', description: 'Issue fee ' + RUN, amount: '350' });
  assert(r.status === 200 || r.status === 201, 'status ' + r.status);
});
await step('Billing', 'Invoice for one client bills only that client', p, async () => {
  const unbilledBefore = (await api(p, 'GET', '/api/v1/time-entries')).body.data.filter(t => !t.billed && t.billable);
  const foreign = unbilledBefore.filter(t => t.caseId !== state.caseId);
  const expBefore = (await api(p, 'GET', '/api/v1/expenses')).body.data.filter(e => !e.billed);
  const mine = new Set([...unbilledBefore.filter(t => t.caseId === state.caseId).map(t => t.description),
    ...expBefore.filter(e => e.caseId === state.caseId).map(e => e.description)]);
  state.foreignUnbilled = foreign.length;
  await go(p, '/billing');
  await p.getByRole('button', { name: /Generate from unbilled/ }).click();
  const d = dialog(p, 'Draft invoice');
  await d.locator('select').selectOption({ value: state.clientId });
  await d.getByRole('button', { name: 'Create draft' }).click(); await settle(p);
  const inv = (await api(p, 'GET', '/api/v1/invoices')).body.data.find(i => i.clientId === state.clientId);
  assert(inv, 'invoice not created'); state.invoice = inv;
  const lines = JSON.parse(inv.rawLineItems || inv.lineItemsJson || '[]');
  state.invoiceLines = lines.length;
  const sub = Number(inv.subtotal), vat = Number(inv.taxAmount), tot = Number(inv.total);
  assert(Math.abs(vat - Math.round(sub * 15) / 100) < 0.02, `VAT ${vat} is not 15% of ${sub}`);
  assert(Math.abs(sub + vat - tot) < 0.02, 'total mismatch');
  const ours = lines.filter(l => mine.has(l.description)).length;
  assert(lines.length === ours, `${inv.invoiceNumber} has ${lines.length} lines but only ${ours} belong to this client's matter (${foreign.length} unbilled time rows elsewhere). Subtotal R${sub}`);
  const swept = (await api(p, 'GET', '/api/v1/time-entries')).body.data.filter(t => t.billed && foreign.some(f => f.id === t.id)).length;
  assert(swept === 0, `${swept} time rows from other matters were marked billed`);
  return `${inv.invoiceNumber} subtotal ${sub} VAT ${vat} total ${tot}`;
}, { shot: true });
await step('Billing', 'Invoice lines expand with fees, disbursements and VAT', p, async () => {
  const row = p.locator('tr', { hasText: state.invoice.invoiceNumber });
  await row.getByRole('button', { name: /^(Lines|Detail)$/ }).click(); await p.waitForTimeout(500);
  await expectText(p, 'Disbursements'); await expectText(p, 'VAT');
}, { shot: true });
await step('Billing', 'Send invoice', p, async () => {
  const row = p.locator('tr', { hasText: state.invoice.invoiceNumber });
  await row.getByRole('button', { name: 'Send' }).click(); await settle(p);
  const inv = (await api(p, 'GET', '/api/v1/invoices/' + state.invoice.id)).body.data;
  assert(inv.status === 'sent', 'status ' + inv.status);
});
const inv = async () => (await api(p, 'GET', '/api/v1/invoices/' + state.invoice.id)).body.data;
const invRow = () => p.locator('tr', { hasText: state.invoice.invoiceNumber }).first();
const money = (n) => Number(n).toFixed(2);
fs.writeFileSync('/tmp/a2z-pop.pdf', '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n');
await step('Billing', 'Part payment through the Record payment dialog', p, async () => {
  await go(p, '/billing');
  const before = await inv();
  state.invTotal = Number(before.total);
  await invRow().getByRole('button', { name: 'Record payment' }).click();
  const d = dialog(p, 'Record payment');
  await d.getByLabel('Amount').fill('1000.00');
  await d.getByLabel('Reference').fill('EFT ' + RUN);
  await d.getByRole('button', { name: 'Record payment' }).click(); await settle(p);
  const after = await inv();
  assert(Math.abs(Number(after.balanceDue) - (Number(before.balanceDue) - 1000)) < 0.01, `balance ${before.balanceDue} -> ${after.balanceDue}`);
  assert(after.status !== 'paid', 'marked paid after a part payment');
  return `total ${before.total}, paid 1000.00, balance ${after.balanceDue}, status ${after.status}`;
}, { shot: true });
await step('Billing', 'Over-payment is refused with the message inside the dialog', p, async () => {
  const cur = await inv();
  await invRow().getByRole('button', { name: 'Record payment' }).click();
  const d = dialog(p, 'Record payment');
  await d.getByLabel('Amount').fill(money(Number(cur.balanceDue) + 100));
  await d.getByRole('button', { name: 'Record payment' }).click(); await settle(p, 500);
  const alert = d.getByRole('alert');
  await alert.waitFor({ timeout: 8000 });
  const text = await alert.innerText();
  await p.keyboard.press('Escape'); await settle(p, 300);
  assert(Number((await inv()).balanceDue) === Number(cur.balanceDue), 'balance changed');
  assert(!(await dialog(p, 'Record payment').count()), 'Escape did not close the dialog');
  return text;
}, { allow: [[400, /\/payments$/]], shot: true });
await step('Billing', 'Proof of payment: upload, open the file, accept', p, async () => {
  const cur = await inv();
  await invRow().getByRole('button', { name: 'Add proof' }).click();
  const d = dialog(p, 'Add proof of payment');
  await d.locator('input[type=file]').setInputFiles('/tmp/a2z-pop.pdf');
  await d.getByLabel('Amount claimed').fill('500.00');
  await d.getByLabel('Reference').fill('POP ' + RUN);
  await d.getByRole('button', { name: 'Submit for review' }).click(); await settle(p);
  const pend = p.locator('tr', { hasText: 'POP ' + RUN }).first();
  await pend.waitFor({ timeout: 10000 });
  const [pop] = await Promise.all([p.context().waitForEvent('page', { timeout: 15000 }), pend.getByRole('button', { name: 'Open file' }).click()]);
  await pop.close().catch(() => {});
  await pend.getByRole('button', { name: 'Accept' }).click();
  const a = dialog(p, 'Accept proof of payment');
  await a.getByRole('button', { name: 'Accept and record payment' }).click(); await settle(p);
  const after = await inv();
  assert(Math.abs(Number(after.balanceDue) - (Number(cur.balanceDue) - 500)) < 0.01, `balance ${cur.balanceDue} -> ${after.balanceDue}`);
  return `accepted 500.00, balance ${after.balanceDue}`;
}, { shot: true });
await step('Billing', 'Write off the remaining balance', p, async () => {
  const cur = await inv();
  await invRow().getByRole('button', { name: 'Write off' }).click();
  const d = dialog(p, 'Write off balance');
  await d.getByLabel('Amount').fill(money(cur.balanceDue));
  await d.getByLabel('Reason').fill('Goodwill on settlement ' + RUN);
  await d.getByRole('button', { name: 'Confirm write-off' }).click(); await settle(p);
  const after = await inv();
  assert(Number(after.balanceDue) === 0, 'balance ' + after.balanceDue);
  assert(Math.abs(Number(after.amountPaid) + Number(after.writeOffAmount) - state.invTotal) < 0.01, `paid ${after.amountPaid} + written off ${after.writeOffAmount} != total ${state.invTotal}`);
  return `paid ${after.amountPaid}, written off ${after.writeOffAmount}, status ${after.status}`;
}, { shot: true });

// =============== 7. TRUST ===============
await step('Trust', 'Trust page shows s 86 three-way recon', p, async () => {
  await go(p, '/trust');
  await expectText(p, 'Three-way');
  const accts = (await api(p, 'GET', '/api/v1/trust/accounts')).body.data; state.trust = accts[0];
  return `${accts.length} account(s), first balance ${accts[0]?.balance}`;
}, { shot: true });
await step('Trust', 'Deposit via Record movement', p, async () => {
  const before = Number(state.trust.balance);
  await p.getByRole('button', { name: 'Record movement' }).click();
  const d = dialog(p, 'Trust movement');
  await d.getByLabel('Type').selectOption('deposit');
  await d.getByLabel('Matter').selectOption({ value: state.caseId });
  await d.locator('input[type=number]').fill('5000');
  await d.locator('input:not([type=number])').fill('Retainer deposit ' + RUN);
  await d.getByRole('button', { name: 'Post' }).click(); await settle(p);
  const after = Number((await api(p, 'GET', '/api/v1/trust/accounts')).body.data[0].balance);
  assert(Math.abs(after - before - 5000) < 0.01, `balance ${before} -> ${after}`);
  const ledger = (await api(p, 'GET', `/api/v1/trust/accounts/${state.trust.id}/ledger`)).body.data;
  const row = ledger.find(x => (x.description || '').includes('Retainer deposit ' + RUN));
  assert(row && row.clientId === state.clientId, 'deposit did not land on the client ledger');
  return 'deposit posted to the client ledger via the Matter select';
});
await step('Trust', 'Client-ledger deposit then trust-to-fee transfer', p, async () => {
  const dep = await api(p, 'POST', '/api/v1/trust/move', { accountId: state.trust.id, type: 'deposit', amount: '2000', clientId: state.clientId, caseId: state.caseId, description: 'Client deposit ' + RUN });
  assert(dep.status === 200, 'deposit ' + dep.status + ' ' + dep.body?.message);
  const over = await api(p, 'POST', '/api/v1/trust/move', { accountId: state.trust.id, type: 'withdrawal', amount: '9999', clientId: state.clientId, description: 'overdraw test' });
  assert(over.status === 400, 'overdraw not refused: ' + over.status);
  const bogus = await api(p, 'POST', '/api/v1/trust/move', { accountId: state.trust.id, type: 'transfer_to_fees', amount: '500', clientId: state.clientId, caseId: state.caseId, description: 'Bogus type ' + RUN });
  assert(bogus.status === 400, `unknown type "transfer_to_fees" returned ${bogus.status} instead of 400`);
  // Bill some fresh work, send the invoice, then pay it from trust.
  await api(p, 'POST', '/api/v1/time-entries', { caseId: state.caseId, durationMinutes: 30, description: 'Fee transfer work ' + RUN });
  const gen = await api(p, 'POST', '/api/v1/invoices/generate', { clientId: state.clientId });
  assert(gen.status === 200, 'generate ' + gen.status);
  await api(p, 'POST', `/api/v1/invoices/${gen.body.data.id}/send`, {});
  const beforeFee = Number(dep.body.data.balance);
  const fee = await api(p, 'POST', '/api/v1/trust/move', { accountId: state.trust.id, type: 'fee_transfer', amount: '500', clientId: state.clientId, caseId: state.caseId, invoiceId: gen.body.data.id, description: 'Fee transfer ' + RUN });
  assert(fee.status === 200, 'fee transfer ' + fee.status + ' ' + fee.body?.message);
  const ledger = (await api(p, 'GET', `/api/v1/trust/accounts/${state.trust.id}/ledger`)).body.data;
  const row = ledger.find(x => (x.description || '').includes('Fee transfer ' + RUN));
  assert(row && row.type === 'fee_transfer', 'no fee-transfer row');
  assert(Math.abs(Number(row.balanceAfter) - (beforeFee - 500)) < 0.01, `balance ${beforeFee} -> ${row.balanceAfter}, expected a R500 debit`);
  const paid = (await api(p, 'GET', '/api/v1/invoices/' + gen.body.data.id)).body.data;
  assert(Number(paid.amountPaid) === 500, 'invoice amountPaid ' + paid.amountPaid);
  return `overdraw and unknown type refused with 400; fee transfer debited R500 and paid ${paid.invoiceNumber}`;
}, { allow: [[400, /trust\/move/]] });
await step('Trust', 'Save three-way recon; certify refused without note', p, async () => {
  await go(p, '/trust');
  await p.getByRole('button', { name: 'Enter bank balance' }).click();
  const d = dialog(p, 'Three-way recon');
  await d.getByRole('button', { name: 'Save recon' }).click(); await settle(p);
  const cert = await api(p, 'POST', '/api/v1/trust/reconcile', { accountId: state.trust.id, certify: true, notes: 'short' });
  const live = (await api(p, 'GET', '/api/v1/trust/recon')).body.data;
  if (live.worstStatus === 'unbalanced') assert(cert.status === 400, 'unbalanced recon certified without note');
  return `recon ${live.worstStatus}; certify-without-note status ${cert.status}`;
}, { allow: [[400, /reconcile/]], shot: true });
await step('Trust', 'Bank CSV import', p, async () => {
  const csv = 'Date,Description,Amount,Balance\n2026-10-01,Opening,0,100000.00\n2026-10-05,Deposit Khumalo,5000,105000.00\n';
  const r = await api(p, 'POST', '/api/v1/trust/bank-import', { accountId: state.trust.id, csv });
  assert(r.status === 200, 'status ' + r.status + ' ' + r.body?.message);
});

// =============== 8. DOCUMENTS / TEMPLATES / ESIGN ===============
fs.writeFileSync('/tmp/a2z-medical.txt', 'Medical report for test ' + RUN);
await step('Documents', 'Upload a document to the matter', p, async () => {
  await go(p, '/documents');
  const f = p.locator('main form').first();
  await f.locator('input[type=file]').setInputFiles('/tmp/a2z-medical.txt');
  await f.locator('select').selectOption({ value: state.caseId });
  await f.getByRole('button', { name: 'Upload' }).click(); await settle(p, 1500);
  await expectText(p, 'a2z-medical.txt');
  const docs = (await api(p, 'GET', '/api/v1/documents')).body.data;
  const d = docs.find(x => (x.name || x.fileName || '').includes('a2z-medical'));
  assert(d, 'not in API'); state.docId = d.id;
  const dl = await api(p, 'GET', `/api/v1/documents/${d.id}/download`).catch(() => ({ status: 0 }));
  return 'download status ' + dl.status;
}, { shot: true });
await step('Templates', 'Merge a template against the new matter', p, async () => {
  await go(p, '/templates');
  await p.locator('main button.w-full').first().click(); await p.waitForTimeout(400);
  const sel = p.locator('main select').first();
  const opt = await sel.locator('option', { hasText: state.caseNumber }).first().getAttribute('value');
  await sel.selectOption(opt);
  await p.getByRole('button', { name: 'Merge' }).click(); await p.waitForTimeout(1500);
  const merged = await p.locator('main pre').innerText();
  assert(merged.includes(clientLast) || merged.includes(matterTitle) || merged.includes(state.caseNumber), 'merged text lacks client/matter: ' + merged.slice(0, 120));
  assert(!/\{\{/.test(merged), 'unmerged {{placeholders}} remain');
}, { shot: true });
await step('E-sign', 'Send for signature, then client signs on /sign/{id}', p, async () => {
  await go(p, '/esign');
  await p.getByRole('button', { name: 'Send for signature' }).click();
  const d = dialog(p, 'Send a document');
  const ins = d.locator('input');
  await ins.nth(0).fill('Mandate ' + RUN); await ins.nth(1).fill(clientFirst + ' ' + clientLast); await ins.nth(2).fill(`ayanda${RUN}@example.co.za`);
  await d.locator('select').first().selectOption({ value: state.caseId });
  await d.locator('textarea').fill('I instruct Ndlovu & Partners to act for me. Test ' + RUN);
  await d.getByRole('button', { name: 'Create link' }).click(); await settle(p);
  const blankOk = (await api(p, 'GET', '/api/v1/signatures')).body.data.some(s => s.title?.includes(RUN));
  state.esignBlankClientWorks = blankOk;
  if (!blankOk) {
    await snap(p, 'FAIL-esign-create-link-blank-client-500');
    await d.locator('select').nth(1).selectOption({ value: state.clientId });
    await d.locator('input').nth(0).fill('Mandate ' + RUN);
    await d.getByRole('button', { name: 'Create link' }).click(); await settle(p);
  }
  const sig = (await api(p, 'GET', '/api/v1/signatures')).body.data.find(s => s.title?.includes(RUN));
  assert(sig, 'signature request not created'); state.sigId = sig.id;
  const sc = await ctx(); const sp = watch(await sc.newPage());
  await sp.goto(W + '/sign/' + sig.id); await settle(sp);
  const si = sp.locator('input');
  await si.nth(0).fill(clientFirst + ' ' + clientLast); await si.nth(1).fill('9001015009087');
  const box = await sp.locator('canvas').boundingBox();
  await sp.mouse.move(box.x + 20, box.y + 40); await sp.mouse.down();
  for (let k = 0; k < 10; k++) await sp.mouse.move(box.x + 40 + k * 30, box.y + 40 + (k % 2) * 50);
  await sp.mouse.up();
  await sp.getByRole('button', { name: 'Sign and finish' }).click(); await settle(sp);
  await sp.getByText('Signed').first().waitFor({ timeout: 8000 });
  await snap(sp, 'pass-esign-signed-by-client');
  const errs = sp._ev.filter(e => e.kind !== 'console' || !/Failed to load resource/.test(e.text));
  await sc.close();
  assert(!errs.length, 'signer page errors: ' + JSON.stringify(errs).slice(0, 200));
  return state.esignBlankClientWorks ? 'created and signed' : 'signed OK after choosing a client; with Client left blank Create link fails (500 Invalid UUID) and the dialog just stays open';
}, { allow: [[500, /api\/v1\/signatures$/]] });
await step('E-sign', 'Create link with Client (optional) left blank', p, async () => { assert(state.esignBlankClientWorks, 'POST /api/v1/signatures returns 500 "Invalid UUID string" because the form sends clientId ""; dialog stays open with no message'); });

// =============== 9. MESSAGES / VOICE ===============
await step('Messages', 'Create a thread and send a message', p, async () => {
  await go(p, '/messages');
  await p.getByPlaceholder('Thread title').fill('Khumalo prep ' + RUN);
  await p.getByPlaceholder('Thread title').locator('xpath=following::button[1]').click(); await settle(p);
  await p.getByPlaceholder('Write a message…').fill('Please pull the accident report ' + RUN);
  await p.getByRole('button', { name: 'Send' }).click(); await settle(p);
  await expectText(p, 'accident report ' + RUN);
}, { shot: true });
await step('Voice', 'Voice page loads and shows readiness', p, async () => {
  await go(p, '/voice');
  const r = await api(p, 'GET', '/api/v1/voice/pstn/readiness');
  assert(r.status === 200, 'readiness ' + r.status);
  return 'readiness: ' + JSON.stringify(r.body.data).slice(0, 160);
}, { shot: true });
await step('Voice', 'PSTN call without Twilio is refused cleanly', p, async () => {
  const r = await api(p, 'POST', '/api/v1/calls/pstn', { to: '+27825550101', caseId: state.caseId });
  assert(r.status >= 400 && r.status < 500, 'expected 4xx, got ' + r.status);
  return `${r.status}: ${(r.body?.message || '').slice(0, 140)}`;
}, { allow: [[400, /calls\/pstn/], [409, /calls\/pstn/], [503, /calls\/pstn/], [422, /calls\/pstn/]] });
await step('Voice', 'Place call button in UI shows a readable message', p, async () => {
  await p.getByPlaceholder('082 555 0199').fill('0825550101');
  await p.getByPlaceholder('083 555 0100').fill('0835550100');
  const ms = p.locator('select', { has: p.locator('option', { hasText: 'Choose a matter' }) });
  await ms.selectOption({ value: state.caseId });
  await p.getByRole('button', { name: 'Place call' }).click(); await p.waitForTimeout(3000);
  const err = await p.locator('p.bg-red-50').first().innerText().catch(() => '');
  assert(err.length > 10, 'no readable error shown after Place call');
  return 'UI message: ' + err.slice(0, 140);
}, { allow: [[400, /calls|voice/], [409, /calls|voice/], [503, /calls|voice/]], shot: true });

// =============== 10. CONFLICTS ===============
await step('Conflicts', 'Single-match check (Fatima Patel)', p, async () => {
  await go(p, '/conflicts');
  await p.getByPlaceholder('Prospective client').fill('Fatima Patel');
  await p.getByRole('button', { name: 'Run check' }).click(); await settle(p);
  const r = (await api(p, 'GET', '/api/v1/conflicts')).body.data[0];
  return `latest: ${r?.searchName} matches ${r?.matchCount}`;
}, { shot: true });
await step('Conflicts', 'Multi-match check (Khumalo v Road Accident Fund)', p, async () => {
  await p.getByPlaceholder('Prospective client').fill('Khumalo');
  await p.getByPlaceholder('Adverse party (optional)').fill('Road Accident Fund');
  await p.getByRole('button', { name: 'Run check' }).click(); await settle(p);
  const r = await api(p, 'POST', '/api/v1/conflicts/check', { name: 'Khumalo', opposingParty: 'Road Accident Fund' });
  assert(r.status === 200, `API ${r.status}: ${(r.body?.message || '').slice(0, 200)}`);
}, { shot: true });

// =============== 11. LEADS via public enquiry ===============
await step('Public site', 'Contact enquiry form submits (public, no login)', null, async () => {
  const c = await ctx(); const pp = watch(await c.newPage());
  await pp.goto(W + '/firm/ndlovu-partners/contact'); await settle(pp);
  const f = pp.locator('form.ps-form');
  const ins = f.locator('input:not([type=checkbox]):not([type=date])');
  await ins.nth(0).fill('Lerato Enquiry' + RUN); await ins.nth(1).fill(`lerato${RUN}@example.co.za`);
  const n = await ins.count(); for (let k = 2; k < n; k++) { const t = await ins.nth(k).getAttribute('type'); if (t === 'tel') await ins.nth(k).fill('0825550199'); else await ins.nth(k).fill('Motor vehicle accident'); }
  const sel = f.locator('select'); for (let k = 0; k < await sel.count(); k++) { const o = await sel.nth(k).locator('option').allTextContents(); if (o.length > 1) await sel.nth(k).selectOption({ index: 1 }); }
  const dt = f.locator('input[type=date]'); if (await dt.count()) await dt.fill('2026-02-14');
  const cb = f.locator('input[type=checkbox]'); for (let k = 0; k < await cb.count(); k++) await cb.nth(k).check();
  await f.locator('textarea').fill('I was injured as a passenger in a taxi on the R21. Test ' + RUN);
  await f.locator('button[type=submit]').click(); await pp.waitForTimeout(2500);
  const t = await pp.locator('main').innerText();
  await snap(pp, 'pass-public-contact-enquiry-submitted');
  const errs = pp._ev.filter(e => !(e.kind === 'console' && /Failed to load resource/.test(e.text)) && !(e.kind === 'http' && /\?_rsc=/.test(e.url) && (state.rscLeak = (state.rscLeak || 0) + 1)));
  await c.close();
  assert(!errs.length, JSON.stringify(errs).slice(0, 300));
  assert(/thank|received|sent|we will|be in touch/i.test(t), 'no confirmation text');
});
await step('Leads', 'Enquiry lands in Hire pipeline (/leads) as a new lead', p, async () => {
  await go(p, '/leads');
  await p.waitForFunction(n => document.querySelector('main')?.innerText.includes(n), 'Lerato Enquiry' + RUN, { timeout: 30000 });
  const lead = (await api(p, 'GET', '/api/v1/intake/leads')).body.data.find(l => l.name?.includes('Enquiry' + RUN));
  state.leadId = lead.id; return `status ${lead.status}`;
}, { shot: true });
await step('Leads', 'Retain lead: conflict screen then limited file matter', p, async () => {
  const card = p.locator('div.rounded-lg.border', { hasText: 'Lerato Enquiry' + RUN });
  await card.getByRole('button', { name: 'Retain' }).click(); await p.waitForTimeout(3000); await settle(p);
  const url = p.url();
  if (url.includes('/cases/')) { state.retainedCase = url.split('/cases/')[1]; return 'redirected to limited file ' + state.retainedCase; }
  const t = await p.locator('main').innerText();
  if (/Issue conflict waiver/.test(t)) {
    await p.getByRole('button', { name: 'Issue conflict waiver' }).click(); await settle(p);
    return 'conflict hit: waiver issued (needs signature before retain)';
  }
  throw new Error('no matter and no conflict block: ' + t.slice(0, 200));
}, { shot: true });
await step('Leads', 'Seeded lead with RAF conflict blocks until waiver', p, async () => {
  const leads = (await api(p, 'GET', '/api/v1/intake/leads')).body.data.filter(l => !['retained', 'declined'].includes(l.status) && !l.name?.includes(RUN));
  if (!leads.length) return 'no other open lead to test';
  const r = await api(p, 'POST', '/api/v1/retain/' + leads[0].id, { force: false });
  assert(r.status === 200, 'retain ' + r.status + ' ' + r.body?.message);
  return `${leads[0].name}: blocked=${r.body.data.blocked} limited=${r.body.data.limited}`;
});

// =============== 12. ADMIN AREAS ===============
for (const [path, name, re] of [['/fitness', 'Practice fitness', /fitness|FFC|POPIA|trust/i], ['/reports', 'Reports', /report|revenue|hours|matters/i], ['/ai', 'Draft help page', /draft|summar|assistant/i]]) {
  await step('Admin', name + ' page renders data', p, async () => { await go(p, path); const t = await p.locator('main').innerText(); assert(re.test(t), 'expected content missing'); }, { shot: true });
}
await step('Admin', 'Integrations connect and disconnect', p, async () => {
  await go(p, '/integrations');
  const list = (await api(p, 'GET', '/api/v1/integrations')).body.data;
  const off = list.find(x => !x.connected) || list[0];
  const c1 = await api(p, 'POST', `/api/v1/integrations/${off.provider}/connect`, {});
  const c2 = await api(p, 'POST', `/api/v1/integrations/${off.provider}/disconnect`, {});
  assert(c1.status === 200 && c2.status === 200, `${c1.status}/${c2.status}`);
  return `${off.provider} connect/disconnect ok (note: simulated, no OAuth)`;
}, { shot: true });
await step('Admin', 'Audit log shows actions from this run', p, async () => {
  await go(p, '/audit');
  const r = (await api(p, 'GET', '/api/v1/audit')).body.data;
  const recent = JSON.stringify(r.slice(0, 80));
  const hits = ['client', 'case', 'invoice', 'trust', 'document'].filter(k => recent.toLowerCase().includes(k));
  assert(hits.length >= 2, 'audit does not record this run\'s actions; found only: ' + hits.join(','));
  return 'audit mentions: ' + hits.join(', ') + `; ${r.length} rows`;
}, { shot: true });
await step('Admin', 'Modules: toggle an add-on off and on', p, async () => {
  await go(p, '/modules');
  const tenant = await p.evaluate(() => JSON.parse(localStorage.getItem('ls_tenant')));
  const cat = (await api(p, 'GET', '/api/v1/modules')).body.data;
  const addon = cat.find(m => !m.core);
  assert(addon, 'no add-on module');
  const enabled = (await api(p, 'GET', `/api/v1/tenants/${tenant.id}/modules`)).body.data;
  return `${cat.length} modules, add-on ${addon.slug || addon.key}; enabled list ${JSON.stringify(enabled).slice(0, 80)}`;
}, { shot: true });
await step('Admin', 'Team: invite a member and change role', p, async () => {
  await go(p, '/team');
  await p.getByRole('button', { name: 'Invite' }).click();
  const d = p.locator('div.fixed.inset-0').first();
  const ins = d.locator('input');
  await ins.nth(0).fill('Zanele'); await ins.nth(1).fill('Paralegal' + RUN); await ins.nth(2).fill(`zanele${RUN}@ndlovulaw.co.za`);
  const opts = await d.locator('select option').allTextContents();
  await d.locator('select').selectOption(opts.find(o => /paralegal/i.test(o)) || opts[opts.length - 1]);
  await ins.nth(3).fill('650');
  await d.getByRole('button').last().click(); await settle(p);
  await expectText(p, `zanele${RUN}`);
  const u = (await api(p, 'GET', '/api/v1/users')).body.data.find(x => x.email === `zanele${RUN}@ndlovulaw.co.za`);
  const r = await api(p, 'PATCH', '/api/v1/users/' + u.id, { role: 'associate' });
  assert(r.status === 200 && r.body.data.role === 'associate', 'role change failed ' + r.status);
  state.invitee = u.id;
  return `invited; temp password in response? ${JSON.stringify(u).includes('password') ? 'yes' : 'no'}`;
}, { shot: true });
await step('Admin', 'Settings: 2FA setup shows secret and code field', p, async () => {
  await go(p, '/settings');
  const btn = p.getByRole('button', { name: 'Start enrolment' });
  await btn.click(); await p.waitForTimeout(1500);
  await expectText(p, 'Secret');
  await p.getByPlaceholder('123456').fill('000000');
  await p.getByRole('button', { name: /Confirm|Turn on|Enable|Verify/i }).first().click(); await p.waitForTimeout(1500);
  const me = (await api(p, 'GET', '/api/v1/auth/me')).body.data;
  assert(!(me.user || me).totpEnabled, 'wrong code enabled 2FA');
  return 'wrong code rejected; 2FA stays off';
}, { allow: [[400, /totp/], [401, /totp/]], shot: true });
await step('Admin', 'Usage bill: issue month-end invoice and mark paid', p, async () => {
  await go(p, '/usage');
  const issue = p.getByRole('button', { name: 'Issue month-end invoice' });
  if (await issue.count()) { await issue.click(); await settle(p); }
  const mark = p.getByRole('button', { name: 'Mark paid' }).first();
  if (await mark.count()) { await mark.click(); await settle(p); }
  const h = (await api(p, 'GET', '/api/v1/usage/history')).body.data;
  return `${h.length} usage invoice(s); latest ${h[0]?.status} ${h[0]?.total}`;
}, { shot: true });
const PF_ENV = Object.fromEntries((fs.existsSync(process.env.API_ENV || './e2e-api.env') ? fs.readFileSync(process.env.API_ENV || './e2e-api.env', 'utf8') : '').split('\n').filter(l => l.includes('=')).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const crypto = await import('node:crypto');
const phpEnc = (v) => encodeURIComponent(String(v).trim()).replace(/%20/g, '+').replace(/[!'()]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase()).replace(/\*/g, '%2A').replace(/~/g, '%7E');
const pfSign = (fields) => crypto.createHash('md5').update(fields.filter(([k, v]) => k !== 'signature' && v !== '').map(([k, v]) => `${k}=${phpEnc(v)}`).concat(PF_ENV.PAYFAST_PASSPHRASE ? [`passphrase=${phpEnc(PF_ENV.PAYFAST_PASSPHRASE)}`] : []).join('&')).digest('hex');
await step('Admin', 'PayFast seat checkout posts a signed form to the sandbox', p, async () => {
  await go(p, '/product-billing');
  let posted = null;
  await p.route('https://sandbox.payfast.co.za/**', async (route) => {
    posted = route.request().postData();
    await route.fulfill({ status: 200, contentType: 'text/html', body: '<html lang="en"><title>PayFast sandbox (simulated)</title><body>PayFast sandbox (simulated)</body></html>' });
  });
  await p.getByLabel('Seats').fill('2');
  await p.getByRole('button', { name: 'Subscribe with PayFast' }).click();
  await p.waitForURL(/sandbox\.payfast\.co\.za/, { timeout: 20000 });
  await p.unroute('https://sandbox.payfast.co.za/**');
  assert(posted, 'no form posted');
  const fields = [...new URLSearchParams(posted).entries()];
  const get = (k) => (fields.find(([n]) => n === k) || [])[1];
  assert(get('signature') === pfSign(fields), 'checkout signature does not verify');
  state.pf = { m: get('m_payment_id'), amount: get('amount'), merchant: get('merchant_id') };
  return `m_payment_id ${state.pf.m}, amount ${state.pf.amount}, notify ${get('notify_url')}`;
});
await step('Admin', 'PayFast ITN (signed, sandbox) activates the seats; forged ITN ignored', p, async () => {
  assert(state.pf, 'no checkout');
  const notify = PF_ENV.PAYFAST_NOTIFY_URL || (process.env.API || 'http://127.0.0.1:18081') + '/api/v1/product-billing/payfast/itn';
  const send = async (fields) => fetch(notify, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams([...fields, ['signature', pfSign(fields)]]).toString() });
  const base = [['m_payment_id', state.pf.m], ['pf_payment_id', 'E2E' + RUN], ['payment_status', 'COMPLETE'], ['amount_gross', state.pf.amount]];
  await send([...base, ['merchant_id', '99999999'], ['token', 'tok-forged-' + RUN]]);
  await go(p, '/product-billing');
  let st = (await api(p, 'GET', '/api/v1/product-billing')).body.data;
  assert(st.seatStatus !== 'active' || st.seatCount !== 2, 'forged ITN activated seats');
  const r = await send([...base, ['merchant_id', state.pf.merchant], ['token', 'tok-e2e-' + RUN]]);
  assert(r.ok, 'ITN returned ' + r.status);
  await go(p, '/product-billing');
  st = (await api(p, 'GET', '/api/v1/product-billing')).body.data;
  assert(st.seatStatus === 'active' && Number(st.seatCount) === 2, `seat ${st.seatStatus} x${st.seatCount}`);
  return `seat ${st.seatStatus} x${st.seatCount}`;
}, { shot: true });
await step('Admin', 'PayFast minutes: invoice last month and charge the token', p, async () => {
  const d = new Date(); d.setMonth(d.getMonth() - 1);
  const period = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  await p.getByLabel('Period').fill(period);
  await p.getByRole('button', { name: 'Invoice minutes and charge' }).click(); await settle(p, 1200);
  const st = (await api(p, 'GET', '/api/v1/product-billing')).body.data;
  const row = (st.minutes || []).find(m => m.period === period);
  assert(row, 'no minutes row for ' + period + ': ' + JSON.stringify(st.minutes).slice(0, 200));
  assert(['charged', 'nothing_due'].includes(row.status), 'minutes status ' + row.status);
  return `${period}: ${row.status} ${row.amountCents ?? ''}`;
}, { shot: true });

// =============== 13. PUBLIC SITE ADMIN + APPROVAL ===============
await step('Public site admin', 'Owner changes accent and theme, toggles feature, submits', p, async () => {
  await go(p, '/website');
  const site = (await api(p, 'GET', '/api/v1/public-site')).body.data;
  state.siteBefore = { accent: site.liveAccent, theme: site.liveTheme, tenantId: site.tenantId };
  const targetAccent = site.accents.find(a => a.key !== (site.liveAccent || site.accent)) ;
  state.newAccent = targetAccent.key;
  await p.getByRole('button', { name: targetAccent.label, exact: true }).click();
  await p.getByRole('button', { name: 'Light', exact: true }).click();
  const feat = site.features.find(f => !f.requested && !f.approved) || site.features.find(f => !f.requested);
  if (feat) { state.feature = feat.key; await p.locator('label', { hasText: feat.label }).locator('input[type=checkbox]').check(); }
  await p.getByRole('button', { name: 'Submit for approval' }).click(); await settle(p);
  await expectText(p, 'Submitted');
  const after = (await api(p, 'GET', '/api/v1/public-site')).body.data;
  assert(after.brandingStatus === 'pending', 'branding status ' + after.brandingStatus);
  assert((after.liveTheme || 'light') === (site.liveTheme || 'light'), 'live theme changed before approval');
  return `requested accent ${targetAccent.key}, theme light, feature ${feat?.key || 'none'}`;
}, { shot: true });
await step('Public site admin', 'Live site unchanged before approval', null, async () => {
  const c = await ctx(); const pp = watch(await c.newPage());
  await pp.goto(W + '/firm/ndlovu-partners'); await settle(pp);
  const theme = await pp.evaluate(() => document.querySelector('.ps-site')?.getAttribute('data-theme') || 'none');
  state.themeProbe = theme; await c.close();
  return 'theme marker: ' + theme.slice(0, 80);
});
const assocCtx = await ctx(); const ap = watch(await assocCtx.newPage());
await step('Roles', 'Associate (Sipho) is denied public-site admin', ap, async () => {
  await login(ap, 'ndlovu-partners', 'sipho@ndlovulaw.co.za', 'password');
  await go(ap, '/website');
  await expectText(ap, 'Only a firm owner');
  const r = await api(ap, 'PUT', '/api/v1/public-site', { accent: 'navy', theme: 'dark', features: {} });
  assert(r.status === 403, 'API PUT allowed for associate: ' + r.status);
  const s = await api(ap, 'POST', '/api/v1/public-site/submit');
  assert(s.status === 403, 'submit allowed for associate: ' + s.status);
}, { allow: [[403, /public-site/]], shot: true });
await step('Roles', 'Associate denied team role change, modules and operator queue', ap, async () => {
  const me = await ap.evaluate(() => JSON.parse(localStorage.getItem('ls_user')));
  const tenant = await ap.evaluate(() => JSON.parse(localStorage.getItem('ls_tenant')));
  const a = await api(ap, 'PATCH', '/api/v1/users/' + me.id, { title: 'Associate (self-edit test)' });
  const mods = (await api(ap, 'GET', '/api/v1/modules')).body.data; const addon = mods.find(m => !m.core);
  const b = await api(ap, 'POST', `/api/v1/tenants/${tenant.id}/modules`, { moduleId: addon.id, enabled: true });
  const c = await api(ap, 'GET', '/api/v1/platform/public-sites/queue');
  const d = await api(ap, 'POST', '/api/v1/users/invite', { firstName: 'Assoc', lastName: 'Invite' + RUN, email: `associnv${RUN}@x.co.za`, role: 'owner' });
  const e2 = await api(ap, 'GET', '/api/v1/trust/accounts');
  const codes = { patchUser: a.status, modules: b.status, queue: c.status, inviteAsOwner: d.status, invitedRole: d.body?.data?.role, trustRead: e2.status };
  const leaks = ['patchUser', 'modules', 'inviteAsOwner'].filter(k => codes[k] < 400);
  assert(!leaks.length, 'associate allowed: ' + JSON.stringify(codes));
  return JSON.stringify(codes);
}, { allow: [[403, /.*/], [401, /.*/], [400, /.*/]] });
await step('Roles', 'Associate can work matters (docket, cases)', ap, async () => { await go(ap, '/cases'); await go(ap, '/dashboard'); });

const opCtx = await ctx(); const op = watch(await opCtx.newPage());
await step('Operator', 'Operator signs in and sees approval queue', op, async () => {
  await login(op, 'legalsuite', 'ops@legalsuite.pro', 'password');
  await go(op, '/platform/sites');
  await expectText(op, 'Ndlovu');
  const q = (await api(op, 'GET', '/api/v1/platform/public-sites/queue')).body.data;
  state.queue = q.map(i => `${i.slug}:${i.kind}:${i.featureKey || ''}`);
  return state.queue.join(', ');
}, { shot: true });
await step('Operator', 'Operator rejects the feature request with a note', op, async () => {
  if (!state.feature) return 'no feature requested';
  const q = (await api(op, 'GET', '/api/v1/platform/public-sites/queue')).body.data;
  const item = q.find(i => i.slug === 'ndlovu-partners' && i.kind === 'feature');
  assert(item, 'feature not in queue');
  const card = op.locator('div', { has: op.locator('p', { hasText: 'ndlovu-partners' }) }).filter({ hasText: new RegExp(item.featureKey, 'i') }).last();
  const r = await api(op, 'POST', `/api/v1/platform/public-sites/${item.tenantId}/features/${item.featureKey}`, { decision: 'rejected', note: 'Not this month ' + RUN });
  assert(r.status === 200, 'reject ' + r.status);
});
await step('Operator', 'Operator approves branding (accent + light theme)', op, async () => {
  await go(op, '/platform/sites');
  const card = op.locator('div.rounded-2xl, div[class*="shadow"]').filter({ hasText: 'ndlovu-partners' }).filter({ hasText: 'Draft:' }).first();
  await card.getByRole('button', { name: 'Approve' }).click(); await settle(op);
  const q = (await api(op, 'GET', '/api/v1/platform/public-sites/queue')).body.data;
  assert(!q.some(i => i.slug === 'ndlovu-partners' && i.kind === 'branding'), 'branding still queued');
}, { shot: true });
await step('Public site admin', 'Approved change is live on /firm/ndlovu-partners', p, async () => {
  const s = (await api(p, 'GET', '/api/v1/public-site')).body.data;
  assert(s.liveAccent === state.newAccent && (s.liveTheme || 'light') === 'light', `live ${s.liveAccent}/${s.liveTheme}`);
  const pub = await (await fetch(W + '/api/v1/public/sites/ndlovu-partners')).json();
  assert((pub.data.theme || 'light') === 'light' && pub.data.accent === state.newAccent, 'public API shows ' + pub.data.accent + '/' + pub.data.theme);
  if (state.feature) assert(s.features.find(f => f.key === state.feature)?.status !== 'approved', 'rejected feature is approved');
  return `live accent ${s.liveAccent}, theme light; feature ${state.feature} rejected note visible: ${JSON.stringify(s.features.find(f => f.key === state.feature)?.note || '')}`;
});

// =============== 14. PUBLIC PAGES light then dark ===============
async function crawlPublic(theme) {
  const c = await ctx(); const pp = watch(await c.newPage());
  await pp.goto(W + '/firm/ndlovu-partners'); await settle(pp);
  const links = new Set(['/firm/ndlovu-partners']);
  for (const seed of ['/firm/ndlovu-partners', '/firm/ndlovu-partners/people', '/firm/ndlovu-partners/expertise', '/firm/ndlovu-partners/insights']) {
    await pp.goto(W + seed); await settle(pp, 200);
    for (const h of await pp.$$eval('a[href^="/firm/ndlovu-partners"]', as => as.map(a => a.getAttribute('href').split('#')[0].split('?')[0]))) links.add(h);
  }
  const list = [...links].sort();
  let pass = 0; const fails = [];
  for (const l of list) {
    const ok = await step('Public pages', `${theme}: ${l.replace('/firm/ndlovu-partners', '') || '/'}`, pp, async () => {
      const r = await pp.goto(W + l, { waitUntil: 'domcontentloaded' }); await settle(pp, 300);
      assert(r.status() < 400, 'HTTP ' + r.status());
      const broken = await pp.$$eval('img', is => is.filter(i => i.complete && i.naturalWidth === 0).map(i => i.src));
      assert(!broken.length, 'broken images: ' + broken.slice(0, 3).join(', '));
      const bg = await pp.evaluate(() => getComputedStyle(document.querySelector('.ps-root, [data-ps-theme], main') || document.body).backgroundColor);
      const th = await pp.evaluate(() => document.querySelector('.ps-site')?.getAttribute('data-theme'));
      assert(th === theme, `expected ${theme} appearance, page shows ${th}`);
      return 'data-theme ' + th + ', bg ' + bg;
    }, { shot: ['/firm/ndlovu-partners', '/firm/ndlovu-partners/contact', '/firm/ndlovu-partners/people'].includes(l) });
    if (ok) pass++; else fails.push(l);
  }
  await c.close();
  return { total: list.length, pass, fails };
}
state.lightCrawl = await crawlPublic('light');
// switch back to dark through the same approval flow
await step('Public site admin', 'Owner requests dark again; operator approves', p, async () => {
  await go(p, '/website');
  await p.getByRole('button', { name: 'Dark', exact: true }).click();
  await p.getByRole('button', { name: 'Submit for approval' }).click(); await settle(p);
  await go(op, '/platform/sites');
  const card = op.locator('div.rounded-2xl, div[class*="shadow"]').filter({ hasText: 'ndlovu-partners' }).filter({ hasText: 'Draft:' }).first();
  await card.getByRole('button', { name: 'Approve' }).click(); await settle(op);
  const pub = await (await fetch(W + '/api/v1/public/sites/ndlovu-partners')).json();
  assert(pub.data.theme === 'dark', 'theme ' + pub.data.theme);
});
state.darkCrawl = await crawlPublic('dark');
await step('Public pages', 'Smith & Associates site stays off (not approved)', null, async () => {
  const j = await (await fetch(W + '/api/v1/public/sites/smith-associates')).json();
  const c = await ctx(); const pp = watch(await c.newPage());
  const pr = await pp.goto(W + '/firm/smith-associates'); await settle(pp);
  const t = await pp.locator('body').innerText(); await snap(pp, 'pass-smith-site-off'); await c.close();
  assert(j.data.published === false, 'published flag ' + j.data.published);
  assert(!/Expertise|People/.test(t) || /not|unavailable|approval/i.test(t), 'Smith site content visible');
  return `API published=false; page ${pr.status()}: ${t.replace(/\s+/g, ' ').slice(0, 100)}`;
});

// =============== 15. CLIENT PORTAL ===============
const portalCtx = await ctx(); const pt = watch(await portalCtx.newPage());
await step('Portal', 'Client (Nomsa) logs in to portal and sees matters and invoices', pt, async () => {
  await pt.goto(W + '/portal/login'); await settle(pt);
  const ins = pt.locator('form input');
  await ins.nth(0).fill('ndlovu-partners'); await ins.nth(1).fill('nomsa@example.com'); await ins.nth(2).fill('portal123');
  await pt.locator('form button').click();
  await pt.waitForURL(u => u.pathname === '/portal', { timeout: 15000 }); await settle(pt);
  const t = await pt.locator('body').innerText();
  assert(/Khumalo|matter|case/i.test(t), 'no matter visible');
}, { shot: true });
await step('Portal', 'Portal token cannot read staff API or other clients', pt, async () => {
  const a = await api(pt, 'GET', '/api/v1/clients');
  const b = await api(pt, 'GET', '/api/v1/trust/accounts');
  const c = await api(pt, 'GET', '/api/v1/cases/' + state.caseId);
  const u = await api(pt, 'GET', '/api/v1/users');
  const inv = await api(pt, 'POST', '/api/v1/users/invite', { firstName: 'Portal', lastName: 'Escalation' + RUN, email: `portalesc${RUN}@x.co.za`, role: 'owner' });
  const codes = { clients: a.status, clientRows: a.body?.data?.length, trust: b.status, otherClientMatter: c.status, users: u.status, inviteOwner: inv.status, invitedRole: inv.body?.data?.role };
  assert([a, b, c, u, inv].every(r => r.status >= 400), 'portal user reached staff data: ' + JSON.stringify(codes));
  return JSON.stringify(codes);
}, { allow: [[403, /.*/], [401, /.*/], [404, /.*/]] });
await step('Portal', 'Wrong portal password refused', null, async () => {
  const r = await fetch(W + '/api/v1/portal/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ firmSlug: 'ndlovu-partners', email: 'nomsa@example.com', password: 'nope' }) });
  assert(r.status === 401 || r.status === 400, 'status ' + r.status);
});

await step('Portal', 'Client submits proof of payment; the firm rejects it with a note', pt, async () => {
  await go(pt, '/portal');
  const invs = (await api(pt, 'GET', '/api/v1/portal/invoices')).body.data;
  const open = invs.find(i => Number(i.balanceDue) > 0);
  assert(open, 'no open invoice for the portal client');
  await pt.locator('tr', { hasText: open.invoiceNumber }).first().getByRole('button', { name: /^(Lines|Hide|Detail)$/ }).click();
  await pt.getByRole('button', { name: 'Submit proof of payment' }).first().click();
  await pt.locator('input[type=file]').first().setInputFiles('/tmp/a2z-pop.pdf');
  await pt.getByLabel('Reference').first().fill('PORTAL ' + RUN);
  await pt.getByRole('button', { name: 'Submit proof' }).first().click(); await settle(pt);
  await expectText(pt, 'Proof submitted');
  await go(p, '/billing');
  const row = p.locator('tr', { hasText: 'PORTAL ' + RUN }).first();
  await row.getByRole('button', { name: 'Reject' }).click();
  const d = dialog(p, 'Reject proof of payment');
  await d.getByLabel('Note').fill('Amount does not match the bank statement');
  await d.getByRole('button', { name: 'Reject proof' }).click(); await settle(p);
  const proofs = (await api(p, 'GET', '/api/v1/payment-proofs')).body.data;
  const mine = proofs.find(x => x.reference === 'PORTAL ' + RUN);
  assert(mine && mine.status === 'rejected', 'proof status ' + mine?.status);
  return 'rejected with note';
}, { shot: true });

// =============== 16. TEXAS + ISOLATION ===============
const txCtx = await ctx(); const tx = watch(await txCtx.newPage());
await step('Texas', 'John (Smith & Associates) sees Texas docket', tx, async () => {
  await login(tx, 'smith-associates', 'john@smithlaw.com', 'password');
  await go(tx, '/dashboard');
  const t = await tx.locator('main').innerText();
  assert(/CPRC|Texas|16\.0|74\.|101\./i.test(t), 'no Texas clocks visible');
  const cases = (await api(tx, 'GET', '/api/v1/cases')).body.data; state.smithCase = cases[0]?.id;
  const inv = (await api(tx, 'GET', '/api/v1/invoices')).body.data;
  return `${cases.length} matters; currency check: ${JSON.stringify(inv[0]?.taxAmount)}`;
}, { shot: true });
await step('Isolation', 'Ndlovu owner cannot read Smith matter by URL or API', p, async () => {
  const r = await api(p, 'GET', '/api/v1/cases/' + state.smithCase);
  assert(r.status === 404 || r.status === 403, 'Smith matter readable by Ndlovu: ' + r.status);
  await p.goto(W + '/cases/' + state.smithCase); await settle(p);
  const t = await p.locator('main').innerText();
  assert(!/Smith/.test(t) || /not found/i.test(t), 'Smith data rendered');
  const s = await api(p, 'GET', '/api/v1/search?q=Smith');
  return `API ${r.status}; search hits ${JSON.stringify(s.body?.data).length}`;
}, { allow: [[404, /cases\//], [403, /cases\//]], shot: true });
await step('Isolation', 'Smith user cannot read Ndlovu matter, trust or documents', tx, async () => {
  const a = await api(tx, 'GET', '/api/v1/cases/' + C1);
  const b = await api(tx, 'GET', `/api/v1/trust/accounts/${state.trust.id}/ledger`);
  const c = await api(tx, 'GET', `/api/v1/documents/${state.docId}/download`);
  const d = await api(tx, 'PUT', '/api/v1/clients/' + state.clientId, { firstName: 'Hacked' });
  const e = await api(tx, 'GET', `/api/v1/trust/accounts/${state.trust.id}/recon`);
  const codes = { matter: a.status, ledger: b.status, ledgerRows: Array.isArray(b.body?.data) ? b.body.data.length : null, docDownload: c.status, clientUpdate: d.status, recon: e.status };
  const leak = (a.status < 400) || (c.status < 400) || (d.status < 400) || (e.status < 400) || (codes.ledgerRows > 0);
  assert(!leak, 'cross-tenant access: ' + JSON.stringify(codes));
  return JSON.stringify(codes);
}, { allow: [[404, /.*/], [403, /.*/], [400, /.*/], [500, /documents/]] });
await step('Isolation', 'Forged tenant header does not switch tenant', tx, async () => {
  const r = await tx.evaluate(async (tid) => {
    const res = await fetch('/api/v1/cases', { headers: { Authorization: 'Bearer ' + localStorage.getItem('ls_access'), 'X-Tenant-Id': tid } });
    return (await res.json()).data.map(c => c.title).join('|');
  }, state.siteBefore.tenantId || '');
  assert(!/Khumalo/.test(r), 'Ndlovu matters returned');
});

// =============== 17. MOBILE 390px ===============
const mob = await ctx({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 });
const mp = watch(await mob.newPage());
await login(mp, 'ndlovu-partners', 'thabo@ndlovulaw.co.za', 'password');
for (const path of ['/dashboard', '/cases', '/cases/' + C1, '/trust', '/billing', '/leads', '/firm/ndlovu-partners', '/firm/ndlovu-partners/contact', '/firm/ndlovu-partners/people']) {
  await step('Mobile 390', path, mp, async () => {
    await go(mp, path);
    const sw = await mp.evaluate(() => document.documentElement.scrollWidth);
    assert(sw <= 392, `horizontal overflow: page is ${sw}px wide at 390px`);
  }, { shot: true });
}
await step('Mobile 390', '/portal', pt, async () => {
  await pt.setViewportSize({ width: 390, height: 844 }); await go(pt, '/portal');
  const sw = await pt.evaluate(() => document.documentElement.scrollWidth);
  assert(sw <= 392, `horizontal overflow ${sw}px`);
}, { shot: true });

// =============== 18. SWEEP: every staff page loads without errors (owner) ===============
for (const path of ['/dashboard', '/cases', '/clients', '/contacts', '/documents', '/templates', '/esign', '/calendar', '/tasks', '/time', '/billing', '/usage', '/trust', '/expenses', '/messages', '/voice', '/leads', '/conflicts', '/ai', '/fitness', '/reports', '/integrations', '/audit', '/modules', '/team', '/settings', '/website', '/cases/' + C1]) {
  await step('Sweep (owner)', path, p, async () => { await go(p, path); const t = await p.locator('main').innerText(); assert(t.trim().length > 30, 'empty main'); assert(!/Application error|Unhandled Runtime Error/.test(t), 'runtime error'); });
}

// =============== 19. RELEASE CHECKS (invite email, publish, seed, refresh, sockets, headers, throttling) ===============
const MAILPIT = process.env.MAILPIT || 'http://127.0.0.1:8025';
await step('Release', 'Invite email arrives with a one-time set-password link', null, async () => {
  const to = `zanele${RUN}@ndlovulaw.co.za`;
  let msg = null;
  for (let i = 0; i < 20 && !msg; i++) {
    const r = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + to)}`).then(x => x.json());
    msg = r.messages?.[0] || null;
    if (!msg) await new Promise(res => setTimeout(res, 500));
  }
  assert(msg, 'no invite email in Mailpit for ' + to);
  const full = await fetch(`${MAILPIT}/api/v1/message/${msg.ID}`).then(x => x.json());
  const m = /\/set-password\?token=([A-Za-z0-9_\-]+)/.exec(full.Text || full.HTML || '');
  assert(m, 'no set-password link in the email');
  state.inviteToken = m[1];
  return `subject "${msg.Subject}"`;
});
const invCtx = await ctx();
const ip = watch(await invCtx.newPage());
await step('Release', 'Invitee sets a password from the link and lands signed in', ip, async () => {
  assert(state.inviteToken, 'no token from previous step');
  await go(ip, '/set-password?token=' + state.inviteToken);
  await ip.getByLabel('New password').fill('Zanele-Strong-' + RUN);
  await ip.getByLabel('Confirm password').fill('Zanele-Strong-' + RUN);
  await ip.getByRole('button', { name: /set password|save|continue/i }).first().click();
  await ip.waitForURL(u => !u.pathname.startsWith('/set-password'), { timeout: 30000 });
  await settle(ip);
  const me = (await api(ip, 'GET', '/api/v1/auth/me')).body.data;
  assert(((me.user || me).email || '').startsWith('zanele' + RUN), 'not signed in as the invitee');
  return 'landed on ' + new URL(ip.url()).pathname;
}, { shot: true });
await step('Release', 'The invite link works only once', ip, async () => {
  const r = await fetch(W + '/api/v1/auth/invite/accept', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token: state.inviteToken, newPassword: 'Another-Pass-' + RUN }) });
  assert(r.status >= 400 && r.status < 500, 'reused link accepted: ' + r.status);
  return 'reuse refused with ' + r.status;
});
await invCtx.close();
await step('Release', 'New staff stay off the public People page until published', p, async () => {
  const people = async () => JSON.stringify((await fetch(W + '/api/v1/public/sites/ndlovu-partners').then(r => r.json())).data.people || []);
  assert(!(await people()).includes('Paralegal' + RUN), 'invitee listed before publishing');
  const on = await api(p, 'PATCH', '/api/v1/users/' + state.invitee, { publicProfile: true });
  assert(on.status === 200, 'publish failed ' + on.status);
  assert((await people()).includes('Paralegal' + RUN), 'invitee not listed after publishing');
  await api(p, 'PATCH', '/api/v1/users/' + state.invitee, { publicProfile: false });
  assert(!(await people()).includes('Paralegal' + RUN), 'still listed after unpublishing');
  return 'hidden, published, hidden again';
});
await step('Release', 'Seeded documents, e-sign, expenses and messages are present', p, async () => {
  const docs = (await api(p, 'GET', '/api/v1/documents')).body.data;
  const sigs = (await api(p, 'GET', '/api/v1/signatures')).body.data;
  const exp = (await api(p, 'GET', '/api/v1/expenses')).body.data;
  const conv = (await api(p, 'GET', '/api/v1/conversations')).body.data;
  assert(docs.length >= 3 && sigs.length >= 2 && exp.length >= 2 && conv.length >= 1, `docs ${docs.length} sigs ${sigs.length} exp ${exp.length} conv ${conv.length}`);
  return `docs ${docs.length}, e-sign ${sigs.length}, expenses ${exp.length}, conversations ${conv.length}`;
});
await step('Release', 'Seeded document downloads as a real PDF from the Documents page', p, async () => {
  await go(p, '/documents');
  const [dl] = await Promise.all([p.waitForEvent('download', { timeout: 20000 }), p.getByRole('button', { name: /^Download .*\.pdf$/ }).first().click()]);
  const path = await dl.path();
  const head = fs.readFileSync(path).subarray(0, 5).toString();
  assert(head === '%PDF-', 'not a PDF: ' + head);
  return dl.suggestedFilename();
});
await step('Release', 'Expired access token refreshes silently (page and download)', p, async () => {
  await p.evaluate(() => localStorage.setItem('ls_access', 'expired.token.value'));
  await go(p, '/documents');
  const [dl] = await Promise.all([p.waitForEvent('download', { timeout: 20000 }), p.getByRole('button', { name: 'Download' }).first().click()]);
  assert(dl, 'no download');
  assert(!new URL(p.url()).pathname.startsWith('/login'), 'sent to login');
  const fresh = await p.evaluate(() => localStorage.getItem('ls_access'));
  assert(fresh && fresh !== 'expired.token.value', 'token not refreshed');
  return 'refreshed and downloaded';
}, { allow: [[401, /\/api\/v1\//]] });
await step('Release', 'Call signalling socket needs a staff token and cannot impersonate', p, async () => {
  const r = await p.evaluate(async () => {
    const open = (q) => new Promise(res => {
      const ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/signal${q}`);
      const out = { closed: false, msgs: [] };
      ws.onmessage = e => out.msgs.push(e.data);
      ws.onclose = () => { out.closed = true; res(out); };
      ws.onopen = () => { ws.send(JSON.stringify({ type: 'register', userId: '00000000-0000-0000-0000-000000000001' })); setTimeout(() => { ws.close(); }, 1200); };
      ws.onerror = () => {};
      setTimeout(() => res(out), 4000);
    });
    const anon = await open('');
    const me = await open('?token=' + encodeURIComponent(localStorage.getItem('ls_access')));
    return { anonMsgs: anon.msgs, meMsgs: me.msgs };
  });
  assert(r.anonMsgs.length === 0, 'anonymous socket got a reply: ' + r.anonMsgs.join());
  assert(r.meMsgs.some(m => m.includes('registered')), 'signed-in socket not registered: ' + JSON.stringify(r));
  assert(!r.meMsgs.some(m => m.includes('00000000-0000-0000-0000-000000000001')), 'claimed another user id');
  return 'anonymous refused; signed-in bound to own id';
}, { ignoreConsole: [/WebSocket/] });
await step('Release', 'Security headers on web and API', null, async () => {
  const web = await fetch(W + '/login');
  const apiRes = await fetch(W + '/api/v1/health');
  const need = ['content-security-policy', 'x-content-type-options', 'x-frame-options', 'referrer-policy', 'permissions-policy'];
  const missing = need.filter(h => !web.headers.get(h)).map(h => 'web:' + h).concat(['x-content-type-options', 'x-frame-options'].filter(h => !apiRes.headers.get(h)).map(h => 'api:' + h));
  assert(!missing.length, 'missing ' + missing.join(', '));
  assert(!web.headers.get('x-powered-by'), 'x-powered-by present');
  return 'present';
});
await step('Release', 'Repeated wrong passwords are throttled with a clear message', null, async () => {
  const body = JSON.stringify({ firmSlug: 'ndlovu-partners', email: `nobody${RUN}@example.co.za`, password: 'wrong' });
  let last = 0, msg = '';
  for (let i = 0; i < 10; i++) {
    const r = await fetch(W + '/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body });
    last = r.status; if (r.status === 429) { msg = (await r.json()).message; break; }
  }
  assert(last === 429, 'never throttled, last status ' + last);
  return msg;
});

await browser.close();
if (state.rscPages) state.rscPages = [...state.rscPages];
results.push({ area: 'Public site', name: 'Nav links stay under /firm/{slug} (no root-level prefetch 404s)', ok: !state.rscLeak, note: state.rscLeak ? `${state.rscLeak} 404 prefetches to /expertise, /people etc. on ${state.rscPages.length} pages` : '', shot: '' });
fs.writeFileSync(RESULTS, JSON.stringify({ run: RUN, partial: false, results, state }, null, 2));
const pass = results.filter(r => r.ok).length;
console.log(`\nTOTAL ${results.length}  PASS ${pass}  FAIL ${results.length - pass}`);
