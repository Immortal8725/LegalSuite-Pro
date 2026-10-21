import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api/api_client.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

/// Entry point for the Owlaw platform operator. Two-factor sign-in is compulsory before any platform screen.
class OperatorRoot extends StatefulWidget {
  const OperatorRoot({super.key, required this.api, required this.onLogout});
  final ApiClient api;
  final Future<void> Function() onLogout;
  @override
  State<OperatorRoot> createState() => _OperatorRootState();
}

class _OperatorRootState extends State<OperatorRoot> {
  bool? enrolled;
  Object? error;

  @override
  void initState() {
    super.initState();
    _check();
  }

  Future<void> _check() async {
    setState(() => error = null);
    try {
      final me = await widget.api.me();
      final u = (me['user'] as Map?) ?? me;
      if (mounted) setState(() => enrolled = u['totpEnabled'] == true);
    } catch (e) {
      if (mounted) setState(() => error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (error != null) {
      return Scaffold(
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Text(cleanError(error!), textAlign: TextAlign.center),
              const SizedBox(height: 12),
              OutlinedButton(onPressed: _check, child: const Text('Try again')),
              TextButton(onPressed: widget.onLogout, child: const Text('Sign out')),
            ]),
          ),
        ),
      );
    }
    if (enrolled == null) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    if (enrolled == false) {
      return TwoFactorSetupScreen(api: widget.api, onLogout: widget.onLogout, onDone: () => setState(() => enrolled = true));
    }
    return OperatorHome(api: widget.api, onLogout: widget.onLogout);
  }
}

/// First sign-in: set up an authenticator app, confirm a code, then save the recovery codes.
class TwoFactorSetupScreen extends StatefulWidget {
  const TwoFactorSetupScreen({super.key, required this.api, required this.onDone, required this.onLogout});
  final ApiClient api;
  final VoidCallback onDone;
  final Future<void> Function() onLogout;
  @override
  State<TwoFactorSetupScreen> createState() => _TwoFactorSetupScreenState();
}

class _TwoFactorSetupScreenState extends State<TwoFactorSetupScreen> {
  final code = TextEditingController();
  Map<String, dynamic>? setup;
  List<String>? recovery;
  bool busy = false;
  String? error;

