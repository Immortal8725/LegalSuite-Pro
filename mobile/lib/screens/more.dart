import 'package:flutter/material.dart';

import '../api/access.dart';
import '../api/api_client.dart';
import '../ui/common.dart';
import '../ui/launch.dart';
import '../ui/theme.dart';
import 'billing.dart';
import 'calls.dart';

class MoreScreen extends StatelessWidget {
  const MoreScreen({super.key, required this.api, required this.onLogout});
  final ApiClient api;
  final Future<void> Function() onLogout;

  void _open(BuildContext context, Widget page) => Navigator.push(context, MaterialPageRoute(builder: (_) => page));

  @override
  Widget build(BuildContext context) {
    final a = Access.current;
    return Scaffold(
      appBar: AppBar(title: const Text('More')),
      body: ListView(children: [
        const SizedBox(height: 8),
        if (a.billing) _item(context, Icons.receipt_outlined, 'Client invoices', 'Generate, record payments, proofs and write-offs', () => _open(context, InvoicesScreen(api: api, access: a))),
        _item(context, Icons.people_outline, 'Clients and contacts', 'Tap to call or email', () => _open(context, PeopleScreen(api: api))),
        _item(context, Icons.phone_in_talk_outlined, 'Calls', 'Call through the firm number', () => _open(context, CallsScreen(api: api))),
        _item(context, Icons.notifications_outlined, 'Notifications', 'Intakes, payments and reminders', () => _open(context, NotificationsScreen(api: api))),
        _item(context, Icons.person_outline, 'Profile', 'Your details and sign out', () => _open(context, ProfileScreen(api: api, onLogout: onLogout))),
        const Padding(
          padding: EdgeInsets.fromLTRB(20, 12, 20, 24),
          child: Text('Team, the public website, the Owlaw subscription and firm settings are managed in the Owlaw web app.',
              style: TextStyle(color: Colors.black54, fontSize: 12)),
        ),
      ]),
    );
  }

  Widget _item(BuildContext context, IconData icon, String title, String sub, VoidCallback onTap) => Card(
        child: ListTile(leading: Icon(icon, color: navy), title: Text(title), subtitle: Text(sub), trailing: const Icon(Icons.chevron_right), onTap: onTap),
      );
}

class PeopleScreen extends StatelessWidget {
  const PeopleScreen({super.key, required this.api});
  final ApiClient api;

  Widget _person(BuildContext context, String name, String sub, String? phone, String? email) {
    final hasPhone = phone != null && phone.isNotEmpty && phone != 'null';
    final hasEmail = email != null && email.isNotEmpty && email != 'null';
    return Card(
      child: ListTile(
        leading: CircleAvatar(backgroundColor: gold.withValues(alpha: 0.3), child: Text(name.isEmpty ? '?' : name[0].toUpperCase(), style: const TextStyle(color: navy))),
        title: Text(name),
        subtitle: Text([sub, if (hasEmail) email, if (hasPhone) phone].where((x) => x.isNotEmpty).join('\n')),
        isThreeLine: hasEmail && hasPhone,
        trailing: Row(mainAxisSize: MainAxisSize.min, children: [
          if (hasPhone) IconButton(tooltip: 'Call $name', icon: const Icon(Icons.phone, color: navy), onPressed: () => openUri(context, telUri(phone))),
          if (hasEmail) IconButton(tooltip: 'Email $name', icon: const Icon(Icons.email_outlined, color: navy), onPressed: () => openUri(context, mailUri(email))),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Clients and contacts'),
          bottom: const TabBar(labelColor: Colors.white, unselectedLabelColor: Colors.white70, indicatorColor: gold, tabs: [Tab(text: 'Clients'), Tab(text: 'Contacts')]),
        ),
        body: TabBarView(children: [
          Loader<List>(
            load: () async => await api.get('/api/v1/clients') as List,
            isEmpty: (d) => d.isEmpty,
            emptyText: 'No clients yet.',
            builder: (context, list, reload) => ListView(
              children: list.map((c) {
                final m = c as Map;
                return _person(context, '${m['displayName'] ?? m['companyName'] ?? ''}', '${m['type'] ?? ''} · ${m['status'] ?? ''}', m['phone']?.toString(), m['email']?.toString());
              }).toList(),
            ),
          ),
          Loader<List>(
            load: () async => await api.get('/api/v1/contacts') as List,
            isEmpty: (d) => d.isEmpty,
            emptyText: 'No contacts yet.',
            builder: (context, list, reload) => ListView(
              children: list.map((c) {
                final m = c as Map;
                final role = '${m['type'] ?? ''}'.replaceAll('_', ' ');
                return _person(context, '${m['name'] ?? ''}', [role, m['company']].where((x) => x != null && '$x'.isNotEmpty).join(' · '), m['phone']?.toString(), m['email']?.toString());
              }).toList(),
            ),
          ),
        ]),
      ),
    );
  }
}

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  final _loader = GlobalKey<LoaderState<List>>();

