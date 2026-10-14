import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../ui/common.dart';
import '../ui/theme.dart';

/// Parses "90", "1:30" or "1.5h" into minutes. Returns null when unclear.
int? parseMinutes(String raw) {
  final s = raw.trim().toLowerCase();
  if (s.isEmpty) return null;
  final hm = RegExp(r'^(\d+):(\d{1,2})$').firstMatch(s);
  if (hm != null) return int.parse(hm[1]!) * 60 + int.parse(hm[2]!);
  final h = RegExp(r'^(\d+(?:[.,]\d+)?)\s*h$').firstMatch(s);
  if (h != null) return (double.parse(h[1]!.replaceAll(',', '.')) * 60).round();
  final m = int.tryParse(s.replaceAll(RegExp(r'\s*min$'), ''));
  return m != null && m > 0 ? m : null;
}

Future<List<Map<String, dynamic>>> _matters(ApiClient api) async =>
    (await api.get('/api/v1/cases') as List).map((e) => (e as Map).cast<String, dynamic>()).toList();

/// Bottom sheet for a manual time entry. Returns true when saved.
Future<bool> showTimeEntrySheet(BuildContext context, ApiClient api, {String? presetCaseId}) async {
  final saved = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    builder: (c) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(c).viewInsets.bottom),
      child: _TimeEntryForm(api: api, presetCaseId: presetCaseId),
    ),
  );
  if (saved == true && context.mounted) snack(context, 'Time entry saved');
  return saved == true;
}

class _TimeEntryForm extends StatefulWidget {
  const _TimeEntryForm({required this.api, this.presetCaseId});
  final ApiClient api;
  final String? presetCaseId;
  @override
  State<_TimeEntryForm> createState() => _TimeEntryFormState();
}

class _TimeEntryFormState extends State<_TimeEntryForm> {
  final minutes = TextEditingController(text: '30');
  final desc = TextEditingController();
  List<Map<String, dynamic>> matters = [];
  late String? caseId = widget.presetCaseId;
  bool billable = true;
  bool busy = false;
  String? error;

  @override
  void initState() {
    super.initState();
    _matters(widget.api).then((m) {
      if (mounted) setState(() => matters = m);
    }).catchError((Object e) {
      if (mounted) setState(() => error = e.toString());
    });
  }

  Future<void> _save() async {
    final mins = parseMinutes(minutes.text);
    if (mins == null) {
      setState(() => error = 'Enter the time as minutes (45), hours and minutes (1:30) or hours (1.5h).');
      return;
    }
    if (desc.text.trim().isEmpty) {
      setState(() => error = 'Describe the work so it reads well on the invoice.');
      return;
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await widget.api.post('/api/v1/time-entries', {
        if (caseId != null) 'caseId': caseId,
        'durationMinutes': mins,
        'description': desc.text.trim(),
        'billable': billable,
        'source': 'mobile',
        'date': DateTime.now().toIso8601String().substring(0, 10),
      });
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('Log time', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: navy)),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            key: const Key('timeMatter'),
            initialValue: matters.any((m) => '${m['id']}' == caseId) ? caseId : null,
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Matter'),
            items: [
              const DropdownMenuItem<String>(value: null, child: Text('Unassigned (no matter)')),
              ...matters.map((m) => DropdownMenuItem(value: '${m['id']}', child: Text('${m['caseNumber']} ${m['title']}', overflow: TextOverflow.ellipsis))),
            ],
            onChanged: (v) => setState(() => caseId = v),
          ),
          const SizedBox(height: 12),
          TextField(key: const Key('timeMinutes'), controller: minutes, decoration: const InputDecoration(labelText: 'Time', helperText: '45, 1:30 or 1.5h')),
          const SizedBox(height: 12),
          TextField(key: const Key('timeDesc'), controller: desc, maxLines: 2, decoration: const InputDecoration(labelText: 'Work done')),
          SwitchListTile(contentPadding: EdgeInsets.zero, value: billable, title: const Text('Billable'), onChanged: (v) => setState(() => billable = v)),
          if (error != null) Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(error!, style: TextStyle(color: Colors.red.shade700))),
          FilledButton(key: const Key('saveTime'), onPressed: busy ? null : _save, child: Text(busy ? 'Saving' : 'Save time entry')),
        ]),
      ),
    );
  }
}

class TimeScreen extends StatefulWidget {
  const TimeScreen({super.key, required this.api});
  final ApiClient api;
  @override
  State<TimeScreen> createState() => _TimeScreenState();
}

