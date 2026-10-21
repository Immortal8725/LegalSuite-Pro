import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:owlaw_mobile/api/access.dart';
import 'package:owlaw_mobile/api/drafts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:owlaw_mobile/api/api_client.dart';
import 'package:owlaw_mobile/api/token_store.dart';
import 'package:owlaw_mobile/screens/billing.dart';
import 'package:owlaw_mobile/screens/fica.dart';
import 'package:owlaw_mobile/screens/more.dart';
import 'package:owlaw_mobile/screens/practice.dart';

final calls = <String>[];
final bodies = <String, dynamic>{};

ApiClient api() => ApiClient(
      baseUrl: 'http://api.test',
      store: MemoryTokenStore()..values[ApiClient.accessKey] = 'A',
      client: MockClient((req) async {
        calls.add('${req.method} ${req.url.path}');
        if (req.body.isNotEmpty && req.headers['content-type']?.contains('json') == true) bodies[req.url.path] = jsonDecode(req.body);
        if (req.url.path.endsWith('.pdf') || req.url.path.endsWith('/bundle')) return http.Response.bytes([37, 80, 68, 70], 200, headers: {'content-type': 'application/pdf'});
        final data = switch (req.url.path) {
          '/api/v1/fica/report' => [
              {'clientId': 'c1', 'clientName': 'Thandi Dlamini', 'status': 'review_due', 'riskRating': 'high', 'nextReviewDate': '2026-10-01'},
              {'clientId': 'c2', 'clientName': 'Sipho Nkosi', 'status': 'verified', 'riskRating': 'low'},
            ],
          '/api/v1/fica/clients/c1' => {
              'clientId': 'c1',
              'clientName': 'Thandi Dlamini',
              'status': 'incomplete',
              'riskRating': 'medium',
              'riskScore': 2,
              'riskFactors': ['cash_heavy'],
              'missing': ['Proof of address'],
            },
          '/api/v1/fica/factors' => {
              'factors': {'pep_or_ppo': 3, 'cash_heavy': 2}
            },
          '/api/v1/reports/practice' => {
              'wip': 1500,
              'wipHours': 1.5,
              'debtors': 4312.5,
              'ageing': {'current': 4312.5, '31-60': 0, '61-90': 0, '90+': 0},
              'topDebtors': [
                {'client': 'Thandi Dlamini', 'owing': 4312.5}
              ],
              'months': [
                {'month': '2026-09', 'billed': 1000, 'collected': 500},
                {'month': '2026-10', 'billed': 4312.5, 'collected': 0}
              ],
              'billed12': 5312.5,
              'collected12': 500,
              'collectionRate': 9.4,
              'perAttorney90': [
                {'attorney': 'Lerato Mokoena', 'minutes': 90, 'billableValue': 3750}
              ],
              'openMatters': 1,
              'openByArea': {'Litigation': 1},
            },
          '/api/v1/trust/audit-pack' => {
              'exceptions': [
                {'severity': 'high', 'check': 'Fidelity Fund Certificate', 'detail': 'No current FFC'}
              ],
              'accounts': [
                {'account': 'Trust account', 'bank': 'FNB', 'closing': 1000}
              ],
            },
          '/api/v1/cases' => [
              {'id': 'm1', 'caseNumber': 'LT-1', 'title': 'Dlamini v RAF'}
            ],
          '/api/v1/cases/m1/bundle/documents' => [
              {'id': 'd1', 'name': 'Summons.pdf', 'pdf': true},
              {'id': 'd2', 'name': 'Photo.jpg', 'pdf': false},
            ],
          '/api/v1/invoices/i1/payment-link' => {
              'url': 'https://owlaw.co.za/pay/abc',
              'amount': 4312.5,
              'mode': 'sandbox',
              'expiresAt': '2026-11-20T10:00:00Z',
              'invoiceNumber': 'INV-1001',
              'clientPhone': '082 000 0000',
              'clientEmail': 'thandi@example.com',
            },
          '/api/v1/import/clients' => {'kind': 'clients', 'rows': 1, 'valid': 1, 'invalid': 0, 'dryRun': true, 'preview': []},
          _ => [],
        };
        return http.Response(jsonEncode({'success': true, 'data': data}), 200);
      }),
    );

Widget host(Widget w) => MaterialApp(home: w);