  @override
  void dispose() {
    code.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final s = await widget.api.totpStart();
      if (mounted) setState(() => setup = s);
    } catch (e) {
      if (mounted) setState(() => error = cleanError(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _confirm() async {
    if (code.text.trim().length != 6) {
      setState(() => error = 'Enter the 6-digit code from your authenticator app.');
      return;
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final codes = await widget.api.totpConfirm(code.text);
      if (mounted) setState(() => recovery = codes);
    } catch (e) {
      if (mounted) setState(() => error = cleanError(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final secret = '${setup?['secret'] ?? ''}';
    final link = '${setup?['otpauthUrl'] ?? ''}';
    return Scaffold(
      appBar: AppBar(
        title: const Text('Set up two-factor sign-in'),
        actions: [TextButton(onPressed: widget.onLogout, child: const Text('Sign out'))],
      ),
      body: ListView(padding: const EdgeInsets.all(20), children: [
        if (recovery != null) ...[
          const Text('Save your recovery codes', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: navy)),
          const SizedBox(height: 8),
          const Text('Each code works once if you lose your phone. Keep them somewhere safe, away from this phone. They are shown only now.'),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: SelectableText(recovery!.join('\n'), key: const Key('recoveryCodes'), style: const TextStyle(fontFamily: 'monospace', fontSize: 16, height: 1.6)),
            ),
          ),
          Wrap(spacing: 8, children: [
            OutlinedButton.icon(
              onPressed: () {
                Clipboard.setData(ClipboardData(text: recovery!.join('\n')));
                snack(context, 'Recovery codes copied');
              },
              icon: const Icon(Icons.copy),
              label: const Text('Copy'),
            ),
            OutlinedButton.icon(
              onPressed: () => SharePlus.instance.share(ShareParams(text: 'Owlaw operator recovery codes\n${recovery!.join('\n')}')),
              icon: const Icon(Icons.save_alt),
              label: const Text('Save or share'),
            ),
          ]),
          const SizedBox(height: 16),
          FilledButton(key: const Key('recoveryDone'), onPressed: widget.onDone, child: const Text('I have saved them, continue')),
        ] else if (setup == null) ...[
          const Text(
            'Owlaw operator accounts must use an authenticator app. Platform screens stay closed until this is done. '
            'Use Google Authenticator, Microsoft Authenticator or any app that supports time-based codes.',
          ),
          const SizedBox(height: 16),
          FilledButton(key: const Key('startTotp'), onPressed: busy ? null : _start, child: const Text('Show my setup key')),
        ] else ...[
          const Text('1. Add Owlaw to your authenticator app', style: TextStyle(fontWeight: FontWeight.bold, color: navy)),
          const SizedBox(height: 8),
          const Text('On this phone: tap "Open in authenticator app", or copy the setup key and paste it into the app. On another device: scan the QR code.'),
          const SizedBox(height: 12),
          Center(
            child: Container(
              color: Colors.white,
              padding: const EdgeInsets.all(8),
              child: QrImageView(key: const Key('totpQr'), data: link, size: 200),
            ),
          ),
          const SizedBox(height: 12),
          const Text('Setup key', style: TextStyle(color: Colors.black54)),
          SelectableText(secret, key: const Key('totpSecret'), style: const TextStyle(fontFamily: 'monospace', fontSize: 16, letterSpacing: 1)),
          Wrap(spacing: 8, children: [
            OutlinedButton.icon(
              key: const Key('copySecret'),
              onPressed: () {
                Clipboard.setData(ClipboardData(text: secret));
                snack(context, 'Setup key copied');
              },
              icon: const Icon(Icons.copy),
              label: const Text('Copy key'),
            ),
            OutlinedButton.icon(
              onPressed: () async {
                final ok = await launchUrl(Uri.parse(link), mode: LaunchMode.externalApplication).catchError((_) => false);
                if (!ok && context.mounted) snack(context, 'No authenticator app opened. Copy the key instead.', error: true);
              },
              icon: const Icon(Icons.open_in_new),
              label: const Text('Open in authenticator app'),
            ),
          ]),
          const SizedBox(height: 20),
          const Text('2. Type the 6-digit code it shows', style: TextStyle(fontWeight: FontWeight.bold, color: navy)),
          TextField(
            key: const Key('totpCode'),
            controller: code,
            keyboardType: TextInputType.number,
            maxLength: 6,
            autofillHints: const [AutofillHints.oneTimeCode],
            decoration: const InputDecoration(labelText: '6-digit code'),
            onSubmitted: (_) => _confirm(),
          ),
          FilledButton(key: const Key('confirmTotp'), onPressed: busy ? null : _confirm, child: const Text('Confirm')),
        ],
        if (error != null) ...[
          const SizedBox(height: 12),
          Text(error!, key: const Key('totpError'), style: TextStyle(color: Colors.red.shade700)),
        ],
      ]),
    );
  }
}

/// Operator home: firms, directory, support access, billing and the platform audit log.
class OperatorHome extends StatefulWidget {
  const OperatorHome({super.key, required this.api, required this.onLogout});
  final ApiClient api;
  final Future<void> Function() onLogout;
  @override
  State<OperatorHome> createState() => _OperatorHomeState();
}

class _OperatorHomeState extends State<OperatorHome> {
  int tab = 0;
  bool? support;
  final _visits = List.filled(5, 0);

  @override
  void initState() {
    super.initState();
    widget.api.isSupport().then((v) => mounted ? setState(() => support = v) : null);
  }

  @override
  Widget build(BuildContext context) {
    final api = widget.api;
    final isSupport = support;
    if (isSupport == null) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    final pages = <(String, IconData, String, Widget)>[
      ('Approvals', Icons.inbox_outlined, 'Approvals', ApprovalsScreen(api: api)),
      if (!isSupport) ('Firms', Icons.apartment_outlined, 'Firms', FirmsScreen(api: api)),
      ('Directory', Icons.verified_user_outlined, 'Directory', DirectoryAdminScreen(api: api)),
      if (!isSupport) ('Support access', Icons.support_agent, 'Support', SupportScreen(api: api)),
      if (!isSupport) ('Owlaw admin', Icons.more_horiz, 'More', OperatorMoreScreen(api: api)),
    ];
    if (tab >= pages.length) tab = 0;
    return Scaffold(
      appBar: AppBar(
        title: Text(pages[tab].$1),
        actions: [IconButton(key: const Key('operatorSignOut'), tooltip: 'Sign out', onPressed: widget.onLogout, icon: const Icon(Icons.logout))],
      ),
      body: IndexedStack(index: tab, children: [for (var i = 0; i < pages.length; i++) KeyedSubtree(key: ValueKey('op$i-${_visits[i]}-$isSupport'), child: pages[i].$4)]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (i) => setState(() {
          if (i != tab) _visits[i]++;
          tab = i;
        }),
        destinations: [for (final p in pages) NavigationDestination(icon: Icon(p.$2), label: p.$3)],
      ),
    );
  }
}

/// One inbox for every pending approval: public sites (publish, branding, features), LPC verification and listings.
class ApprovalsScreen extends StatefulWidget {
  const ApprovalsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<ApprovalsScreen> createState() => _ApprovalsScreenState();
}

class _ApprovalsScreenState extends State<ApprovalsScreen> {
  final _loader = GlobalKey<LoaderState<Map<String, dynamic>>>();

  Future<void> _decideSite(Map<String, dynamic> item, String decision) async {
    final r = await askReason(context, title: decision == 'approved' ? 'Approve' : 'Reject', action: decision == 'approved' ? 'Approve' : 'Reject', required: decision != 'approved');
    if (r == null) return;
    final t = item['tenantId'];
    final path = switch ('${item['kind']}') {
      'publish' => '/api/v1/platform/public-sites/$t/publish',
      'branding' => '/api/v1/platform/public-sites/$t/branding',
      _ => '/api/v1/platform/public-sites/$t/features/${item['featureKey']}',
    };
    try {
      await widget.api.post(path, {'decision': decision, 'note': r['reason']});
      if (mounted) snack(context, decision == 'approved' ? 'Approved' : 'Rejected');
      await _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Loader<Map<String, dynamic>>(
        key: _loader,
        load: () async => Map<String, dynamic>.from(await widget.api.get('/api/v1/platform/approvals') as Map),
        builder: (context, d, reload) {
          final counts = Map<String, dynamic>.from(d['counts'] as Map? ?? const {});
          final sites = _rows(d['publicSites']);
          final lpc = _rows(d['practitioners']);
          Widget count(String label, dynamic n, {Key? key}) => Expanded(
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(children: [Text('${n ?? 0}', key: key, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: navy)), Text(label, textAlign: TextAlign.center, style: const TextStyle(fontSize: 12))]),
                  ),
                ),
              );
          return ListView(padding: const EdgeInsets.only(bottom: 24), children: [
            Padding(
              padding: const EdgeInsets.all(8),
              child: Row(children: [
                count('Public sites', counts['publicSites'], key: const Key('countSites')),
                count('LPC checks', counts['practitioners'], key: const Key('countLpc')),
                count('Hidden listings', counts['hiddenListings']),
              ]),
            ),
            const SectionTitle('Public sites'),
            if (sites.isEmpty) const ListTile(title: Text('Nothing waiting.')),
            for (final s in sites)
              Card(
                child: ListTile(
                  title: Text('${s['firmName']}'),
                  subtitle: Text([
                    switch ('${s['kind']}') { 'publish' => 'Publish the site', 'branding' => 'Branding change', _ => 'Feature: ${s['featureLabel'] ?? s['featureKey']}' },
                    if (s['draftTagline'] != null) '${s['draftTagline']}',
                    '${s['siteUrl'] ?? ''}',
                  ].join('\n')),
                  isThreeLine: true,
                  trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                    IconButton(key: Key('approve-${s['tenantId']}-${s['kind']}'), tooltip: 'Approve', icon: const Icon(Icons.check_circle_outline, color: Colors.green), onPressed: () => _decideSite(s, 'approved')),
                    IconButton(tooltip: 'Reject', icon: Icon(Icons.cancel_outlined, color: Colors.red.shade700), onPressed: () => _decideSite(s, 'rejected')),
                  ]),
                ),
              ),
            const SectionTitle('LPC verification'),
            if (lpc.isEmpty) const ListTile(title: Text('Nothing waiting.')),
            for (final p in lpc) Card(child: ListTile(title: Text('${p['fullName']}'), subtitle: Text('LPC ${p['lpcNumber'] ?? ''} · ${p['firmName'] ?? ''}'), trailing: const Icon(Icons.chevron_right), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => Scaffold(appBar: AppBar(title: const Text('Directory')), body: DirectoryAdminScreen(api: widget.api)))))),
            const Padding(padding: EdgeInsets.all(16), child: Text('Every decision is written to the platform log and to the firm\'s own log.', style: TextStyle(fontSize: 12, color: Colors.black54))),
          ]);
        },
      );
}

