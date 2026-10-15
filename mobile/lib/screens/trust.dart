import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../api/owlaw_api.dart';
import '../ui/common.dart';
import '../ui/forms.dart';
import 'billing.dart';

num _n(dynamic v) => v is num ? v : num.tryParse('$v') ?? 0;

/// Pays an invoice from the client's trust ledger. The API checks the invoice is sent,
/// the ledger belongs to the same client and holds enough, and that a limited file is not posted.
Future<bool> showTrustToFeeSheet(BuildContext context, ApiClient api, {required Json invoice}) async {
  final accounts = (invoice['trustAccounts'] as List? ?? const []).map((e) => (e as Map).cast<String, dynamic>()).where((a) => _n(a['clientLedger']) > 0).toList();
  if (accounts.isEmpty) {
    snack(context, 'This client has no trust money to apply.', error: true);
    return false;
  }
  final cur = currencyOf(api);
  String? account = '${accounts.first['accountId']}';
  num suggest(String? id) {
    final a = accounts.where((x) => '${x['accountId']}' == id).firstOrNull;
    final ledger = a == null ? 0 : _n(a['clientLedger']);
    final owing = _n(invoice['balanceDue']);
    return ledger < owing ? ledger : owing;
  }

  final amount = TextEditingController(text: suggest(account).toStringAsFixed(2));
  final note = TextEditingController(text: 'Trust to fees: ${invoice['invoiceNumber']}');
  return showActionSheet(
    context,
    title: 'Pay from trust',
    intro: '${invoice['invoiceNumber']} has ${money(invoice['balanceDue'], cur)} owing. Only this client\'s ledger can be used.',
    actionLabel: 'Transfer to fees',
    fields: (setState) => [
      choice('Trust account', account, accounts.map((a) => MapEntry('${a['accountId']}', '${a['accountName']} · ${money(a['clientLedger'], cur)} for this client')).toList(), (v) {
        setState(() {
          account = v;
          amount.text = suggest(v).toStringAsFixed(2);
        });
      }),
      TextField(key: const Key('trustAmount'), controller: amount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: field('Amount')),
      TextField(controller: note, decoration: field('Note')),
    ],
    onSubmit: () async {
      if (account == null) throw Exception('Choose the trust account.');
      await api.applyTrust('${invoice['id']}', accountId: account!, amount: requireAmount(amount.text), note: note.text);
    },
  );
}
