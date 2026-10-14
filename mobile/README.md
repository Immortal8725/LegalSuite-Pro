# Owlaw attorney app (Flutter)

The Owlaw mobile app for attorneys and staff. It talks to the same Spring Boot API as the web app and adds no new endpoints.

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
| Notifications | List and mark as read | `GET /api/v1/notifications`, `PATCH /notifications/{id}/read` |
| Profile | Your details, firm, server, sign out | `GET /api/v1/auth/me` |

Every list shows a spinner while loading, a readable error with **Try again**, an empty message, and supports pull to refresh. Tokens are kept in the platform keystore (`flutter_secure_storage`). An expired access token is refreshed once; if that fails the app clears the session and returns to sign-in.

## Server address

Build-time default:

```bash
flutter run --dart-define=API_BASE_URL=https://api.owlaw.co.za
```

Without it the default is `http://10.0.2.2:18081` (the host machine from an Android emulator). The attorney can change it on the sign-in screen; it is remembered on the device.

For Flutter web, add the web origin to `CORS_ALLOWED_ORIGINS` on the API.

## Develop

Flutter 3.24 or newer (built and tested with 3.35.6).

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
flutter build apk --release --dart-define=API_BASE_URL=https://api.owlaw.co.za
flutter build appbundle --release --dart-define=API_BASE_URL=https://api.owlaw.co.za   # Play Store
flutter build web --release --dart-define=API_BASE_URL=https://api.owlaw.co.za
flutter build ipa --dart-define=API_BASE_URL=https://api.owlaw.co.za                  # needs a Mac with Xcode
```

The release APK is signed with the debug key for now. Before the Play Store, create an upload keystore and set `signingConfigs` in `android/app/build.gradle.kts`.

`android:usesCleartextTraffic="true"` is on so a local `http://` API works during testing. Turn it off for the store build once the API is on HTTPS.

Icons and splash come from `assets/` (`dart run flutter_launcher_icons`, `dart run flutter_native_splash:create`).

## Browser test

`tool/e2e-web.mjs` drives the web build with Playwright: sign in, matters, search, matter detail, log 1:15 against a matter, check it on the Time tab, every other screen, sign out. It saves screenshots to `/workspace/owlaw-mobile-shots/`. Serve `build/web` on port 43300 and run the API on 18081 with that origin allowed.

## Not yet

- In-app calling (WebRTC or CallKit). Calls use the firm callback bridge.
- Push notifications (needs Firebase and APNs keys).
- Document download and upload from the phone.
- Offline cache.
