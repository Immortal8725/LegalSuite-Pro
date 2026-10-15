# End-to-end and probe scripts

Browser and API checks used for the A to Z production-readiness runs. They drive a running stack; nothing here starts servers.

| Script | What it covers | Needs |
|---|---|---|
| `e2e-a2z.mjs` | The whole web app: auth and throttling, every staff page, roles, operator approvals, client portal and portal-token scope, tenant isolation, PayFast seat checkout and signed ITN, invite email through Mailpit, security headers, 390 px mobile sweep, public firm sites | web, API with demo data, Mailpit, `e2e-api.env` |
| `e2e-doc-templates.mjs` | Letterhead logo, all 9 system templates generated and previewed from a matter, axe on the template screens | web, API with demo data |
| `e2e-axe-perf.mjs` | axe (WCAG 2.1 A and AA) and horizontal overflow on public, legal, staff and portal pages at 1366 px and 390 px, plus load timings. Exit code 1 when any page has a violation | web, API with demo data |
| `../../mobile/tool/e2e-web.mjs` | Flutter web build of the attorney app: sign-in, matters, time, trust deposit, invoice, payment, trust to fees, upload, new matter with conflict check | Flutter web build, API |
| `probes/register_plans.py` | Register on Solo, Firm and Practice, signed PayFast checkout with staff seats, ITN, replay and tampered amount | API with `PAYFAST_SANDBOX_LOCAL=true` |
| `probes/itn_tamper.py` | ITN with a wrong amount or signature never activates seats | same |
| `probes/security.py` | Second-firm template generation, cross-tenant documents, templates and logos, upload validation (types, names, size, malformed), CORS, headers, error bodies, JWT tampering | API with demo data |
| `probes/twofa_roles.py` | Authenticator enrolment, sign-in, replay guard, attorney and associate blocked from firm admin endpoints | API with demo data |

## Running

Use your own ports so you do not collide with another stack.

```bash
# API (H2, demo data, local PayFast sandbox answers, Mailpit on 1025)
cd backend && ./mvnw -q -DskipTests package
set -a; . scripts/e2e/e2e-api.env; set +a     # copy from e2e-api.env.example
LEGALSUITE_DEMO_DATA=true PORT=18291 java -jar target/owlaw-api-1.0.0.jar

# Web against that API
cd frontend && NEXT_PUBLIC_API_URL=http://127.0.0.1:18291 npm run build && npx next start -p 43291 -H 127.0.0.1

# Browser scripts (Playwright and axe-core installed in the folder you run from)
npm i playwright axe-core && npx playwright install chromium
WEB=http://127.0.0.1:43291 API=http://127.0.0.1:18291 API_ENV=scripts/e2e/e2e-api.env node scripts/e2e/e2e-a2z.mjs
WEB=http://127.0.0.1:43291 AXE=node_modules/axe-core/axe.min.js node scripts/e2e/e2e-doc-templates.mjs
WEB=http://127.0.0.1:43291 AXE=node_modules/axe-core/axe.min.js node scripts/e2e/e2e-axe-perf.mjs

# API probes (Python 3, standard library only)
cd scripts/e2e/probes && API=http://127.0.0.1:18291 API_ENV=../e2e-api.env python3 security.py
```

Demo sign-ins: firm `ndlovu-partners`, `thabo@ndlovulaw.co.za` / `password` (owner); portal `nomsa@example.com` / `portal123`; firm `smith-associates`, `john@smithlaw.com` / `password`.

Each run creates new firms and records with a run id, so scripts can be repeated against the same API. Restarting the H2 API resets everything.
