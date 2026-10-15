import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../ui/theme.dart';

/// Generate a firm-branded document for one matter: pick a template, fill the blanks the
/// server could not fill, and save PDF and Word copies to the matter (optionally for e-signature).
/// Uses the same API as the web app: /api/v1/doc-templates.
class GenerateDocumentScreen extends StatefulWidget {
  const GenerateDocumentScreen({super.key, required this.api, required this.caseId, this.clientEmail});
  final ApiClient api;
  final String caseId;
  final String? clientEmail;

  @override
  State<GenerateDocumentScreen> createState() => _GenerateDocumentScreenState();
}

class _GenerateDocumentScreenState extends State<GenerateDocumentScreen> {
  List<Map<String, dynamic>> _library = [];
  String? _templateId;
  List<Map<String, dynamic>> _fields = [];
  final Map<String, TextEditingController> _ctrls = {};
  bool _pdf = true;
  bool _docx = true;
  bool _sign = false;
  final _signerName = TextEditingController();
  final _signerEmail = TextEditingController();
  bool _busy = false;
  String? _error;
  String? _result;

  @override
  void initState() {
    super.initState();
    _signerEmail.text = widget.clientEmail ?? '';
    _loadLibrary();
  }

  @override
  void dispose() {
    for (final c in _ctrls.values) {
      c.dispose();
    }
    _signerName.dispose();
    _signerEmail.dispose();
    super.dispose();
  }

  Future<void> _loadLibrary() async {
    try {
      final rows = (await widget.api.get('/api/v1/doc-templates') as List).cast<Map>().map((e) => e.cast<String, dynamic>()).toList();
      if (mounted) setState(() => _library = rows);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _choose(String? id) async {
    setState(() {
      _templateId = id;
      _fields = [];
      _result = null;
      _error = null;
    });
    if (id == null) return;
    try {
      final form = (await widget.api.get('/api/v1/doc-templates/$id/form?caseId=${widget.caseId}') as Map).cast<String, dynamic>();
      final fields = (form['fields'] as List).cast<Map>().map((e) => e.cast<String, dynamic>()).toList();
      for (final c in _ctrls.values) {
        c.dispose();
      }
      _ctrls.clear();
      for (final f in fields.where((f) => f['system'] != true)) {
        _ctrls[f['key'] as String] = TextEditingController(text: '${f['value'] ?? ''}');
      }
      final client = fields.firstWhere((f) => f['key'] == 'client_name', orElse: () => const {})['value'];
      if (client != null) _signerName.text = '$client';
      if (mounted) setState(() => _fields = fields);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _generate() async {
    if (!_pdf && !_docx) {
      setState(() => _error = 'Choose PDF, Word or both.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final body = <String, dynamic>{
        'caseId': widget.caseId,
        'values': {for (final e in _ctrls.entries) e.key: e.value.text.trim()},
        'formats': [if (_pdf) 'pdf', if (_docx) 'docx'],
        if (_sign && _signerEmail.text.trim().isNotEmpty) 'sign': {'signerName': _signerName.text.trim(), 'signerEmail': _signerEmail.text.trim()},
      };
      final out = (await widget.api.post('/api/v1/doc-templates/$_templateId/generate', body) as Map).cast<String, dynamic>();
      final names = (out['documents'] as List).map((d) => (d as Map)['name']).join(', ');
      setState(() => _result = 'Saved to the matter: $names${out['signature'] != null ? '\nE-signature request sent.' : ''}');
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _label(Map<String, dynamic> f) => '${f['label'] ?? f['key']}';

  @override
  Widget build(BuildContext context) {
    final fillIns = _fields.where((f) => f['system'] != true).toList();
    final firmCount = _fields.length - fillIns.length;
    return Scaffold(
      appBar: AppBar(title: const Text('Generate document')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        DropdownButtonFormField<String>(
          key: const Key('templatePicker'),
          isExpanded: true,
          initialValue: _templateId,
          decoration: const InputDecoration(labelText: 'Template', border: OutlineInputBorder()),
          items: _library
              .map((t) => DropdownMenuItem<String>(
                    value: t['id'] as String,
                    child: Text('${t['name']}${t['system'] == true ? '' : ' (firm)'}', overflow: TextOverflow.ellipsis),
                  ))
              .toList(),
          onChanged: _choose,
        ),
        if (_templateId != null && _fields.isNotEmpty) ...[
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text('Your letterhead fills $firmCount firm field${firmCount == 1 ? '' : 's'}. Blanks you leave empty print as lines to complete by hand.',
                style: const TextStyle(color: Colors.black54)),
          ),
          ...fillIns.map((f) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: TextField(
                  key: Key('field-${f['key']}'),
                  controller: _ctrls[f['key']],
                  decoration: InputDecoration(labelText: _label(f), border: const OutlineInputBorder()),
                ),
              )),
          CheckboxListTile(value: _pdf, onChanged: (v) => setState(() => _pdf = v ?? false), title: const Text('PDF')),
          CheckboxListTile(value: _docx, onChanged: (v) => setState(() => _docx = v ?? false), title: const Text('Word (DOCX)')),
          SwitchListTile(value: _sign, onChanged: (v) => setState(() => _sign = v), title: const Text('Send for e-signature')),
          if (_sign) ...[
            TextField(controller: _signerName, decoration: const InputDecoration(labelText: 'Signer name', border: OutlineInputBorder())),
            const SizedBox(height: 10),
            TextField(
              controller: _signerEmail,
              keyboardType: TextInputType.emailAddress,
              decoration: const InputDecoration(labelText: 'Signer email', border: OutlineInputBorder()),
            ),
          ],
          const SizedBox(height: 16),
          FilledButton.icon(
            key: const Key('generateDocument'),
            onPressed: _busy ? null : _generate,
            icon: const Icon(Icons.description_outlined),
            label: Text(_busy ? 'Generating...' : 'Generate and save to matter'),
          ),
        ],
        if (_error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(_error!, style: const TextStyle(color: Colors.red))),
        if (_result != null)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Text(_result!, key: const Key('generateResult'), style: const TextStyle(color: navy, fontWeight: FontWeight.w600)),
          ),
      ]),
    );
  }
}
