import 'package:flutter/material.dart';

import '../api/owlaw_api.dart';
import 'common.dart';
import 'theme.dart';

/// A bottom sheet with a title, fields and one primary action.
/// The action returns normally to close the sheet, or throws to show the error inline.
Future<bool> showActionSheet(
  BuildContext context, {
  required String title,
  required String actionLabel,
  required List<Widget> Function(StateSetter setState) fields,
  required Future<void> Function() onSubmit,
  String? intro,
}) async {
  final done = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (c) {
      String? error;
      var busy = false;
      return StatefulBuilder(builder: (c, setState) {
        return Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.of(c).viewInsets.bottom),
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
              Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: navy)),
              if (intro != null) Padding(padding: const EdgeInsets.only(top: 6), child: Text(intro, style: const TextStyle(color: Colors.black54))),
              const SizedBox(height: 12),
              ...fields(setState).expand((w) => [w, const SizedBox(height: 12)]),
              if (error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(error!, style: TextStyle(color: Colors.red.shade700)),
                ),
              FilledButton(
                onPressed: busy
                    ? null
                    : () async {
                        setState(() {
                          busy = true;
                          error = null;
                        });
                        try {
                          await onSubmit();
                          if (c.mounted) Navigator.pop(c, true);
                        } catch (e) {
                          setState(() {
                            busy = false;
                            error = cleanError(e);
                          });
                        }
                      },
                child: busy ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2)) : Text(actionLabel),
              ),
            ]),
          ),
        );
      });
    },
  );
  return done == true;
}

InputDecoration field(String label, {String? hint}) => InputDecoration(labelText: label, hintText: hint, border: const OutlineInputBorder());

/// A dropdown of labelled choices. Value null means "none chosen".
Widget choice(String label, String? value, List<MapEntry<String, String>> options, ValueChanged<String?> onChanged, {String? noneLabel}) {
  final ids = options.map((e) => e.key).toSet();
  return DropdownButtonFormField<String>(
    initialValue: ids.contains(value) ? value : null,
    isExpanded: true,
    decoration: field(label),
    items: [
      if (noneLabel != null) DropdownMenuItem<String>(value: null, child: Text(noneLabel)),
      ...options.map((e) => DropdownMenuItem<String>(value: e.key, child: Text(e.value, overflow: TextOverflow.ellipsis))),
    ],
    onChanged: onChanged,
  );
}

String clientLabel(Json c) {
  final n = '${c['displayName'] ?? c['companyName'] ?? ''}'.trim();
  if (n.isNotEmpty && n != 'null') return n;
  return '${c['firstName'] ?? ''} ${c['lastName'] ?? ''}'.trim();
}

String matterLabel(Json m) => '${m['caseNumber'] ?? ''} · ${m['title'] ?? ''}';

String today() {
  final d = DateTime.now();
  return '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
}

/// Accepts "1200", "1 200,50" or "R1,200.50" and returns "1200.50", or null if it is not an amount.
String? parseAmount(String raw) {
  var s = raw.trim().replaceAll(RegExp(r'[Rr$\s]'), '');
  if (s.isEmpty) return null;
  if (s.contains(',') && !s.contains('.')) {
    s = s.replaceAll(',', '.');
  } else {
    s = s.replaceAll(',', '');
  }
  final v = double.tryParse(s);
  if (v == null || v <= 0) return null;
  return v.toStringAsFixed(2);
}

String requireAmount(String raw) {
  final a = parseAmount(raw);
  if (a == null) throw Exception('Enter the amount as a number more than zero.');
  return a;
}
