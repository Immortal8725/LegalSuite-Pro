# LegalSuite Pro — Flutter client (Phase 6)

Attorney mobile slice against the same Spring Boot API as the web app.

## What it does

- Sign in with firm slug + email + password
- Dashboard counts
- Matter list
- Time entries
- Start a public-network call (Calls tab)

The Calls tab posts to `/api/v1/calls/pstn`. Twilio rings the phone you enter, then connects the other party and shows the automatic caller ID: a verified personal number, `TWILIO_VOICE_FROM`, or a number already on the Twilio account. Buying a local number is optional. Choose a matter, or mark the call as not on a matter. Emergency numbers are refused so they stay on the device dialer.

In-app WebRTC, e-sign, and AI stay on the responsive web app (installable as a PWA).

## CallKit follow-up

This client does not register CallKit (iOS) or ConnectionService (Android). The cellular dialer is the ringing UI. A later native project can add an in-app incoming call screen, which needs a VoIP push entitlement and a push provider. The bridge itself stays on the server.

## Run

Flutter SDK is not bundled with this repo. Install Flutter 3.24+, then:

```bash
cd mobile
flutter pub get
# iOS / Android / Chrome
flutter run
```

If platform folders are missing (this tree ships `lib/` only so the API client stays readable):

```bash
cd mobile
flutter create . --project-name legalsuite_mobile
flutter pub get
flutter run
```

Point `LegalSuiteApi.baseUrl` in `lib/api.dart` at your API host (default `http://127.0.0.1:18081`). Android emulator uses `http://10.0.2.2:18081`.

Demo: `smith-associates` / `john@smithlaw.com` / `password`
