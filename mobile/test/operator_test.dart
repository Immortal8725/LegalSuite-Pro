import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:owlaw_mobile/api/api_client.dart';
import 'package:owlaw_mobile/api/token_store.dart';
import 'package:owlaw_mobile/main.dart';
import 'package:owlaw_mobile/screens/operator.dart';
import 'package:shared_preferences/shared_preferences.dart';

final firms = [
  {'id': 'f1', 'firmName': 'Ndlovu & Partners', 'slug': 'ndlovu-partners', 'email': 'info@ndlovulaw.co.za', 'planSlug': 'firm', 'planName': 'Firm', 'status': 'trial', 'seats': 4, 'createdAt': '2026-10-01T08:00:00Z', 'trialEndsAt': '2026-10-31T08:00:00Z', 'lastActivityAt': '2026-10-21T08:00:00Z'},
  {'id': 'f2', 'firmName': 'Mokoena Attorneys', 'slug': 'mokoena', 'email': 'hello@mokoena.co.za', 'planSlug': 'solo', 'planName': 'Solo', 'status': 'active', 'seats': 1, 'createdAt': '2026-09-01T08:00:00Z'},
];

class Fake {
  bool totpEnabled;
  final calls = <String>[];
  final bodies = <String, Map>{};
  Fake({this.totpEnabled = true});

  ApiClient api() {
    final store = MemoryTokenStore();
    store.values[ApiClient.accessKey] = 'A';
    store.values[ApiClient.roleKey] = 'superadmin';
    return ApiClient(
      baseUrl: 'http://api.test',
      store: store,
      client: MockClient((req) async {
        final path = req.url.path;
        calls.add('${req.method} $path${req.url.hasQuery ? '?${req.url.query}' : ''}');
        if (req.body.isNotEmpty) bodies[path] = jsonDecode(req.body) as Map;
        Object? data;
        switch (path) {
          case '/api/v1/auth/me':
            data = {'user': {'role': 'superadmin', 'totpEnabled': totpEnabled, 'mfaRequired': true}, 'tenant': {}};
          case '/api/v1/auth/totp/start':
            data = {'secret': 'JBSWY3DPEHPK3PXP', 'otpauthUrl': 'otpauth://totp/Owlaw:ops?secret=JBSWY3DPEHPK3PXP&issuer=Owlaw', 'totpEnabled': false};
          case '/api/v1/auth/totp/confirm':
            totpEnabled = true;
            data = {'accessToken': 'A2', 'refreshToken': 'R2', 'totpEnabled': true, 'recoveryCodes': ['AAAA-1111', 'BBBB-2222']};
          case '/api/v1/platform/firms':
            data = firms;
          case '/api/v1/platform/plans':
            data = [{'slug': 'solo', 'name': 'Solo', 'priceMonthly': 899}, {'slug': 'firm', 'name': 'Firm', 'priceMonthly': 1199}];
          case '/api/v1/platform/directory/practitioners':
            data = [{'id': 'p1', 'fullName': 'Thabo Ndlovu', 'lpcNumber': 'LPC 12345', 'firmName': 'Ndlovu & Partners', 'fidelityExpiresOn': '2027-02-28', 'fidelityCurrent': true}];
          case '/api/v1/platform/directory/listings':
            data = [{'tenantId': 'f1', 'firmName': 'Ndlovu & Partners', 'slug': 'ndlovu-partners', 'listed': true, 'operatorHidden': false, 'practiceAreaLabels': ['Wills and estates'], 'cityLabels': ['Pretoria']}];
          case '/api/v1/platform/support':
            data = [{'id': 'g1', 'tenantId': 'f1', 'firmName': 'Ndlovu & Partners', 'reason': 'Invoice totals look wrong', 'grantedByEmail': 'thabo@ndlovulaw.co.za', 'expiresAt': '2026-10-24T08:00:00Z'}];
          case '/api/v1/platform/support/f1/matters':
            data = {'section': 'matters', 'firmName': 'Ndlovu & Partners', 'readOnly': true, 'rows': [{'id': 'c1', 'caseNumber': 'C-2001', 'title': 'Estate late M Dlamini'}]};
          case '/api/v1/platform/billing':
            data = {'currency': 'ZAR', 'totalMrr': 899, 'totalUnpaid': 0, 'payingFirms': 1, 'firms': [
              {'id': 'f2', 'name': 'Mokoena Attorneys', 'status': 'active', 'plan': 'Solo', 'attorneySeats': 1, 'staffSeats': 0, 'mrr': 899, 'listValue': 899, 'unpaidCount': 0, 'unpaidTotal': 0, 'callMinutesThisMonth': 12, 'callChargesThisMonth': 17.88},
            ]};
          case '/api/v1/platform/audit':
            data = [{'id': 'a1', 'createdAt': '2026-10-21T09:00:00Z', 'actorEmail': 'operator@owlaw.co.za', 'action': 'platform.firm.trial_extended', 'label': 'Trial extended', 'firmName': 'Ndlovu & Partners', 'detail': '7 days'}];
          default:
            data = {};
        }
        return http.Response(jsonEncode({'success': true, 'data': data}), 200);
      }),
    );
  }
}

