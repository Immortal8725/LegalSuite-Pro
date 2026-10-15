import 'package:flutter/material.dart';

import '../api/access.dart';
import '../api/api_client.dart';
import '../api/owlaw_api.dart';
import '../ui/common.dart';
import '../ui/files.dart';
import '../ui/forms.dart';
import '../ui/theme.dart';
import 'trust.dart';

String currencyOf(ApiClient api) => '${api.tenant?['currency'] ?? 'ZAR'}';

bool _looksLikeId(dynamic v) => RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-').hasMatch('${v ?? ''}');

num _n(dynamic v) => v is num ? v : num.tryParse('$v') ?? 0;

String statusLabel(dynamic s) {
  final t = '${s ?? ''}'.replaceAll('_', ' ');
  return t.isEmpty ? '' : t[0].toUpperCase() + t.substring(1);
}

Color statusColour(dynamic s) => switch ('$s') {
      'paid' => Colors.green.shade700,
      'partial' || 'partially_paid' => Colors.amber.shade800,
      'overdue' => Colors.red.shade700,
      'void' || 'written_off' => Colors.black45,
      'draft' => Colors.blueGrey,
      _ => navy,
    };

/// Unbilled billable time and expenses for a client (optionally one matter), so the
/// attorney sees what an invoice will pick up before generating it.
({int time, int expenses, num amount}) unbilledFor(List<Json> cases, List<Json> time, List<Json> expenses, String clientId, {String? caseId}) {
  final caseIds = cases.where((c) => '${c['clientId']}' == clientId).map((c) => '${c['id']}').toSet();
  bool inScope(Json e) => caseId != null ? '${e['caseId']}' == caseId : caseIds.contains('${e['caseId']}');
  final t = time.where((e) => e['billed'] != true && e['billable'] != false && inScope(e)).toList();
  final x = expenses.where((e) => e['billed'] != true && e['billable'] != false && e['invoiceId'] == null && inScope(e)).toList();
  final amount = t.fold<num>(0, (a, e) => a + _n(e['totalAmount'])) + x.fold<num>(0, (a, e) => a + _n(e['amount']));
  return (time: t.length, expenses: x.length, amount: amount);
}

class InvoicesScreen extends StatefulWidget {
  const InvoicesScreen({super.key, required this.api, required this.access, this.caseId});
  final ApiClient api;
  final Access access;
  final String? caseId;
  @override
  State<InvoicesScreen> createState() => _InvoicesScreenState();
}

class _InvoicesScreenState extends State<InvoicesScreen> {
  final _loader = GlobalKey<LoaderState<_InvoiceData>>();
  String filter = 'open';

  Future<_InvoiceData> _load() async {
    final r = await Future.wait([widget.api.invoices(), widget.api.clients(), widget.api.pendingProofs().catchError((_) => <Json>[])]);
    return _InvoiceData(r[0], {for (final c in r[1]) '${c['id']}': clientLabel(c)}, r[2]);
  }

  Future<void> _generate() async {
    final inv = await showGenerateInvoiceSheet(context, widget.api, caseId: widget.caseId);
    if (inv == null || !mounted) return;
    snack(context, 'Invoice ${inv['invoiceNumber'] ?? ''} generated');
    _loader.currentState?.reload();
    await Navigator.push(context, MaterialPageRoute(builder: (_) => InvoiceDetailScreen(api: widget.api, access: widget.access, invoiceId: '${inv['id']}')));
    _loader.currentState?.reload();
  }

