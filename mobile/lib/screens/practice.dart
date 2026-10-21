import 'dart:convert';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../api/owlaw_api.dart';
import '../ui/admin_kit.dart';
import '../ui/common.dart';
import '../ui/files.dart';
import '../ui/forms.dart';
import '../ui/theme.dart';
import 'billing.dart' show currencyOf;

num _n(dynamic v) => v is num ? v : num.tryParse('$v') ?? 0;

String yearStart() => '${DateTime.now().year}-01-01';

/// Practice dashboard: unbilled work, debtors ageing, billed against collected, time per attorney.
class PracticeDashboardScreen extends StatelessWidget {
  const PracticeDashboardScreen({super.key, required this.api});
  final ApiClient api;

  Widget _tile(String label, String value, {String? note, Key? key}) => Expanded(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(label, style: const TextStyle(fontSize: 11, color: Colors.black54)),
              const SizedBox(height: 4),
              Text(value, key: key, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: navy)),
              if (note != null) Text(note, style: const TextStyle(fontSize: 11, color: Colors.black54)),
            ]),
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final cur = currencyOf(api);
    return Scaffold(
      appBar: AppBar(title: const Text('Practice dashboard'), actions: [
        IconButton(key: const Key('exportCsv'), tooltip: 'Export to Xero or Sage', icon: const Icon(Icons.file_download_outlined), onPressed: () => showAccountingExport(context, api)),
      ]),
      body: Loader<Json>(
        load: api.practiceReport,
        builder: (context, d, reload) {
          final months = (d['months'] as List? ?? const []).cast<Map>();
          final ageing = ((d['ageing'] as Map?) ?? const {}).cast<String, dynamic>();
          final maxM = months.fold<num>(1, (m, e) => [m, _n(e['billed']), _n(e['collected'])].reduce((a, b) => a > b ? a : b));
          final maxA = ageing.values.fold<num>(1, (m, v) => _n(v) > m ? _n(v) : m);
          final per = (d['perAttorney90'] as List? ?? const []).cast<Map>();
          final areas = ((d['openByArea'] as Map?) ?? const {}).cast<String, dynamic>();
          final top = (d['topDebtors'] as List? ?? const []).cast<Map>();
          return ListView(padding: const EdgeInsets.fromLTRB(4, 8, 4, 32), children: [
            Row(children: [
              _tile('Unbilled work', money(d['wip'], cur), note: '${d['wipHours'] ?? 0} hours', key: const Key('wip')),
              _tile('Debtors', money(d['debtors'], cur), key: const Key('debtors')),
            ]),
            Row(children: [
              _tile('Billed (12 months)', money(d['billed12'], cur)),
              _tile('Collected (12 months)', money(d['collected12'], cur), note: d['collectionRate'] == null ? null : '${d['collectionRate']}% of billed'),
            ]),
            const SectionTitle('Billed and collected by month'),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(children: [
                  SizedBox(
                    height: 120,
                    child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                      for (final m in months)
                        Expanded(
                          child: Tooltip(
                            message: '${m['month']}: billed ${money(m['billed'], cur)}, collected ${money(m['collected'], cur)}',
                            child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                              Expanded(child: Container(height: 120 * _n(m['billed']) / maxM, color: navy)),
                              Expanded(child: Container(height: 120 * _n(m['collected']) / maxM, color: Colors.green)),
                            ]),
                          ),
                        ),
                    ]),
                  ),
                  const SizedBox(height: 6),
                  Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                    Text(months.isEmpty ? '' : '${months.first['month']}', style: const TextStyle(fontSize: 11)),
                    const Text('Navy billed · green collected', style: TextStyle(fontSize: 11)),
                    Text(months.isEmpty ? '' : '${months.last['month']}', style: const TextStyle(fontSize: 11)),
                  ]),
                ]),
              ),
            ),
            const SectionTitle('Debtors ageing'),
            Card(
              child: Column(children: [
                for (final e in ageing.entries)
                  ListTile(
                    dense: true,
                    title: Text(e.key == 'current' ? '0 to 30 days' : '${e.key} days'),
                    subtitle: LinearProgressIndicator(value: _n(e.value) / maxA, color: e.key == '90+' ? Colors.red : Colors.amber, backgroundColor: Colors.black12),
                    trailing: Text(money(e.value, cur)),
                  ),
                for (final t in top) ListTile(dense: true, leading: const Icon(Icons.person_outline), title: Text('${t['client']}'), trailing: Text(money(t['owing'], cur))),
              ]),
            ),
            const SectionTitle('Time recorded, last 90 days'),
            Card(
              child: Column(children: [
                if (per.isEmpty) const ListTile(title: Text('No time recorded.')),
                for (final a in per)
                  ListTile(dense: true, title: Text('${a['attorney']}'), subtitle: Text(hoursMinutes(a['minutes'])), trailing: Text(money(a['billableValue'], cur))),
              ]),
            ),
            SectionTitle('Open matters: ${d['openMatters'] ?? 0}'),
            Card(child: Column(children: [for (final e in areas.entries) ListTile(dense: true, title: Text(e.key), trailing: Text('${e.value}'))])),
          ]);
        },
      ),
    );
  }
}

