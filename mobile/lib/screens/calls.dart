import 'package:flutter/material.dart';
import 'package:legalsuite_mobile/api.dart';

/// Starts a real PSTN callback. The handset rings; CallKit is a later native step.
class CallsScreen extends StatefulWidget {
  const CallsScreen({super.key, required this.api});

  final LegalSuiteApi api;

  @override
  State<CallsScreen> createState() => _CallsScreenState();
}

class _CallsScreenState extends State<CallsScreen> {
  final toCtrl = TextEditingController();
  final fromCtrl = TextEditingController();
  List cases = [];
  List numbers = [];
  String? matterId;
  String? callerId;
  bool nonMatter = false;
  bool busy = false;
  String? message;
  String? error;

  @override
  void initState() {
    super.initState();
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
      setState(() {
        cases = matters;
        numbers = (ready['callerIds'] as List? ?? const [])
            .where((n) => (n as Map)['status'] == 'active')
            .toList();
        message = ready['message']?.toString();
      });
    } catch (e) {
      setState(() => error = e.toString().replaceFirst('Exception: ', ''));
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
      setState(() => error = e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text(
          'Public network',
          style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Color(0xFF1A365D)),
        ),
        const SizedBox(height: 8),
        const Text(
          'Your mobile rings first. The person you call sees a verified personal number or TWILIO_VOICE_FROM. Buying a number is optional. Emergency numbers stay on the phone dialer. An in-app CallKit screen is a later step.',
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
            child: Text('No number is saved on this firm. Verify a personal mobile or landline on the web app, or set TWILIO_VOICE_FROM. Buying a number is optional.'),
          ),
      ],
    );
  }
}
