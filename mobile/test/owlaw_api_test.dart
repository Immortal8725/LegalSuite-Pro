import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:owlaw_mobile/api/api_client.dart';
import 'package:owlaw_mobile/api/owlaw_api.dart';
import 'package:owlaw_mobile/api/token_store.dart';
import 'package:owlaw_mobile/screens/billing.dart';
import 'package:owlaw_mobile/ui/forms.dart';

class Call {
  Call(this.method, this.path, this.query, this.body, this.contentType);
  final String method;
  final String path;
  final Map<String, String> query;
  final String body;
  final String? contentType;
  Map<String, dynamic> get json => jsonDecode(body) as Map<String, dynamic>;
}

(ApiClient, List<Call>) recordingApi([Object? Function(Call c)? reply]) {
  final calls = <Call>[];
  final store = MemoryTokenStore()..values[ApiClient.accessKey] = 'A';
  final api = ApiClient(
    baseUrl: 'http://api.test',
    store: store,
    client: MockClient((req) async {
      final c = Call(req.method, req.url.path, req.url.queryParameters, req.body, req.headers['content-type']);
      calls.add(c);
      return http.Response(jsonEncode({'success': true, 'data': reply?.call(c) ?? {}}), 200);
    }),
  );
  return (api, calls);
}

void main() {
  group('billing', () {
    test('generate invoice sends client and optional matter', () async {
      final (api, calls) = recordingApi((_) => {'id': 'i1', 'invoiceNumber': 'INV-1'});
      final inv = await api.generateInvoice('cl1');
      expect(inv['invoiceNumber'], 'INV-1');
      expect(calls.single.path, '/api/v1/invoices/generate');
      expect(calls.single.json, {'clientId': 'cl1'});
      await api.generateInvoice('cl1', caseId: 'c1');
      expect(calls.last.json, {'clientId': 'cl1', 'caseId': 'c1'});
    });

    test('record payment, write-off, send and trust to fees hit the invoice endpoints', () async {
      final (api, calls) = recordingApi();
      await api.recordPayment('i1', amount: '100.00', method: 'cash', paidAt: '2026-10-15', reference: '', note: 'Counter');
      expect(calls.last.path, '/api/v1/invoices/i1/payments');
      expect(calls.last.json, {'amount': '100.00', 'method': 'cash', 'paidAt': '2026-10-15', 'note': 'Counter'});
      await api.writeOff('i1', amount: '20.00', reason: 'Goodwill');
      expect(calls.last.path, '/api/v1/invoices/i1/write-off');
      expect(calls.last.json['reason'], 'Goodwill');
      await api.sendInvoice('i1');
      expect(calls.last.path, '/api/v1/invoices/i1/send');
      await api.applyTrust('i1', accountId: 'a1', amount: '50.00');
      expect(calls.last.path, '/api/v1/invoices/i1/apply-trust');
      expect(calls.last.json, {'accountId': 'a1', 'amount': '50.00'});
    });

    test('proof upload is multipart with the file field', () async {
      final (api, calls) = recordingApi();
      await api.uploadProof('i1', utf8.encode('pdf'), 'pop.pdf', amount: '10.00');
      expect(calls.last.path, '/api/v1/invoices/i1/proofs');
      expect(calls.last.contentType, startsWith('multipart/form-data'));
      expect(calls.last.body, contains('name="file"; filename="pop.pdf"'));
      expect(calls.last.body, contains('name="amount"'));
      expect(calls.last.body, isNot(contains('name="reference"')));
    });

    test('proof review lists pending and accepts or rejects', () async {
      final (api, calls) = recordingApi((c) => c.method == 'GET' ? [] : {});
      await api.pendingProofs();
      expect(calls.last.query['status'], 'pending_review');
      await api.acceptProof('p1', amount: '5.00');
      expect(calls.last.path, '/api/v1/payment-proofs/p1/accept');
      await api.rejectProof('p1', 'Unreadable');
      expect(calls.last.json, {'note': 'Unreadable'});
    });

    test('expense drops blank optional fields', () async {
      final (api, calls) = recordingApi();
      await api.addExpense({'caseId': null, 'description': 'Sheriff', 'amount': '250.00', 'vendor': '  ', 'billable': true});
      expect(calls.last.json, {'description': 'Sheriff', 'amount': '250.00', 'billable': true});
    });

    test('unbilled summary counts only unbilled billable work for the client', () {
      final cases = [
        {'id': 'c1', 'clientId': 'cl1'},
        {'id': 'c2', 'clientId': 'cl2'},
      ];
      final time = [
        {'caseId': 'c1', 'billed': false, 'billable': true, 'totalAmount': 100},
        {'caseId': 'c1', 'billed': true, 'billable': true, 'totalAmount': 999},
        {'caseId': 'c2', 'billed': false, 'billable': true, 'totalAmount': 50},
      ];
      final expenses = [
        {'caseId': 'c1', 'billed': false, 'billable': true, 'amount': 25.5},
        {'caseId': 'c1', 'billed': false, 'billable': false, 'amount': 10},
      ];
      final u = unbilledFor(cases, time, expenses, 'cl1');
      expect(u.time, 1);
      expect(u.expenses, 1);
      expect(u.amount, 125.5);
      expect(unbilledFor(cases, time, expenses, 'cl2').amount, 50);
    });

    test('amount parser accepts rand formats', () {
      expect(parseAmount('1200'), '1200.00');
      expect(parseAmount('R1,200.50'), '1200.50');
      expect(parseAmount('99,95'), '99.95');
      expect(parseAmount('0'), isNull);
      expect(parseAmount('abc'), isNull);
    });
  });

  group('trust', () {
    test('accounts, ledger and deposit', () async {
      final (api, calls) = recordingApi((c) => c.method == 'GET' ? [{'id': 'a1'}] : {});
      expect((await api.trustAccounts()).single['id'], 'a1');
      await api.trustLedger('a1');
      expect(calls.last.path, '/api/v1/trust/accounts/a1/ledger');
      await api.trustMove({'accountId': 'a1', 'type': 'deposit', 'amount': '500.00', 'clientId': 'cl1', 'caseId': null});
      expect(calls.last.path, '/api/v1/trust/move');
      expect(calls.last.json, {'accountId': 'a1', 'type': 'deposit', 'amount': '500.00', 'clientId': 'cl1'});
    });
  });
}
