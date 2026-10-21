// Browser e2e for Find a Lawyer against a demo-data stack. Needs playwright-core and Chrome. BASE=http://localhost:3000 node e2e/find-a-lawyer.e2e.mjs
import { chromium } from "playwright-core";
const BASE = process.env.BASE || "http://localhost:3311";
const OUT = process.env.OUT || "/workspace/owlaw-fal/shots-local";
const api = async (path, opts = {}, token) => {
  const r = await fetch(BASE + path, { ...opts, headers: { "Content-Type": "application/json", ...(token ? { Authorization: "Bearer " + token } : {}) } });
  const j = await r.json(); if (!r.ok) throw new Error(path + " " + r.status + " " + j.message); return j.data;
};
const login = async (firmSlug, email) => (await api("/api/v1/auth/login", { method: "POST", body: JSON.stringify({ firmSlug, email, password: "password" }) })).accessToken;
const expect = (c, m) => { if (!c) throw new Error("FAIL: " + m); console.log("ok:", m); };

// Seed: list Ndlovu & Partners and verify a practitioner.
const thabo = await login("ndlovu-partners", "thabo@ndlovulaw.co.za");
await api("/api/v1/directory/profile", { method: "PUT", body: JSON.stringify({ practiceAreas: ["raf-claims", "divorce-and-family"], cities: ["sandton", "johannesburg"], languages: ["english", "isizulu"], consultOptions: ["free-first", "online"], responseTime: "one-day", bio: "Road accident and family matters in Gauteng.", listed: true }) }, thabo);
let prof = await api("/api/v1/directory/practitioners", { method: "POST", body: JSON.stringify({ fullName: "Thabo Ndlovu", lpcNumber: "LPC GP 44821", fidelityExpiresOn: "2027-12-31" }) }, thabo);
const ops = await login("legalsuite", "ops@legalsuite.pro");
for (const p of prof.practitioners.filter((x) => x.status === "pending"))
  await api(`/api/v1/platform/directory/practitioners/${p.id}/verify`, { method: "POST", body: JSON.stringify({ decision: "verified", note: "Checked on the LPC register" }) }, ops);

const browser = await chromium.launch({ executablePath: process.env.CHROME || "/opt/google/chrome/chrome", args: ["--no-sandbox"] });
const page = await browser.newPage({ viewport: { width: 1360, height: 900 } });
await page.goto(BASE + "/find-a-lawyer");
await page.getByTestId("result-count").waitFor();
await page.screenshot({ path: `${OUT}/01-search.png`, fullPage: true });
await page.selectOption("#area", "raf-claims");
await page.selectOption("#province", "gauteng");
await page.selectOption("#city", "sandton");
await page.selectOption("#language", "isizulu");
await page.getByRole("button", { name: "Search" }).click();
await page.waitForURL(/area=raf-claims/);
await page.getByTestId("firm-card").first().waitFor();
expect((await page.getByTestId("firm-card").allInnerTexts()).join().includes("Ndlovu"), "filtered search finds Ndlovu");
await page.screenshot({ path: `${OUT}/02-search-filtered.png`, fullPage: true });
await page.goto(BASE + "/find-a-lawyer?area=tax&province=western-cape");
await page.getByTestId("result-count").waitFor();
expect(!(await page.content()).includes("Ndlovu &amp; Partners</a>"), "non-matching filter hides Ndlovu");
await page.goto(BASE + "/find-a-lawyer/ndlovu-partners");
await page.getByTestId("practitioners").waitFor();
expect((await page.getByTestId("practitioners").innerText()).includes("LPC GP 44821"), "firm page shows LPC number");
await page.screenshot({ path: `${OUT}/03-firm-page.png`, fullPage: true });
await page.fill("#name", "Zanele E2E");
await page.fill("#email", "zanele.e2e@example.com");
await page.fill("#phone", "082 555 0101");
await page.selectOption("#practiceArea", "raf-claims");
await page.fill("#opposingParty", "Nomsa Khumalo");
await page.fill("#message", "I was hurt in a taxi crash in May and need advice.");
expect(await page.getByRole("button", { name: "Send enquiry" }).isDisabled(), "send disabled until consent ticked");
await page.getByTestId("consent").check();
await page.getByRole("button", { name: "Send enquiry" }).click();
await page.getByTestId("enquiry-done").waitFor();
await page.screenshot({ path: `${OUT}/04-enquiry-sent.png`, fullPage: true });

// Firm sees it in intake.
const leads = await api("/api/v1/intake/leads", {}, thabo);
const lead = leads.find((l) => l.name === "Zanele E2E");
expect(!!lead, "enquiry is a lead in firm intake");
const enq = await api(`/api/v1/directory/enquiries/${lead.id}`, {}, thabo);
expect(enq.conflictStatus === "potential_conflict" && enq.consentVersion, "conflict check ran and consent recorded");

const signIn = async (slug, email) => {
  const ctx = await browser.newContext({ viewport: { width: 1360, height: 900 } });
  const p = await ctx.newPage();
  await p.goto(BASE + "/login");
  await p.getByLabel("Firm ID").fill(slug);
  await p.getByLabel("Email").fill(email);
  await p.getByLabel("Password").fill("password");
  await p.getByRole("button", { name: "Sign in" }).click();
  await p.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 30000 });
  return p;
};
const fp = await signIn("ndlovu-partners", "thabo@ndlovulaw.co.za");
await fp.goto(BASE + "/leads"); await fp.waitForTimeout(2500);
await fp.screenshot({ path: `${OUT}/05-firm-intake.png`, fullPage: true });
await fp.goto(BASE + "/directory-listing"); await fp.getByText("LPC-verified", { exact: false }).first().waitFor().catch(() => {}); await fp.waitForTimeout(1500);
await fp.screenshot({ path: `${OUT}/06-firm-listing-settings.png`, fullPage: true });

const op = await signIn("legalsuite", "ops@legalsuite.pro");
await op.goto(BASE + "/platform/directory/listings");
await op.getByTestId("listing-row").first().waitFor();
await op.screenshot({ path: `${OUT}/07-operator-listings.png`, fullPage: true });
const row = op.getByTestId("listing-row").filter({ hasText: "ndlovu-partners" });
await row.getByPlaceholder("Reason the firm will see").fill("E2E test: hidden then restored");
await row.getByRole("button", { name: "Hide listing" }).click();
await row.getByRole("button", { name: "Restore listing" }).waitFor();
await op.screenshot({ path: `${OUT}/08-operator-hidden.png`, fullPage: true });
const r404 = await fetch(BASE + "/api/v1/public/directory/firms/ndlovu-partners");
expect(r404.status === 404, "hidden firm page returns 404");
await row.getByRole("button", { name: "Restore listing" }).click();
await row.getByRole("button", { name: "Hide listing" }).waitFor();
expect((await fetch(BASE + "/api/v1/public/directory/firms/ndlovu-partners")).status === 200, "restored firm page is back");
await op.goto(BASE + "/platform/directory?x=1"); await op.waitForTimeout(1500);
await op.screenshot({ path: `${OUT}/09-operator-lpc-verification.png`, fullPage: true });
await browser.close();
console.log("E2E PASS");