Future<void> pump(WidgetTester t, Widget w) async {
  await t.pumpWidget(MaterialApp(home: Scaffold(body: w)));
  await t.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('en_ZA');
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('a saved operator session opens the operator home, not the firm app', (t) async {
    final f = Fake();
    await t.pumpWidget(OwlawApp(api: f.api(), signedIn: true, operator: true));
    await t.pumpAndSettle();
    expect(find.text('Ndlovu & Partners'), findsOneWidget);
    for (final l in ['Firms', 'Directory', 'Support', 'Billing', 'Audit']) {
      expect(find.text(l), findsWidgets);
    }
  });

  testWidgets('an operator without 2FA must enrol: setup key, QR, code, recovery codes', (t) async {
    t.view.physicalSize = const Size(1080, 3200);
    t.view.devicePixelRatio = 2;
    addTearDown(t.view.reset);
    final f = Fake(totpEnabled: false);
    await t.pumpWidget(OwlawApp(api: f.api(), signedIn: true, operator: true));
    await t.pumpAndSettle();
    expect(find.text('Set up two-factor sign-in'), findsOneWidget);
    expect(f.calls.any((c) => c.contains('/platform/')), isFalse);
    await t.tap(find.byKey(const Key('startTotp')));
    await t.pumpAndSettle();
    expect(find.byKey(const Key('totpQr')), findsOneWidget);
    expect(find.text('JBSWY3DPEHPK3PXP'), findsOneWidget);
    expect(find.byKey(const Key('copySecret')), findsOneWidget);
    await t.enterText(find.byKey(const Key('totpCode')), '123456');
    await t.tap(find.byKey(const Key('confirmTotp')));
    await t.pumpAndSettle();
    expect(f.bodies['/api/v1/auth/totp/confirm'], {'code': '123456'});
    expect(find.textContaining('AAAA-1111'), findsOneWidget);
    await t.ensureVisible(find.byKey(const Key('recoveryDone')));
    await t.tap(find.byKey(const Key('recoveryDone')));
    await t.pumpAndSettle();
    expect(find.text('Mokoena Attorneys'), findsOneWidget);
  });

  testWidgets('firms: search, suspend with a reason, extend trial, change plan', (t) async {
    final f = Fake();
    await pump(t, FirmsScreen(api: f.api()));
    await t.enterText(find.byKey(const Key('firmSearch')), 'moko');
    await t.pump();
    expect(find.text('Ndlovu & Partners'), findsNothing);
    await t.enterText(find.byKey(const Key('firmSearch')), '');
    await t.pump();

    await t.tap(find.byKey(const Key('firm-ndlovu-partners')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('suspend')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('reasonOk')));
    await t.pump();
    expect(find.textContaining('at least 5 characters'), findsOneWidget);
    await t.enterText(find.byKey(const Key('reason')), 'Unpaid for three months');
    await t.tap(find.byKey(const Key('reasonOk')));
    await t.pumpAndSettle();
    expect(f.bodies['/api/v1/platform/firms/f1/suspend'], {'reason': 'Unpaid for three months'});

    await t.tap(find.byKey(const Key('firm-ndlovu-partners')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('extendTrial')));
    await t.pumpAndSettle();
    await t.enterText(find.byKey(const Key('days')), '14');
    await t.enterText(find.byKey(const Key('reason')), 'Needs more time to migrate');
    await t.tap(find.byKey(const Key('reasonOk')));
    await t.pumpAndSettle();
    expect(f.bodies['/api/v1/platform/firms/f1/extend-trial'], {'reason': 'Needs more time to migrate', 'days': 14});

    await t.tap(find.byKey(const Key('firm-ndlovu-partners')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('changePlan')));
    await t.pumpAndSettle();
    await t.enterText(find.byKey(const Key('reason')), 'Upgrade requested by firm');
    await t.tap(find.byKey(const Key('reasonOk')));
    await t.pumpAndSettle();
    expect(f.bodies['/api/v1/platform/firms/f1/plan'], {'reason': 'Upgrade requested by firm', 'planSlug': 'solo'});
  });

  testWidgets('directory: verify a practitioner and hide a listing with a reason', (t) async {
    final f = Fake();
    await pump(t, DirectoryAdminScreen(api: f.api()));
    expect(find.text('Thabo Ndlovu'), findsOneWidget);
    await t.tap(find.byKey(const Key('verify-p1')));
    await t.pumpAndSettle();
    await t.tap(find.byKey(const Key('reasonOk')));
    await t.pumpAndSettle();
    expect(f.bodies['/api/v1/platform/directory/practitioners/p1/verify']?['decision'], 'verified');

    await t.tap(find.text('Listings'));
    await t.pumpAndSettle();
    expect(find.textContaining('Wills and estates'), findsOneWidget);
    await t.tap(find.text('Hide'));
    await t.pumpAndSettle();
    await t.enterText(find.byKey(const Key('reason')), 'Complaint under review');
    await t.tap(find.byKey(const Key('reasonOk')));
    await t.pumpAndSettle();
    expect(f.bodies['/api/v1/platform/directory/listings/f1/visibility'], {'hidden': true, 'reason': 'Complaint under review'});
  });

  testWidgets('support access lists live grants and opens a read-only view', (t) async {
    final f = Fake();
    await pump(t, SupportScreen(api: f.api()));
    expect(find.text('Invoice totals look wrong'), findsOneWidget);
    await t.tap(find.byKey(const Key('look-matters')));
    await t.pumpAndSettle();
    expect(find.textContaining('C-2001'), findsOneWidget);
    expect(find.textContaining('This view was logged'), findsOneWidget);
  });

  testWidgets('billing overview and audit log render', (t) async {
    final f = Fake();
    await pump(t, BillingOverviewScreen(api: f.api()));
    expect(find.text('Mokoena Attorneys'), findsOneWidget);
    expect(find.textContaining('12 min'), findsOneWidget);
    await pump(t, AuditScreen(api: f.api()));
    expect(find.textContaining('Trial extended'), findsOneWidget);
  });
}