/// Operator-only extras: billing overview, platform log and support staff.
class OperatorMoreScreen extends StatelessWidget {
  const OperatorMoreScreen({super.key, required this.api});
  final ApiClient api;
  @override
  Widget build(BuildContext context) {
    void open(String title, Widget w) => Navigator.push(context, MaterialPageRoute(builder: (_) => Scaffold(appBar: AppBar(title: Text(title)), body: w)));
    return ListView(children: [
      Card(child: ListTile(key: const Key('opStaff'), leading: const Icon(Icons.badge_outlined, color: navy), title: const Text('Support staff'), subtitle: const Text('Invite or remove Owlaw support'), onTap: () => open('Support staff', SupportStaffScreen(api: api)))),
      Card(child: ListTile(leading: const Icon(Icons.payments_outlined, color: navy), title: const Text('Billing overview'), onTap: () => open('Billing overview', BillingOverviewScreen(api: api)))),
      Card(child: ListTile(leading: const Icon(Icons.history, color: navy), title: const Text('Platform audit log'), onTap: () => open('Platform audit log', AuditScreen(api: api)))),
    ]);
  }
}

class SupportStaffScreen extends StatefulWidget {
  const SupportStaffScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<SupportStaffScreen> createState() => _SupportStaffScreenState();
}

class _SupportStaffScreenState extends State<SupportStaffScreen> {
  final _loader = GlobalKey<LoaderState<List<Map<String, dynamic>>>>();