  Future<void> _markRead(Map n) async {
    try {
      await widget.api.patch('/api/v1/notifications/${n['id']}/read');
      _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, e.toString(), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications')),
      body: Loader<List>(
        key: _loader,
        load: () async => await widget.api.get('/api/v1/notifications') as List,
        isEmpty: (d) => d.isEmpty,
        emptyText: 'You are all caught up.',
        emptyIcon: Icons.notifications_none,
        builder: (context, list, reload) => ListView(
          children: list.map((n) {
            final m = n as Map;
            final unread = m['read'] != true;
            return Card(
              child: ListTile(
                leading: Icon(unread ? Icons.circle : Icons.circle_outlined, size: 12, color: unread ? gold : Colors.black26),
                title: Text('${m['title']}', style: TextStyle(fontWeight: unread ? FontWeight.bold : FontWeight.normal)),
                subtitle: Text('${m['body'] ?? ''}\n${niceDate(m['createdAt'], withTime: true)}'),
                isThreeLine: true,
                trailing: unread ? TextButton(onPressed: () => _markRead(m), child: const Text('Mark read')) : null,
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key, required this.api, required this.onLogout});
  final ApiClient api;
  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Profile')),
      body: Loader<Map<String, dynamic>>(
        load: api.me,
        builder: (context, d, reload) {
          final u = (d['user'] as Map? ?? const {}).cast<String, dynamic>();
          final t = (d['tenant'] as Map? ?? const {}).cast<String, dynamic>();
          String v(dynamic x) => x == null || '$x' == 'null' ? '' : '$x';
          final rows = [
            ['Email', v(u['email'])],
            ['Role', v(u['role'])],
            ['Title', v(u['title'])],
            ['Admission number', v(u['barNumber'])],
            ['Firm', v(t['firmName'])],
            ['Firm ID', v(t['slug'])],
            ['Two-factor sign-in', u['totpEnabled'] == true ? 'On' : 'Off'],
            ['Server', api.baseUrl],
          ].where((r) => r[1].isNotEmpty);
          return ListView(padding: const EdgeInsets.all(8), children: [
            ListTile(
              leading: CircleAvatar(radius: 26, backgroundColor: gold, child: Text(v(u['initials']), style: const TextStyle(color: navyDark, fontWeight: FontWeight.bold))),
              title: Text(v(u['fullName']), style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: navy)),
              subtitle: Text(v(t['firmName'])),
            ),
            Card(child: Column(children: rows.map((r) => ListTile(dense: true, title: Text(r[1]), subtitle: Text(r[0]))).toList())),
            const SizedBox(height: 16),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: OutlinedButton.icon(
                key: const Key('logout'),
                icon: const Icon(Icons.logout),
                label: const Text('Sign out'),
                onPressed: () async {
                  Navigator.of(context).popUntil((r) => r.isFirst);
                  await onLogout();
                },
              ),
            ),
          ]);
        },
      ),
    );
  }
}
