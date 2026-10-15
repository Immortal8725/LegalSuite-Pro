import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../api/owlaw_api.dart';
import '../ui/common.dart';
import '../ui/forms.dart';
import '../ui/theme.dart';
import 'billing.dart' show statusLabel;

/// Adds or edits a client. Returns the saved client.
Future<Json?> showClientForm(BuildContext context, ApiClient api, {Json? client}) async {
  var type = '${client?['type'] ?? 'individual'}';
  final first = TextEditingController(text: '${client?['firstName'] ?? ''}');
  final last = TextEditingController(text: '${client?['lastName'] ?? ''}');
  final company = TextEditingController(text: '${client?['companyName'] ?? ''}');
  final email = TextEditingController(text: '${client?['email'] ?? ''}');
  final phone = TextEditingController(text: '${client?['phone'] ?? ''}');
  final city = TextEditingController(text: '${client?['city'] ?? ''}');
  for (final c in [first, last, company, email, phone, city]) {
    if (c.text == 'null') c.clear();
  }
  Json? saved;
  final ok = await showActionSheet(
    context,
    title: client == null ? 'New client' : 'Edit client',
    actionLabel: client == null ? 'Save client' : 'Save changes',
    fields: (setState) => [
      choice('Type', type, const [MapEntry('individual', 'Individual'), MapEntry('company', 'Company')], (v) => setState(() => type = v ?? 'individual')),
      if (type == 'company') TextField(controller: company, decoration: field('Company name')),
      TextField(key: const Key('clientFirstName'), controller: first, decoration: field(type == 'company' ? 'Contact first name' : 'First name')),
      TextField(controller: last, decoration: field(type == 'company' ? 'Contact last name' : 'Last name')),
      TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: field('Email')),
      TextField(controller: phone, keyboardType: TextInputType.phone, decoration: field('Phone')),
      TextField(controller: city, decoration: field('City')),
    ],
    onSubmit: () async {
      if (type == 'company' ? company.text.trim().isEmpty : (first.text.trim().isEmpty || last.text.trim().isEmpty)) {
        throw Exception(type == 'company' ? 'Enter the company name.' : 'Enter the first and last name.');
      }
      saved = await api.saveClient({
        'type': type,
        'firstName': first.text,
        'lastName': last.text,
        'companyName': type == 'company' ? company.text : null,
        'email': email.text,
        'phone': phone.text,
        'city': city.text,
      }, id: client?['id']?.toString());
    },
  );
  return ok ? saved : null;
}

/// Whether a conflict check result lets the attorney open the matter without a second look.
bool conflictClear(Json? result) => result != null && (result['matchCount'] ?? 0) == 0;

class MatterFormScreen extends StatefulWidget {
  const MatterFormScreen({super.key, required this.api, this.matter});
  final ApiClient api;
  final Json? matter;
  @override
  State<MatterFormScreen> createState() => _MatterFormScreenState();
}

class _MatterFormScreenState extends State<MatterFormScreen> {
  ApiClient get api => widget.api;
  bool get editing => widget.matter != null;
  List<Json> clients = [];
  String? clientId;
  late final title = TextEditingController(text: _v('title'));
  late final practiceArea = TextEditingController(text: _v('practiceArea'));
  late final caseType = TextEditingController(text: _v('caseType'));
  late final description = TextEditingController(text: _v('description'));
  late final opposingParty = TextEditingController(text: _v('opposingParty'));
  late final opposingCounsel = TextEditingController(text: _v('opposingCounsel'));
  late final court = TextEditingController(text: _v('courtName'));
  late final rate = TextEditingController(text: _v('billingRate'));
  late String priority = widget.matter?['priority']?.toString() ?? 'medium';
  late String status = widget.matter?['status']?.toString() ?? 'open';
  Json? conflict;
  String? conflictFor;
  bool acknowledged = false;
  bool busy = false;
  String? error;

  String _v(String k) {
    final v = widget.matter?[k];
    return v == null || '$v' == 'null' ? '' : '$v';
  }

  @override
  void initState() {
    super.initState();
    clientId = widget.matter?['clientId']?.toString();
    _loadClients();
  }

