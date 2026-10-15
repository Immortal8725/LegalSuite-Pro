import 'package:flutter/material.dart';

import '../api/access.dart';
import '../api/api_client.dart';
import '../ui/common.dart';
import '../ui/launch.dart';
import '../ui/theme.dart';
import 'billing.dart';
import 'calls.dart';
import 'documents.dart';
import 'trust_ledger.dart';
import 'time.dart';

class MatterDetailScreen extends StatefulWidget {
  const MatterDetailScreen({super.key, required this.api, required this.caseId});
  final ApiClient api;
  final String caseId;
  @override
  State<MatterDetailScreen> createState() => _MatterDetailScreenState();
}

class _MatterDetailScreenState extends State<MatterDetailScreen> {
  final _loader = GlobalKey<LoaderState<Map<String, dynamic>>>();

  Future<Map<String, dynamic>> _load() async {
    final m = (await widget.api.get('/api/v1/cases/${widget.caseId}') as Map).cast<String, dynamic>();
    final docs = m['documents'] as List?;
    if (docs == null || docs.isEmpty) {
      final all = await widget.api.get('/api/v1/documents') as List;
      m['documents'] = all.where((d) => '${(d as Map)['caseId']}' == widget.caseId).toList();
    }
    return m;
  }

  Future<void> _addNote() async {
    final ctrl = TextEditingController();
    final text = await showDialog<String>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Add a file note'),
        content: TextField(key: const Key('noteBody'), controller: ctrl, maxLines: 5, autofocus: true, decoration: const InputDecoration(hintText: 'What happened?')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(c, ctrl.text.trim()), child: const Text('Save note')),
        ],
      ),
    );
    if (text == null || text.isEmpty || !mounted) return;
    try {
      await widget.api.post('/api/v1/cases/${widget.caseId}/notes', {'title': 'Mobile note', 'body': text, 'type': 'note'});
      if (!mounted) return;
      snack(context, 'Note saved');
      _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, e.toString(), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Matter')),
      body: Loader<Map<String, dynamic>>(
        key: _loader,
        load: _load,
        builder: (context, m, reload) {
          final clocks = (m['docketClocks'] as List? ?? const []).cast<Map>();
          final notes = (m['notes'] as List? ?? const []).cast<Map>();
          final docs = (m['documents'] as List? ?? const []).cast<Map>();
          final phone = m['clientPhone']?.toString();
          final email = m['clientEmail']?.toString();
          final parties = <List<String>>[
            ['Client', '${m['clientName'] ?? ''}'],
            ['Opposing party', '${m['opposingParty'] ?? ''}'],
            ['Opposing counsel', '${m['opposingCounsel'] ?? ''}'],
            ['Court', '${m['courtName'] ?? ''}'],
            ['Judge', '${m['judgeName'] ?? ''}'],
          ].where((p) => p[1].isNotEmpty && p[1] != 'null').toList();
          return ListView(padding: const EdgeInsets.only(bottom: 32), children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
              child: Text('${m['caseNumber'] ?? ''} · ${m['title'] ?? ''}', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: navy)),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
              child: Text([m['practiceArea'], m['status'], if (m['dateOpened'] != null) 'opened ${niceDate(m['dateOpened'])}'].where((x) => x != null).join(' · '),
                  style: const TextStyle(color: Colors.black54)),
            ),
            if ((m['description'] ?? '').toString().isNotEmpty)
              Padding(padding: const EdgeInsets.fromLTRB(16, 8, 16, 0), child: Text('${m['description']}')),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
              child: Wrap(spacing: 8, runSpacing: 8, children: [
                ActionChip(
                  key: const Key('logTimeOnMatter'),
                  avatar: const Icon(Icons.timer_outlined, size: 18),
                  label: const Text('Log time'),
                  onPressed: () => showTimeEntrySheet(context, widget.api, presetCaseId: widget.caseId),
                ),
                ActionChip(avatar: const Icon(Icons.note_add_outlined, size: 18), label: const Text('Add note'), onPressed: _addNote),
                if (Access.current.billing)
                  ActionChip(
                    key: const Key('matterInvoices'),
                    avatar: const Icon(Icons.receipt_outlined, size: 18),
                    label: const Text('Invoices'),
                    onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => InvoicesScreen(api: widget.api, access: Access.current, caseId: widget.caseId))),
                  ),
                if (Access.current.trust && m['clientId'] != null)
                  ActionChip(
                    avatar: const Icon(Icons.account_balance_outlined, size: 18),
                    label: const Text('Client trust'),
                    onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => TrustScreen(api: widget.api, access: Access.current, clientId: '${m['clientId']}'))),
                  ),
                if (Access.current.documents)
                  ActionChip(
                    key: const Key('matterUpload'),
                    avatar: const Icon(Icons.upload_file, size: 18),
                    label: const Text('Upload document'),
                    onPressed: () async {
                      if (await uploadDocumentFlow(context, widget.api, caseId: widget.caseId, clientId: m['clientId']?.toString())) _loader.currentState?.reload();
                    },
                  ),
                if (Access.current.esign)
                  ActionChip(
                    avatar: const Icon(Icons.draw_outlined, size: 18),
                    label: const Text('E-sign'),
                    onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => SignaturesScreen(api: widget.api, caseId: widget.caseId))),
                  ),
                if (Access.current.expenses)
                  ActionChip(
                    avatar: const Icon(Icons.receipt_long_outlined, size: 18),
                    label: const Text('Add expense'),
                    onPressed: () async {
                      if (await showExpenseSheet(context, widget.api, caseId: widget.caseId) && context.mounted) snack(context, 'Expense saved');
                    },
                  ),
                if (phone != null && phone.isNotEmpty && phone != 'null') ...[
                  ActionChip(avatar: const Icon(Icons.phone, size: 18), label: const Text('Call client'), onPressed: () => openUri(context, telUri(phone))),
                  ActionChip(
                    avatar: const Icon(Icons.support_agent, size: 18),
                    label: const Text('Call via firm'),
                    onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => CallsScreen(api: widget.api, initialTo: phone))),
                  ),
                ],
                if (email != null && email.isNotEmpty && email != 'null')
                  ActionChip(
                    avatar: const Icon(Icons.email_outlined, size: 18),
                    label: const Text('Email client'),
                    onPressed: () => openUri(context, mailUri(email, subject: '${m['caseNumber']} ${m['title']}')),
                  ),
              ]),
            ),
            const SectionTitle('Parties'),
            Card(
              child: Column(children: parties.map((p) => ListTile(dense: true, title: Text(p[1]), subtitle: Text(p[0]))).toList()),
            ),
            const SectionTitle('Docket'),
            if (clocks.isEmpty) const ListTile(title: Text('No clocks on this matter.')),
            ...clocks.map((c) => Card(
                  child: ListTile(
                    title: Text('${c['title'] ?? c['kind']}'),
                    subtitle: Text([niceDate(c['date']), c['citation']].where((x) => x != null && '$x'.isNotEmpty).join(' · ')),
                    trailing: Text(daysLabel(c['daysLeft']), style: TextStyle(color: urgencyColour(c['daysLeft']), fontWeight: FontWeight.bold, fontSize: 12)),
                  ),
                )),
            const SectionTitle('Notes'),
            if (notes.isEmpty) const ListTile(title: Text('No notes yet.')),
            ...notes.map((n) => Card(
                  child: ListTile(title: Text('${n['title'] ?? 'Note'}'), subtitle: Text('${n['body'] ?? ''}\n${niceDate(n['createdAt'], withTime: true)}')),
                )),
            const SectionTitle('Documents'),
            if (docs.isEmpty) const ListTile(title: Text('No documents on this matter.')),
            ...docs.map((d) => Card(
                  child: ListTile(
                    leading: Icon(d['privileged'] == true ? Icons.lock_outline : Icons.description_outlined, color: navy),
                    title: Text('${d['name']}'),
                    subtitle: Text([d['category'], niceDate(d['createdAt'])].where((x) => x != null && '$x'.isNotEmpty).join(' · ')),
                    trailing: const Icon(Icons.open_in_new, size: 18),
                    onTap: () => openDocument(context, widget.api, d.cast<String, dynamic>()),
                  ),
                )),
          ]);
        },
      ),
    );
  }
}