  Future<void> _invite() async {
    final first = TextEditingController(), last = TextEditingController(), email = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Invite support staff'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(controller: first, decoration: const InputDecoration(labelText: 'First name')),
          TextField(controller: last, decoration: const InputDecoration(labelText: 'Last name')),
          TextField(key: const Key('staffEmail'), controller: email, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email')),
          const SizedBox(height: 8),
          const Text('They must set up two-factor sign-in and can only work approvals.', style: TextStyle(fontSize: 12)),
        ]),
        actions: [TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')), FilledButton(key: const Key('staffInvite'), onPressed: () => Navigator.pop(c, true), child: const Text('Invite'))],
      ),
    );
    if (ok != true) return;
    try {
      final r = await widget.api.post('/api/v1/platform/support-staff', {'firstName': first.text.trim(), 'lastName': last.text.trim(), 'email': email.text.trim()}) as Map;
      if (!mounted) return;
      snack(context, r['inviteEmailSent'] == true ? 'Invite emailed' : 'Invite created. Share the set-up link from the web if email is off.');
      await _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Stack(children: [
        Loader<List<Map<String, dynamic>>>(
          key: _loader,
          load: () async => _rows(await widget.api.get('/api/v1/platform/support-staff')),
          isEmpty: (d) => d.isEmpty,
          emptyText: 'No support staff yet.',
          builder: (context, rows, reload) => ListView(children: [
            for (final u in rows)
              Card(
                child: ListTile(
                  title: Text('${u['fullName']}'),
                  subtitle: Text('${u['email']} · ${u['status'] == 'inactive' ? 'Removed' : u['totpEnabled'] == true ? '2FA on' : '2FA not set up yet'}'),
                  trailing: u['status'] == 'inactive'
                      ? null
                      : TextButton(
                          onPressed: () async {
                            final r = await askReason(context, title: 'Remove ${u['fullName']}', action: 'Remove', required: false);
                            if (r == null) return;
                            try {
                              await widget.api.post('/api/v1/platform/support-staff/${u['id']}/remove');
                              await reload();
                            } catch (e) {
                              if (context.mounted) snack(context, cleanError(e), error: true);
                            }
                          },
                          child: const Text('Remove'),
                        ),
                ),
              ),
          ]),
        ),
        Positioned(right: 16, bottom: 16, child: FloatingActionButton.extended(key: const Key('addStaff'), onPressed: _invite, icon: const Icon(Icons.person_add_alt), label: const Text('Invite'))),
      ]);
}

List<Map<String, dynamic>> _rows(dynamic d) => [for (final x in (d as List? ?? const [])) Map<String, dynamic>.from(x as Map)];
String _cap(String s) => s.isEmpty ? s : s[0].toUpperCase() + s.substring(1);
String _label(String key) => _cap(key.replaceAllMapped(RegExp(r'([a-z])([A-Z])'), (m) => '${m[1]} ${m[2]!.toLowerCase()}'));

Color _statusColour(String s) => switch (s) {
      'active' || 'verified' => Colors.green.shade700,
      'suspended' || 'rejected' || 'cancelled' || 'deleted' => Colors.red.shade700,
      _ => Colors.blueGrey,
    };

Widget statusChip(String s) => Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(color: _statusColour(s).withValues(alpha: 0.12), borderRadius: BorderRadius.circular(12)),
      child: Text(_cap(s), style: TextStyle(color: _statusColour(s), fontSize: 12, fontWeight: FontWeight.w600)),
    );

/// Asks for a reason (and optionally more fields). Returns null when cancelled.
Future<Map<String, String>?> askReason(BuildContext context, {required String title, required String action, String? note, Widget Function(void Function(String) set)? extra, bool required = true}) {
  final reason = TextEditingController();
  String extraValue = '';
  return showDialog<Map<String, String>>(
    context: context,
    builder: (c) {
      String? err;
      return StatefulBuilder(
        builder: (c, setS) => AlertDialog(
          title: Text(title),
          content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            if (note != null) Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(note)),
            if (extra != null) extra((v) => extraValue = v),
            TextField(key: const Key('reason'), controller: reason, maxLines: 2, decoration: InputDecoration(labelText: required ? 'Reason (required)' : 'Note (optional)', errorText: err)),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
            FilledButton(
              key: const Key('reasonOk'),
              onPressed: () {
                if (required && reason.text.trim().length < 5) {
                  setS(() => err = 'Give a reason of at least 5 characters.');
                  return;
                }
                Navigator.pop(c, {'reason': reason.text.trim(), 'extra': extraValue});
              },
              child: Text(action),
            ),
          ],
        ),
      );
    },
  );
}

class FirmsScreen extends StatefulWidget {
  const FirmsScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<FirmsScreen> createState() => _FirmsScreenState();
}

class _FirmsScreenState extends State<FirmsScreen> {
  String q = '';
  final _loader = GlobalKey<LoaderState<List<Map<String, dynamic>>>>();

