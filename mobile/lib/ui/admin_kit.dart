import 'package:flutter/material.dart';

import '../api/owlaw_api.dart';
import 'common.dart';
import 'forms.dart';
import 'theme.dart';

/// Building blocks for the firm administration screens: typed form fields, a form sheet,
/// a list screen with row actions and a read-only record view. Every screen calls existing API endpoints.
enum FieldType { text, multiline, number, boolean, choice, date, csv, email, phone, password }

class FieldSpec {
  const FieldSpec(this.key, this.label, {this.type = FieldType.text, this.options = const [], this.hint, this.required = false});
  final String key;
  final String label;
  final FieldType type;
  final List<MapEntry<String, String>> options;
  final String? hint;
  final bool required;
}

String _str(dynamic v) {
  if (v == null || '$v' == 'null') return '';
  if (v is List) return v.join(', ');
  return '$v';
}

/// Shows a sheet with [fields], pre-filled from [initial]. Returns true once [onSubmit] succeeds.
/// Values are sent typed: numbers as numbers, switches as booleans, comma lists as lists.
Future<bool> showFormSheet(
  BuildContext context, {
  required String title,
  required List<FieldSpec> fields,
  Json initial = const {},
  String actionLabel = 'Save',
  String? intro,
  required Future<void> Function(Json values) onSubmit,
}) {
  final ctrls = {for (final f in fields) if (f.type != FieldType.boolean && f.type != FieldType.choice) f.key: TextEditingController(text: _date(f, initial[f.key]))};
  final picks = <String, dynamic>{
    for (final f in fields)
      if (f.type == FieldType.boolean) f.key: initial[f.key] == true,
    for (final f in fields)
      if (f.type == FieldType.choice) f.key: initial[f.key] == null ? null : '${initial[f.key]}',
  };
  return showActionSheet(
    context,
    title: title,
    intro: intro,
    actionLabel: actionLabel,
    fields: (setState) => fields.map<Widget>((f) {
      switch (f.type) {
        case FieldType.boolean:
          return SwitchListTile(
            key: Key('f_${f.key}'),
            contentPadding: EdgeInsets.zero,
            title: Text(f.label),
            subtitle: f.hint == null ? null : Text(f.hint!),
            value: picks[f.key] == true,
            onChanged: (v) => setState(() => picks[f.key] = v),
          );
        case FieldType.choice:
          return KeyedSubtree(key: Key('f_${f.key}'), child: choice(f.label, picks[f.key] as String?, f.options, (v) => setState(() => picks[f.key] = v), noneLabel: f.required ? null : 'Not set'));
        default:
          return TextField(
            key: Key('f_${f.key}'),
            controller: ctrls[f.key],
            obscureText: f.type == FieldType.password,
            maxLines: f.type == FieldType.multiline ? 5 : 1,
            keyboardType: switch (f.type) {
              FieldType.number => const TextInputType.numberWithOptions(decimal: true),
              FieldType.email => TextInputType.emailAddress,
              FieldType.phone => TextInputType.phone,
              FieldType.multiline => TextInputType.multiline,
              _ => TextInputType.text,
            },
            decoration: field(f.label, hint: f.hint ?? (f.type == FieldType.date ? 'YYYY-MM-DD' : f.type == FieldType.csv ? 'Separate with commas' : null)),
          );
      }
    }).toList(),
    onSubmit: () async {
      final out = <String, dynamic>{};
      for (final f in fields) {
        if (f.type == FieldType.boolean) {
          out[f.key] = picks[f.key] == true;
          continue;
        }
        if (f.type == FieldType.choice) {
          if (f.required && picks[f.key] == null) throw Exception('Choose ${f.label.toLowerCase()}.');
          out[f.key] = picks[f.key];
          continue;
        }
        final t = ctrls[f.key]!.text.trim();
        if (f.required && t.isEmpty) throw Exception('Enter ${f.label.toLowerCase()}.');
        if (f.type == FieldType.number) {
          out[f.key] = t.isEmpty ? null : (num.tryParse(t.replaceAll(',', '.')) ?? (throw Exception('${f.label} must be a number.')));
        } else if (f.type == FieldType.csv) {
          out[f.key] = t.isEmpty ? <String>[] : t.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
        } else if (f.type == FieldType.date && t.isNotEmpty && DateTime.tryParse(t) == null) {
          throw Exception('${f.label} must be a date like 2026-10-21.');
        } else {
          out[f.key] = t.isEmpty ? null : t;
        }
      }
      await onSubmit(out);
    },
  );
}