/// Download invoices as a Xero or Sage Business Cloud CSV and share it.
Future<void> showAccountingExport(BuildContext context, ApiClient api) async {
  var format = 'xero';
  final from = TextEditingController(text: yearStart());
  final to = TextEditingController(text: today());
  final code = TextEditingController();
  List<int>? bytes;
  final ok = await showActionSheet(
    context,
    title: 'Export invoices',
    intro: 'Sent invoices as a CSV ready to import into your accounting system as sales invoices.',
    actionLabel: 'Download CSV',
    fields: (setState) => [
      choice('System', format, const [MapEntry('xero', 'Xero'), MapEntry('sage', 'Sage Business Cloud')], (v) => setState(() => format = v ?? 'xero')),
      TextField(key: const Key('expFrom'), controller: from, decoration: field('From', hint: 'YYYY-MM-DD')),
      TextField(key: const Key('expTo'), controller: to, decoration: field('To', hint: 'YYYY-MM-DD')),
      TextField(controller: code, decoration: field('Sales account code (optional)')),
    ],
    onSubmit: () async => bytes = await api.accountingCsv(format, from.text.trim(), to.text.trim(), accountCode: code.text.trim()),
  );
  if (ok && bytes != null && context.mounted) await shareBytes(context, bytes!, '$format-invoices-${from.text}-${to.text}.csv');
}

/// Trust audit pack: summary for a period, PDF and CSV to share with the auditor, and the investments register.
class TrustAuditScreen extends StatefulWidget {
  const TrustAuditScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<TrustAuditScreen> createState() => _TrustAuditScreenState();
}

class _TrustAuditScreenState extends State<TrustAuditScreen> {
  final from = TextEditingController(text: yearStart());
  final to = TextEditingController(text: today());
  int v = 0;

  Future<void> _file(bool pdf) async {
    try {
      final b = pdf ? await widget.api.trustAuditPdf(from.text, to.text) : await widget.api.trustAuditCsv(from.text, to.text);
      if (mounted) await openOrShare(context, b, 'trust-audit-pack-${from.text}-${to.text}.${pdf ? 'pdf' : 'csv'}');
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cur = currencyOf(widget.api);
    return Scaffold(
      appBar: AppBar(title: const Text('Trust audit pack')),
      body: ListView(padding: const EdgeInsets.only(bottom: 32), children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: Row(children: [
            Expanded(child: TextField(key: const Key('auditFrom'), controller: from, decoration: field('From'))),
            const SizedBox(width: 8),
            Expanded(child: TextField(key: const Key('auditTo'), controller: to, decoration: field('To'))),
            IconButton(tooltip: 'Refresh', icon: const Icon(Icons.refresh), onPressed: () => setState(() => v++)),
          ]),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Row(children: [
            Expanded(child: FilledButton.icon(key: const Key('auditPdf'), icon: const Icon(Icons.picture_as_pdf_outlined), label: const Text('PDF'), onPressed: () => _file(true))),
            const SizedBox(width: 8),
            Expanded(child: OutlinedButton.icon(icon: const Icon(Icons.table_chart_outlined), label: const Text('CSV'), onPressed: () => _file(false))),
          ]),
        ),
        SizedBox(
          height: 520,
          child: Loader<Json>(
            key: ValueKey(v),
            load: () => widget.api.trustAuditPack(from.text, to.text),
            builder: (context, d, reload) {
              final ex = (d['exceptions'] as List? ?? const []).cast<Map>();
              final accounts = (d['accounts'] as List? ?? const []).cast<Map>();
              return ListView(children: [
                const SectionTitle('Exceptions'),
                if (ex.isEmpty) const ListTile(leading: Icon(Icons.check_circle_outline, color: Colors.green), title: Text('No exceptions for this period.')),
                for (final e in ex)
                  ListTile(
                    dense: true,
                    leading: Icon(Icons.warning_amber, color: e['severity'] == 'high' ? Colors.red : Colors.orange),
                    title: Text('${e['check']}'),
                    subtitle: Text('${e['detail']}'),
                  ),
                const SectionTitle('Trust accounts'),
                for (final a in accounts)
                  ListTile(dense: true, title: Text('${a['account']}'), subtitle: Text('${a['bank'] ?? ''} ${a['accountNumber'] ?? ''}'), trailing: Text(money(a['closing'] ?? a['balance'], cur))),
              ]);
            },
          ),
        ),
        ListTile(
          key: const Key('investments'),
          leading: const Icon(Icons.savings_outlined, color: navy),
          title: const Text('Investments register'),
          subtitle: const Text('Section 86(3) and 86(4) investments'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => InvestmentsScreen(api: widget.api))),
        ),
      ]),
    );
  }
}