  Future<void> _act(Map<String, dynamic> f, String path, Map<String, dynamic> body, String done) async {
    try {
      await widget.api.post('/api/v1/platform/firms/${f['id']}/$path', body);
      if (mounted) snack(context, done);
      await _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  Future<void> _actions(Map<String, dynamic> f) async {
    final suspended = f['status'] == 'suspended';
    final deleted = f['status'] == 'deleted' || f['status'] == 'purged';
    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (c) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(title: Text('${f['firmName']}', style: const TextStyle(fontWeight: FontWeight.bold))),
          if (f['status'] == 'deleted')
            ListTile(key: const Key('restore'), leading: const Icon(Icons.restore), title: const Text('Restore firm'), onTap: () => Navigator.pop(c, 'restore')),
          if (f['status'] == 'deleted')
            ListTile(
              key: const Key('purgeFirm'),
              leading: Icon(Icons.local_fire_department_outlined, color: Colors.red.shade700),
              title: Text('Purge for good', style: TextStyle(color: Colors.red.shade700)),
              subtitle: Text('Allowed from ${niceDate(f['purgeAvailableAt'])}. This cannot be undone.'),
              onTap: () => Navigator.pop(c, 'purge'),
            ),
          if (deleted)
            const ListTile(subtitle: Text('Deleted firms can be restored until they are purged.'))
          else ...[
          if (suspended)
            ListTile(key: const Key('reactivate'), leading: const Icon(Icons.play_circle_outline), title: const Text('Reactivate'), onTap: () => Navigator.pop(c, 'reactivate'))
          else
            ListTile(key: const Key('suspend'), leading: const Icon(Icons.block), title: const Text('Suspend'), onTap: () => Navigator.pop(c, 'suspend')),
          if (f['status'] == 'trial') ListTile(key: const Key('extendTrial'), leading: const Icon(Icons.more_time), title: const Text('Extend trial'), onTap: () => Navigator.pop(c, 'extend')),
          ListTile(key: const Key('changePlan'), leading: const Icon(Icons.swap_horiz), title: const Text('Change plan'), onTap: () => Navigator.pop(c, 'plan')),
          ListTile(key: const Key('deleteFirm'), leading: Icon(Icons.delete_forever, color: Colors.red.shade700), title: Text('Delete firm', style: TextStyle(color: Colors.red.shade700)), onTap: () => Navigator.pop(c, 'delete')),
          ],
        ]),
      ),
    );
    if (!mounted || choice == null) return;
    switch (choice) {
      case 'suspend':
        final r = await askReason(context, title: 'Suspend ${f['firmName']}', action: 'Suspend', note: 'Everyone at the firm is signed out and cannot sign in. Their client portal closes too.');
        if (r != null) await _act(f, 'suspend', {'reason': r['reason']}, 'Firm suspended');
      case 'delete':
        final name = '${f['firmName']}'.trim();
        final r = await askReason(context, title: 'Delete $name', action: 'Delete firm',
            note: 'Everyone at the firm is signed out and blocked, and it leaves the directory and public site at once. You can restore it. Its data can be purged for good after 30 days.',
            extra: (set) => TextField(key: const Key('confirmName'), decoration: InputDecoration(labelText: 'Type "$name" to confirm'), onChanged: set));
        if (r == null) return;
        if ((r['extra'] ?? '').trim() != name) {
          if (mounted) snack(context, 'The firm name did not match, so nothing was deleted.', error: true);
          return;
        }
        await _act(f, 'delete', {'reason': r['reason'], 'confirmName': r['extra']!.trim()}, 'Firm deleted');
      case 'purge':
        final name = '${f['firmName']}'.trim();
        final r = await askReason(context, title: 'Purge $name', action: 'Purge for good',
            note: 'Every record of this firm is removed permanently. The platform log keeps only that the purge happened.',
            extra: (set) => TextField(key: const Key('confirmPurgeName'), decoration: InputDecoration(labelText: 'Type "$name" to confirm'), onChanged: set));
        if (r == null) return;
        if ((r['extra'] ?? '').trim() != name) {
          if (mounted) snack(context, 'The firm name did not match, so nothing was purged.', error: true);
          return;
        }
        await _act(f, 'purge', {'reason': r['reason'], 'confirmName': r['extra']!.trim()}, 'Firm purged');
      case 'restore':
        final r = await askReason(context, title: 'Restore ${f['firmName']}', action: 'Restore');
        if (r != null) await _act(f, 'restore', {'reason': r['reason']}, 'Firm restored');
      case 'reactivate':
        final r = await askReason(context, title: 'Reactivate ${f['firmName']}', action: 'Reactivate');
        if (r != null) await _act(f, 'reactivate', {'reason': r['reason']}, 'Firm reactivated');
      case 'extend':
        final r = await askReason(context, title: 'Extend trial', action: 'Extend', extra: (set) {
          set('7');
          return TextFormField(key: const Key('days'), initialValue: '7', keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Days (1 to 90)'), onChanged: set);
        });
        if (r != null) await _act(f, 'extend-trial', {'reason': r['reason'], 'days': int.tryParse(r['extra'] ?? '') ?? 0}, 'Trial extended');
      case 'plan':
        List<Map<String, dynamic>> plans;
        try {
          plans = _rows(await widget.api.get('/api/v1/platform/plans'));
        } catch (e) {
          if (mounted) snack(context, cleanError(e), error: true);
          return;
        }
        if (!mounted) return;
        final r = await askReason(context, title: 'Change plan', action: 'Change plan', extra: (set) {
          final first = plans.firstWhere((p) => p['slug'] != f['planSlug'], orElse: () => plans.first);
          set('${first['slug']}');
          return DropdownButtonFormField<String>(
            key: const Key('plan'),
            initialValue: '${first['slug']}',
            decoration: const InputDecoration(labelText: 'New plan'),
            items: [for (final p in plans) DropdownMenuItem(value: '${p['slug']}', child: Text('${p['name']} (${money(p['priceMonthly'])})'))],
            onChanged: (v) => set(v ?? ''),
          );
        });
        if (r != null) await _act(f, 'plan', {'reason': r['reason'], 'planSlug': r['extra']}, 'Plan changed');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
        child: TextField(
          key: const Key('firmSearch'),
          decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search firms by name, Firm ID or email'),
          onChanged: (v) => setState(() => q = v.trim().toLowerCase()),
        ),
      ),
      Expanded(
        child: Loader<List<Map<String, dynamic>>>(
          key: _loader,
          load: () async => _rows(await widget.api.get('/api/v1/platform/firms')),
          isEmpty: (d) => d.isEmpty,
          emptyText: 'No firms yet.',
          builder: (context, firms, reload) {
            final shown = firms.where((f) => q.isEmpty || '${f['firmName']} ${f['slug']} ${f['email']}'.toLowerCase().contains(q)).toList();
            return ListView.separated(
              padding: const EdgeInsets.all(12),
              itemCount: shown.length,
              separatorBuilder: (_, __) => const SizedBox(height: 8),
              itemBuilder: (c, i) {
                final f = shown[i];
                return Card(
                  margin: EdgeInsets.zero,
                  child: ListTile(
                    key: Key('firm-${f['slug']}'),
                    onTap: () => _actions(f),
                    title: Row(children: [Expanded(child: Text('${f['firmName']}', style: const TextStyle(fontWeight: FontWeight.w600))), statusChip('${f['status']}')]),
                    subtitle: Text([
                      '${f['planName']} · ${f['seats']} seat${f['seats'] == 1 ? '' : 's'} · Firm ID ${f['slug']}',
                      'Created ${niceDate(f['createdAt'])}${f['trialEndsAt'] != null && f['status'] == 'trial' ? ' · trial ends ${niceDate(f['trialEndsAt'])}' : ''}',
                      'Last activity: ${f['lastActivityAt'] == null ? 'none' : niceDate(f['lastActivityAt'], withTime: true)}',
                      if (f['suspendedReason'] != null && f['status'] == 'suspended') 'Suspended: ${f['suspendedReason']}',
                      if (f['status'] == 'deleted') 'Deleted: ${f['deletedReason'] ?? ''} · purge from ${niceDate(f['purgeAvailableAt'])}',
                    ].join('\n')),
                    isThreeLine: true,
                  ),
                );
              },
            );
          },
        ),
      ),
    ]);
  }
}

