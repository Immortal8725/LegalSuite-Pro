import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'matter_detail.dart';
import 'matter_form.dart';

/// Filters matters on number, title, client, opposing party and practice area.
List<Map<String, dynamic>> filterMatters(List<Map<String, dynamic>> all, String query) {
  final q = query.trim().toLowerCase();
  if (q.isEmpty) return all;
  return all.where((m) {
    final hay = [m['caseNumber'], m['title'], m['clientName'], m['opposingParty'], m['practiceArea'], m['status']]
        .where((x) => x != null)
        .join(' ')
        .toLowerCase();
    return q.split(RegExp(r'\s+')).every(hay.contains);
  }).toList();
}

class MattersScreen extends StatefulWidget {
  const MattersScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<MattersScreen> createState() => _MattersScreenState();
}

class _MattersScreenState extends State<MattersScreen> {
  String query = '';
  final _loader = GlobalKey<LoaderState<List<Map<String, dynamic>>>>();

  Future<void> _newMatter() async {
    final m = await Navigator.push<Map<String, dynamic>>(context, MaterialPageRoute(builder: (_) => MatterFormScreen(api: widget.api)));
    if (m == null || !mounted) return;
    snack(context, 'Matter ${m['caseNumber'] ?? ''} opened');
    _loader.currentState?.reload();
    await Navigator.push(context, MaterialPageRoute(builder: (_) => MatterDetailScreen(api: widget.api, caseId: '${m['id']}')));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Matters')),
      floatingActionButton: FloatingActionButton.extended(key: const Key('newMatter'), onPressed: _newMatter, icon: const Icon(Icons.create_new_folder_outlined), label: const Text('New matter')),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
          child: TextField(
            key: const Key('matterSearch'),
            decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search matters, clients, parties', filled: true, fillColor: Colors.white),
            onChanged: (v) => setState(() => query = v),
          ),
        ),
        Expanded(
          child: Loader<List<Map<String, dynamic>>>(
            key: _loader,
            load: () async => (await widget.api.get('/api/v1/cases') as List).map((e) => (e as Map).cast<String, dynamic>()).toList(),
            isEmpty: (d) => d.isEmpty,
            emptyText: 'No matters yet. Tap New matter to open one.',
            emptyIcon: Icons.folder_off_outlined,
            builder: (context, all, reload) {
              final list = filterMatters(all, query);
              if (list.isEmpty) {
                return ListView(children: const [Padding(padding: EdgeInsets.all(24), child: Text('No matter matches that search.', textAlign: TextAlign.center))]);
              }
              return ListView.builder(
                padding: const EdgeInsets.only(bottom: 96),
                itemCount: list.length,
                itemBuilder: (context, i) => MatterTile(api: widget.api, matter: list[i]),
              );
            },
          ),
        ),
      ]),
    );
  }
}

class MatterTile extends StatelessWidget {
  const MatterTile({super.key, required this.api, required this.matter});
  final ApiClient api;
  final Map<String, dynamic> matter;
  @override
  Widget build(BuildContext context) {
    final clocks = (matter['docketClocks'] as List? ?? const []).cast<Map>();
    clocks.sort((a, b) => ((a['daysLeft'] as num?) ?? 9999).compareTo((b['daysLeft'] as num?) ?? 9999));
    final next = clocks.isEmpty ? null : clocks.first;
    return Card(
      child: ListTile(
        title: Text('${matter['caseNumber'] ?? ''} · ${matter['title'] ?? ''}', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text([matter['clientName'], matter['practiceArea'], matter['status']].where((x) => x != null).join(' · ')),
        trailing: next == null
            ? const Icon(Icons.chevron_right)
            : Text(daysLabel(next['daysLeft']), style: TextStyle(color: urgencyColour(next['daysLeft']), fontWeight: FontWeight.bold, fontSize: 12)),
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => MatterDetailScreen(api: api, caseId: '${matter['id']}'))),
        iconColor: navy,
      ),
    );
  }
}
