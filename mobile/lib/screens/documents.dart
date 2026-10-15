import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../api/owlaw_api.dart';
import '../config.dart';
import '../ui/common.dart';
import '../ui/files.dart';
import '../ui/forms.dart';
import '../ui/theme.dart';
import 'billing.dart' show statusLabel;

String fileSize(dynamic bytes) {
  final b = bytes is num ? bytes : num.tryParse('$bytes') ?? 0;
  if (b >= 1024 * 1024) return '${(b / (1024 * 1024)).toStringAsFixed(1)} MB';
  if (b >= 1024) return '${(b / 1024).toStringAsFixed(0)} KB';
  return '$b B';
}

IconData fileIcon(String? mime, String name) {
  final m = (mime ?? '').toLowerCase();
  final n = name.toLowerCase();
  if (m.startsWith('image/') || n.endsWith('.jpg') || n.endsWith('.png') || n.endsWith('.jpeg')) return Icons.image_outlined;
  if (m.contains('pdf') || n.endsWith('.pdf')) return Icons.picture_as_pdf_outlined;
  return Icons.insert_drive_file_outlined;
}

/// Uploads a photo or file to the firm's documents, optionally on a matter. Returns true when saved.
Future<bool> uploadDocumentFlow(BuildContext context, ApiClient api, {String? caseId, String? clientId}) async {
  final file = await pickAnyFile(context);
  if (file == null || !context.mounted) return false;
  final name = TextEditingController(text: file.name);
  var category = 'correspondence';
  final ok = await showActionSheet(
    context,
    title: 'Upload document',
    intro: '${file.name} · ${fileSize(file.bytes.length)}',
    actionLabel: 'Upload',
    fields: (setState) => [
      TextField(controller: name, decoration: field('File name')),
      choice('Category', category, const [
        MapEntry('correspondence', 'Correspondence'),
        MapEntry('pleadings', 'Pleadings'),
        MapEntry('evidence', 'Evidence'),
        MapEntry('contracts', 'Contracts'),
        MapEntry('identity', 'Identity and FICA'),
        MapEntry('other', 'Other'),
      ], (v) => setState(() => category = v ?? 'other')),
    ],
    onSubmit: () async {
      final n = name.text.trim().isEmpty ? file.name : name.text.trim();
      await api.uploadDocument(file.bytes, n, caseId: caseId, clientId: clientId, category: category);
    },
  );
  if (ok && context.mounted) snack(context, 'Document uploaded');
  return ok;
}

Future<void> openDocument(BuildContext context, ApiClient api, Json d) async {
  try {
    snack(context, 'Opening ${d['name']}');
    final bytes = await api.downloadDocument('${d['id']}');
    if (context.mounted) await openBytes(context, bytes, '${d['name']}');
  } catch (e) {
    if (context.mounted) snack(context, cleanError(e), error: true);
  }
}

class DocumentsScreen extends StatefulWidget {
  const DocumentsScreen({super.key, required this.api, this.caseId, this.title = 'Documents'});
  final ApiClient api;
  final String? caseId;
  final String title;
  @override
  State<DocumentsScreen> createState() => _DocumentsScreenState();
}

class _DocumentsScreenState extends State<DocumentsScreen> {
  final _loader = GlobalKey<LoaderState<List<Json>>>();
  String query = '';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('uploadDocument'),
        icon: const Icon(Icons.upload_file),
        label: const Text('Upload'),
        onPressed: () async {
          if (await uploadDocumentFlow(context, widget.api, caseId: widget.caseId)) _loader.currentState?.reload();
        },
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
          child: TextField(
            decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search documents', filled: true, fillColor: Colors.white),
            onChanged: (v) => setState(() => query = v.trim().toLowerCase()),
          ),
        ),
        Expanded(
          child: Loader<List<Json>>(
            key: _loader,
            load: () async {
              final all = await widget.api.documents();
              return widget.caseId == null ? all : all.where((d) => '${d['caseId']}' == widget.caseId).toList();
            },
            isEmpty: (d) => d.isEmpty,
            emptyText: 'No documents yet. Upload a photo or file.',
            builder: (context, all, reload) {
              final list = all.where((d) => query.isEmpty || '${d['name']} ${d['category']}'.toLowerCase().contains(query)).toList();
              return ListView(
                padding: const EdgeInsets.only(bottom: 96),
                children: list
                    .map((d) => Card(
                          child: ListTile(
                            leading: Icon(fileIcon(d['mimeType']?.toString(), '${d['name']}'), color: navy),
                            title: Text('${d['name']}', maxLines: 2, overflow: TextOverflow.ellipsis),
                            subtitle: Text([d['category'], fileSize(d['sizeBytes']), niceDate(d['createdAt']), if (d['privileged'] == true) 'privileged']
                                .where((x) => x != null && '$x'.isNotEmpty && '$x' != 'null')
                                .join(' · ')),
                            trailing: const Icon(Icons.open_in_new),
                            onTap: () => openDocument(context, widget.api, d),
                          ),
                        ))
                    .toList(),
              );
            },
          ),
        ),
      ]),
    );
  }
}