class DirectoryAdminScreen extends StatelessWidget {
  const DirectoryAdminScreen({super.key, required this.api});
  final ApiClient api;
  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Column(children: [
        const TabBar(labelColor: navy, tabs: [Tab(text: 'LPC checks'), Tab(text: 'Listings')]),
        Expanded(child: TabBarView(children: [_LpcQueue(api: api), _Listings(api: api)])),
      ]),
    );
  }
}

class _LpcQueue extends StatefulWidget {
  const _LpcQueue({required this.api});
  final ApiClient api;
  @override
  State<_LpcQueue> createState() => _LpcQueueState();
}

class _LpcQueueState extends State<_LpcQueue> {
  final _loader = GlobalKey<LoaderState<List<Map<String, dynamic>>>>();

  Future<void> _decide(Map<String, dynamic> p, String decision) async {
    final r = await askReason(context,
        title: decision == 'verified' ? 'Mark LPC-verified' : 'Reject',
        action: decision == 'verified' ? 'Verify' : 'Reject',
        note: 'Check ${p['fullName']} (${p['lpcNumber']}) on the Legal Practice Council roll first.',
        required: decision == 'rejected');
    if (r == null) return;
    try {
      await widget.api.post('/api/v1/platform/directory/practitioners/${p['id']}/verify', {'decision': decision, 'note': r['reason']});
      if (mounted) snack(context, decision == 'verified' ? 'Practitioner verified' : 'Practitioner rejected');
      await _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Loader<List<Map<String, dynamic>>>(
        key: _loader,
        load: () async => _rows(await widget.api.get('/api/v1/platform/directory/practitioners?status=pending')),
        isEmpty: (d) => d.isEmpty,
        emptyText: 'No practitioners waiting for an LPC check.',
        emptyIcon: Icons.verified_outlined,
        builder: (context, rows, reload) => ListView(padding: const EdgeInsets.all(12), children: [
          for (final p in rows)
            Card(
              key: Key('lpc-${p['id']}'),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${p['fullName']}', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                  Text('${p['lpcNumber']} · ${p['firmName'] ?? ''}'),
                  Text(p['fidelityExpiresOn'] == null ? 'No Fidelity Fund certificate date' : 'Fidelity Fund certificate until ${niceDate(p['fidelityExpiresOn'])}',
                      style: TextStyle(color: p['fidelityCurrent'] == false ? Colors.red.shade700 : Colors.black54)),
                  const SizedBox(height: 8),
                  Wrap(spacing: 8, children: [
                    FilledButton(key: Key('verify-${p['id']}'), onPressed: () => _decide(p, 'verified'), child: const Text('Verify')),
                    OutlinedButton(key: Key('reject-${p['id']}'), onPressed: () => _decide(p, 'rejected'), child: const Text('Reject')),
                  ]),
                ]),
              ),
            ),
        ]),
      );
}

