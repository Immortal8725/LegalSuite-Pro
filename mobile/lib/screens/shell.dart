import 'package:flutter/material.dart';

import '../api/access.dart';
import '../api/api_client.dart';
import 'dashboard.dart';
import 'diary.dart';
import 'matters.dart';
import 'more.dart';
import 'time.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.api, required this.onLogout});
  final ApiClient api;
  final Future<void> Function() onLogout;
  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int tab = 0;

  @override
  void initState() {
    super.initState();
    Access.current = Access.everything;
    Access.load(widget.api).then((a) {
      if (!mounted) return;
      setState(() {
        Access.current = a;
        _visits[4]++;
      });
    });
  }
  final _visits = List<int>.filled(5, 0);

  void _go(int i) => setState(() {
        // Coming back to a tab reloads it, so work saved elsewhere shows up.
        if (i != tab) _visits[i]++;
        tab = i;
      });

  @override
  Widget build(BuildContext context) {
    final pages = [
      DashboardScreen(api: widget.api, onOpenTab: _go),
      MattersScreen(api: widget.api),
      TimeScreen(api: widget.api),
      DiaryScreen(api: widget.api),
      MoreScreen(api: widget.api, onLogout: widget.onLogout),
    ];
    return Scaffold(
      body: IndexedStack(index: tab, children: [for (var i = 0; i < pages.length; i++) KeyedSubtree(key: ValueKey('tab$i-${_visits[i]}'), child: pages[i])]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: _go,
        destinations: const [
          NavigationDestination(icon: Icon(Icons.space_dashboard_outlined), selectedIcon: Icon(Icons.space_dashboard), label: 'Home'),
          NavigationDestination(icon: Icon(Icons.folder_outlined), selectedIcon: Icon(Icons.folder), label: 'Matters'),
          NavigationDestination(icon: Icon(Icons.timer_outlined), selectedIcon: Icon(Icons.timer), label: 'Time'),
          NavigationDestination(icon: Icon(Icons.event_note_outlined), selectedIcon: Icon(Icons.event_note), label: 'Diary'),
          NavigationDestination(icon: Icon(Icons.menu), label: 'More'),
        ],
      ),
    );
  }
}