class _TimeScreenState extends State<TimeScreen> {
  final _loader = GlobalKey<LoaderState<_TimeData>>();
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    _tick = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted && (_loader.currentState?.data?.timer['running'] == true)) setState(() {});
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  Future<_TimeData> _load() async {
    final results = await Future.wait([
      widget.api.get('/api/v1/time-entries'),
      widget.api.get('/api/v1/timers/active'),
      _matters(widget.api),
    ]);
    final entries = (results[0] as List).map((e) => (e as Map).cast<String, dynamic>()).toList()
      ..sort((a, b) => '${b['date']}'.compareTo('${a['date']}'));
    final names = {for (final m in results[2] as List<Map<String, dynamic>>) '${m['id']}': '${m['caseNumber']} ${m['title']}'};
    return _TimeData(entries, (results[1] as Map).cast<String, dynamic>(), names, results[2] as List<Map<String, dynamic>>);
  }

  Future<void> _start(_TimeData d) async {
    final caseId = await showDialog<String>(
      context: context,
      builder: (c) => SimpleDialog(title: const Text('Start the timer on'), children: [
        SimpleDialogOption(onPressed: () => Navigator.pop(c, ''), child: const Text('No matter yet')),
        ...d.matters.map((m) => SimpleDialogOption(onPressed: () => Navigator.pop(c, '${m['id']}'), child: Text('${m['caseNumber']} ${m['title']}'))),
      ]),
    );
    if (caseId == null) return;
    await _run(() => widget.api.post('/api/v1/timers/start', {if (caseId.isNotEmpty) 'caseId': caseId}), 'Timer running');
  }

  Future<void> _stop() async {
    final ctrl = TextEditingController();
    final text = await showDialog<String>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Stop the timer'),
        content: TextField(controller: ctrl, autofocus: true, decoration: const InputDecoration(labelText: 'Work done')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c), child: const Text('Keep running')),
          FilledButton(onPressed: () => Navigator.pop(c, ctrl.text.trim()), child: const Text('Stop and save')),
        ],
      ),
    );
    if (text == null) return;
    await _run(() => widget.api.post('/api/v1/timers/stop', {if (text.isNotEmpty) 'description': text}), 'Time saved from the timer');
  }

  Future<void> _run(Future<dynamic> Function() f, String ok) async {
    try {
      await f();
      if (!mounted) return;
      snack(context, ok);
      _loader.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, e.toString(), error: true);
    }
  }

  String _elapsed(Map<String, dynamic> t) {
    final start = DateTime.tryParse('${t['startedAt'] ?? t['startTime'] ?? ''}');
    if (start == null) return 'Running';
    final s = DateTime.now().difference(start).inSeconds.clamp(0, 1 << 30);
    String two(int v) => v.toString().padLeft(2, '0');
    return '${two(s ~/ 3600)}:${two((s % 3600) ~/ 60)}:${two(s % 60)}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Time')),
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('addTime'),
        backgroundColor: gold,
        foregroundColor: navyDark,
        icon: const Icon(Icons.add),
        label: const Text('Log time'),
        onPressed: () async {
          if (await showTimeEntrySheet(context, widget.api)) _loader.currentState?.reload();
        },
      ),
      body: Loader<_TimeData>(
        key: _loader,
        load: _load,
        builder: (context, d, reload) {
          final running = d.timer['running'] == true;
          final total = d.entries.fold<int>(0, (a, e) => a + ((e['durationMinutes'] as num?)?.toInt() ?? 0));
          return ListView(padding: const EdgeInsets.only(bottom: 96), children: [
            Card(
              color: running ? navy : Colors.white,
              child: ListTile(
                leading: Icon(Icons.timer, color: running ? gold : navy),
                title: Text(running ? _elapsed(d.timer) : 'Timer stopped',
                    style: TextStyle(color: running ? Colors.white : navy, fontWeight: FontWeight.bold, fontSize: 18)),
                subtitle: Text(running ? (d.names['${d.timer['caseId']}'] ?? 'No matter') : 'Start it when you pick up a file.',
                    style: TextStyle(color: running ? Colors.white70 : Colors.black54)),
                trailing: FilledButton(
                  key: const Key('timerButton'),
                  style: FilledButton.styleFrom(minimumSize: const Size(90, 40)),
                  onPressed: () => running ? _stop() : _start(d),
                  child: Text(running ? 'Stop' : 'Start'),
                ),
              ),
            ),
            SectionTitle('Entries · ${hoursMinutes(total)}'),
            if (d.entries.isEmpty) const ListTile(title: Text('No time logged yet.')),
            ...d.entries.map((e) => Card(
                  child: ListTile(
                    title: Text('${e['description'] ?? ''}'),
                    subtitle: Text([d.names['${e['caseId']}'] ?? 'Unassigned', niceDate(e['date'])].join(' · ')),
                    trailing: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.end, children: [
                      Text(hoursMinutes(e['durationMinutes']), style: const TextStyle(fontWeight: FontWeight.bold, color: navy)),
                      Text(e['billed'] == true ? 'Billed' : (e['billable'] == false ? 'Not billable' : 'Unbilled'), style: const TextStyle(fontSize: 11, color: Colors.black54)),
                    ]),
                  ),
                )),
          ]);
        },
      ),
    );
  }
}

class _TimeData {
  _TimeData(this.entries, this.timer, this.names, this.matters);
  final List<Map<String, dynamic>> entries;
  final Map<String, dynamic> timer;
  final Map<String, String> names;
  final List<Map<String, dynamic>> matters;
}
