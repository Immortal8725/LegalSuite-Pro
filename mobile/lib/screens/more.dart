import 'package:flutter/material.dart';

import '../api/access.dart';
import '../api/api_client.dart';
import '../config.dart';
import '../ui/common.dart';
import '../ui/launch.dart';
import '../ui/theme.dart';
import 'assist.dart';
import 'billing.dart';
import 'calls.dart';
import 'documents.dart';
import 'matter_form.dart';
import 'fica.dart';
import 'practice.dart';
import 'trust_ledger.dart';

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
        if (a.trust) _item(context, Icons.account_balance_outlined, 'Trust', 'Client ledgers, deposits and trust to fees', () => _open(context, TrustScreen(api: api, access: a))),
        if (a.documents) _item(context, Icons.folder_copy_outlined, 'Documents', 'Open, upload from camera or files', () => _open(context, DocumentsScreen(api: api))),
        if (a.esign) _item(context, Icons.draw_outlined, 'E-sign', 'Send for signature and track status', () => _open(context, SignaturesScreen(api: api))),
        if (a.ai) _item(context, Icons.edit_note, 'Draft help', 'Ask about a matter, draft client emails', () => _open(context, DraftHelpScreen(api: api))),
        if (a.clients) _item(context, Icons.verified_user_outlined, 'FICA', 'Client due diligence, ID capture and reviews due', () => _open(context, FicaScreen(api: api))),
        if (a.billing) _item(context, Icons.insights_outlined, 'Practice dashboard', 'Unbilled work, debtors, collections, time', () => _open(context, PracticeDashboardScreen(api: api))),
        if (a.trust) _item(context, Icons.fact_check_outlined, 'Trust audit pack', 'Auditor PDF and CSV, investments register', () => _open(context, TrustAuditScreen(api: api))),
        if (a.documents) _item(context, Icons.menu_book_outlined, 'Court bundles', 'Indexed, paginated PDF for CaseLines', () => _open(context, BundleScreen(api: api))),
        if (a.isFirmAdmin) _item(context, Icons.upload_file_outlined, 'Import data', 'CSV of clients, matters, time or trust balances', () => _open(context, ImportScreen(api: api))),
        _item(context, Icons.people_outline, 'Clients and contacts', 'Tap to call or email', () => _open(context, PeopleScreen(api: api))),
        _item(context, Icons.phone_in_talk_outlined, 'Calls', 'Call through the firm number', () => _open(context, CallsScreen(api: api))),
        _item(context, Icons.notifications_outlined, 'Notifications', 'Intakes, payments and reminders', () => _open(context, NotificationsScreen(api: api))),
        _item(context, Icons.person_outline, 'Profile', 'Your details and sign out', () => _open(context, ProfileScreen(api: api, onLogout: onLogout))),
        const SectionTitle('Help'),
        _web(context, Icons.help_outline, 'Help centre', '/help'),
        _web(context, Icons.lock_outline, 'Security and POPIA', '/security'),
        _web(context, Icons.monitor_heart_outlined, 'System status', '/status'),
        const SectionTitle('More on the web'),
        const Padding(
          padding: EdgeInsets.fromLTRB(20, 0, 20, 8),
          child: Text('Firm administration opens in your phone browser. Sign in once and the pages fit your screen.', style: TextStyle(color: Colors.black54, fontSize: 12)),
        ),
        for (final w in webPages(a)) _web(context, w.$1, w.$2, w.$3),
        const SizedBox(height: 24),
      ]),
    );
  }

  Widget _web(BuildContext context, IconData icon, String title, String path) => Card(
        child: ListTile(
          key: Key('web$path'),
          leading: Icon(icon, color: navy),
          title: Text(title),
          trailing: const Icon(Icons.open_in_new, size: 18),
          onTap: () => openUri(context, Uri.parse(AppConfig.webLink(api.baseUrl, path))),
        ),
      );

  Widget _item(BuildContext context, IconData icon, String title, String sub, VoidCallback onTap) => Card(
        child: ListTile(leading: Icon(icon, color: navy), title: Text(title), subtitle: Text(sub), trailing: const Icon(Icons.chevron_right), onTap: onTap),
      );
}

class PeopleScreen extends StatefulWidget {
  const PeopleScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<PeopleScreen> createState() => _PeopleScreenState();
}

class _PeopleScreenState extends State<PeopleScreen> {
  ApiClient get api => widget.api;
  int _version = 0;

  Future<void> _editClient(Map? c) async {
    final saved = await showClientForm(context, api, client: c?.cast<String, dynamic>());
    if (saved != null && mounted) {
      snack(context, 'Client saved');
      setState(() => _version++);
    }
  }

  Widget _person(BuildContext context, String name, String sub, String? phone, String? email, {VoidCallback? onTap}) {
    final hasPhone = phone != null && phone.isNotEmpty && phone != 'null';
    final hasEmail = email != null && email.isNotEmpty && email != 'null';
    return Card(
      child: ListTile(
        leading: CircleAvatar(backgroundColor: gold.withValues(alpha: 0.3), child: Text(name.isEmpty ? '?' : name[0].toUpperCase(), style: const TextStyle(color: navy))),
        title: Text(name),
        subtitle: Text([sub, if (hasEmail) email, if (hasPhone) phone].where((x) => x.isNotEmpty).join('\n')),
        isThreeLine: hasEmail && hasPhone,
        onTap: onTap,
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
        floatingActionButton: FloatingActionButton.extended(onPressed: () => _editClient(null), icon: const Icon(Icons.person_add_alt_1), label: const Text('New client')),
        body: TabBarView(children: [
          Loader<List>(
            key: ValueKey('clients$_version'),
            load: () async => await api.get('/api/v1/clients') as List,
            isEmpty: (d) => d.isEmpty,
            emptyText: 'No clients yet.',
            builder: (context, list, reload) => ListView(
              children: list.map((c) {
                final m = c as Map;
                return _person(context, '${m['displayName'] ?? m['companyName'] ?? ''}', '${m['type'] ?? ''} · ${m['status'] ?? ''}', m['phone']?.toString(), m['email']?.toString(), onTap: () => _editClient(m));
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

/// Admin pages kept on the phone-friendly web app rather than rebuilt in the app.
List<(IconData, String, String)> webPages(Access a) => [
      if (a.isFirmAdmin) (Icons.settings_outlined, 'Firm settings, letterhead, 2FA and PAIA', '/settings'),
      if (a.isFirmAdmin) (Icons.group_outlined, 'Team and roles', '/team'),
      if (a.isFirmAdmin) (Icons.extension_outlined, 'Modules', '/modules'),
      if (a.isFirmAdmin) (Icons.hub_outlined, 'Integrations', '/integrations'),
      if (a.isFirmAdmin) (Icons.credit_card_outlined, 'PayFast online payments', '/settings/payments'),
      (Icons.article_outlined, 'Templates', '/templates'),
      if (a.isFirmAdmin) (Icons.public, 'Website setup and branding', '/website'),
      (Icons.badge_outlined, 'Directory listing and practitioners', '/directory-listing'),
      (Icons.mark_email_unread_outlined, 'Leads', '/leads'),
      (Icons.summarize_outlined, 'Reports and accounting export', '/reports'),
      if (a.isFirmAdmin) (Icons.support_agent, 'Support access', '/support-access'),
      if (a.isFirmAdmin) (Icons.manage_search, 'Owlaw staff activity', '/staff-activity'),
      if (a.isFirmAdmin) (Icons.workspace_premium_outlined, 'Owlaw plan and billing', '/product-billing'),
      (Icons.history, 'Audit log', '/audit'),
    ];