class _Listings extends StatefulWidget {
  const _Listings({required this.api});
  final ApiClient api;
  @override
  State<_Listings> createState() => _ListingsState();
}

class _ListingsState extends State<_Listings> {
  final _loader = GlobalKey<LoaderState<List<Map<String, dynamic>>>>();

  Future<void> _toggle(Map<String, dynamic> l) async {
    final hide = l['operatorHidden'] != true;
    final r = await askReason(context, title: hide ? 'Hide ${l['firmName']}' : 'Restore ${l['firmName']}', action: hide ? 'Hide listing' : 'Restore', required: hide);
    if (r == null) return;
    try {
      await widget.api.post('/api/v1/platform/directory/listings/${l['tenantId']}/visibility', {'hidden': hide, 'reason': r['reason']});
      if (mounted) snack(context, hide ? 'Listing hidden' : 'Listing restored');
      await _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Loader<List<Map<String, dynamic>>>(
        key: _loader,
        load: () async => _rows(await widget.api.get('/api/v1/platform/directory/listings')),
        isEmpty: (d) => d.isEmpty,
        emptyText: 'No firm has a directory profile yet.',
        builder: (context, rows, reload) => ListView(padding: const EdgeInsets.all(12), children: [
          for (final l in rows)
            Card(
              key: Key('listing-${l['slug']}'),
              child: ListTile(
                title: Row(children: [
                  Expanded(child: Text('${l['firmName']}', style: const TextStyle(fontWeight: FontWeight.w600))),
                  statusChip(l['operatorHidden'] == true ? 'hidden' : (l['listed'] == true ? 'listed' : 'not listed')),
                ]),
                subtitle: Text([
                  (_strings(l['practiceAreaLabels'] ?? l['practiceAreas'])).join(', '),
                  (_strings(l['cityLabels'] ?? l['cities'])).join(', '),
                  if (l['operatorHidden'] == true && l['hiddenReason'] != null) 'Hidden: ${l['hiddenReason']}',
                ].where((x) => x.isNotEmpty).join('\n')),
                trailing: TextButton(onPressed: () => _toggle(l), child: Text(l['operatorHidden'] == true ? 'Restore' : 'Hide')),
              ),
            ),
        ]),
      );
}

List<String> _strings(dynamic v) => [for (final x in (v as List? ?? const [])) '$x'];

class SupportScreen extends StatelessWidget {
  const SupportScreen({super.key, required this.api});
  final ApiClient api;
  @override
  Widget build(BuildContext context) => Loader<List<Map<String, dynamic>>>(
        load: () async => _rows(await api.get('/api/v1/platform/support')),
        isEmpty: (d) => d.isEmpty,
        emptyText: 'No firm has granted support access right now. Firms grant read-only access from their Owlaw support access page.',
        emptyIcon: Icons.lock_outline,
        builder: (context, grants, reload) => ListView(padding: const EdgeInsets.all(12), children: [
          const Padding(
            padding: EdgeInsets.only(bottom: 8),
            child: Text('Read-only. Every view is logged and shown to the firm.', style: TextStyle(color: Colors.black54)),
          ),
          for (final g in grants)
            Card(
              key: Key('grant-${g['id']}'),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${g['firmName']}', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                  Text('${g['reason']}'),
                  Text('Granted by ${g['grantedByEmail'] ?? ''} · ends ${niceDate(g['expiresAt'], withTime: true)}', style: const TextStyle(color: Colors.black54)),
                  Wrap(spacing: 8, children: [
                    for (final s in const ['overview', 'matters', 'clients'])
                      OutlinedButton(
                        key: Key('look-$s'),
                        onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => SupportLookScreen(api: api, tenantId: '${g['tenantId']}', firmName: '${g['firmName']}', section: s))),
                        child: Text('View $s'),
                      ),
                  ]),
                ]),
              ),
            ),
        ]),
      );
}

