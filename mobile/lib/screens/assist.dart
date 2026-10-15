import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../api/owlaw_api.dart';
import '../ui/common.dart';
import '../ui/files.dart';
import '../ui/forms.dart';
import '../ui/theme.dart';
import 'billing.dart' show statusLabel;

/// Draft help: ask about a matter, draft a client email, or summarise the file.
/// Output is assistive. The attorney reads and edits it before anything goes out.
class DraftHelpScreen extends StatefulWidget {
  const DraftHelpScreen({super.key, required this.api, this.caseId});
  final ApiClient api;
  final String? caseId;
  @override
  State<DraftHelpScreen> createState() => _DraftHelpScreenState();
}

class _Turn {
  _Turn(this.mine, this.text, {this.notice});
  final bool mine;
  final String text;
  final String? notice;
}

class _DraftHelpScreenState extends State<DraftHelpScreen> {
  List<Json> cases = [];
  late String? caseId = widget.caseId;
  final prompt = TextEditingController();
  final turns = <_Turn>[];
  bool busy = false;
  String kind = 'status';

  @override
  void initState() {
    super.initState();
    widget.api.cases().then((c) {
      if (mounted) setState(() => cases = c);
    }).catchError((_) {});
  }

  Future<void> _go(Future<Json> Function() call, String asked, String Function(Json) pick) async {
    setState(() {
      busy = true;
      turns.add(_Turn(true, asked));
    });
    try {
      final r = await call();
      setState(() => turns.add(_Turn(false, pick(r), notice: (r['notice'] ?? r['disclaimer'])?.toString())));
    } catch (e) {
      setState(() => turns.add(_Turn(false, cleanError(e), notice: 'Request failed')));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  void _ask() {
    final q = prompt.text.trim();
    if (q.isEmpty) return;
    prompt.clear();
    _go(() => widget.api.aiChat(q, caseId: caseId), q, (r) => '${r['reply'] ?? ''}');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Draft help')),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
          child: choice('Matter', caseId, cases.map((m) => MapEntry('${m['id']}', matterLabel(m))).toList(), (v) => setState(() => caseId = v), noneLabel: 'Whole firm docket'),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
          child: Wrap(spacing: 8, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
            DropdownButton<String>(
              value: kind,
              items: const [
                DropdownMenuItem(value: 'status', child: Text('Status update')),
                DropdownMenuItem(value: 'retainer', child: Text('Engagement letter cover')),
                DropdownMenuItem(value: 'demand', child: Text('Demand letter')),
              ],
              onChanged: (v) => setState(() => kind = v ?? 'status'),
            ),
            ActionChip(
              avatar: const Icon(Icons.edit_note, size: 18),
              label: const Text('Draft email'),
              onPressed: busy ? null : () => _go(() => widget.api.aiDraftEmail(kind, caseId: caseId), 'Draft a ${statusLabel(kind).toLowerCase()} email', (r) => '${r['draft'] ?? ''}'),
            ),
            if (caseId != null)
              ActionChip(
                avatar: const Icon(Icons.summarize_outlined, size: 18),
                label: const Text('Summarise matter'),
                onPressed: busy ? null : () => _go(() => widget.api.aiSummarize(caseId!), 'Summarise this matter', (r) => '${r['summary'] ?? ''}'),
              ),
          ]),
        ),
        const Divider(),
        Expanded(
          child: turns.isEmpty
              ? const Center(
                  child: Padding(
                    padding: EdgeInsets.all(24),
                    child: Text('Ask about deadlines, what is outstanding, or have a client email drafted. Check every draft before you send it.',
                        textAlign: TextAlign.center, style: TextStyle(color: Colors.black54)),
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.all(12),
                  children: turns
                      .map((t) => Align(
                            alignment: t.mine ? Alignment.centerRight : Alignment.centerLeft,
                            child: Card(
                              color: t.mine ? navy : Colors.white,
                              child: Padding(
                                padding: const EdgeInsets.all(12),
                                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                  SelectableText(t.text, style: TextStyle(color: t.mine ? Colors.white : Colors.black87)),
                                  if (!t.mine) ...[
                                    if (t.notice != null && t.notice!.isNotEmpty && t.notice != 'null')
                                      Padding(padding: const EdgeInsets.only(top: 6), child: Text(t.notice!, style: const TextStyle(fontSize: 11, color: Colors.black45))),
                                    Row(mainAxisSize: MainAxisSize.min, children: [
                                      IconButton(
                                        tooltip: 'Copy',
                                        icon: const Icon(Icons.copy, size: 18),
                                        onPressed: () async {
                                          await Clipboard.setData(ClipboardData(text: t.text));
                                          if (context.mounted) snack(context, 'Copied');
                                        },
                                      ),
                                      IconButton(tooltip: 'Share', icon: const Icon(Icons.share_outlined, size: 18), onPressed: () => shareText(context, t.text)),
                                    ]),
                                  ],
                                ]),
                              ),
                            ),
                          ))
                      .toList(),
                ),
        ),
        if (busy) const LinearProgressIndicator(),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 4, 8, 8),
            child: Row(children: [
              Expanded(
                child: TextField(
                  controller: prompt,
                  minLines: 1,
                  maxLines: 4,
                  textInputAction: TextInputAction.send,
                  onSubmitted: (_) => _ask(),
                  decoration: const InputDecoration(hintText: 'Ask about this matter', border: OutlineInputBorder()),
                ),
              ),
              IconButton(tooltip: 'Send', icon: const Icon(Icons.send, color: navy), onPressed: busy ? null : _ask),
            ]),
          ),
        ),
      ]),
    );
  }
}

