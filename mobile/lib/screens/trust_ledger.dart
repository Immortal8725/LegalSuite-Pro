import 'package:flutter/material.dart';

import '../api/access.dart';
import '../api/api_client.dart';
import '../api/owlaw_api.dart';
import '../ui/common.dart';
import '../ui/forms.dart';
import '../ui/theme.dart';
import 'billing.dart';

class TrustScreen extends StatefulWidget {
  const TrustScreen({super.key, required this.api, required this.access, this.clientId});
  final ApiClient api;
  final Access access;
  final String? clientId;
  @override
  State<TrustScreen> createState() => _TrustScreenState();
}

class _TrustData {
  _TrustData(this.accounts, this.clients, this.cases);
  final List<Json> accounts;
  final List<Json> clients;
  final List<Json> cases;
}

class _TrustScreenState extends State<TrustScreen> {
  final _loader = GlobalKey<LoaderState<_TrustData>>();

  Future<_TrustData> _load() async {
    final r = await Future.wait([widget.api.trustAccounts(), widget.api.clients(), widget.api.cases()]);
    return _TrustData(r[0], r[1], r[2]);
  }

  @override
  Widget build(BuildContext context) {
    final cur = currencyOf(widget.api);
    final label = '${widget.api.tenant?['trustLabel'] ?? 'Trust'}';
    return Scaffold(
      appBar: AppBar(title: Text(label == 'null' ? 'Trust' : '$label trust')),
      body: Loader<_TrustData>(
        key: _loader,
        load: _load,
        isEmpty: (d) => d.accounts.isEmpty,
        emptyText: 'No trust account yet. The firm sets one up on the web.',
        builder: (context, d, reload) {
          final names = {for (final c in d.clients) '${c['id']}': clientLabel(c)};
          return ListView(padding: const EdgeInsets.only(bottom: 32), children: [
            for (final a in d.accounts) ...[
              Card(
                child: ListTile(
                  leading: const Icon(Icons.account_balance, color: navy),
                  title: Text('${a['accountName']}'),
                  subtitle: Text('${a['bankName'] ?? ''}\nRecon: ${statusLabel((a['recon'] as Map?)?['status'] ?? 'unknown')} · last ${niceDate(a['lastReconciledAt'])}'),
                  isThreeLine: true,
                  trailing: Text(money(a['balance'], cur), style: const TextStyle(fontWeight: FontWeight.bold, color: navy)),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: OutlinedButton.icon(
                  icon: const Icon(Icons.add),
                  label: const Text('Record trust deposit'),
                  onPressed: () async {
                    if (await _deposit(a, d) && context.mounted) {
                      snack(context, 'Trust deposit recorded');
                      reload();
                    }
                  },
                ),
              ),
              const SectionTitle('Client ledgers'),
              ...(((a['recon'] as Map?)?['ledgers'] as List?) ?? const [])
                  .cast<Map>()
                  .where((l) => widget.clientId == null || '${l['clientId']}' == widget.clientId)
                  .map((l) => ListTile(
                        leading: const Icon(Icons.person_outline),
                        title: Text(names['${l['clientId']}'] ?? (l['clientId'] == null ? 'Unallocated' : 'Client')),
                        trailing: Text(money(l['balance'], cur)),
                        onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                                builder: (_) => TrustLedgerScreen(
                                    api: widget.api, accountId: '${a['id'] ?? a['recon']?['accountId']}', clientId: l['clientId']?.toString(), title: names['${l['clientId']}'] ?? 'Ledger'))),
                      )),
              ListTile(
                leading: const Icon(Icons.list_alt),
                title: const Text('Whole account ledger'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => TrustLedgerScreen(api: widget.api, accountId: '${a['id'] ?? a['recon']?['accountId']}', title: '${a['accountName']}'))),
              ),
            ],
          ]);
        },
      ),
    );
  }

  Future<bool> _deposit(Json account, _TrustData d) async {
    String? client = widget.clientId;
    String? matter;
    final amount = TextEditingController();
    final memo = TextEditingController();
    return showActionSheet(
      context,
      title: 'Record trust deposit',
      intro: 'Money received into ${account['accountName']}. It goes on one client\'s ledger and is never mixed with another client\'s.',
      actionLabel: 'Record deposit',
      fields: (setState) => [
        choice('Client', client, d.clients.map((c) => MapEntry('${c['id']}', clientLabel(c))).toList(), (v) => setState(() {
              client = v;
              matter = null;
            }), noneLabel: 'Unallocated (no client yet)'),
        choice('Matter', matter, d.cases.where((m) => client == null || '${m['clientId']}' == client).map((m) => MapEntry('${m['id']}', matterLabel(m))).toList(),
            (v) => setState(() => matter = v), noneLabel: 'No matter'),
        TextField(controller: amount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: field('Amount')),
        TextField(controller: memo, decoration: field('Memo', hint: 'Deposit from client for costs')),
      ],
      onSubmit: () => widget.api.trustMove({
        'accountId': '${account['id'] ?? account['recon']?['accountId']}',
        'type': 'deposit',
        'amount': requireAmount(amount.text),
        'description': memo.text.trim().isEmpty ? 'Trust deposit' : memo.text,
        'clientId': client,
        'caseId': matter,
      }),
    );
  }
}

class TrustLedgerScreen extends StatelessWidget {
  const TrustLedgerScreen({super.key, required this.api, required this.accountId, required this.title, this.clientId});
  final ApiClient api;
  final String accountId;
  final String? clientId;
  final String title;

  @override
  Widget build(BuildContext context) {
    final cur = currencyOf(api);
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Loader<List<Json>>(
        load: () async => (await api.trustLedger(accountId)).where((t) => clientId == null || '${t['clientId']}' == clientId).toList(),
        isEmpty: (d) => d.isEmpty,
        emptyText: 'No trust movements yet.',
        builder: (context, list, reload) => ListView(
          children: list.map((t) {
            final type = '${t['type']}';
            final out = type != 'deposit';
            return ListTile(
              leading: Icon(out ? Icons.arrow_upward : Icons.arrow_downward, color: out ? Colors.red.shade700 : Colors.green.shade700),
              title: Text('${t['description'] ?? statusLabel(type)}'),
              subtitle: Text('${statusLabel(type)} · ${niceDate(t['createdAt'], withTime: true)}'),
              trailing: Text('${out ? '-' : ''}${money(t['amount'], cur)}', style: TextStyle(color: out ? Colors.red.shade700 : Colors.green.shade800, fontWeight: FontWeight.w600)),
            );
          }).toList(),
        ),
      ),
    );
  }
}
