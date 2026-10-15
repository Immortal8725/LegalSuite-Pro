import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:owlaw_mobile/api/api_client.dart';
import 'package:owlaw_mobile/api/token_store.dart';
import 'package:owlaw_mobile/screens/generate_document.dart';

void main() {
  testWidgets('generates a branded document for the matter with filled blanks', (tester) async {
    Map<String, dynamic>? posted;
    final store = MemoryTokenStore()..values[ApiClient.accessKey] = 'A';
    final api = ApiClient(
      baseUrl: 'http://api.test',
      store: store,
      client: MockClient((req) async {
        Object? data;
        if (req.url.path == '/api/v1/doc-templates') {
          data = [
            {'id': 'sys-master-deceased-estate-report', 'name': 'Letter to the Master', 'category': 'Deceased estates', 'system': true},
          ];
        } else if (req.url.path.endsWith('/form')) {
          expect(req.url.queryParameters['caseId'], 'c1');
          data = {
            'id': 'sys-master-deceased-estate-report',
            'fields': [
              {'key': 'firm_name', 'label': 'Firm name', 'system': true, 'value': 'Ndlovu & Partners'},
              {'key': 'client_name', 'label': 'Client name', 'system': false, 'value': 'Lerato Molefe'},
              {'key': 'deceased_name', 'label': "Deceased's full names", 'system': false, 'value': ''},
            ],
          };
        } else if (req.url.path.endsWith('/generate')) {
          posted = jsonDecode(req.body) as Map<String, dynamic>;
          data = {
            'documents': [
              {'id': 'd1', 'name': 'Letter to the Master - EST-1.pdf'},
              {'id': 'd2', 'name': 'Letter to the Master - EST-1.docx'},
            ],
          };
        }
        return http.Response(jsonEncode({'success': true, 'data': data}), 200);
      }),
    );
    await tester.pumpWidget(MaterialApp(home: GenerateDocumentScreen(api: api, caseId: 'c1')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('templatePicker')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Letter to the Master').last);
    await tester.pumpAndSettle();
    expect(find.text('Lerato Molefe'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('field-deceased_name')), 'Johannes Molefe');
    await tester.ensureVisible(find.byKey(const Key('generateDocument')));
    await tester.tap(find.byKey(const Key('generateDocument')));
    await tester.pumpAndSettle();
    expect(posted!['caseId'], 'c1');
    expect((posted!['values'] as Map)['deceased_name'], 'Johannes Molefe');
    expect(posted!['formats'], ['pdf', 'docx']);
    expect(find.byKey(const Key('generateResult')), findsOneWidget);
  });
}