/// SMS, WhatsApp and email from a matter, through the firm's own Twilio and SMTP set-up.
class MessageScreen extends StatefulWidget {
  const MessageScreen({super.key, required this.api, this.caseId, this.clientId, this.phone, this.email, this.subject});
  final ApiClient api;
  final String? caseId;
  final String? clientId;
  final String? phone;
  final String? email;
  final String? subject;
  @override
  State<MessageScreen> createState() => _MessageScreenState();
}

class _MessageScreenState extends State<MessageScreen> {
  String channel = 'sms';
  late final to = TextEditingController(text: _clean(widget.phone));
  late final subject = TextEditingController(text: widget.subject ?? '');
  final body = TextEditingController();
  Json? ready;
  bool busy = false;
  final _history = GlobalKey<LoaderState<List<Json>>>();

  String _clean(String? v) => v == null || v == 'null' ? '' : v;

  @override
  void initState() {
    super.initState();
    widget.api.outboundReadiness().then((r) {
      if (mounted) setState(() => ready = r);
    }).catchError((_) {});
  }

  void _setChannel(String c) => setState(() {
        channel = c;
        to.text = c == 'email' ? _clean(widget.email) : _clean(widget.phone);
      });

  String? get _hint => ready == null ? null : '${ready!['${channel}Hint'] ?? ''}'.trim();
  bool get _ready => ready?['${channel}Ready'] == true;

  Future<void> _send() async {
    if (to.text.trim().isEmpty || body.text.trim().isEmpty) {
      snack(context, channel == 'email' ? 'Enter the email address and message.' : 'Enter the number and message.', error: true);
      return;
    }
    setState(() => busy = true);
    try {
      final r = await widget.api.sendMessage(channel, {
        'to': to.text,
        'body': body.text,
        if (channel == 'email') 'subject': subject.text,
        'caseId': widget.caseId,
        'clientId': widget.clientId,
      });
      if (!mounted) return;
      final dry = r['status'] == 'dry_run';
      snack(context, '${r['notice'] ?? (dry ? 'Logged on this firm. Not delivered.' : 'Sent.')}', error: dry);
      if (!dry) body.clear();
      _history.currentState?.reload();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Message client')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'sms', label: Text('SMS'), icon: Icon(Icons.sms_outlined)),
            ButtonSegment(value: 'whatsapp', label: Text('WhatsApp'), icon: Icon(Icons.chat_outlined)),
            ButtonSegment(value: 'email', label: Text('Email'), icon: Icon(Icons.email_outlined)),
          ],
          selected: {channel},
          onSelectionChanged: (s) => _setChannel(s.first),
        ),
        if (ready != null && !_ready && (_hint ?? '').isNotEmpty)
          Padding(padding: const EdgeInsets.only(top: 8), child: Text(_hint!, style: TextStyle(color: Colors.orange.shade900, fontSize: 12))),
        const SizedBox(height: 12),
        TextField(controller: to, keyboardType: channel == 'email' ? TextInputType.emailAddress : TextInputType.phone, decoration: field(channel == 'email' ? 'To (email)' : 'To (mobile number)', hint: channel == 'email' ? null : '+27 82 000 0000')),
        if (channel == 'email') ...[const SizedBox(height: 12), TextField(controller: subject, decoration: field('Subject'))],
        const SizedBox(height: 12),
        TextField(controller: body, minLines: 4, maxLines: 10, decoration: field('Message')),
        const SizedBox(height: 12),
        FilledButton.icon(onPressed: busy ? null : _send, icon: const Icon(Icons.send), label: Text('Send ${channel == 'whatsapp' ? 'WhatsApp' : channel.toUpperCase()}')),
        const SectionTitle('Sent from this matter'),
        SizedBox(
          height: 360,
          child: Loader<List<Json>>(
            key: _history,
            load: () => widget.api.outbound(caseId: widget.caseId),
            isEmpty: (d) => d.isEmpty,
            emptyText: 'Nothing sent yet.',
            builder: (context, list, reload) => ListView(
              children: list
                  .map((m) => ListTile(
                        dense: true,
                        leading: Icon(m['channel'] == 'email' ? Icons.email_outlined : m['channel'] == 'whatsapp' ? Icons.chat_outlined : Icons.sms_outlined, color: navy),
                        title: Text('${m['subject'] ?? m['body'] ?? ''}', maxLines: 2, overflow: TextOverflow.ellipsis),
                        subtitle: Text('${m['to']} · ${statusLabel(m['status'])} · ${niceDate(m['createdAt'], withTime: true)}'),
                      ))
                  .toList(),
            ),
          ),
        ),
      ]),
    );
  }
}