void main() {
  setUpAll(() async => initializeDateFormatting('en_ZA'));
  setUp(() {
    calls.clear();
    bodies.clear();
  });

  testWidgets('FICA register filters to review due and opens a profile', (tester) async {
    await tester.pumpWidget(host(FicaScreen(api: api())));
    await tester.pumpAndSettle();
    expect(find.text('Thandi Dlamini'), findsOneWidget);
    expect(find.text('Sipho Nkosi'), findsOneWidget);
    await tester.tap(find.byKey(const Key('filterDue')));
    await tester.pumpAndSettle();
    expect(find.text('Sipho Nkosi'), findsNothing);
    await tester.tap(find.text('Thandi Dlamini'));
    await tester.pumpAndSettle();
    expect(find.text('Proof of address'), findsOneWidget);
    expect(find.byKey(const Key('captureId')), findsOneWidget);
    expect(find.byKey(const Key('capturePoa')), findsOneWidget);
  });

  testWidgets('FICA risk factor toggles save to the API', (tester) async {
    await tester.pumpWidget(host(FicaClientScreen(api: api(), clientId: 'c1')));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Pep or ppo'), 200);
    await tester.tap(find.text('Pep or ppo'));
    await tester.pumpAndSettle();
    expect(bodies['/api/v1/fica/clients/c1'], {'riskFactors': ['cash_heavy', 'pep_or_ppo']});
  });

  testWidgets('practice dashboard shows unbilled work, debtors and attorney time', (tester) async {
    await tester.pumpWidget(host(PracticeDashboardScreen(api: api())));
    await tester.pumpAndSettle();
    expect((tester.widget<Text>(find.byKey(const Key('wip'))).data ?? '').replaceAll(RegExp(r'\s'), ''), 'R1500,00');
    expect(find.byKey(const Key('debtors')), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Lerato Mokoena'), 200);
    expect(find.text('1 h 30 min'), findsOneWidget);
  });

  testWidgets('trust audit pack lists exceptions and offers the PDF', (tester) async {
    await tester.pumpWidget(host(TrustAuditScreen(api: api())));
    await tester.pumpAndSettle();
    expect(find.text('Fidelity Fund Certificate'), findsOneWidget);
    await tester.tap(find.byKey(const Key('auditPdf')));
    await tester.pumpAndSettle();
    expect(calls, contains('GET /api/v1/trust/audit-pack.pdf'));
    expect(find.byKey(const Key('shareFile')), findsOneWidget);
  });

  testWidgets('court bundle adds a matter PDF to section A and builds', (tester) async {
    await tester.pumpWidget(host(BundleScreen(api: api())));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('bundleMatter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('LT-1 Dlamini v RAF').last);
    await tester.pumpAndSettle();
    expect(find.textContaining('Only PDFs'), findsOneWidget);
    await tester.tap(find.byKey(const Key('addDoc0')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Summons.pdf').last);
    await tester.pumpAndSettle();
    expect(find.text('1. Summons.pdf'), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('buildBundle')));
    await tester.tap(find.byKey(const Key('buildBundle')));
    await tester.pumpAndSettle();
    expect(bodies['/api/v1/cases/m1/bundle'], {
      'title': 'Trial bundle',
      'sections': [
        {'name': 'Pleadings', 'documentIds': ['d1']}
      ]
    });
  });

  testWidgets('payment link sheet builds a WhatsApp message in Afrikaans', (tester) async {
    late BuildContext ctx;
    await tester.pumpWidget(host(Builder(builder: (c) {
      ctx = c;
      return const Scaffold();
    })));
    showPayLinkSheet(ctx, api(), {'id': 'i1', 'invoiceNumber': 'INV-1001'});
    await tester.pumpAndSettle();
    expect(find.textContaining('Test mode'), findsOneWidget);
    expect(find.byKey(const Key('shareWhatsApp')), findsOneWidget);
    await tester.tap(find.text('Afrikaans'));
    await tester.pumpAndSettle();
    expect(find.textContaining('U kan faktuur INV-1001'), findsOneWidget);
  });

  test('payment link messages exist in three languages', () {
    for (final l in ['en', 'af', 'zu']) {
      final m = payLinkMessage(l, firm: 'F', invoice: 'INV-1', amount: 'R 1,00', url: 'https://x');
      expect(m, contains('https://x'));
      expect(m, isNot(contains('\u2014')));
    }
  });

  testWidgets('More lists daily tools and web admin links for a firm admin', (tester) async {
    Access.current = Access.from('owner', [
      {'slug': 'billing', 'enabled': true},
      {'slug': 'trust', 'enabled': true},
      {'slug': 'documents', 'enabled': true},
      {'slug': 'clients', 'enabled': true},
    ]);
    await tester.pumpWidget(host(MoreScreen(api: api(), onLogout: () async {})));
    expect(find.text('FICA'), findsOneWidget);
    expect(find.text('Practice dashboard'), findsOneWidget);
    await tester.scrollUntilVisible(find.byKey(const Key('web/help')), 300);
    expect(find.text('Security and POPIA'), findsOneWidget);
    await tester.scrollUntilVisible(find.byKey(const Key('web/website')), 300);
    expect(find.text('Website setup and branding'), findsOneWidget);
    Access.current = Access.everything;
  });

  test('web admin links follow the role', () {
    expect(webPages(Access.from('associate', const [])).any((w) => w.$3 == '/team'), isFalse);
    expect(webPages(Access.from('owner', const [])).any((w) => w.$3 == '/team'), isTrue);
  });

  test('time saved without signal waits on the phone and syncs later', () async {
    SharedPreferences.setMockInitialValues({});
    expect(OfflineDrafts.isOffline(Exception('Cannot reach the Owlaw server at x')), isTrue);
    await OfflineDrafts.add({'durationMinutes': 30, 'description': 'Call with client'});
    expect((await OfflineDrafts.all()).length, 1);
    final sent = await OfflineDrafts.sync(api());
    expect(sent, 1);
    expect(calls, contains('POST /api/v1/time-entries'));
    expect(await OfflineDrafts.all(), isEmpty);
  });
}