  Future<void> _loadClients() async {
    try {
      final c = await api.clients();
      if (mounted) setState(() => clients = c);
    } catch (e) {
      if (mounted) setState(() => error = cleanError(e));
    }
  }

  String get _clientName => clientLabel(clients.firstWhere((c) => '${c['id']}' == clientId, orElse: () => <String, dynamic>{}));
  String get _checkKey => '$_clientName|${opposingParty.text.trim()}';

  Future<void> _check() async {
    if (clientId == null) {
      setState(() => error = 'Choose or add the client first.');
      return;
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final r = await api.conflictCheck(_clientName, opposingParty: opposingParty.text);
      setState(() {
        conflict = r;
        conflictFor = _checkKey;
        acknowledged = false;
      });
    } catch (e) {
      setState(() => error = cleanError(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _save() async {
    if (clientId == null || title.text.trim().isEmpty) {
      setState(() => error = 'Choose the client and enter a matter title.');
      return;
    }
    if (!editing) {
      if (conflict == null || conflictFor != _checkKey) {
        setState(() => error = 'Run the conflict check for this client and opposing party first.');
        return;
      }
      if (!conflictClear(conflict) && !acknowledged) {
        setState(() => error = 'Review the possible conflicts and confirm before opening the matter.');
        return;
      }
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final saved = await api.saveCase({
        'clientId': clientId,
        'title': title.text,
        'practiceArea': practiceArea.text,
        'caseType': caseType.text,
        'description': description.text,
        'opposingParty': opposingParty.text,
        'opposingCounsel': opposingCounsel.text,
        'courtName': court.text,
        'priority': priority,
        if (editing) 'status': status,
        if (rate.text.trim().isNotEmpty) 'billingRate': parseAmount(rate.text) ?? rate.text,
      }, id: widget.matter?['id']?.toString());
      if (mounted) Navigator.pop(context, saved);
    } catch (e) {
      setState(() {
        error = cleanError(e);
        busy = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final matches = (conflict?['matches'] as List? ?? const []).cast<Map>();
    final stale = conflict != null && conflictFor != _checkKey;
    return Scaffold(
      appBar: AppBar(title: Text(editing ? 'Edit matter' : 'New matter')),
      body: SingleChildScrollView(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(children: [
          Expanded(
            child: choice('Client', clientId, clients.map((c) => MapEntry('${c['id']}', clientLabel(c))).toList(), (v) => setState(() => clientId = v)),
          ),
          IconButton(
            tooltip: 'Add a client',
            icon: const Icon(Icons.person_add_alt_1, color: navy),
            onPressed: () async {
              final c = await showClientForm(context, api);
              if (c == null) return;
              await _loadClients();
              setState(() => clientId = '${c['id']}');
            },
          ),
        ]),
        const SizedBox(height: 12),
        TextField(controller: opposingParty, decoration: field('Opposing party'), onChanged: (_) => setState(() {})),
        if (!editing) ...[
          const SizedBox(height: 12),
          OutlinedButton.icon(
            key: const Key('runConflictCheck'),
            icon: const Icon(Icons.shield_outlined),
            label: Text(conflict == null || stale ? 'Run conflict check' : 'Run the check again'),
            onPressed: busy ? null : _check,
          ),
          if (conflict != null && !stale)
            Card(
              color: conflictClear(conflict) ? Colors.green.shade50 : Colors.orange.shade50,
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(
                    conflictClear(conflict) ? 'No conflicts found for ${conflict!['searchName']}.' : '${matches.length} possible conflict${matches.length == 1 ? '' : 's'} for ${conflict!['searchName']}',
                    key: const Key('conflictResult'),
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  ...matches.map((m) => Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: Text('${m['name']} · ${m['role'] ?? m['type']}${'${m['caseNumber'] ?? ''}'.isEmpty ? '' : ' · ${m['caseNumber']}'} · ${m['how'] ?? ''}'),
                      )),
                  if (!conflictClear(conflict))
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      value: acknowledged,
                      onChanged: (v) => setState(() => acknowledged = v ?? false),
                      title: const Text('I have reviewed these and the firm may act'),
                    ),
                ]),
              ),
            ),
          if (stale) const Padding(padding: EdgeInsets.only(top: 8), child: Text('The client or opposing party changed. Run the check again.', style: TextStyle(color: Colors.black54))),
        ],
        const SizedBox(height: 12),
        TextField(key: const Key('matterTitle'), controller: title, decoration: field('Matter title', hint: 'Dlamini v Road Accident Fund')),
        const SizedBox(height: 12),
        TextField(controller: practiceArea, decoration: field('Practice area', hint: 'Litigation')),
        const SizedBox(height: 12),
        TextField(controller: caseType, decoration: field('Matter type', hint: 'Personal injury')),
        const SizedBox(height: 12),
        TextField(controller: opposingCounsel, decoration: field('Opposing counsel')),
        const SizedBox(height: 12),
        TextField(controller: court, decoration: field('Court')),
        const SizedBox(height: 12),
        choice('Priority', priority, const [MapEntry('low', 'Low'), MapEntry('medium', 'Medium'), MapEntry('high', 'High'), MapEntry('urgent', 'Urgent')], (v) => setState(() => priority = v ?? 'medium')),
        if (editing) ...[
          const SizedBox(height: 12),
          choice('Status', status, const [MapEntry('open', 'Open'), MapEntry('pending', 'Pending'), MapEntry('on_hold', 'On hold'), MapEntry('closed', 'Closed')], (v) => setState(() => status = v ?? 'open')),
        ],
        const SizedBox(height: 12),
        TextField(controller: rate, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: field('Hourly rate (optional)')),
        const SizedBox(height: 12),
        TextField(controller: description, maxLines: 4, decoration: field('Description')),
        if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, style: TextStyle(color: Colors.red.shade700))),
        const SizedBox(height: 16),
        FilledButton(key: const Key('saveMatter'), onPressed: busy ? null : _save, child: Text(editing ? 'Save changes' : 'Open matter')),
      ])),
    );
  }
}

