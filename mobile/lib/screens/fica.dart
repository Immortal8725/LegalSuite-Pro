import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../api/owlaw_api.dart';
import '../ui/admin_kit.dart';
import '../ui/common.dart';
import '../ui/files.dart';
import '../ui/theme.dart';

const ficaStatuses = {
  'not_started': 'Not started',
  'incomplete': 'Incomplete',
  'review_due': 'Review due',
  'verified': 'Verified',
  'blocked': 'Blocked (sanctions match)',
};

Color ficaColour(String s) => switch (s) {
      'verified' => Colors.green.shade700,
      'review_due' => Colors.orange.shade800,
      'blocked' => Colors.red.shade700,
      _ => Colors.black54,
    };

/// FICA register: every client with status and risk; filter to review due or incomplete.
class FicaScreen extends StatefulWidget {
  const FicaScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<FicaScreen> createState() => _FicaScreenState();
}

class _FicaScreenState extends State<FicaScreen> {
  final _loader = GlobalKey<LoaderState<List<Json>>>();
  String filter = 'all';

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('FICA'), actions: [
          IconButton(
            tooltip: 'RMCP template',
            icon: const Icon(Icons.description_outlined),
            onPressed: () async {
              try {
                final b = await widget.api.download('/api/v1/fica/rmcp.docx');
                if (context.mounted) await openOrShare(context, b, 'RMCP-template.docx');
              } catch (e) {
                if (context.mounted) snack(context, cleanError(e), error: true);
              }
            },
          ),
          IconButton(
            tooltip: 'Send review reminders',
            icon: const Icon(Icons.notifications_active_outlined),
            onPressed: () async {
              try {
                final r = await widget.api.ficaRemind();
                if (context.mounted) snack(context, '${r['created'] ?? 0} reminders created');
              } catch (e) {
                if (context.mounted) snack(context, cleanError(e), error: true);
              }
            },
          ),
        ]),
        body: Loader<List<Json>>(
          key: _loader,
          load: widget.api.ficaReport,
          isEmpty: (d) => d.isEmpty,
          emptyText: 'No clients yet. Add a client to start their FICA record.',
          builder: (context, rows, reload) {
            final due = rows.where((r) => r['status'] == 'review_due').length;
            final shown = rows.where((r) => switch (filter) {
                  'due' => r['status'] == 'review_due',
                  'open' => r['status'] != 'verified',
                  _ => true,
                }).toList();
            return ListView(padding: const EdgeInsets.only(bottom: 24), children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                child: Wrap(spacing: 8, children: [
                  ChoiceChip(label: Text('All (${rows.length})'), selected: filter == 'all', onSelected: (_) => setState(() => filter = 'all')),
                  ChoiceChip(key: const Key('filterDue'), label: Text('Review due ($due)'), selected: filter == 'due', onSelected: (_) => setState(() => filter = 'due')),
                  ChoiceChip(label: const Text('Not verified'), selected: filter == 'open', onSelected: (_) => setState(() => filter = 'open')),
                ]),
              ),
              if (shown.isEmpty) const Padding(padding: EdgeInsets.all(24), child: Text('No clients match.', textAlign: TextAlign.center)),
              for (final r in shown)
                Card(
                  child: ListTile(
                    title: Text('${r['clientName']}'),
                    subtitle: Text([
                      ficaStatuses['${r['status']}'] ?? '${r['status']}',
                      if (r['riskRating'] != null) '${labelOf('${r['riskRating']}')} risk',
                      if (r['nextReviewDate'] != null) 'review ${niceDate(r['nextReviewDate'])}',
                    ].join(' · ')),
                    trailing: Icon(r['status'] == 'verified' ? Icons.verified_outlined : Icons.pending_outlined, color: ficaColour('${r['status']}')),
                    onTap: () async {
                      await Navigator.push(context, MaterialPageRoute(builder: (_) => FicaClientScreen(api: widget.api, clientId: '${r['clientId']}')));
                      _loader.currentState?.reload();
                    },
                  ),
                ),
            ]);
          },
        ),
      );
}

const ficaFields = [
  FieldSpec('idType', 'ID type', type: FieldType.choice, options: [
    MapEntry('sa_id', 'South African ID'),
    MapEntry('passport', 'Passport'),
    MapEntry('company_reg', 'Company registration'),
    MapEntry('trust_loa', 'Trust letter of authority'),
  ]),
  FieldSpec('idNumber', 'ID or registration number', hint: 'Leave blank to keep the saved number'),
  FieldSpec('idExpiry', 'ID expiry date', type: FieldType.date),
  FieldSpec('poaDate', 'Proof of address date', type: FieldType.date),
  FieldSpec('pepStatus', 'Prominent influential person (PEP)', type: FieldType.choice, options: [MapEntry('no', 'No'), MapEntry('yes', 'Yes')]),
  FieldSpec('sanctionsResult', 'Sanctions screening', type: FieldType.choice, options: [MapEntry('clear', 'Clear'), MapEntry('possible_match', 'Possible match'), MapEntry('match', 'Match')]),
  FieldSpec('sanctionsSources', 'Lists checked'),
  FieldSpec('sourceOfFunds', 'Source of funds', type: FieldType.multiline),
  FieldSpec('riskRating', 'Risk rating (override)', type: FieldType.choice, options: [MapEntry('low', 'Low'), MapEntry('medium', 'Medium'), MapEntry('high', 'High')]),
  FieldSpec('notes', 'Notes', type: FieldType.multiline),
];