const investmentFields = [
  FieldSpec('section', 'Section', type: FieldType.choice, required: true, options: [MapEntry('86(3)', 'Section 86(3) firm investment'), MapEntry('86(4)', 'Section 86(4) on client instruction')]),
  FieldSpec('institution', 'Bank or institution', required: true),
  FieldSpec('accountNumber', 'Account number'),
  FieldSpec('reference', 'Reference'),
  FieldSpec('principal', 'Principal', type: FieldType.number, required: true),
  FieldSpec('interestEarned', 'Interest earned', type: FieldType.number),
  FieldSpec('interestPaidOut', 'Interest paid out', type: FieldType.number),
  FieldSpec('openedOn', 'Opened on', type: FieldType.date, required: true),
  FieldSpec('closedOn', 'Closed on', type: FieldType.date),
  FieldSpec('notes', 'Notes', type: FieldType.multiline),
];

class InvestmentsScreen extends StatelessWidget {
  const InvestmentsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  Widget build(BuildContext context) => RecordListScreen(
        title: 'Investments register',
        load: api.trustInvestments,
        titleOf: (r) => '${r['institution']} · ${r['section']}',
        subtitleOf: (r) => 'Opened ${niceDate(r['openedOn'])}${r['closedOn'] == null ? '' : ', closed ${niceDate(r['closedOn'])}'}',
        trailingOf: (r) => money(r['principal'], currencyOf(api)),
        emptyText: 'No trust investments recorded.',
        addLabel: 'Add investment',
        onAdd: (c) => showFormSheet(c, title: 'New investment', fields: investmentFields, onSubmit: (v) => api.saveTrustInvestment(v)),
        actions: [
          RowAction('Edit', Icons.edit_outlined, (c, r) async {
            final ok = await showFormSheet(c, title: 'Edit investment', fields: investmentFields, initial: r, onSubmit: (v) => api.saveTrustInvestment(v, id: '${r['id']}'));
            return ok ? 'Investment saved' : null;
          }),
        ],
      );
}

/// Court bundle builder: sections A, B, C of matter PDFs, built into one indexed, paginated PDF.
class BundleScreen extends StatefulWidget {
  const BundleScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<BundleScreen> createState() => _BundleScreenState();
}

class _BundleScreenState extends State<BundleScreen> {
  List<Json> matters = [];
  List<Json> docs = [];
  String? caseId;
  final title = TextEditingController(text: 'Trial bundle');
  final sections = <({TextEditingController name, List<String> ids})>[];
  bool busy = false;
  String? error;

  static const names = ['Pleadings', 'Notices', 'Affidavits', 'Expert reports', 'Correspondence'];

  @override
  void initState() {
    super.initState();
    widget.api.cases().then((m) => mounted ? setState(() => matters = m) : null).catchError((e) => mounted ? setState(() => error = cleanError(e)) : null);
  }

  Future<void> _pickMatter(String? id) async {
    setState(() {
      caseId = id;
      docs = [];
      sections
        ..clear()
        ..add((name: TextEditingController(text: names[0]), ids: <String>[]));
    });
    if (id == null) return;
    try {
      final d = await widget.api.bundleDocuments(id);
      if (mounted) setState(() => docs = d);
    } catch (e) {
      if (mounted) setState(() => error = cleanError(e));
    }
  }

  String _name(String id) => '${docs.firstWhere((d) => '${d['id']}' == id, orElse: () => {'name': id})['name']}';