String _date(FieldSpec f, dynamic v) {
  final s = _str(v);
  return f.type == FieldType.date && s.length >= 10 ? s.substring(0, 10) : s;
}

/// Human label for an API key such as "nextReviewDate" or "fica_id".
String labelOf(String key) {
  final spaced = key.replaceAll('_', ' ').replaceAllMapped(RegExp(r'(?<=[a-z0-9])([A-Z])'), (m) => ' ${m[1]!.toLowerCase()}');
  return spaced.isEmpty ? key : spaced[0].toUpperCase() + spaced.substring(1);
}

String pretty(dynamic v) {
  if (v == null || '$v' == 'null') return '';
  if (v is bool) return v ? 'Yes' : 'No';
  if (v is List) return v.map(pretty).join(', ');
  if (v is Map) return v.entries.map((e) => '${labelOf('${e.key}')}: ${pretty(e.value)}').join('\n');
  final s = '$v';
  if (RegExp(r'^\d{4}-\d{2}-\d{2}T').hasMatch(s)) return niceDate(s, withTime: true);
  return s.contains('_') && !s.contains(' ') && s.length < 30 ? labelOf(s) : s;
}

/// Read-only view of every field on a record, skipping ids and empty values.
class RecordView extends StatelessWidget {
  const RecordView({super.key, required this.record, this.hide = const {}});
  final Json record;
  final Set<String> hide;
  @override
  Widget build(BuildContext context) {
    final rows = record.entries.where((e) => !hide.contains(e.key) && !e.key.startsWith('_') && e.key != 'id' && !e.key.endsWith('Id') && pretty(e.value).isNotEmpty);
    return Card(
      child: Column(children: rows.map((e) => ListTile(dense: true, title: Text(pretty(e.value)), subtitle: Text(labelOf(e.key)))).toList()),
    );
  }
}

class RowAction {
  const RowAction(this.label, this.icon, this.run, {this.visible, this.confirm});
  final String label;
  final IconData icon;
  final Future<String?> Function(BuildContext context, Json row) run;
  final bool Function(Json row)? visible;

  /// When set, asks this question before running.
  final String? confirm;
}

Future<bool> confirmDialog(BuildContext context, String question, {String yes = 'Yes'}) async =>
    await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        content: Text(question),
        actions: [TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')), FilledButton(onPressed: () => Navigator.pop(c, true), child: Text(yes))],
      ),
    ) ==
    true;

/// A list of records with pull to refresh, a detail sheet with actions, and an optional add button.
class RecordListScreen extends StatefulWidget {
  const RecordListScreen({
    super.key,
    required this.title,
    required this.load,
    required this.titleOf,
    this.subtitleOf,
    this.trailingOf,
    this.actions = const [],
    this.onAdd,
    this.addLabel = 'Add',
    this.emptyText = 'Nothing here yet.',
    this.intro,
    this.onTap,
  });
  final String title;
  final Future<List<Json>> Function() load;
  final String Function(Json) titleOf;
  final String Function(Json)? subtitleOf;
  final String Function(Json)? trailingOf;
  final List<RowAction> actions;
  final Future<bool> Function(BuildContext context)? onAdd;
  final String addLabel;
  final String emptyText;
  final String? intro;
  final Future<void> Function(BuildContext context, Json row)? onTap;
  @override
  State<RecordListScreen> createState() => _RecordListScreenState();
}

class _RecordListScreenState extends State<RecordListScreen> {
  final _loader = GlobalKey<LoaderState<List<Json>>>();

