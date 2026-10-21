import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:owlaw_mobile/api/access.dart';
import 'package:owlaw_mobile/api/api_client.dart';
import 'package:owlaw_mobile/api/token_store.dart';
import 'package:owlaw_mobile/screens/billing.dart';
import 'package:owlaw_mobile/screens/matter_form.dart';
import 'package:owlaw_mobile/screens/more.dart';

final posted = <String, Map>{};

ApiClient api({int conflicts = 0}) => ApiClient(
      baseUrl: 'http://api.test',
      store: MemoryTokenStore()..values[ApiClient.accessKey] = 'A',
      client: MockClient((req) async {
        if (req.method == 'POST') posted[req.url.path] = req.body.isEmpty ? {} : jsonDecode(req.body) as Map;
        final data = switch (req.url.path) {
          '/api/v1/clients' => [
              {'id': 'cl1', 'displayName': 'Thandi Dlamini', 'clientId': 'cl1'}
            ],
          '/api/v1/conflicts/check' => {
              'searchName': 'Thandi Dlamini',
              'matchCount': conflicts,
              'matches': [for (var i = 0; i < conflicts; i++) {'name': 'Thandi Dlamini', 'role': 'opposing party', 'how': 'exact name'}]
            },
          '/api/v1/cases' when req.method == 'POST' => {'id': 'c9', 'caseNumber': 'C-1009'},
          '/api/v1/invoices/i1' => {
              'id': 'i1',
              'invoiceNumber': 'INV-1',
              'clientId': 'cl1',
              'status': 'sent',
              'total': 1000,
              'amountPaid': 0,
              'balanceDue': 1000,
              'clientTrustAvailable': 500,
              'trustAccounts': [
                {'accountId': 'a1', 'accountName': 'Trust', 'clientLedger': 500}
              ],
              'lineItems': {
                'items': [
                  {'description': 'Consultation', 'amount': 1000}
                ]
              },
              'payments': [],
              'writeOffs': [],
              'proofs': [],
            },
          _ => [],
        };
        return http.Response(jsonEncode({'success': true, 'data': data}), 200);
      }),
    );

Widget host(Widget w) => MaterialApp(home: w);

void main() {
  setUpAll(() async => initializeDateFormatting('en_ZA'));
  setUp(posted.clear);

  test('access mirrors the web: modules gate pages, clients cannot bill', () {
    final a = Access.from('attorney', [
      {'slug': 'billing', 'enabled': true},
      {'slug': 'trust', 'enabled': false},
      {'slug': 'cases', 'core': true, 'enabled': false},
    ]);
    expect(a.billing, isTrue);
    expect(a.trust, isFalse);
    expect(a.module('cases'), isTrue);
    expect(a.isFirmAdmin, isFalse);
    expect(Access.from('client', [{'slug': 'billing', 'enabled': true}]).billing, isFalse);
    expect(Access.from('owner', const []).isFirmAdmin, isTrue);
    expect(Access.everything.billing, isTrue);
  });

  testWidgets('More shows only enabled attorney pages and points admin to the web', (tester) async {
    Access.current = Access.from('attorney', [{'slug': 'billing', 'enabled': true}, {'slug': 'documents', 'enabled': true}]);
    await tester.pumpWidget(host(MoreScreen(api: api(), onLogout: () async {})));
    expect(find.text('Client invoices'), findsOneWidget);
    expect(find.text('Documents'), findsOneWidget);
    expect(find.text('Trust'), findsNothing);
    expect(find.text('E-sign'), findsNothing);
    await tester.scrollUntilVisible(find.byKey(const Key('web/help')), 300);
    expect(find.text('Help centre'), findsOneWidget);
    Access.current = Access.everything;
  });

  testWidgets('a new matter needs a conflict check before it opens', (tester) async {
    await tester.pumpWidget(host(MatterFormScreen(api: api())));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(DropdownButtonFormField<String>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Thandi Dlamini').last);
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('matterTitle')), 'Dlamini v RAF');
    await tester.ensureVisible(find.byKey(const Key('saveMatter')));
    await tester.tap(find.byKey(const Key('saveMatter')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Run the conflict check'), findsWidgets);
    expect(posted.containsKey('/api/v1/cases'), isFalse);
    await tester.ensureVisible(find.byKey(const Key('runConflictCheck')));
    await tester.tap(find.byKey(const Key('runConflictCheck')));
    await tester.pumpAndSettle();
    expect(find.text('No conflicts found for Thandi Dlamini.'), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('saveMatter')));
    await tester.tap(find.byKey(const Key('saveMatter')));
    await tester.pumpAndSettle();
    expect(posted['/api/v1/cases']?['title'], 'Dlamini v RAF');
  });

  testWidgets('a possible conflict must be acknowledged', (tester) async {
    await tester.pumpWidget(host(MatterFormScreen(api: api(conflicts: 1))));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(DropdownButtonFormField<String>).first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Thandi Dlamini').last);
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('matterTitle')), 'Dlamini v RAF');
    await tester.ensureVisible(find.byKey(const Key('runConflictCheck')));
    await tester.tap(find.byKey(const Key('runConflictCheck')));
    await tester.pumpAndSettle();
    expect(find.text('1 possible conflict for Thandi Dlamini'), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('saveMatter')));
    await tester.tap(find.byKey(const Key('saveMatter')));
    await tester.pumpAndSettle();
    expect(posted.containsKey('/api/v1/cases'), isFalse);
    await tester.ensureVisible(find.byType(CheckboxListTile));
    await tester.tap(find.byType(CheckboxListTile));
    await tester.ensureVisible(find.byKey(const Key('saveMatter')));
    await tester.tap(find.byKey(const Key('saveMatter')));
    await tester.pumpAndSettle();
    expect(posted.containsKey('/api/v1/cases'), isTrue);
  });

  testWidgets('invoice detail offers payment, trust to fees and write-off, and records a payment', (tester) async {
    await tester.pumpWidget(host(InvoiceDetailScreen(api: api(), access: Access.everything, invoiceId: 'i1')));
    await tester.pumpAndSettle();
    expect(find.text('Record payment'), findsOneWidget);
    expect(find.text('Pay from trust'), findsOneWidget);
    expect(find.text('Write off'), findsOneWidget);
    expect(find.text('Consultation'), findsOneWidget);
    await tester.tap(find.text('Record payment'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('payAmount')), '400');
    await tester.tap(find.widgetWithText(FilledButton, 'Record payment'));
    await tester.pumpAndSettle();
    expect(posted['/api/v1/invoices/i1/payments']?['amount'], '400.00');
  });

  testWidgets('trust to fees is hidden when the trust module is off', (tester) async {
    await tester.pumpWidget(host(InvoiceDetailScreen(api: api(), access: Access.from('attorney', [{'slug': 'billing', 'enabled': true}]), invoiceId: 'i1')));
    await tester.pumpAndSettle();
    expect(find.text('Pay from trust'), findsNothing);
  });
}