/// One client's FICA profile: edit, risk factors, capture ID and proof of address, verify.
class FicaClientScreen extends StatefulWidget {
  const FicaClientScreen({super.key, required this.api, required this.clientId});
  final ApiClient api;
  final String clientId;
  @override
  State<FicaClientScreen> createState() => _FicaClientScreenState();
}

class _FicaClientScreenState extends State<FicaClientScreen> {
  final _loader = GlobalKey<LoaderState<List<Json>>>();
  ApiClient get api => widget.api;

  Future<void> _run(Future<void> Function() f, String done) async {
    try {
      await f();
      if (!mounted) return;
      snack(context, done);
      _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  Future<void> _capture(String kind) async {
    final f = await pickAnyFile(context);
    if (f == null || !mounted) return;
    await _run(() => api.uploadFicaDoc(widget.clientId, kind, f.bytes, f.name), kind == 'id' ? 'ID document saved' : 'Proof of address saved');
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('FICA profile')),
        body: Loader<List<Json>>(
          key: _loader,
          load: () async => [await api.ficaProfile(widget.clientId), await api.ficaFactors().catchError((_) => <String, dynamic>{})],
          builder: (context, d, reload) {
            final p = d[0];
            final factors = ((d[1]['factors'] as Map?) ?? const {}).cast<String, dynamic>();
            final chosen = ((p['riskFactors'] as List?) ?? const []).map((e) => e is Map ? '${e['key']}' : '$e').toSet();
            final missing = (p['missing'] as List? ?? const []).cast<dynamic>();
            final status = '${p['status']}';
            return ListView(padding: const EdgeInsets.only(bottom: 32), children: [
              ListTile(
                title: Text('${p['clientName']}', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: navy)),
                subtitle: Text([ficaStatuses[status] ?? status, if (p['riskRating'] != null) '${labelOf('${p['riskRating']}')} risk (score ${p['riskScore'] ?? 0})'].join(' · ')),
                trailing: Icon(Icons.shield_outlined, color: ficaColour(status)),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: Wrap(spacing: 8, runSpacing: 8, children: [
                  ActionChip(
                    key: const Key('ficaEdit'),
                    avatar: const Icon(Icons.edit_outlined, size: 18),
                    label: const Text('Edit details'),
                    onPressed: () async {
                      final ok = await showFormSheet(context, title: 'FICA details', fields: ficaFields, initial: {...p, 'idNumber': null}, onSubmit: (v) async {
                        final body = Map<String, dynamic>.from(v)..removeWhere((k, val) => k == 'idNumber' && val == null);
                        await api.saveFica(widget.clientId, body);
                      });
                      if (ok && context.mounted) {
                        snack(context, 'FICA details saved');
                        reload();
                      }
                    },
                  ),
                  ActionChip(key: const Key('captureId'), avatar: const Icon(Icons.badge_outlined, size: 18), label: const Text('Capture ID'), onPressed: () => _capture('id')),
                  ActionChip(key: const Key('capturePoa'), avatar: const Icon(Icons.home_outlined, size: 18), label: const Text('Capture proof of address'), onPressed: () => _capture('poa')),
                  ActionChip(
                    key: const Key('ficaVerify'),
                    avatar: const Icon(Icons.verified_outlined, size: 18),
                    label: const Text('Mark verified'),
                    onPressed: () => _run(() => api.verifyFica(widget.clientId), 'Client verified'),
                  ),
                ]),
              ),
              if (missing.isNotEmpty) ...[
                const SectionTitle('Still needed'),
                ...missing.map((m) => ListTile(dense: true, leading: const Icon(Icons.error_outline, color: Colors.orange), title: Text('$m'))),
              ],
              const SectionTitle('Risk factors'),
              for (final f in factors.keys)
                CheckboxListTile(
                  dense: true,
                  title: Text(labelOf(f)),
                  subtitle: Text('Weight ${factors[f]}'),
                  value: chosen.contains(f),
                  onChanged: (on) => _run(() => api.saveFica(widget.clientId, {'riskFactors': on == true ? [...chosen, f] : chosen.where((x) => x != f).toList()}), 'Risk updated'),
                ),
              const SectionTitle('Record'),
              RecordView(record: p, hide: const {'riskFactors', 'missing', 'clientName'}),
            ]);
          },
        ),
      );
}
