import 'package:flutter/material.dart';
import 'package:legalsuite_mobile/api.dart';
import 'package:legalsuite_mobile/screens/calls.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.api, required this.onLogout});

  final LegalSuiteApi api;
  final VoidCallback onLogout;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int tab = 0;
  Map<String, dynamic>? dash;
  List cases = [];
  List time = [];
  String? error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final d = await widget.api.get('/api/v1/dashboard') as Map<String, dynamic>;
      final c = await widget.api.get('/api/v1/cases') as List;
      final t = await widget.api.get('/api/v1/time-entries') as List;
      setState(() {
        dash = d;
        cases = c;
        time = t;
      });
    } catch (e) {
      setState(() => error = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    final pages = [_dash(), _cases(), _time(), CallsScreen(api: widget.api)];
    return Scaffold(
      appBar: AppBar(
        title: const Text('LegalSuite Pro'),
        actions: [
          IconButton(
            onPressed: () async {
              await widget.api.logout();
              widget.onLogout();
            },
            icon: const Icon(Icons.logout),
          )
        ],
      ),
      body: error != null ? Center(child: Text(error!)) : pages[tab],
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (i) => setState(() => tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.dashboard_outlined), label: 'Desk'),
          NavigationDestination(icon: Icon(Icons.folder_outlined), label: 'Matters'),
          NavigationDestination(icon: Icon(Icons.timer_outlined), label: 'Time'),
          NavigationDestination(icon: Icon(Icons.phone_outlined), label: 'Calls'),
        ],
      ),
    );
  }

  Widget _dash() {
    if (dash == null) return const Center(child: CircularProgressIndicator());
    final cards = [
      ('Active cases', '${dash!['activeCases']}'),
      ('Clients', '${dash!['totalClients']}'),
      ('Open tasks', '${dash!['pendingTasks']}'),
      ('Hours billed', '${dash!['hoursBilled']}'),
    ];
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: cards
              .map((c) => SizedBox(
                    width: 160,
                    child: Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(c.$1, style: const TextStyle(color: Colors.black54, fontSize: 12)),
                            const SizedBox(height: 6),
                            Text(c.$2, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Color(0xFF1A365D))),
                          ],
                        ),
                      ),
                    ),
                  ))
              .toList(),
        ),
        const SizedBox(height: 16),
        const Text('Pull to refresh after a hearing. Public network calls start on the Calls tab. The phone rings on your mobile.'),
      ],
    );
  }

  Widget _cases() {
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.builder(
        itemCount: cases.length,
        itemBuilder: (_, i) {
          final c = cases[i] as Map<String, dynamic>;
          return ListTile(
            title: Text(c['title']?.toString() ?? 'Matter'),
            subtitle: Text('${c['caseNumber']} · ${c['status']} · ${c['clientName'] ?? ''}'),
            leading: const Icon(Icons.gavel, color: Color(0xFFC6A052)),
          );
        },
      ),
    );
  }

  Widget _time() {
    return ListView.builder(
      itemCount: time.length,
      itemBuilder: (_, i) {
        final t = time[i] as Map<String, dynamic>;
        return ListTile(
          title: Text(t['description']?.toString() ?? 'Time'),
          subtitle: Text('${t['durationMinutes']} min · ${t['billable'] == true ? 'billable' : 'non-billable'}'),
        );
      },
    );
  }
}
