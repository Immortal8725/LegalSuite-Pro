import 'api_client.dart';

typedef Json = Map<String, dynamic>;

List<Json> _list(dynamic d) => (d as List? ?? const []).map((e) => (e as Map).cast<String, dynamic>()).toList();
Json _map(dynamic d) => (d as Map? ?? const {}).cast<String, dynamic>();

/// Drops blank optional values so the API sees "not given" rather than an empty string.
Json clean(Map<String, dynamic> m) {
  final out = <String, dynamic>{};
  m.forEach((k, v) {
    if (v == null) return;
    if (v is String && v.trim().isEmpty) return;
    out[k] = v is String ? v.trim() : v;
  });
  return out;
}

/// The calls the attorney screens make. Each maps to an existing Owlaw API endpoint.
extension OwlawApi on ApiClient {
  // Matters, clients, tasks, diary
  Future<List<Json>> cases() async => _list(await get('/api/v1/cases'));
  Future<Json> caseDetail(String id) async => _map(await get('/api/v1/cases/$id'));
  Future<Json> saveCase(Json body, {String? id}) async =>
      _map(id == null ? await post('/api/v1/cases', clean(body)) : await put('/api/v1/cases/$id', clean(body)));
  Future<List<Json>> clients() async => _list(await get('/api/v1/clients'));
  Future<Json> saveClient(Json body, {String? id}) async =>
      _map(id == null ? await post('/api/v1/clients', clean(body)) : await put('/api/v1/clients/$id', clean(body)));
  Future<Json> conflictCheck(String name, {String? opposingParty}) async =>
      _map(await post('/api/v1/conflicts/check', clean({'name': name, 'opposingParty': opposingParty})));
  Future<List<Json>> tasks() async => _list(await get('/api/v1/tasks'));
  Future<Json> saveTask(Json body) async => _map(await post('/api/v1/tasks', clean(body)));
  Future<Json> setTaskStatus(String id, String status) async => _map(await patch('/api/v1/tasks/$id/status', {'status': status}));
  Future<Json> saveEvent(Json body) async => _map(await post('/api/v1/events', clean(body)));

  // Billing
  Future<List<Json>> invoices() async => _list(await get('/api/v1/invoices'));
  Future<Json> invoice(String id) async => _map(await get('/api/v1/invoices/$id'));
  Future<Json> generateInvoice(String clientId, {String? caseId}) async =>
      _map(await post('/api/v1/invoices/generate', clean({'clientId': clientId, 'caseId': caseId})));
  Future<Json> sendInvoice(String id) async => _map(await post('/api/v1/invoices/$id/send'));
  Future<Json> recordPayment(String id, {required String amount, String method = 'eft', String? paidAt, String? reference, String? note}) async =>
      _map(await post('/api/v1/invoices/$id/payments',
          clean({'amount': amount, 'method': method, 'paidAt': paidAt, 'reference': reference, 'note': note})));
  Future<Json> writeOff(String id, {String? amount, required String reason}) async =>
      _map(await post('/api/v1/invoices/$id/write-off', clean({'amount': amount, 'reason': reason})));
  Future<Json> applyTrust(String invoiceId, {required String accountId, required String amount, String? note}) async =>
      _map(await post('/api/v1/invoices/$invoiceId/apply-trust', clean({'accountId': accountId, 'amount': amount, 'note': note})));
  Future<Json> uploadProof(String invoiceId, List<int> bytes, String filename, {String? amount, String? reference, String? note}) async =>
      _map(await upload('/api/v1/invoices/$invoiceId/proofs',
          bytes: bytes, filename: filename, fields: {'amount': amount ?? '', 'reference': reference ?? '', 'note': note ?? ''}));
  Future<List<Json>> pendingProofs() async => _list(await get('/api/v1/payment-proofs?status=pending_review'));
  Future<Json> acceptProof(String id, {String? amount, String method = 'eft', String? note}) async =>
      _map(await post('/api/v1/payment-proofs/$id/accept', clean({'amount': amount, 'method': method, 'note': note})));
  Future<Json> rejectProof(String id, String note) async => _map(await post('/api/v1/payment-proofs/$id/reject', {'note': note}));
  Future<List<Json>> expenses() async => _list(await get('/api/v1/expenses'));
  Future<Json> addExpense(Json body) async => _map(await post('/api/v1/expenses', clean(body)));
  Future<List<Json>> timeEntries() async => _list(await get('/api/v1/time-entries'));

  // Trust
  Future<List<Json>> trustAccounts() async => _list(await get('/api/v1/trust/accounts'));
  Future<List<Json>> trustLedger(String accountId) async => _list(await get('/api/v1/trust/accounts/$accountId/ledger'));
  Future<Json> trustMove(Json body) async => _map(await post('/api/v1/trust/move', clean(body)));