  Future<void> _build() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final b = await widget.api.buildBundle(caseId!, {
        'title': title.text.trim(),
        'sections': [for (final s in sections) {'name': s.name.text.trim(), 'documentIds': s.ids}],
      });
      if (mounted) await openOrShare(context, b, '${title.text.replaceAll(RegExp(r'[^A-Za-z0-9 -]'), '')}.pdf');
    } catch (e) {
      if (mounted) setState(() => error = cleanError(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final used = sections.expand((s) => s.ids).toSet();
    final free = docs.where((d) => d['pdf'] == true && !used.contains('${d['id']}')).toList();
    return Scaffold(
      appBar: AppBar(title: const Text('Court bundles')),
      body: ListView(padding: const EdgeInsets.fromLTRB(12, 12, 12, 32), children: [
        const Text('Build a paginated, indexed PDF ready to upload to CaseLines or Court Online.', style: TextStyle(color: Colors.black54)),
        const SizedBox(height: 12),
        KeyedSubtree(key: const Key('bundleMatter'), child: choice('Matter', caseId, matters.map((m) => MapEntry('${m['id']}', '${m['caseNumber']} ${m['title']}')).toList(), _pickMatter, noneLabel: 'Choose a matter')),
        const SizedBox(height: 12),
        TextField(controller: title, decoration: field('Bundle title')),
        if (error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(error!, style: TextStyle(color: Colors.red.shade700))),
        if (caseId != null) ...[
          for (var i = 0; i < sections.length; i++)
            Card(
              margin: const EdgeInsets.only(top: 12),
              child: Padding(
                padding: const EdgeInsets.all(8),
                child: Column(children: [
                  Row(children: [
                    CircleAvatar(radius: 14, backgroundColor: gold, child: Text(String.fromCharCode(65 + i), style: const TextStyle(color: navyDark, fontWeight: FontWeight.bold))),
                    const SizedBox(width: 8),
                    Expanded(child: TextField(controller: sections[i].name, decoration: const InputDecoration(isDense: true, labelText: 'Section name'))),
                    IconButton(tooltip: 'Remove section', icon: const Icon(Icons.delete_outline), onPressed: () => setState(() => sections.removeAt(i))),
                  ]),
                  for (var j = 0; j < sections[i].ids.length; j++)
                    ListTile(
                      dense: true,
                      title: Text('${j + 1}. ${_name(sections[i].ids[j])}'),
                      trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                        IconButton(tooltip: 'Move up', icon: const Icon(Icons.arrow_upward, size: 18), onPressed: j == 0 ? null : () => setState(() => sections[i].ids.insert(j - 1, sections[i].ids.removeAt(j)))),
                        IconButton(tooltip: 'Remove', icon: const Icon(Icons.close, size: 18), onPressed: () => setState(() => sections[i].ids.removeAt(j))),
                      ]),
                    ),
                  if (free.isNotEmpty)
                    KeyedSubtree(
                      key: Key('addDoc$i'),
                      child: choice('Add a document', null, free.map((d) => MapEntry('${d['id']}', '${d['name']}')).toList(), (v) {
                        if (v != null) setState(() => sections[i].ids.add(v));
                      }, noneLabel: 'Add a document'),
                    ),
                ]),
              ),
            ),
          if (docs.any((d) => d['pdf'] != true))
            const Padding(padding: EdgeInsets.only(top: 8), child: Text('Only PDFs can go in a bundle. Convert other files to PDF and upload them to the matter first.', style: TextStyle(fontSize: 12, color: Colors.black54))),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            icon: const Icon(Icons.add),
            label: const Text('Add section'),
            onPressed: () => setState(() => sections.add((name: TextEditingController(text: sections.length < names.length ? names[sections.length] : 'Section ${String.fromCharCode(65 + sections.length)}'), ids: <String>[]))),
          ),
          const SizedBox(height: 8),
          FilledButton.icon(
            key: const Key('buildBundle'),
            icon: const Icon(Icons.picture_as_pdf_outlined),
            label: Text(busy ? 'Building...' : 'Build bundle PDF'),
            onPressed: busy || used.isEmpty ? null : _build,
          ),
          const SizedBox(height: 6),
          const Text('Each page is stamped with its section page (A-1, A-2) and a running number, with an index first and a bookmark per document.', style: TextStyle(fontSize: 12, color: Colors.black54)),
        ],
      ]),
    );
  }
}

/// CSV import: pick a file, check it in a dry run, then commit. Only firm administrators can commit.
class ImportScreen extends StatefulWidget {
  const ImportScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<ImportScreen> createState() => _ImportScreenState();
}