class SignaturesScreen extends StatefulWidget {
  const SignaturesScreen({super.key, required this.api, this.caseId});
  final ApiClient api;
  final String? caseId;
  @override
  State<SignaturesScreen> createState() => _SignaturesScreenState();
}

class _SignaturesScreenState extends State<SignaturesScreen> {
  final _loader = GlobalKey<LoaderState<List<Json>>>();

  Future<void> _send() async {
    List<Json> cases, clients;
    try {
      final r = await Future.wait([widget.api.cases(), widget.api.clients()]);
      cases = r[0];
      clients = r[1];
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
      return;
    }
    String? matter = widget.caseId;
    String? client = cases.where((c) => '${c['id']}' == matter).map((c) => '${c['clientId']}').firstOrNull;
    final title = TextEditingController(text: 'Engagement letter');
    final name = TextEditingController();
    final email = TextEditingController();
    final body = TextEditingController();
    void fillFromClient() {
      final c = clients.where((c) => '${c['id']}' == client).firstOrNull;
      if (c == null) return;
      if (name.text.isEmpty) name.text = clientLabel(c);
      if (email.text.isEmpty && c['email'] != null) email.text = '${c['email']}';
    }

    fillFromClient();
    Json? created;
    if (!mounted) return;
    final ok = await showActionSheet(
      context,
      title: 'Send for signature',
      intro: 'Creates a signing request. Share the link with the signer.',
      actionLabel: 'Create signing request',
      fields: (setState) => [
        choice('Matter', matter, cases.map((m) => MapEntry('${m['id']}', matterLabel(m))).toList(), (v) => setState(() {
              matter = v;
              client = cases.where((c) => '${c['id']}' == v).map((c) => '${c['clientId']}').firstOrNull ?? client;
              fillFromClient();
            }), noneLabel: 'No matter'),
        TextField(controller: title, decoration: field('Title')),
        TextField(controller: name, decoration: field('Signer name')),
        TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: field('Signer email')),
        TextField(controller: body, maxLines: 6, decoration: field('Document text', hint: 'The words the signer agrees to')),
      ],
      onSubmit: () async {
        if (name.text.trim().isEmpty || email.text.trim().isEmpty) throw Exception('Enter the signer name and email.');
        if (body.text.trim().isEmpty) throw Exception('Enter the document text.');
        created = await widget.api.sendForSignature({
          'title': title.text,
          'signerName': name.text,
          'signerEmail': email.text,
          'documentBody': body.text,
          'caseId': matter,
          'clientId': client,
        });
      },
    );
    if (!ok || !mounted) return;
    _loader.currentState?.reload();
    final link = AppConfig.webLink(widget.api.baseUrl, '${created?['signUrl'] ?? ''}');
    await shareText(context, 'Please sign "${title.text.trim()}": $link', subject: title.text.trim());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('E-sign')),
      floatingActionButton: FloatingActionButton.extended(onPressed: _send, icon: const Icon(Icons.draw_outlined), label: const Text('Send for signature')),
      body: Loader<List<Json>>(
        key: _loader,
        load: () async {
          final all = await widget.api.signatures();
          return widget.caseId == null ? all : all.where((s) => '${s['caseId']}' == widget.caseId).toList();
        },
        isEmpty: (d) => d.isEmpty,
        emptyText: 'No signing requests yet.',
        builder: (context, list, reload) => ListView(
          padding: const EdgeInsets.only(bottom: 96),
          children: list.map((s) {
            final status = '${s['status']}';
            final pending = status == 'pending';
            return Card(
              child: ListTile(
                leading: Icon(status == 'signed' ? Icons.verified_outlined : pending ? Icons.hourglass_top : Icons.block, color: status == 'signed' ? Colors.green.shade700 : navy),
                title: Text('${s['title']}'),
                subtitle: Text('${s['signerName']} · ${s['signerEmail']}\n${statusLabel(status)}${s['signedAt'] != null ? ' ${niceDate(s['signedAt'], withTime: true)}' : ' · sent ${niceDate(s['createdAt'])}'}'),
                isThreeLine: true,
                trailing: pending
                    ? PopupMenuButton<String>(
                        tooltip: 'Signing request actions',
                        onSelected: (v) async {
                          if (v == 'share') {
                            await shareText(context, 'Please sign "${s['title']}": ${AppConfig.webLink(widget.api.baseUrl, '${s['signUrl']}')}');
                          } else if (v == 'void') {
                            try {
                              await widget.api.voidSignature('${s['id']}');
                              reload();
                            } catch (e) {
                              if (context.mounted) snack(context, cleanError(e), error: true);
                            }
                          }
                        },
                        itemBuilder: (_) => const [
                          PopupMenuItem(value: 'share', child: Text('Share signing link')),
                          PopupMenuItem(value: 'void', child: Text('Void request')),
                        ],
                      )
                    : null,
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}