  @override
  Widget build(BuildContext context) {
    final cur = currencyOf(widget.api);
    return Scaffold(
      appBar: AppBar(title: const Text('Client invoices'), actions: [
        if (widget.access.expenses)
          IconButton(
            tooltip: 'Add expense',
            icon: const Icon(Icons.receipt_long_outlined),
            onPressed: () async {
              if (await showExpenseSheet(context, widget.api, caseId: widget.caseId) && context.mounted) snack(context, 'Expense saved');
            },
          ),
      ]),
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('generateInvoice'),
        onPressed: _generate,
        icon: const Icon(Icons.add),
        label: const Text('Generate invoice'),
      ),
      body: Loader<_InvoiceData>(
        key: _loader,
        load: _load,
        builder: (context, d, reload) {
          var list = d.invoices;
          if (widget.caseId != null) list = list.where((i) => '${i['caseId']}' == widget.caseId).toList();
          final shown = list.where((i) {
            final s = '${i['status']}';
            return switch (filter) {
              'open' => _n(i['balanceDue']) > 0 && s != 'void',
              'draft' => s == 'draft',
              'paid' => s == 'paid' || _n(i['balanceDue']) <= 0,
              _ => true,
            };
          }).toList();
          return ListView(padding: const EdgeInsets.only(bottom: 96), children: [
            if (d.proofs.isNotEmpty)
              Card(
                color: gold.withValues(alpha: 0.18),
                child: ListTile(
                  leading: const Icon(Icons.fact_check_outlined, color: navy),
                  title: Text('${d.proofs.length} proof${d.proofs.length == 1 ? '' : 's'} of payment to review'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () async {
                    await Navigator.push(context, MaterialPageRoute(builder: (_) => ProofsScreen(api: widget.api)));
                    reload();
                  },
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
              child: Wrap(spacing: 8, children: [
                for (final f in const [('open', 'Owing'), ('draft', 'Draft'), ('paid', 'Paid'), ('all', 'All')])
                  ChoiceChip(label: Text(f.$2), selected: filter == f.$1, onSelected: (_) => setState(() => filter = f.$1)),
              ]),
            ),
            if (shown.isEmpty) const Padding(padding: EdgeInsets.all(24), child: Text('No invoices in this view.', textAlign: TextAlign.center)),
            ...shown.map((i) => Card(
                  child: ListTile(
                    title: Text('${i['invoiceNumber'] ?? ''} · ${d.clientNames['${i['clientId']}'] ?? 'Client'}', style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text('Issued ${niceDate(i['dateIssued'])} · due ${niceDate(i['dateDue'])}\nTotal ${money(i['total'], cur)} · owing ${money(i['balanceDue'], cur)}'),
                    isThreeLine: true,
                    trailing: Text(statusLabel(i['status']), style: TextStyle(color: statusColour(i['status']), fontWeight: FontWeight.bold, fontSize: 12)),
                    onTap: () async {
                      await Navigator.push(context, MaterialPageRoute(builder: (_) => InvoiceDetailScreen(api: widget.api, access: widget.access, invoiceId: '${i['id']}')));
                      reload();
                    },
                  ),
                )),
          ]);
        },
      ),
    );
  }
}

class _InvoiceData {
  _InvoiceData(this.invoices, this.clientNames, this.proofs);
  final List<Json> invoices;
  final Map<String, String> clientNames;
  final List<Json> proofs;
}

/// Generates an invoice from the client's unbilled time and expenses. Returns the new invoice.
Future<Json?> showGenerateInvoiceSheet(BuildContext context, ApiClient api, {String? clientId, String? caseId}) async {
  late List<Json> clients, cases, time, expenses;
  try {
    final r = await Future.wait([api.clients(), api.cases(), api.timeEntries(), api.expenses().catchError((_) => <Json>[])]);
    clients = r[0];
    cases = r[1];
    time = r[2];
    expenses = r[3];
  } catch (e) {
    if (context.mounted) snack(context, cleanError(e), error: true);
    return null;
  }
  if (caseId != null && clientId == null) {
    clientId = cases.where((c) => '${c['id']}' == caseId).map((c) => '${c['clientId']}').firstOrNull;
  }
  String? client = clientId;
  String? matter = caseId;
  Json? created;
  if (!context.mounted) return null;
  final ok = await showActionSheet(
    context,
    title: 'Generate invoice',
    intro: 'Picks up unbilled billable time and expenses for the client, or only one matter if you choose it.',
    actionLabel: 'Generate invoice',
    fields: (setState) {
      final matters = cases.where((c) => client == null || '${c['clientId']}' == client).toList();
      final u = client == null ? null : unbilledFor(cases, time, expenses, client!, caseId: matter);
      return [
        choice('Client', client, clients.map((c) => MapEntry('${c['id']}', clientLabel(c))).toList(), (v) => setState(() {
              client = v;
              matter = null;
            })),
        choice('Matter', matter, matters.map((m) => MapEntry('${m['id']}', matterLabel(m))).toList(), (v) => setState(() => matter = v), noneLabel: 'All matters for this client'),
        if (u != null)
          Text(
            u.time + u.expenses == 0
                ? 'Nothing unbilled yet for this choice.'
                : 'Unbilled: ${u.time} time entr${u.time == 1 ? 'y' : 'ies'} and ${u.expenses} expense${u.expenses == 1 ? '' : 's'}, about ${money(u.amount, currencyOf(api))}.',
            key: const Key('unbilledSummary'),
            style: const TextStyle(color: Colors.black54),
          ),
      ];
    },
    onSubmit: () async {
      if (client == null) throw Exception('Choose the client to invoice.');
      created = await api.generateInvoice(client!, caseId: matter);
    },
  );
  return ok ? created : null;
}

Future<bool> showExpenseSheet(BuildContext context, ApiClient api, {String? caseId}) async {
  List<Json> cases;
  try {
    cases = await api.cases();
  } catch (e) {
    if (context.mounted) snack(context, cleanError(e), error: true);
    return false;
  }
  String? matter = caseId;
  var category = 'filing fees';
  var billable = true;
  final desc = TextEditingController();
  final amount = TextEditingController();
  final vendor = TextEditingController();
  final date = TextEditingController(text: today());
  if (!context.mounted) return false;
  return showActionSheet(
    context,
    title: 'Add expense',
    actionLabel: 'Save expense',
    fields: (setState) => [
      choice('Matter', matter, cases.map((m) => MapEntry('${m['id']}', matterLabel(m))).toList(), (v) => setState(() => matter = v), noneLabel: 'No matter'),
      TextField(controller: desc, decoration: field('Description', hint: 'Sheriff fee for service')),
      TextField(controller: amount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: field('Amount')),
      choice('Category', category, const [
        MapEntry('filing fees', 'Filing fees'),
        MapEntry('sheriff', 'Sheriff'),
        MapEntry('travel', 'Travel'),
        MapEntry('copies', 'Copies and printing'),
        MapEntry('expert', 'Expert'),
        MapEntry('other', 'Other'),
      ], (v) => setState(() => category = v ?? 'other')),
      TextField(controller: vendor, decoration: field('Paid to (optional)')),
      TextField(controller: date, decoration: field('Date', hint: 'YYYY-MM-DD')),
      SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Bill to the client'), value: billable, onChanged: (v) => setState(() => billable = v)),
    ],
    onSubmit: () async {
      if (desc.text.trim().isEmpty) throw Exception('Describe the expense.');
      await api.addExpense({
        'caseId': matter,
        'description': desc.text,
        'amount': requireAmount(amount.text),
        'category': category,
        'vendor': vendor.text,
        'date': date.text,
        'billable': billable,
      });
    },
  );
}

class InvoiceDetailScreen extends StatefulWidget {
  const InvoiceDetailScreen({super.key, required this.api, required this.access, required this.invoiceId});
  final ApiClient api;
  final Access access;
  final String invoiceId;
  @override
  State<InvoiceDetailScreen> createState() => _InvoiceDetailScreenState();
}

class _InvoiceDetailScreenState extends State<InvoiceDetailScreen> {
  final _loader = GlobalKey<LoaderState<Json>>();
  ApiClient get api => widget.api;

  void _reload() => _loader.currentState?.reload();

  Future<void> _run(Future<void> Function() action, String done) async {
    try {
      await action();
      if (!mounted) return;
      snack(context, done);
      _reload();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  Future<void> _pay(Json inv) async {
    final amount = TextEditingController(text: _n(inv['balanceDue']).toStringAsFixed(2));
    final reference = TextEditingController();
    final note = TextEditingController();
    final paidAt = TextEditingController(text: today());
    var method = 'eft';
    final ok = await showActionSheet(
      context,
      title: 'Record payment',
      intro: '${inv['invoiceNumber']} has ${money(inv['balanceDue'], currencyOf(api))} owing.',
      actionLabel: 'Record payment',
      fields: (setState) => [
        TextField(key: const Key('payAmount'), controller: amount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: field('Amount')),
        choice('Method', method, const [MapEntry('eft', 'EFT'), MapEntry('cash', 'Cash'), MapEntry('card', 'Card'), MapEntry('other', 'Other')], (v) => setState(() => method = v ?? 'eft')),
        TextField(controller: paidAt, decoration: field('Date paid', hint: 'YYYY-MM-DD')),
        TextField(controller: reference, decoration: field('Reference (optional)')),
        TextField(controller: note, decoration: field('Note (optional)')),
      ],
      onSubmit: () async {
        await api.recordPayment(widget.invoiceId, amount: requireAmount(amount.text), method: method, paidAt: paidAt.text, reference: reference.text, note: note.text);
      },
    );
    if (ok && mounted) {
      snack(context, 'Payment recorded');
      _reload();
    }
  }

  Future<void> _writeOff(Json inv) async {
    final amount = TextEditingController(text: _n(inv['balanceDue']).toStringAsFixed(2));
    final reason = TextEditingController();
    final ok = await showActionSheet(
      context,
      title: 'Write off',
      intro: 'The invoice total stays ${money(inv['total'], currencyOf(api))}. The write-off is stored on its own so totals, payments and write-offs still reconcile.',
      actionLabel: 'Write off',
      fields: (_) => [
        TextField(controller: amount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: field('Amount')),
        TextField(controller: reason, maxLines: 3, decoration: field('Reason', hint: 'Why this balance is being written off')),
      ],
      onSubmit: () async {
        if (reason.text.trim().isEmpty) throw Exception('A write-off reason is required.');
        await api.writeOff(widget.invoiceId, amount: requireAmount(amount.text), reason: reason.text);
      },
    );
    if (ok && mounted) {
      snack(context, 'Written off');
      _reload();
    }
  }

  Future<void> _proof(Json inv) async {
    final file = await pickAnyFile(context);
    if (file == null || !mounted) return;
    final amount = TextEditingController(text: _n(inv['balanceDue']).toStringAsFixed(2));
    final reference = TextEditingController();
    final ok = await showActionSheet(
      context,
      title: 'Upload proof of payment',
      intro: file.name,
      actionLabel: 'Upload proof',
      fields: (_) => [
        TextField(controller: amount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: field('Amount on the proof')),
        TextField(controller: reference, decoration: field('Reference (optional)')),
      ],
      onSubmit: () async {
        await api.uploadProof(widget.invoiceId, file.bytes, file.name, amount: parseAmount(amount.text), reference: reference.text);
      },
    );
    if (ok && mounted) {
      snack(context, 'Proof uploaded for review');
      _reload();
    }
  }

  Future<void> _share(Json inv, String client) async {
    final cur = currencyOf(api);
    final items = (((inv['lineItems'] as Map?)?['items'] as List?) ?? const []).cast<Map>();
    final firm = '${api.tenant?['firmName'] ?? 'Our firm'}';
    final text = StringBuffer()
      ..writeln('$firm: invoice ${inv['invoiceNumber']} for $client')
      ..writeln('Issued ${niceDate(inv['dateIssued'])}, due ${niceDate(inv['dateDue'])}')
      ..writeln();
    for (final i in items) {
      text.writeln('${i['description']}: ${money(i['amount'], cur)}');
    }
    text
      ..writeln()
      ..writeln('Total ${money(inv['total'], cur)}')
      ..writeln('Paid ${money(inv['amountPaid'], cur)}')
      ..writeln('Owing ${money(inv['balanceDue'], cur)}');
    await shareText(context, text.toString(), subject: 'Invoice ${inv['invoiceNumber']}');
  }

  @override
  Widget build(BuildContext context) {
    final cur = currencyOf(api);
    return Scaffold(
      appBar: AppBar(title: const Text('Invoice')),
      body: Loader<Json>(
        key: _loader,
        load: () async {
          final r = await Future.wait([api.invoice(widget.invoiceId), api.clients().catchError((_) => <Json>[])]);
          final inv = r[0] as Json;
          final c = (r[1] as List<Json>).where((c) => '${c['id']}' == '${inv['clientId']}').firstOrNull;
          inv['_clientName'] = c == null ? 'Client' : clientLabel(c);
          return inv;
        },
        builder: (context, inv, reload) {
          final items = (((inv['lineItems'] as Map?)?['items'] as List?) ?? const []).cast<Map>();
          final payments = (inv['payments'] as List? ?? const []).cast<Map>();
          final writeOffs = (inv['writeOffs'] as List? ?? const []).cast<Map>();
          final proofs = (inv['proofs'] as List? ?? const []).cast<Map>();
          final status = '${inv['status']}';
          final owing = _n(inv['balanceDue']);
          final open = owing > 0 && status != 'void';
          final trustAvail = _n(inv['clientTrustAvailable']);
          final client = '${inv['_clientName']}';
          return ListView(padding: const EdgeInsets.only(bottom: 32), children: [
            ListTile(
              title: Text('${inv['invoiceNumber']} · $client', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: navy)),
              subtitle: Text('Issued ${niceDate(inv['dateIssued'])} · due ${niceDate(inv['dateDue'])}'),
              trailing: Text(statusLabel(status), style: TextStyle(color: statusColour(status), fontWeight: FontWeight.bold)),
            ),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Row(mainAxisAlignment: MainAxisAlignment.spaceAround, children: [
                  _figure('Total', money(inv['total'], cur)),
                  _figure('Paid', money(inv['amountPaid'], cur)),
                  _figure('Owing', money(owing, cur), key: const Key('invoiceOwing')),
                ]),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 0),
              child: Wrap(spacing: 8, runSpacing: 8, children: [
                if (status == 'draft')
                  ActionChip(
                    avatar: const Icon(Icons.send_outlined, size: 18),
                    label: const Text('Mark as sent'),
                    onPressed: () => _run(() => api.sendInvoice(widget.invoiceId), 'Invoice marked as sent'),
                  ),
                if (open) ActionChip(avatar: const Icon(Icons.payments_outlined, size: 18), label: const Text('Record payment'), onPressed: () => _pay(inv)),
                if (open && widget.access.trust && trustAvail > 0)
                  ActionChip(
                    avatar: const Icon(Icons.account_balance_outlined, size: 18),
                    label: const Text('Pay from trust'),
                    onPressed: () async {
                      if (await showTrustToFeeSheet(context, api, invoice: inv) && mounted) {
                        snack(this.context, 'Trust transferred to fees');
                        _reload();
                      }
                    },
                  ),
                if (open) ActionChip(avatar: const Icon(Icons.upload_file, size: 18), label: const Text('Upload proof'), onPressed: () => _proof(inv)),
                if (open) ActionChip(avatar: const Icon(Icons.money_off, size: 18), label: const Text('Write off'), onPressed: () => _writeOff(inv)),
                ActionChip(avatar: const Icon(Icons.share_outlined, size: 18), label: const Text('Share'), onPressed: () => _share(inv, client)),
              ]),
            ),
            if (open && trustAvail > 0)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                child: Text('${inv['clientTrustAvailable'] == null ? '' : money(trustAvail, cur)} held in trust for this client.', style: const TextStyle(color: Colors.black54)),
              ),
            const SectionTitle('Line items'),
            if (items.isEmpty) const ListTile(title: Text('No line items.')),
            ...items.map((i) => ListTile(dense: true, title: Text('${i['description']}'), trailing: Text(money(i['amount'], cur)))),
            if (payments.isNotEmpty) const SectionTitle('Payments'),
            ...payments.map((p) => ListTile(
                  dense: true,
                  leading: const Icon(Icons.check_circle_outline, color: Colors.green),
                  title: Text('${money(p['amount'], cur)} · ${'${p['method'] ?? ''}'.toUpperCase()}'),
                  subtitle: Text([niceDate(p['paidAt'] ?? p['createdAt']), if (!_looksLikeId(p['reference'])) p['reference'], p['note']].where((x) => x != null && '$x'.isNotEmpty).join(' · ')),
                )),
            if (writeOffs.isNotEmpty) const SectionTitle('Write-offs'),
            ...writeOffs.map((w) => ListTile(dense: true, title: Text(money(w['amount'], cur)), subtitle: Text('${w['reason'] ?? w['note'] ?? ''}'))),
            if (proofs.isNotEmpty) const SectionTitle('Proofs of payment'),
            ...proofs.map((p) => ListTile(
                  dense: true,
                  leading: const Icon(Icons.description_outlined),
                  title: Text('${p['fileName'] ?? 'Proof'} · ${statusLabel(p['status'])}'),
                  subtitle: Text([if (p['amountClaimed'] != null) money(p['amountClaimed'], cur), p['reference'], p['reviewNote']].where((x) => x != null && '$x'.isNotEmpty).join(' · ')),
                  trailing: IconButton(
                    tooltip: 'Open proof',
                    icon: const Icon(Icons.open_in_new),
                    onPressed: () async {
                      try {
                        final b = await api.downloadProof('${p['id']}');
                        if (context.mounted) await openBytes(context, b, '${p['fileName'] ?? 'proof'}');
                      } catch (e) {
                        if (context.mounted) snack(context, cleanError(e), error: true);
                      }
                    },
                  ),
                )),
          ]);
        },
      ),
    );
  }