class _ImportScreenState extends State<ImportScreen> {
  String kind = 'clients';
  String? csv;
  String? fileName;
  Json? result;
  bool busy = false;
  String? error;
  String? trustAccountId;
  List<Json> trustAccounts = [];

  static const kinds = {'clients': 'Clients', 'contacts': 'Contacts', 'matters': 'Matters', 'trust_opening': 'Trust opening balances', 'time': 'Time entries'};

  Future<void> _run(bool dry) async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final r = await widget.api.runImport(kind, csv!, dryRun: dry, trustAccountId: trustAccountId);
      if (!mounted) return;
      setState(() => result = r);
      if (!dry) snack(context, 'Imported ${r['imported'] ?? r['valid']} rows');
    } catch (e) {
      if (mounted) setState(() => error = cleanError(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final r = result;
    final preview = (r?['preview'] as List? ?? const []).cast<Map>();
    final clean = r != null && r['invalid'] == 0 && r['dryRun'] == true;
    return Scaffold(
      appBar: AppBar(title: const Text('Import data')),
      body: ListView(padding: const EdgeInsets.fromLTRB(12, 12, 12, 32), children: [
        const Text('Move from another system with a CSV. Every file is checked first and nothing is saved until the whole file is clean.', style: TextStyle(color: Colors.black54)),
        const SizedBox(height: 12),
        KeyedSubtree(
          key: const Key('importKind'),
          child: choice('What are you importing?', kind, kinds.entries.toList(), (v) async {
            setState(() {
              kind = v ?? 'clients';
              result = null;
            });
            if (kind == 'trust_opening' && trustAccounts.isEmpty) {
              final a = await widget.api.trustAccounts().catchError((_) => <Json>[]);
              if (mounted) setState(() => trustAccounts = a);
            }
          }),
        ),
        if (kind == 'trust_opening') ...[
          const SizedBox(height: 12),
          choice('Trust account', trustAccountId, trustAccounts.map((a) => MapEntry('${a['id']}', '${a['accountName']}')).toList(), (v) => setState(() => trustAccountId = v), noneLabel: 'Choose'),
        ],
        const SizedBox(height: 8),
        Row(children: [
          Expanded(
            child: OutlinedButton.icon(
              icon: const Icon(Icons.download_outlined),
              label: const Text('Template'),
              onPressed: () async {
                try {
                  final b = await widget.api.importTemplate(kind);
                  if (context.mounted) await shareBytes(context, b, 'owlaw-$kind-template.csv');
                } catch (e) {
                  if (context.mounted) snack(context, cleanError(e), error: true);
                }
              },
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: FilledButton.icon(
              key: const Key('pickCsv'),
              icon: const Icon(Icons.upload_file),
              label: Text(fileName ?? 'Choose CSV'),
              onPressed: () async {
                final f = await pickCsv(context);
                if (f == null) return;
                setState(() {
                  csv = utf8.decode(f.bytes, allowMalformed: true);
                  fileName = f.name;
                  result = null;
                });
                await _run(true);
              },
            ),
          ),
        ]),
        if (error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(error!, style: TextStyle(color: Colors.red.shade700))),
        if (r != null) ...[
          const SizedBox(height: 12),
          Card(
            child: ListTile(
              key: const Key('importSummary'),
              title: Text('${r['rows']} rows: ${r['valid']} ready, ${r['invalid']} with problems'),
              subtitle: Text(r['dryRun'] == true ? 'Checked only. Nothing has been saved yet.' : 'Imported and written to the audit log.'),
            ),
          ),
          if (clean)
            FilledButton(key: const Key('commitImport'), onPressed: busy ? null : () => _run(false), child: Text('Import ${r['valid']} rows')),
          for (final p in preview.take(50))
            ListTile(
              dense: true,
              leading: Icon((p['errors'] as List? ?? const []).isEmpty ? Icons.check : Icons.error_outline, color: (p['errors'] as List? ?? const []).isEmpty ? Colors.green : Colors.red),
              title: Text('Line ${p['line']}: ${((p['values'] as Map?) ?? const {}).values.where((v) => '$v'.isNotEmpty).take(3).join(', ')}'),
              subtitle: (p['errors'] as List? ?? const []).isEmpty ? null : Text((p['errors'] as List).join('; '), style: TextStyle(color: Colors.red.shade700)),
            ),
        ],
      ]),
    );
  }
}