class SupportLookScreen extends StatelessWidget {
  const SupportLookScreen({super.key, required this.api, required this.tenantId, required this.firmName, required this.section});
  final ApiClient api;
  final String tenantId, firmName, section;
  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text('$firmName: ${_cap(section)}')),
        body: Loader<Map<String, dynamic>>(
          load: () async => Map<String, dynamic>.from(await api.get('/api/v1/platform/support/$tenantId/$section') as Map),
          builder: (context, d, reload) {
            final rows = _rows(d['rows']);
            return ListView(padding: const EdgeInsets.all(12), children: [
              const Text('Read-only. This view was logged and the firm can see it.', style: TextStyle(color: Colors.black54)),
              const SizedBox(height: 8),
              if (section == 'overview') ...[
                Text('${d['matters'] ?? 0} matters (${d['openMatters'] ?? 0} open), ${d['clients'] ?? 0} clients', style: const TextStyle(fontWeight: FontWeight.w600)),
                for (final u in _rows(d['users'])) ListTile(dense: true, title: Text('${u['name']}'), subtitle: Text('${u['role']} · ${u['status']}')),
              ] else if (rows.isEmpty)
                const Text('Nothing here.')
              else
                for (final r in rows)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Text([for (final e in r.entries) if (e.key != 'id' && e.value != null) '${_label(e.key)}: ${e.value}'].join('\n')),
                    ),
                  ),
            ]);
          },
        ),
      );
}

class BillingOverviewScreen extends StatelessWidget {
  const BillingOverviewScreen({super.key, required this.api});
  final ApiClient api;
  @override
  Widget build(BuildContext context) => Loader<Map<String, dynamic>>(
        load: () async => Map<String, dynamic>.from(await api.get('/api/v1/platform/billing') as Map),
        builder: (context, d, reload) {
          final firms = _rows(d['firms']);
          Widget tile(String label, String value) => Expanded(
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(label, style: const TextStyle(color: Colors.black54, fontSize: 12)),
                      Text(value, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: navy)),
                    ]),
                  ),
                ),
              );
          return ListView(padding: const EdgeInsets.all(12), children: [
            Row(children: [tile('MRR (excl. VAT)', money(d['totalMrr'])), tile('Paying firms', '${d['payingFirms'] ?? 0}'), tile('Unpaid', money(d['totalUnpaid']))]),
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 8),
              child: Text('From active firms only: seat price times active attorneys, plus staff seats at R349.', style: TextStyle(color: Colors.black54)),
            ),
            for (final f in firms)
              Card(
                key: Key('bill-${f['id']}'),
                child: ListTile(
                  title: Row(children: [Expanded(child: Text('${f['name']}', style: const TextStyle(fontWeight: FontWeight.w600))), statusChip('${f['status']}')]),
                  subtitle: Text([
                    '${f['plan']} · ${f['attorneySeats']} attorney${f['attorneySeats'] == 1 ? '' : 's'}, ${f['staffSeats']} staff',
                    'MRR ${money(f['mrr'])}${(f['mrr'] as num? ?? 0) == 0 && (f['listValue'] as num? ?? 0) > 0 ? ' (${money(f['listValue'])} when active)' : ''}',
                    'Unpaid: ${(f['unpaidCount'] ?? 0) == 0 ? 'none' : '${f['unpaidCount']} (${money(f['unpaidTotal'])})'}',
                    'Calls this month: ${f['callMinutesThisMonth'] ?? 0} min, ${money(f['callChargesThisMonth'])}',
                  ].join('\n')),
                  isThreeLine: true,
                ),
              ),
          ]);
        },
      );
}

class AuditScreen extends StatelessWidget {
  const AuditScreen({super.key, required this.api});
  final ApiClient api;
  @override
  Widget build(BuildContext context) => Loader<List<Map<String, dynamic>>>(
        load: () async => _rows(await api.get('/api/v1/platform/audit')),
        isEmpty: (d) => d.isEmpty,
        emptyText: 'No platform actions yet.',
        builder: (context, rows, reload) => ListView.separated(
          padding: const EdgeInsets.all(12),
          itemCount: rows.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (c, i) {
            final r = rows[i];
            return ListTile(
              key: Key('audit-$i'),
              title: Text('${r['label'] ?? r['action']}${r['firmName'] != null ? ' · ${r['firmName']}' : ''}'),
              subtitle: Text('${niceDate(r['createdAt'], withTime: true)} · ${r['actorEmail'] ?? ''}${r['detail'] != null ? '\n${r['detail']}' : ''}'),
            );
          },
        ),
      );
}
