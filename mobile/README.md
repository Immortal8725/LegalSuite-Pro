# Owlaw attorney app (Flutter)

The Owlaw mobile app for attorneys and staff. Attorneys can do all their day-to-day work on the phone. Firm administration (team, the public website, the Owlaw subscription and firm settings) stays in the web app. It talks to the same Spring Boot API as the web app. The only API addition is for in-app calling (see below).

Package `owlaw_mobile`, application ID `za.co.owlaw.owlaw_mobile`, display name **Owlaw**, OwLaw wordmark in navy and gold.

## What it does

| Area | Screen | API used |
|---|---|---|
| Sign-in | Firm ID, email, password. Asks for the authenticator code when the firm requires it. Server address under **Server settings**. | `POST /api/v1/auth/login`, `/auth/refresh`, `/auth/logout`, `GET /auth/me` |
| Home | Active matters, outstanding fees, hours, unread alerts, the five most urgent clocks, coming events | `GET /api/v1/dashboard` |
| Matters | List with search (number, title, client, opposing party, area, status) | `GET /api/v1/cases` |
| Matter | Parties, docket clocks, notes (add a note), documents, log time, call or email the client, call through the firm | `GET /api/v1/cases/{id}`, `POST /cases/{id}/notes`, `GET /documents` |
| Time | Live timer (start on a matter, stop with a description), manual entry (45, 1:30 or 1.5h), entry list | `GET/POST /api/v1/time-entries`, `/timers/start`, `/timers/active`, `/timers/stop` |
| Diary | Deadlines from the docket, soonest first, and diary events | `GET /api/v1/docket`, `GET /api/v1/events` |
| Clients and contacts | Tap to call (`tel:`) or email (`mailto:`) | `GET /api/v1/clients`, `GET /api/v1/contacts` |
| Calls | Firm callback call: your phone rings first, then the other party sees the firm caller ID. Recent calls. | `GET /api/v1/voice/pstn/readiness`, `POST /api/v1/calls/pstn`, `GET /api/v1/calls` |
| Client invoices | Owing, draft, paid and all filters. Generate an invoice from unbilled time and expenses (shows what will be picked up first), mark as sent, record a payment, write off with a reason, pay from trust, upload a proof of payment from the camera or files, review proofs (open, accept, reject), share the invoice as text. Add an expense. | `GET /api/v1/invoices`, `/invoices/{id}`, `POST /invoices/generate`, `/invoices/{id}/send`, `/payments`, `/write-off`, `/apply-trust`, `/proofs`, `GET /payment-proofs`, `POST /payment-proofs/{id}/accept`, `/reject`, `GET /payment-proofs/{id}/file`, `GET/POST /expenses` |
| Trust | Accounts with recon status, client ledgers, account ledger, record a deposit on one client's ledger, trust to fees from the invoice. The API enforces the section 86 rules (same client, enough on the ledger, invoice sent, limited files not posted). | `GET /api/v1/trust/accounts`, `/trust/accounts/{id}/ledger`, `POST /trust/move`, `POST /invoices/{id}/apply-trust` |
| Documents | Search, open (downloads and opens in the phone's viewer), upload from camera, photos or files, on a matter or firm-wide | `GET /api/v1/documents`, `GET /documents/{id}/download`, `POST /documents/upload` |
| E-sign | Send for signature (pre-fills the client), share the signing link, void, status | `GET/POST /api/v1/signatures`, `POST /signatures/{id}/void` |
| New and edit matter | Conflict check on the client and opposing party is required before a new matter opens. Possible conflicts are listed and must be acknowledged. Add a client inline. | `POST /api/v1/conflicts/check`, `POST/PUT /api/v1/cases`, `POST/PUT /api/v1/clients` |
| Tasks | Per matter or all, tick to complete, add | `GET/POST /api/v1/tasks`, `PATCH /tasks/{id}/status` |
| Deadlines and events | Add from a matter or the Diary tab | `POST /api/v1/events` |
| Draft help | Ask about a matter, draft a status, engagement cover or demand email, summarise the matter. Copy or share the result. | `POST /api/v1/ai/chat`, `/ai/draft-email`, `/ai/summarize` |
| Message client | SMS, WhatsApp or email from a matter through the firm's Twilio and SMTP set-up, with the sent history for the matter. Shows the readiness hint when a channel is not set up. | `GET /api/v1/outbound/readiness`, `GET /outbound?caseId=`, `POST /outbound/{sms,whatsapp,email}` |
| In-app call | Call the client from the matter inside the app, firm number as caller ID, logged to the matter. Mute, speaker, hang up, call timer. Falls back to the callback call. | `POST /api/v1/calls/app`, `GET /api/v1/voice/app/token`, Twilio fetches `POST /api/v1/voice/twilio/app-dial` |
| Notifications | List and mark as read | `GET /api/v1/notifications`, `PATCH /notifications/{id}/read` |
| Profile | Your details, firm, server, sign out | `GET /api/v1/auth/me` |

Navigation: the bottom bar stays Home, Matters, Time, Diary, More. Matter-specific work sits on the matter (Log time, notes, contact, then a **Work on this matter** group). Firm-wide pages sit under More.

**Visibility.** Like the web app, a page shows only when its module is enabled for the firm (`GET /api/v1/tenants/{id}/modules`). Client portal users cannot change invoices, trust or send firm messages (the API refuses them too). If the module list cannot be loaded the app shows the page and lets the API decide.

Every list shows a spinner while loading, a readable error with **Try again**, an empty message, and supports pull to refresh. Tokens are kept in the platform keystore (`flutter_secure_storage`). An expired access token is refreshed once; if that fails the app clears the session and returns to sign-in.

## Server address

Build-time default:

```bash
flutter run --dart-define=API_BASE_URL=https://owlaw-api.onrender.com
```

Debug and profile builds default to `http://10.0.2.2:18081` (the host machine from an Android emulator). Release builds default to the live API, `https://owlaw-api.onrender.com`; pass `--dart-define=API_BASE_URL=https://...` to point at another host. The Gradle build stops if that value is not `https://`. The attorney can change the address on the sign-in screen; it is remembered on the device. A release build refuses any address that is not `https://`.

For Flutter web, add the web origin to `CORS_ALLOWED_ORIGINS` on the API.

## Develop

Flutter 3.24 or newer (built and tested with 3.47.7). Android minimum SDK 26 (Twilio Voice).

```bash
cd mobile
flutter pub get
flutter analyze
flutter test
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:18081
```

Demo sign-in on a local API with demo data: firm `smith-associates`, `john@smithlaw.com`, `password`, or firm `ndlovu-partners`, `thabo@ndlovulaw.co.za`, `password`.

## Build

```bash
flutter build apk --release --dart-define=API_BASE_URL=https://owlaw-api.onrender.com
flutter build appbundle --release --dart-define=API_BASE_URL=https://owlaw-api.onrender.com   # Play Store
flutter build web --release --dart-define=API_BASE_URL=https://owlaw-api.onrender.com
flutter build ipa --dart-define=API_BASE_URL=https://owlaw-api.onrender.com                  # needs a Mac with Xcode
```

### Release signing

A release build will not run without a signing key, so a store build can never go out with the debug key.

1. Create the upload keystore once and keep it (and its passwords) in your password manager, never in git:

   ```bash
   keytool -genkeypair -v -keystore ~/owlaw-upload.jks -storetype JKS -keyalg RSA -keysize 4096 \
     -validity 10000 -alias owlaw-upload
   ```

2. Either create `mobile/android/key.properties` (already in `.gitignore`):

   ```properties
   storeFile=/home/you/owlaw-upload.jks
   storePassword=...
   keyAlias=owlaw-upload
   keyPassword=...
   ```

   or set `OWLAW_KEYSTORE_PATH`, `OWLAW_KEYSTORE_PASSWORD`, `OWLAW_KEY_ALIAS` and `OWLAW_KEY_PASSWORD` in the build environment (CI secrets).
3. Enrol in Play App Signing and upload the `.aab`. Google keeps the app signing key; this file is only the upload key.

Check a built APK: `apksigner verify --print-certs app-release.apk` must not show `CN=Android Debug`.

### HTTPS only in release

The release manifest uses `res/xml/network_security_config.xml`, which refuses plain HTTP. Debug and profile builds replace that file with one that allows HTTP to `10.0.2.2`, `localhost` and `127.0.0.1` only, for a local API.

Icons and splash come from `assets/` (`dart run flutter_launcher_icons`, `dart run flutter_native_splash:create`).

## In-app calling (Twilio Voice)

The app uses `twilio_voice` (native Twilio Voice SDK on Android and iOS, `ConnectionService` on Android). The web build does not place in-app calls and offers the callback call instead.

How a call works:

1. The app posts `POST /api/v1/calls/app` with the number and the matter. The API applies the same rules as the callback call (a matter or an explicit non-matter call, no emergency numbers, recording only with consent), logs the call to the matter, and returns a short-lived Voice access token and a one-time bridge token.
2. The app places the call with the Twilio SDK, passing the bridge token.
3. Twilio fetches the TwiML App Voice URL `POST /api/v1/voice/twilio/app-dial`. The API checks the Twilio signature and that the caller identity is the attorney who created the call, then dials the client with the firm number as caller ID. The call closes through the existing dial-result callback, so duration and cost land on the matter.

If the server is not set up, or the phone refuses, the call screen shows the reason and **Use callback call instead**.

Server set-up (API environment):

| Variable | What |
|---|---|
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PUBLIC_BASE_URL` | Already used by the callback call |
| `TWILIO_API_KEY_SID`, `TWILIO_API_KEY_SECRET` | A Twilio API key (Console, Account, API keys). Signs the access token. |
| `TWILIO_TWIML_APP_SID` | A TwiML App whose Voice request URL is `https://<api>/api/v1/voice/twilio/app-dial` (HTTP POST) |
| `TWILIO_PUSH_CREDENTIAL_SID` | Optional. Only for incoming in-app calls. |

Build flags: `--dart-define=OWLAW_IN_APP_CALLS=false` hides in-app calling. `--dart-define=OWLAW_INCOMING_CALLS=true` registers the phone for incoming in-app calls.

**Incoming calls are behind that flag and not live yet.** Twilio rings a closed app through push only:

- Android: a Firebase project, `android/app/google-services.json`, the Google Services Gradle plugin, and `firebase_messaging` in the app to pass the FCM token to `registerForIncoming(deviceToken: ...)`. Upload the FCM server key or service account to Twilio as a Push Credential and set `TWILIO_PUSH_CREDENTIAL_SID`.
- iOS: a VoIP Services certificate (APNs) uploaded to Twilio as a Push Credential, Push Notifications and Background Modes (Voice over IP) enabled in Xcode.
- The firm number's inbound TwiML would then need to `<Dial><Client>` the attorney's identity (`owlaw_<user id with underscores>`). The inbound route still forwards to the firm's phones today.

Live test steps (needs a Twilio account):

1. Set the variables above on the API and restart it. `GET /api/v1/voice/app/token` should return a token.
2. Rent or verify a firm number on the web Voice page so there is an active caller ID.
3. Install the release APK on an Android phone (not an emulator for audio), sign in, open a matter with a client phone number, tap **Call in app**.
4. Allow the microphone and phone-account prompts. The client phone should ring showing the firm number. Try mute, speaker, hang up.
5. Check the call on the web Voice page and the matter: duration, cost per minute and the matter link.

Unit tests cover the token (signature, grants, identity), the app-dial ownership check and TwiML, and the call screen with a fake engine. The call itself has not been tested live.

## Browser test

`tool/e2e-web.mjs` drives the web build with Playwright: sign in, matters, search, matter detail, log time, Time tab, then the attorney flows: trust deposit, log time on a matter, generate an invoice from unbilled work, mark as sent, record a payment, trust to fees, upload a document to the matter (file chooser), open a new matter with the conflict check and acknowledgement, the Trust, Draft help and E-sign pages, every other screen, sign out. Screenshots go to `/workspace/owlaw-mobile-shots/v2/` (set `OUT` to change) with `results.json`. Serve `build/web` on port 43300 and run the API on 18081 with that origin allowed in `CORS_ALLOWED_ORIGINS`.

## Not yet

- Incoming in-app calls (needs FCM and APNs keys, see above). Outbound in-app calls are built but untested live.
- Push notifications for alerts (same Firebase and APNs set-up).
- Invoice PDF: the API has no PDF endpoint, so Share sends a text summary.
- E-sign links use `--dart-define=WEB_BASE_URL` (the web app address). Without it the API address is used.
- Offline cache.
