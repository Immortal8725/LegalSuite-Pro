import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import 'matter_form.dart';
import 'matter_detail.dart';

/// Deadlines from the docket and diary events, soonest first.
class DiaryScreen extends StatelessWidget {
  const DiaryScreen({super.key, required this.api});
  final ApiClient api;

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Diary'),
          bottom: const TabBar(
            labelColor: Colors.white,
            unselectedLabelColor: Colors.white70,
            indicatorColor: gold,
            tabs: [Tab(text: 'Deadlines'), Tab(text: 'Events')],
          ),
        ),
        floatingActionButton: Builder(
          builder: (context) => FloatingActionButton(
            tooltip: 'Add deadline or event',
            onPressed: () async {
              if (await showEventSheet(context, api) && context.mounted) snack(context, 'Saved to the diary. Pull down to refresh.');
            },
            child: const Icon(Icons.add),
          ),
        ),
        body: TabBarView(children: [
          Loader<List>(
            load: () async => await api.get('/api/v1/docket') as List,
            isEmpty: (d) => d.isEmpty,
            emptyText: 'No deadlines on the docket.',
            builder: (context, list, reload) => ListView(
              padding: const EdgeInsets.only(bottom: 16),
              children: list.map((c) {
                final m = c as Map;
                final colour = urgencyColour(m['daysLeft']);
                return Card(
                  child: ListTile(
                    leading: CircleAvatar(backgroundColor: colour.withValues(alpha: 0.15), child: Icon(Icons.alarm, color: colour)),
                    title: Text('${m['label'] ?? m['kind']}'),
                    subtitle: Text('${m['caseNumber'] ?? ''} ${m['title'] ?? ''}\n${niceDate(m['date'])}${m['citation'] != null ? ' · ${m['citation']}' : ''}'),
                    isThreeLine: true,
                    trailing: Text(daysLabel(m['daysLeft']), style: TextStyle(color: colour, fontWeight: FontWeight.bold, fontSize: 12)),
                    onTap: m['caseId'] == null
                        ? null
                        : () => Navigator.push(context, MaterialPageRoute(builder: (_) => MatterDetailScreen(api: api, caseId: '${m['caseId']}'))),
                  ),
                );
              }).toList(),
            ),
          ),
          Loader<List>(
            load: () async => await api.get('/api/v1/events') as List,
            isEmpty: (d) => d.isEmpty,
            emptyText: 'Nothing in the diary.',
            builder: (context, list, reload) {
              final sorted = [...list]..sort((a, b) => '${(a as Map)['startTime']}'.compareTo('${(b as Map)['startTime']}'));
              return ListView(
                padding: const EdgeInsets.only(bottom: 16),
                children: sorted.map((e) {
                  final m = e as Map;
                  return Card(
                    child: ListTile(
                      leading: Icon(m['type'] == 'court_date' ? Icons.gavel : Icons.event, color: navy),
                      title: Text('${m['title']}'),
                      subtitle: Text([niceDate(m['startTime'], withTime: true), m['location']].where((x) => x != null && '$x'.isNotEmpty).join(' · ')),
                    ),
                  );
                }).toList(),
              );
            },
          ),
        ]),
      ),
    );
  }
}
