import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:owlaw_mobile/api/api_client.dart';
import 'package:owlaw_mobile/api/owlaw_api.dart';
import 'package:owlaw_mobile/api/token_store.dart';
import 'package:owlaw_mobile/screens/billing.dart';
import 'package:owlaw_mobile/screens/matter_form.dart';
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

  group('documents and e-sign', () {
    test('upload sends the file with the matter and category', () async {
      final (api, calls) = recordingApi();
      await api.uploadDocument(utf8.encode('hello'), 'note.txt', caseId: 'c1', category: 'evidence');
      expect(calls.last.path, '/api/v1/documents/upload');
      expect(calls.last.body, contains('filename="note.txt"'));
      expect(calls.last.body, contains('name="caseId"'));
      expect(calls.last.body, isNot(contains('name="clientId"')));
    });

    test('download returns the raw bytes', () async {
      final api = ApiClient(
        baseUrl: 'http://api.test',
        store: MemoryTokenStore()..values[ApiClient.accessKey] = 'A',
        client: MockClient((req) async {
          expect(req.url.path, '/api/v1/documents/d1/download');
          expect(req.headers['Authorization'], 'Bearer A');
          return http.Response.bytes([1, 2, 3], 200);
        }),
      );
      expect(await api.downloadDocument('d1'), [1, 2, 3]);
    });

    test('signature request and void', () async {
      final (api, calls) = recordingApi((_) => {'id': 's1', 'signUrl': '/sign/s1'});
      final s = await api.sendForSignature({'title': 'Engagement', 'signerName': 'A', 'signerEmail': 'a@b.co.za', 'documentBody': 'x', 'caseId': null});
      expect(s['signUrl'], '/sign/s1');
      expect(calls.last.json.containsKey('caseId'), isFalse);
      await api.voidSignature('s1');
      expect(calls.last.path, '/api/v1/signatures/s1/void');
    });
  });

  group('matters, clients, tasks and diary', () {
    test('new matter posts, edit puts', () async {
      final (api, calls) = recordingApi((_) => {'id': 'c9'});
      await api.saveCase({'clientId': 'cl1', 'title': 'Dlamini v RAF', 'courtName': ''});
      expect(calls.last.method, 'POST');
      expect(calls.last.json, {'clientId': 'cl1', 'title': 'Dlamini v RAF'});
      await api.saveCase({'title': 'Renamed'}, id: 'c9');
      expect(calls.last.method, 'PUT');
      expect(calls.last.path, '/api/v1/cases/c9');
      await api.saveClient({'firstName': 'Thandi', 'lastName': 'Dlamini'});
      expect(calls.last.path, '/api/v1/clients');
    });

    test('conflict check sends name and opposing party, and clear means no matches', () async {
      final (api, calls) = recordingApi((_) => {'matchCount': 1, 'matches': [{'name': 'ABC Corp'}]});
      final r = await api.conflictCheck('ABC Corp', opposingParty: 'City');
      expect(calls.last.json, {'name': 'ABC Corp', 'opposingParty': 'City'});
      expect(conflictClear(r), isFalse);
      expect(conflictClear({'matchCount': 0}), isTrue);
      expect(conflictClear(null), isFalse);
    });

    test('event times go to the API in UTC and tasks use an instant due date', () async {
      final start = DateTime(2026, 11, 2, 9, 30);
      final b = eventBody(title: 'Hearing', type: 'hearing', start: start, minutes: 60, caseId: 'c1');
      expect(b['startTime'], start.toUtc().toIso8601String());
      expect(DateTime.parse(b['endTime'] as String).difference(DateTime.parse(b['startTime'] as String)).inMinutes, 60);
      expect(eventBody(title: 'x', type: 'deadline', start: start, minutes: 0).containsKey('endTime'), isFalse);
      expect(taskDue('2026-11-02'), '2026-11-02T00:00:00Z');
      final (api, calls) = recordingApi();
      await api.setTaskStatus('t1', 'completed');
      expect(calls.last.method, 'PATCH');
      expect(calls.last.json, {'status': 'completed'});
    });
  });

  group('draft help and messaging', () {
    test('chat, draft email and summary', () async {
      final (api, calls) = recordingApi((c) => {'reply': 'ok', 'draft': 'Dear', 'summary': 'S'});
      expect((await api.aiChat('What is due?', caseId: 'c1'))['reply'], 'ok');
      expect(calls.last.json, {'prompt': 'What is due?', 'caseId': 'c1'});
      await api.aiDraftEmail('demand');
      expect(calls.last.json, {'kind': 'demand'});
      await api.aiSummarize('c1');
      expect(calls.last.path, '/api/v1/ai/summarize');
    });

    test('outbound posts to the channel and lists by matter', () async {
      final (api, calls) = recordingApi((c) => c.method == 'GET' ? [] : {'status': 'dry_run'});
      await api.sendMessage('whatsapp', {'to': '+27820000000', 'body': 'Hi', 'caseId': 'c1', 'clientId': null});
      expect(calls.last.path, '/api/v1/outbound/whatsapp');
      expect(calls.last.json, {'to': '+27820000000', 'body': 'Hi', 'caseId': 'c1'});
      await api.outbound(caseId: 'c1');
      expect(calls.last.query['caseId'], 'c1');
    });
  });
}
