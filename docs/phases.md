# Phases

| Phase | Status | Surface |
| --- | --- | --- |
| 1 Foundation | Shipped | Register, login, JWT, tenant, shell, onboarding |
| 2 Landing + practice | Shipped | `/firm/[slug]`, cases, clients, contacts, documents, calendar, tasks |
| 3 Financial | Shipped | Time, invoices, payments, write-offs, trust-to-fee, proof of payment, trust, expenses |
| 4 Communication | Shipped | Messages, WebRTC voice, leads |
| 5 Advanced | Shipped | Conflicts, reports, modules, team, settings, search |
| 6 Mobile | Shipped | Responsive web + PWA; Flutter in `mobile/` |
| 7 Assistant and integrations | Shipped | `/ai`, `/templates`, `/esign`, `/sign/[id]`, `/integrations`, `/audit` |
| 8 Polish | Shipped | README, ERD, class diagrams, tests, PWA |
| Unique loop | Shipped | Hire motion, Texas clocks, signed conflict waiver, limited file until engagement |

Phase 6's native binary is not produced in this environment (Flutter SDK is not installed). `mobile/` is a real Dart client; run `flutter create .` then `flutter run`. The installable web app is the supported preview.
