# LegalSuite Pro Flutter client (Phase 6)

Attorney mobile slice against the same Spring Boot API as the web app.

## What it does

- Sign in with firm slug + email + password
- Dashboard counts
- Matter list
- Time entries

Voice calling, e-sign, and draft help stay on the responsive web app (installable as a PWA). This client is the native docket.

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