  Future<void> _runAction(RowAction a, Json row) async {
    if (a.confirm != null && !await confirmDialog(context, a.confirm!, yes: a.label)) return;
    if (!mounted) return;
    try {
      final done = await a.run(context, row);
      if (!mounted) return;
      if (done != null) snack(context, done);
      _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  Future<void> _open(Json row) async {
    if (widget.onTap != null) {
      await widget.onTap!(context, row);
      _loader.currentState?.reload();
      return;
    }
    final acts = widget.actions.where((a) => a.visible?.call(row) ?? true).toList();
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (c) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.7,
        builder: (c, scroll) => ListView(controller: scroll, padding: const EdgeInsets.only(bottom: 24), children: [
          ListTile(title: Text(widget.titleOf(row), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: navy))),
          if (acts.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Wrap(spacing: 8, runSpacing: 8, children: [
                for (final a in acts)
                  ActionChip(
                    avatar: Icon(a.icon, size: 18),
                    label: Text(a.label),
                    onPressed: () {
                      Navigator.pop(c);
                      _runAction(a, row);
                    },
                  ),
              ]),
            ),
          RecordView(record: row),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(widget.title)),
        floatingActionButton: widget.onAdd == null
            ? null
            : FloatingActionButton.extended(
                key: const Key('add'),
                icon: const Icon(Icons.add),
                label: Text(widget.addLabel),
                onPressed: () async {
                  if (await widget.onAdd!(context)) _loader.currentState?.reload();
                },
              ),
        body: Loader<List<Json>>(
          key: _loader,
          load: widget.load,
          isEmpty: (d) => d.isEmpty && widget.intro == null,
          emptyText: widget.emptyText,
          builder: (context, rows, reload) => ListView(padding: const EdgeInsets.only(bottom: 88), children: [
            if (widget.intro != null) Padding(padding: const EdgeInsets.fromLTRB(16, 12, 16, 4), child: Text(widget.intro!, style: const TextStyle(color: Colors.black54))),
            if (rows.isEmpty) Padding(padding: const EdgeInsets.all(24), child: Text(widget.emptyText, textAlign: TextAlign.center)),
            for (final r in rows)
              Card(
                child: ListTile(
                  title: Text(widget.titleOf(r)),
                  subtitle: widget.subtitleOf == null ? null : Text(widget.subtitleOf!(r)),
                  trailing: widget.trailingOf == null ? const Icon(Icons.chevron_right) : Text(widget.trailingOf!(r), style: const TextStyle(fontWeight: FontWeight.w600, color: navy)),
                  onTap: () => _open(r),
                ),
              ),
          ]),
        ),
      );
}

/// Loads one record, shows it and lets the user edit [fields] with [save].
class RecordFormScreen extends StatelessWidget {
  const RecordFormScreen({super.key, required this.title, required this.load, required this.fields, required this.save, this.intro, this.extra});
  final String title;
  final Future<Json> Function() load;
  final List<FieldSpec> fields;
  final Future<void> Function(Json values, Json current) save;
  final String? intro;
  final List<Widget> Function(BuildContext context, Json data, Future<void> Function() reload)? extra;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(title)),
        body: Loader<Json>(
          load: load,
          builder: (context, d, reload) => ListView(padding: const EdgeInsets.only(bottom: 32), children: [
            if (intro != null) Padding(padding: const EdgeInsets.fromLTRB(16, 12, 16, 4), child: Text(intro!, style: const TextStyle(color: Colors.black54))),
            Padding(
              padding: const EdgeInsets.all(12),
              child: FilledButton.icon(
                key: const Key('edit'),
                icon: const Icon(Icons.edit_outlined),
                label: const Text('Edit'),
                onPressed: () async {
                  final ok = await showFormSheet(context, title: title, fields: fields, initial: d, onSubmit: (v) => save(v, d));
                  if (ok && context.mounted) {
                    snack(context, 'Saved');
                    await reload();
                  }
                },
              ),
            ),
            ...?extra?.call(context, d, reload),
            Card(
              child: Column(children: [
                for (final f in fields) ListTile(dense: true, title: Text(pretty(d[f.key]).isEmpty ? 'Not set' : pretty(d[f.key])), subtitle: Text(f.label)),
              ]),
            ),
          ]),
        ),
      );
}

List<MapEntry<String, String>> opts(List<String> keys) => keys.map((k) => MapEntry(k, labelOf(k))).toList();
