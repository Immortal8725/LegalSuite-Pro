import { mkdirSync, writeFileSync } from 'fs';
import { chromium } from 'playwright';
const W=process.env.WEB||'http://127.0.0.1:43300', O=process.env.OUT||'/workspace/owlaw-mobile-shots/v2/';
mkdirSync(O,{recursive:true});
const results=[]; const ok=(n)=>{results.push(n);console.log('PASS',n)};
const b=await chromium.launch(); const p=await b.newPage({viewport:{width:412,height:860},deviceScaleFactor:2});
p.on('pageerror',e=>console.log('pageerror',e.message));
const shot=async n=>{await p.waitForTimeout(1500);await p.screenshot({path:O+n+'.png'});console.log('shot',n)};
const sem=async()=>{const s=p.locator('flt-semantics-placeholder');if(await s.count())await s.evaluate(e=>e.click());await p.waitForTimeout(500)};
await p.goto(W);await p.waitForTimeout(5000);await sem();
await shot('01-login');
const box=n=>p.getByRole('textbox',{name:n});
// Newer Flutter web exposes ActionChip as a checkbox, so chips match either role.
const chip=n=>p.getByRole('button',{name:n,exact:true}).or(p.getByRole('checkbox',{name:n,exact:true}));
await box('Firm ID').click();await p.waitForTimeout(400);await p.keyboard.type('smith-associates');
await box('Email').click();await p.waitForTimeout(400);await p.keyboard.type('john@smithlaw.com');
await box('Password').click();await p.waitForTimeout(400);await p.keyboard.type('password');
await p.getByRole('button',{name:'Sign in'}).click();
await p.waitForTimeout(4000);await p.screenshot({path:'/tmp/afterlogin.png'});await p.getByText('Welcome back, John').waitFor({timeout:20000});console.log('LOGIN OK');
await shot('02-dashboard');
await p.getByRole('tab',{name:/Matters/}).click().catch(async()=>p.getByText('Matters').last().click());
await p.getByText(/Taylor Contract Dispute/).first().waitFor({timeout:15000});console.log('MATTERS OK');
await shot('03-matters');
await box('Search matters, clients, parties').click().catch(()=>{});await p.waitForTimeout(400);await p.keyboard.type('taylor');await shot('04-matter-search');
await p.getByText(/Taylor Contract Dispute/).first().click();
await p.getByText('Parties').first().waitFor({timeout:15000});console.log('DETAIL OK');
await shot('05-matter-detail');
await p.mouse.wheel(0,700);await shot('06-matter-detail-docs');
await chip('Log time').first().click();
await p.getByText('Save time entry').waitFor();
const desc='Mobile E2E '+Date.now();
await box('Time').click();await p.keyboard.press('Control+A');await p.keyboard.type('1:15');
await box('Work done').click();await p.waitForTimeout(400);await p.keyboard.type(desc);
await shot('07-log-time');
await p.getByRole('button',{name:'Save time entry'}).click();
await p.getByText('Time entry saved').first().waitFor({timeout:15000});console.log('TIME SAVED UI');
await p.goBack().catch(()=>{});
await p.getByRole('button',{name:'Back'}).click().catch(()=>{});
await p.waitForTimeout(1000);
await p.getByRole('tab',{name:/Time/}).click();
await p.waitForTimeout(2000);for(let i=0;i<12&&!(await p.getByText(desc).count());i++){await p.mouse.move(200,500);await p.mouse.wheel(0,600);await p.waitForTimeout(400)}
await p.getByText(desc).first().waitFor({timeout:15000});console.log('TIME LISTED');
await shot('08-time');