/// Adds a diary event or deadline. Times are entered in the phone's local time.
Future<bool> showEventSheet(BuildContext context, ApiClient api, {String? caseId}) async {
  final title = TextEditingController();
  final location = TextEditingController();
  var type = 'deadline';
  var date = DateTime.now().add(const Duration(days: 1));
  var time = const TimeOfDay(hour: 9, minute: 0);
  var minutes = 60;
  return showActionSheet(
    context,
    title: 'Add deadline or event',
    actionLabel: 'Save to diary',
    fields: (setState) => [
      choice('Type', type, const [
        MapEntry('deadline', 'Deadline'),
        MapEntry('hearing', 'Hearing'),
        MapEntry('meeting', 'Meeting'),
        MapEntry('consultation', 'Consultation'),
        MapEntry('deposition', 'Deposition'),
      ], (v) => setState(() => type = v ?? 'deadline')),
      TextField(controller: title, decoration: field('Title', hint: 'File replying affidavit')),
      Row(children: [
        Expanded(
          child: OutlinedButton.icon(
            icon: const Icon(Icons.event),
            label: Text(niceDate(date.toIso8601String())),
            onPressed: () async {
              final d = await showDatePicker(context: context, initialDate: date, firstDate: DateTime(2000), lastDate: DateTime(2100));
              if (d != null) setState(() => date = d);
            },
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: OutlinedButton.icon(
            icon: const Icon(Icons.schedule),
            label: Text(time.format(context)),
            onPressed: () async {
              final t = await showTimePicker(context: context, initialTime: time);
              if (t != null) setState(() => time = t);
            },
          ),
        ),
      ]),
      choice('Duration', '$minutes', const [MapEntry('0', 'No end time'), MapEntry('30', '30 min'), MapEntry('60', '1 hour'), MapEntry('120', '2 hours'), MapEntry('480', 'All day')],
          (v) => setState(() => minutes = int.tryParse(v ?? '60') ?? 60)),
      TextField(controller: location, decoration: field('Location (optional)')),
    ],
    onSubmit: () async {
      if (title.text.trim().isEmpty) throw Exception('Enter a title.');
      final start = DateTime(date.year, date.month, date.day, time.hour, time.minute);
      await api.saveEvent(eventBody(title: title.text, type: type, start: start, minutes: minutes, location: location.text, caseId: caseId));
    },
  );
}

/// The API stores a task due date as an instant. A plain date becomes midnight UTC.
String taskDue(String raw) {
  final t = raw.trim();
  final d = DateTime.tryParse(t);
  if (d == null) throw Exception('Enter the due date as YYYY-MM-DD.');
  return t.length == 10 ? '${t}T00:00:00Z' : d.toUtc().toIso8601String();
}

Json eventBody({required String title, required String type, required DateTime start, int minutes = 60, String? location, String? caseId}) => {
      'title': title,
      'type': type,
      'startTime': start.toUtc().toIso8601String(),
      if (minutes > 0) 'endTime': start.add(Duration(minutes: minutes)).toUtc().toIso8601String(),
      'location': location,
      'caseId': caseId,
    };

class TasksScreen extends StatefulWidget {
  const TasksScreen({super.key, required this.api, this.caseId});
  final ApiClient api;
  final String? caseId;
  @override
  State<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends State<TasksScreen> {
  final _loader = GlobalKey<LoaderState<List<Json>>>();
  bool showDone = false;

  Future<void> _add() async {
    final title = TextEditingController();
    final desc = TextEditingController();
    final due = TextEditingController();
    var priority = 'medium';
    final ok = await showActionSheet(
      context,
      title: 'New task',
      actionLabel: 'Save task',
      fields: (setState) => [
        TextField(controller: title, decoration: field('Task')),
        TextField(controller: due, decoration: field('Due date (optional)', hint: 'YYYY-MM-DD')),
        choice('Priority', priority, const [MapEntry('low', 'Low'), MapEntry('medium', 'Medium'), MapEntry('high', 'High')], (v) => setState(() => priority = v ?? 'medium')),
        TextField(controller: desc, maxLines: 3, decoration: field('Details (optional)')),
      ],
      onSubmit: () async {
        if (title.text.trim().isEmpty) throw Exception('Describe the task.');
        await widget.api.saveTask({
          'title': title.text,
          'description': desc.text,
          'priority': priority,
          'status': 'todo',
          'caseId': widget.caseId,
          'assignedTo': widget.api.user?['id'],
          if (due.text.trim().isNotEmpty) 'dueDate': taskDue(due.text),
        });
      },
    );
    if (ok && mounted) {
      snack(context, 'Task saved');
      _loader.currentState?.reload();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Tasks'), actions: [
        IconButton(
          tooltip: showDone ? 'Hide completed' : 'Show completed',
          icon: Icon(showDone ? Icons.check_box : Icons.check_box_outline_blank),
          onPressed: () => setState(() => showDone = !showDone),
        ),
      ]),
      floatingActionButton: FloatingActionButton.extended(onPressed: _add, icon: const Icon(Icons.add_task), label: const Text('New task')),
      body: Loader<List<Json>>(
        key: _loader,
        load: () async {
          final all = await widget.api.tasks();
          return widget.caseId == null ? all : all.where((t) => '${t['caseId']}' == widget.caseId).toList();
        },
        isEmpty: (d) => d.isEmpty,
        emptyText: 'No tasks.',
        builder: (context, all, reload) {
          final list = all.where((t) => showDone || t['status'] != 'completed').toList()
            ..sort((a, b) => '${a['dueDate'] ?? '9999'}'.compareTo('${b['dueDate'] ?? '9999'}'));
          return ListView(
            padding: const EdgeInsets.only(bottom: 96),
            children: list.map((t) {
              final done = t['status'] == 'completed';
              return Card(
                child: CheckboxListTile(
                  value: done,
                  controlAffinity: ListTileControlAffinity.leading,
                  title: Text('${t['title']}', style: TextStyle(decoration: done ? TextDecoration.lineThrough : null)),
                  subtitle: Text([statusLabel(t['status']), if (t['dueDate'] != null) 'due ${niceDate(t['dueDate'])}', t['priority']].where((x) => x != null && '$x'.isNotEmpty).join(' · ')),
                  onChanged: (v) async {
                    try {
                      await widget.api.setTaskStatus('${t['id']}', v == true ? 'completed' : 'todo');
                      reload();
                    } catch (e) {
                      if (context.mounted) snack(context, cleanError(e), error: true);
                    }
                  },
                ),
              );
            }).toList(),
          );
        },
      ),
    );
  }
}