  Widget _figure(String label, String value, {Key? key}) => Column(children: [
        Text(label, style: const TextStyle(color: Colors.black54, fontSize: 12)),
        const SizedBox(height: 4),
        Text(value, key: key, style: const TextStyle(fontWeight: FontWeight.bold, color: navy)),
      ]);
}

class ProofsScreen extends StatefulWidget {
  const ProofsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<ProofsScreen> createState() => _ProofsScreenState();
}

class _ProofsScreenState extends State<ProofsScreen> {
  final _loader = GlobalKey<LoaderState<List<Json>>>();

  Future<void> _accept(Json p) async {
    final amount = TextEditingController(text: '${p['amountClaimed'] ?? ''}');
    final note = TextEditingController();
    var method = 'eft';
    final ok = await showActionSheet(
      context,
      title: 'Accept proof',
      intro: 'Accepting records the payment on ${p['invoiceNumber'] ?? 'the invoice'}.',
      actionLabel: 'Accept and record payment',
      fields: (setState) => [
        TextField(controller: amount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: field('Amount received')),
        choice('Method', method, const [MapEntry('eft', 'EFT'), MapEntry('cash', 'Cash'), MapEntry('card', 'Card'), MapEntry('other', 'Other')], (v) => setState(() => method = v ?? 'eft')),
        TextField(controller: note, decoration: field('Note (optional)')),
      ],
      onSubmit: () => widget.api.acceptProof('${p['id']}', amount: parseAmount(amount.text), method: method, note: note.text),
    );
    if (ok && mounted) {
      snack(context, 'Proof accepted and payment recorded');
      _loader.currentState?.reload();
    }
  }