// ---------- Attorney work on mobile (v2) ----------
const back=async()=>{await p.getByRole('button',{name:'Back'}).first().click();await p.waitForTimeout(800)};
const btn=(n)=>chip(n);
const toast=async(t)=>{await p.getByText(t).first().waitFor({timeout:20000})};
const openMatter=async(q,title)=>{
  await p.getByRole('tab',{name:/Matters/}).click();await p.waitForTimeout(1200);
  await box('Search matters, clients, parties').click();await p.keyboard.press('Control+A');await p.keyboard.type(q);await p.waitForTimeout(600);
  await p.getByText(title).first().click();await p.getByText('Parties').first().waitFor({timeout:15000});
};
// 0. Trust deposit for the client so trust-to-fee has money on every run
await p.getByRole('tab',{name:/More/}).click();await p.waitForTimeout(1200);
await p.getByText(/Client ledgers, deposits/).first().click();await p.getByText('Client ledgers').first().waitFor({timeout:15000});
await btn('Record trust deposit').first().click();await p.getByText(/never mixed/).waitFor({timeout:10000});
await p.getByRole('button',{name:/^Client/}).first().click();await p.waitForTimeout(1000);await p.getByRole('menuitem',{name:'Sarah Williams'}).click();await p.waitForTimeout(600);
await box('Amount').click();await p.waitForTimeout(400);await p.keyboard.type('500');
await box('Memo').click();await p.waitForTimeout(400);await p.keyboard.type('Mobile E2E deposit');await shot('18a-trust-deposit');
await btn('Record deposit').click();await toast('Trust deposit recorded');ok('trust deposit');
await back();
// 1. Generate invoice from unbilled time and expenses, record a payment, trust to fees
await openMatter('johnson',/Johnson v\. Corp Inc\./);
await shot('18-matter-actions');
// fresh unbilled work so the run repeats cleanly
await chip('Log time').first().click();await p.getByText('Save time entry').waitFor();
await box('Time').click();await p.keyboard.press('Control+A');await p.keyboard.type('0:30');
await box('Work done').click();await p.waitForTimeout(400);await p.keyboard.type('Mobile E2E consultation '+Date.now());
await p.getByRole('button',{name:'Save time entry'}).click();await p.getByText('Time entry saved').first().waitFor({timeout:15000});await p.waitForTimeout(1500);
await btn('Invoices').first().click();await p.getByText('Client invoices').first().waitFor({timeout:15000});await shot('19-invoices');
await btn('Generate invoice').first().click();await p.getByText(/Picks up unbilled/).waitFor({timeout:15000});
await p.getByText(/Unbilled: /).waitFor({timeout:15000});await shot('20-generate-invoice');
await btn('Generate invoice').last().click();
await p.getByText(/^Invoice INV-\d+ generated$/).first().waitFor({timeout:20000});ok('generate invoice from unbilled time and expenses');
await p.getByText('Line items').waitFor({timeout:15000});await shot('21-invoice-detail');
await btn('Mark as sent').click();await toast('Invoice marked as sent');
await btn('Record payment').first().click();await p.getByText(/has .* owing/).waitFor();
await box('Amount').click();await p.keyboard.press('Control+A');await p.keyboard.type('100');
await box('Reference (optional)').click();await p.waitForTimeout(400);await p.keyboard.type('E2E EFT');await shot('22-record-payment');
await btn('Record payment').last().click();await toast('Payment recorded');ok('record payment');
await p.waitForTimeout(1200);
await btn('Pay from trust').click();await p.getByText(/Only this client/).waitFor();await shot('23-trust-to-fee');
await btn('Transfer to fees').click();await toast('Trust transferred to fees');ok('trust to fee transfer');
await p.waitForTimeout(1500);await shot('24-invoice-paid');
await back();await back();
// 2. Upload a document to the matter
const fname='e2e-upload-'+Date.now()+'.txt';
await btn('Upload document').click();await p.getByText('Choose a file').waitFor();
const [fc]=await Promise.all([p.waitForEvent('filechooser',{timeout:15000}),p.getByText('Choose a file').click()]);
await fc.setFiles({name:fname,mimeType:'text/plain',buffer:Buffer.from('Uploaded from the Owlaw mobile E2E')});
await p.getByText('Upload document').last().waitFor({timeout:15000});await shot('25-upload-document');
await btn('Upload').last().click();await toast('Document uploaded');
await p.mouse.move(200,500);for(let i=0;i<6;i++){await p.mouse.wheel(0,1500);await p.waitForTimeout(300)}await p.getByText(fname).first().waitFor({timeout:15000});ok('upload document to matter');await shot('26-matter-documents');
await back();
// 3. New matter with a conflict check first
await p.getByRole('tab',{name:/Matters/}).click();await p.waitForTimeout(1200);
await btn('New matter').click();await p.getByText('Run conflict check').waitFor({timeout:15000});
await p.getByRole('button',{name:'Client',exact:true}).click();await p.waitForTimeout(1000);await p.getByRole('menuitem',{name:'ABC Corp'}).click();await p.waitForTimeout(600);
await box('Opposing party').click();await p.waitForTimeout(400);await p.keyboard.type('Metro Transit Authority');
const mtitle='ABC Corp v Metro E2E '+Date.now();
await box('Matter title').click();await p.waitForTimeout(400);await p.keyboard.type(mtitle);
await btn('Open matter').click();await p.getByText(/Run the conflict check for this client/).waitFor({timeout:10000});
await btn('Run conflict check').click();await p.getByText(/possible conflict|No conflicts found/).or(p.getByLabel(/possible conflict|No conflicts found/)).first().waitFor({timeout:15000});await shot('27-conflict-check');
if(await p.getByLabel(/possible conflict/).or(p.getByText(/possible conflict/)).count()){await p.getByRole('checkbox',{name:/I have reviewed/}).click();}
await btn('Open matter').click();await p.getByText(/^Matter C-\d+ opened$/).first().waitFor({timeout:20000});
await p.getByText(mtitle).first().waitFor({timeout:15000});ok('create matter with conflict check');await shot('28-new-matter');
await back();
// More screen and the extra pages
await p.getByRole('tab',{name:/More/}).click();await p.waitForTimeout(1200);
await p.getByText(/Client ledgers, deposits/).first().click();await p.getByText('Client ledgers').first().waitFor({timeout:15000});await shot('29-trust');await back();
await p.getByText('Draft help').click();await p.getByText(/Ask about deadlines/).waitFor({timeout:15000});await shot('30-draft-help');await back();
await p.getByText('E-sign').click();await p.waitForTimeout(2500);await shot('31-esign');await back();
await p.getByRole('tab',{name:/Time/}).click();await p.waitForTimeout(800);
await p.getByRole('tab',{name:/Diary/}).click();await p.getByRole('tab',{name:'Deadlines'}).waitFor({timeout:15000});await p.waitForTimeout(1500);await shot('09-diary-deadlines');
await p.getByRole('tab',{name:'Events'}).click();await shot('10-diary-events');
await p.getByRole('tab',{name:/More/}).click();await shot('11-more');
await p.getByText('Clients and contacts').click();await p.getByText('ABC Corp').first().waitFor({timeout:15000});await shot('12-clients');
await p.getByRole('tab',{name:'Contacts'}).click();await shot('13-contacts');
await p.getByRole('button',{name:'Back'}).click();
await p.getByText('Calls').first().click();await p.getByText('Recent calls').first().waitFor({timeout:15000});await shot('14-calls');
await p.getByRole('button',{name:'Back'}).click();
await p.getByText('Notifications').first().click();await p.waitForTimeout(2500);await shot('15-notifications');
await p.getByRole('button',{name:'Back'}).click();
await p.getByText('Profile').first().click();await p.waitForTimeout(2500);await shot('16-profile');
await p.getByRole('button',{name:'Sign out'}).click();await p.getByRole('button',{name:'Sign in'}).waitFor({timeout:15000});console.log('LOGOUT OK');await shot('17-signed-out');
console.log('DESC',desc);writeFileSync(O+'results.json',JSON.stringify({pass:results},null,1));console.log('ATTORNEY FLOWS',results.length+'/6');await b.close();
