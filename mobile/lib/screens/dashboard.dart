import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'matter_detail.dart';

class DashboardScreen extends StatelessWidget {
  const DashboardScreen({super.key, required this.api, required this.onOpenTab});
  final ApiClient api;
  final void Function(int tab) onOpenTab;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Wordmark(size: 18)),
      body: Loader<Map<String, dynamic>>(
        load: () async => (await api.get('/api/v1/dashboard') as Map).cast<String, dynamic>(),
        builder: (context, d, reload) {
          final cur = d['currency']?.toString();
          final docket = (d['docket'] as List? ?? const []).take(5).toList();
          final events = (d['upcomingEvents'] as List? ?? const []).take(5).toList();
          final first = api.user?['firstName'];
          return ListView(padding: const EdgeInsets.only(bottom: 24), children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
              child: Text(first == null ? 'Docket' : 'Welcome back, $first', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: navy)),
            ),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16),
              child: Text('Clocks first. The attorney remains responsible for every date.', style: TextStyle(color: Colors.black54)),
            ),
            const SizedBox(height: 8),
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              padding: const EdgeInsets.symmetric(horizontal: 8),
              childAspectRatio: 2.1,
              children: [
                _Stat('Active matters', '${d['activeCases'] ?? 0}', Icons.folder_open, () => onOpenTab(1)),
                _Stat('Outstanding', money(d['outstanding'], cur), Icons.receipt_long, null),
                _Stat('Hours billed', '${d['hoursBilled'] ?? 0}', Icons.timer, () => onOpenTab(2)),
                _Stat('Unread alerts', '${d['unreadNotifications'] ?? 0}', Icons.notifications, () => onOpenTab(4)),
              ],
            ),
            const SectionTitle('What blows up if you ignore it'),
            if (docket.isEmpty) const ListTile(title: Text('No clocks running.')),
            ...docket.map((c) => _ClockTile(api: api, clock: (c as Map).cast<String, dynamic>())),
            const SectionTitle('Coming up'),
            if (events.isEmpty) const ListTile(title: Text('Nothing in the diary.')),
            ...events.map((e) {
              final m = e as Map;
              return Card(
                child: ListTile(
                  leading: const Icon(Icons.event, color: navy),
                  title: Text('${m['title']}'),
                  subtitle: Text([niceDate(m['startTime'], withTime: true), m['location']].where((x) => x != null && '$x'.isNotEmpty).join(' · ')),
                ),
              );
            }),
          ]);
        },
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat(this.label, this.value, this.icon, this.onTap);
  final String label;
  final String value;
  final IconData icon;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Card(
        margin: const EdgeInsets.all(6),
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(children: [
              Icon(icon, color: gold),
              const SizedBox(width: 10),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.center, children: [
                  Text(label, style: const TextStyle(fontSize: 12, color: Colors.black54)),
                  FittedBox(child: Text(value, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: navy))),
                ]),
              ),
            ]),
          ),
        ),
      );
}

class _ClockTile extends StatelessWidget {
  const _ClockTile({required this.api, required this.clock});
  final ApiClient api;
  final Map<String, dynamic> clock;
  @override
  Widget build(BuildContext context) {
    final colour = urgencyColour(clock['daysLeft']);
    return Card(
      shape: RoundedRectangleBorder(side: BorderSide(color: colour.withValues(alpha: 0.5)), borderRadius: BorderRadius.circular(12)),
      child: ListTile(
        title: Text('${clock['caseNumber'] ?? ''} · ${clock['title'] ?? ''}', maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text('${clock['label'] ?? clock['kind'] ?? ''}\n${niceDate(clock['date'])}'),
        isThreeLine: true,
        trailing: Text(daysLabel(clock['daysLeft']), style: TextStyle(color: colour, fontWeight: FontWeight.bold)),
        onTap: clock['caseId'] == null
            ? null
            : () => Navigator.push(context, MaterialPageRoute(builder: (_) => MatterDetailScreen(api: api, caseId: '${clock['caseId']}'))),
      ),
    );
  }
}