  Future<void> _reject(Json p) async {
    final note = TextEditingController();
    final ok = await showActionSheet(
      context,
      title: 'Reject proof',
      actionLabel: 'Reject',
      fields: (_) => [TextField(controller: note, maxLines: 3, decoration: field('Why it is rejected', hint: 'The client sees this note'))],
      onSubmit: () async {
        if (note.text.trim().isEmpty) throw Exception('A rejection note is required.');
        await widget.api.rejectProof('${p['id']}', note.text);
      },
    );
    if (ok && mounted) {
      snack(context, 'Proof rejected');
      _loader.currentState?.reload();
    }
  }

  @override
  Widget build(BuildContext context) {
    final cur = currencyOf(widget.api);
    return Scaffold(
      appBar: AppBar(title: const Text('Proofs to review')),
      body: Loader<List<Json>>(
        key: _loader,
        load: widget.api.pendingProofs,
        isEmpty: (d) => d.isEmpty,
        emptyText: 'No proofs of payment are waiting.',
        builder: (context, list, reload) => ListView(
          children: list
              .map((p) => Card(
                    child: Column(children: [
                      ListTile(
                        title: Text('${p['invoiceNumber'] ?? 'Invoice'} · ${p['amountClaimed'] == null ? 'amount not given' : money(p['amountClaimed'], cur)}'),
                        subtitle: Text([p['fileName'], p['reference'], p['note'], niceDate(p['submittedAt'])].where((x) => x != null && '$x'.isNotEmpty).join(' · ')),
                      ),
                      OverflowBar(alignment: MainAxisAlignment.end, children: [
                        TextButton(
                          onPressed: () async {
                            try {
                              final b = await widget.api.downloadProof('${p['id']}');
                              if (context.mounted) await openBytes(context, b, '${p['fileName'] ?? 'proof'}');
                            } catch (e) {
                              if (context.mounted) snack(context, cleanError(e), error: true);
                            }
                          },
                          child: const Text('Open'),
                        ),
                        TextButton(onPressed: () => _reject(p), child: const Text('Reject')),
                        FilledButton(onPressed: () => _accept(p), child: const Text('Accept')),
                      ]),
                    ]),
                  ))
              .toList(),
        ),
      ),
    );
  }
}
