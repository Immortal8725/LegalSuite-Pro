import 'package:flutter/material.dart';
import '../api/api_client.dart';
import '../ui/common.dart';

/// Starts a real PSTN callback. The handset rings; CallKit is a later native step.
class CallsScreen extends StatefulWidget {
  const CallsScreen({super.key, required this.api, this.initialTo});

  final ApiClient api;
  final String? initialTo;

  @override
  State<CallsScreen> createState() => _CallsScreenState();
}

class _CallsScreenState extends State<CallsScreen> {
  final toCtrl = TextEditingController();
  final fromCtrl = TextEditingController();
  List cases = [];
  List numbers = [];
  List history = [];
  String? matterId;
  String? callerId;
  bool nonMatter = false;
  bool busy = false;
  String? message;
  String? error;

  @override
  void initState() {
    super.initState();
    if (widget.initialTo != null) toCtrl.text = widget.initialTo!;
    _load();
  }

  @override
  void dispose() {
    toCtrl.dispose();
    fromCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final matters = await widget.api.get('/api/v1/cases') as List;
      final ready = await widget.api.get('/api/v1/voice/pstn/readiness') as Map<String, dynamic>;
      final log = await widget.api.get('/api/v1/calls') as List;
      if (!mounted) return;
      setState(() {
        history = log;
        cases = matters;
        numbers = (ready['callerIds'] as List? ?? const [])
            .where((n) => (n as Map)['status'] == 'active')
            .toList();
        message = ready['message']?.toString();
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => error = e.toString());
    }
  }

  Future<void> _dial() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await widget.api.post('/api/v1/calls/pstn', {
        'to': toCtrl.text.trim(),
        'staffCallback': fromCtrl.text.trim(),
        if (callerId != null && callerId!.isNotEmpty) 'firmPhoneNumberId': callerId,
        if (!nonMatter && matterId != null) 'caseId': matterId,
        'nonMatter': nonMatter,
        'recordingEnabled': false,
        'recordingConsentGiven': false,
      });
      if (!mounted) return;
      setState(() {
        message = 'Calling your phone. Answer it to connect. The other party sees the firm caller ID.';
        toCtrl.clear();
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Calls')),
      body: RefreshIndicator(onRefresh: _load, child: ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text(
          'Call through the firm',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Color(0xFF1A365D)),
        ),
        const SizedBox(height: 8),
        const Text(
          'Your mobile rings first. When you answer, Owlaw connects the other party, who sees the firm caller ID. Time is written to the matter. Emergency numbers stay on your phone dialler.',
          style: TextStyle(color: Colors.black54),
        ),
        if (message != null) ...[
          const SizedBox(height: 12),
          Text(message!, style: const TextStyle(color: Color(0xFF1A365D))),
        ],
        if (error != null) ...[
          const SizedBox(height: 12),
          Text(error!, style: const TextStyle(color: Colors.red)),
        ],
        const SizedBox(height: 16),
        TextField(
          controller: toCtrl,
          keyboardType: TextInputType.phone,
          decoration: const InputDecoration(labelText: 'Number to call', border: OutlineInputBorder()),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: fromCtrl,
          keyboardType: TextInputType.phone,
          decoration: const InputDecoration(labelText: 'Your phone (rings first)', border: OutlineInputBorder()),
        ),
        const SizedBox(height: 12),
        InputDecorator(
          decoration: const InputDecoration(labelText: 'Caller ID', border: OutlineInputBorder()),
          child: DropdownButtonHideUnderline(
            child: DropdownButton<String>(
              isExpanded: true,
              value: callerId ?? '',
              items: [
                const DropdownMenuItem(value: '', child: Text('Automatic caller ID')),
                ...numbers.map((n) {
                  final row = n as Map;
                  return DropdownMenuItem(value: row['id']?.toString(), child: Text('${row['e164']} (${row['kind']})'));
                }),
              ],
              onChanged: (v) => setState(() => callerId = v),
            ),
          ),
        ),
        const SizedBox(height: 12),
        InputDecorator(
          decoration: const InputDecoration(labelText: 'Matter', border: OutlineInputBorder()),
          child: DropdownButtonHideUnderline(
            child: DropdownButton<String>(
              isExpanded: true,
              value: nonMatter ? '' : (matterId ?? ''),
              items: [
                const DropdownMenuItem(value: '', child: Text('Choose a matter')),
                ...cases.map((c) {
                  final row = c as Map;
                  return DropdownMenuItem(
                    value: row['id']?.toString(),
                    child: Text('${row['caseNumber']} ${row['title']}'),
                  );
                }),
              ],
              onChanged: nonMatter
                  ? null
                  : (v) => setState(() {
                        matterId = v == null || v.isEmpty ? null : v;
                      }),
            ),
          ),
        ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: nonMatter,
          title: const Text('Not on a matter'),
          onChanged: (v) => setState(() {
            nonMatter = v ?? false;
            if (nonMatter) matterId = null;
          }),
        ),
        const SizedBox(height: 8),
        FilledButton(
          onPressed: busy ? null : _dial,
          child: Text(busy ? 'Placing call' : 'Place call'),
        ),
        if (numbers.isEmpty)
          const Padding(
            padding: EdgeInsets.only(top: 12),
            child: Text('No caller ID is set up for this firm yet. Verify a mobile or landline in the Owlaw web app first.'),
          ),
        const SizedBox(height: 24),
        const Text('Recent calls', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF1A365D))),
        if (history.isEmpty) const Padding(padding: EdgeInsets.only(top: 8), child: Text('No calls yet.', style: TextStyle(color: Colors.black54))),
        ...history.take(30).map((c) {
          final row = c as Map;
          final secs = (row['durationSeconds'] as num?)?.toInt() ?? 0;
          final kind = row['callType'] == 'webrtc' ? 'In-app' : 'Phone';
          return ListTile(
            contentPadding: EdgeInsets.zero,
            leading: Icon(row['direction'] == 'inbound' ? Icons.call_received : Icons.call_made, color: const Color(0xFF1A365D)),
            title: Text('$kind ${row['direction'] ?? ''} call, ${secs ~/ 60} min ${secs % 60} s'),
            subtitle: Text(niceDate(row['startedAt'], withTime: true)),
          );
        }),
      ],
    )));
  }
}