  // Documents and e-sign
  Future<List<Json>> documents() async => _list(await get('/api/v1/documents'));
  Future<Json> uploadDocument(List<int> bytes, String filename, {String? caseId, String? clientId, String? category}) async =>
      _map(await upload('/api/v1/documents/upload',
          bytes: bytes, filename: filename, fields: {'caseId': caseId ?? '', 'clientId': clientId ?? '', 'category': category ?? ''}));
  Future<List<int>> downloadDocument(String id) => download('/api/v1/documents/$id/download');
  Future<List<int>> downloadProof(String id) => download('/api/v1/payment-proofs/$id/file');
  Future<List<Json>> signatures() async => _list(await get('/api/v1/signatures'));
  Future<Json> sendForSignature(Json body) async => _map(await post('/api/v1/signatures', clean(body)));
  Future<Json> voidSignature(String id) async => _map(await post('/api/v1/signatures/$id/void'));

  // Draft help
  Future<Json> aiChat(String prompt, {String? caseId}) async => _map(await post('/api/v1/ai/chat', clean({'prompt': prompt, 'caseId': caseId})));
  Future<Json> aiDraftEmail(String kind, {String? caseId}) async => _map(await post('/api/v1/ai/draft-email', clean({'kind': kind, 'caseId': caseId})));
  Future<Json> aiSummarize(String caseId) async => _map(await post('/api/v1/ai/summarize', {'caseId': caseId}));

  // Messaging
  Future<Json> outboundReadiness() async => _map(await get('/api/v1/outbound/readiness'));
  Future<List<Json>> outbound({String? caseId}) async => _list(await get(caseId == null ? '/api/v1/outbound' : '/api/v1/outbound?caseId=$caseId'));
  Future<Json> sendMessage(String channel, Json body) async => _map(await post('/api/v1/outbound/$channel', clean(body)));

  // In-app calling
  Future<Json> voiceAppToken() async => _map(await get('/api/v1/voice/app/token'));


  // FICA
  Future<List<Json>> ficaReport() async => _list(await get('/api/v1/fica/report'));
  Future<Json> ficaProfile(String clientId) async => _map(await get('/api/v1/fica/clients/$clientId'));
  Future<Json> saveFica(String clientId, Json body) async => _map(await put('/api/v1/fica/clients/$clientId', body));
  Future<Json> verifyFica(String clientId) async => _map(await post('/api/v1/fica/clients/$clientId/verify'));
  Future<Json> uploadFicaDoc(String clientId, String kind, List<int> bytes, String filename) async =>
      _map(await upload('/api/v1/fica/clients/$clientId/documents', bytes: bytes, filename: filename, fields: {'kind': kind}));
  Future<Json> ficaFactors() async => _map(await get('/api/v1/fica/factors'));
  Future<Json> ficaRemind() async => _map(await post('/api/v1/fica/reminders/run'));

  // Online payments and statements
  Future<Json> paymentLink(String invoiceId) async => _map(await post('/api/v1/invoices/$invoiceId/payment-link'));
  Future<Json> paymentSettings() async => _map(await get('/api/v1/payments/settings'));
  Future<Json> savePaymentSettings(Json body) async => _map(await put('/api/v1/payments/settings', body));
  Future<List<int>> statementPdf(String clientId) => download('/api/v1/clients/$clientId/statement.pdf');

  // Reports, trust audit, bundles, import
  Future<Json> practiceReport() async => _map(await get('/api/v1/reports/practice'));
  Future<List<int>> accountingCsv(String format, String from, String to, {String? accountCode}) =>
      download('/api/v1/exports/accounting.csv?format=$format&from=$from&to=$to${accountCode == null || accountCode.isEmpty ? '' : '&accountCode=${Uri.encodeQueryComponent(accountCode)}'}');
  Future<Json> trustAuditPack(String from, String to) async => _map(await get('/api/v1/trust/audit-pack?from=$from&to=$to'));
  Future<List<int>> trustAuditPdf(String from, String to) => download('/api/v1/trust/audit-pack.pdf?from=$from&to=$to');
  Future<List<int>> trustAuditCsv(String from, String to) => download('/api/v1/trust/audit-pack.csv?from=$from&to=$to');
  Future<List<Json>> trustInvestments() async => _list(await get('/api/v1/trust/investments'));
  Future<Json> saveTrustInvestment(Json body, {String? id}) async =>
      _map(id == null ? await post('/api/v1/trust/investments', body) : await put('/api/v1/trust/investments/$id', body));
  Future<List<Json>> bundleDocuments(String caseId) async => _list(await get('/api/v1/cases/$caseId/bundle/documents'));
  Future<List<int>> buildBundle(String caseId, Json body) => download('/api/v1/cases/$caseId/bundle', body: body);
  Future<List<Json>> importKinds() async => _list(await get('/api/v1/import/kinds'));
  Future<List<int>> importTemplate(String kind) => download('/api/v1/import/templates/$kind');
  Future<Json> runImport(String kind, String csv, {required bool dryRun, String? trustAccountId}) async =>
      _map(await post('/api/v1/import/$kind', clean({'csv': csv, 'dryRun': dryRun, 'trustAccountId': trustAccountId})));

  // Access
  Future<List<Json>> tenantModules(String tenantId) async => _list(await get('/api/v1/tenants/$tenantId/modules'));
}
