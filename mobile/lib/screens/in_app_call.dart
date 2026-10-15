import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../ui/common.dart';
import '../ui/theme.dart';
import '../voice/voice_engine.dart';
import 'calls.dart';

String callStateLabel(CallState s) => switch (s) {
      CallState.idle => 'Ready',
      CallState.connecting => 'Connecting',
      CallState.ringing => 'Ringing',
      CallState.connected => 'Connected',
      CallState.reconnecting => 'Reconnecting',
      CallState.ended => 'Call ended',
      CallState.failed => 'Call failed',
    };

String clock(Duration d) => '${d.inMinutes.toString().padLeft(2, '0')}:${(d.inSeconds % 60).toString().padLeft(2, '0')}';

/// Places a call from inside the app with the firm number as caller ID. The call is logged
/// to the matter on the server before it connects. If in-app calling is not available,
/// the attorney can fall back to the callback call (the phone rings first).
class InAppCallScreen extends StatefulWidget {
  const InAppCallScreen({super.key, required this.api, required this.to, this.name, this.caseId, this.firmPhoneNumberId, VoiceEngine? engine})
      : _engine = engine;
  final ApiClient api;
  final String to;
  final String? name;
  final String? caseId;
  final String? firmPhoneNumberId;
  final VoiceEngine? _engine;
  @override
  State<InAppCallScreen> createState() => _InAppCallScreenState();
}

class _InAppCallScreenState extends State<InAppCallScreen> {
  late final VoiceEngine engine = widget._engine ?? VoiceEngine.instance;
  CallState state = CallState.idle;
  String? error;
  bool muted = false;
  bool speaker = false;
  Duration elapsed = Duration.zero;
  Timer? _timer;
  StreamSubscription<CallState>? _sub;

  @override
  void initState() {
    super.initState();
    _sub = engine.states.listen(_onState);
    WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  @override
  void dispose() {
    _timer?.cancel();
    _sub?.cancel();
    super.dispose();
  }

  void _onState(CallState s) {
    if (!mounted) return;
    setState(() => state = s);
    if (s == CallState.connected && _timer == null) {
      _timer = Timer.periodic(const Duration(seconds: 1), (_) => setState(() => elapsed += const Duration(seconds: 1)));
    }
    if (s == CallState.ended || s == CallState.failed) _timer?.cancel();
  }

  Future<void> _start() async {
    if (!engine.supported) {
      setState(() {
        state = CallState.failed;
        error = 'In-app calling is not available on this device. Use the callback call: your phone rings first, then the client.';
      });
      return;
    }
    setState(() => state = CallState.connecting);
    try {
      final r = await widget.api.post('/api/v1/calls/app', {
        'to': widget.to,
        if (widget.caseId != null) 'caseId': widget.caseId else 'nonMatter': true,
        if (widget.firmPhoneNumberId != null) 'firmPhoneNumberId': widget.firmPhoneNumberId,
        'recordingEnabled': false,
        'recordingConsentGiven': false,
      }) as Map;
      await engine.place(
        accessToken: '${r['accessToken']}',
        identity: '${r['identity']}',
        to: '${r['toNumber'] ?? widget.to}',
        params: {'bridgeToken': '${r['bridgeToken']}'},
      );
    } catch (e) {
      if (!mounted) return;
      setState(() {
        state = CallState.failed;
        error = cleanError(e);
      });
    }
  }

  Future<void> _toggle(Future<void> Function() f) async {
    try {
      await f();
    } catch (e) {
      if (mounted) snack(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final live = state == CallState.connecting || state == CallState.ringing || state == CallState.connected || state == CallState.reconnecting;
    return Scaffold(
      backgroundColor: navyDark,
      appBar: AppBar(title: const Text('In-app call')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(children: [
            const SizedBox(height: 24),
            CircleAvatar(radius: 44, backgroundColor: gold, child: Text((widget.name ?? widget.to).characters.first.toUpperCase(), style: const TextStyle(fontSize: 36, color: navyDark))),
            const SizedBox(height: 16),
            Text(widget.name ?? widget.to, style: const TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold)),
            if (widget.name != null) Text(widget.to, style: const TextStyle(color: Colors.white70)),
            const SizedBox(height: 12),
            Text(state == CallState.connected ? clock(elapsed) : callStateLabel(state), key: const Key('callState'), style: const TextStyle(color: gold, fontSize: 18)),
            const SizedBox(height: 8),
            const Text('The client sees the firm number. The call is logged to the matter.', textAlign: TextAlign.center, style: TextStyle(color: Colors.white54, fontSize: 12)),
            if (error != null) ...[
              const SizedBox(height: 16),
              Text(error!, key: const Key('callError'), textAlign: TextAlign.center, style: const TextStyle(color: Colors.white)),
              const SizedBox(height: 12),
              FilledButton.icon(
                key: const Key('useCallback'),
                icon: const Icon(Icons.phone_callback),
                label: const Text('Use callback call instead'),
                onPressed: () => Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => CallsScreen(api: widget.api, initialTo: widget.to))),
              ),
            ],
            const SizedBox(height: 40),
            Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
              _round(
                key: const Key('muteButton'),
                icon: muted ? Icons.mic_off : Icons.mic,
                label: muted ? 'Unmute' : 'Mute',
                active: muted,
                onTap: live
                    ? () => _toggle(() async {
                          await engine.setMuted(!muted);
                          setState(() => muted = !muted);
                        })
                    : null,
              ),
              _round(
                key: const Key('speakerButton'),
                icon: speaker ? Icons.volume_up : Icons.volume_down,
                label: 'Speaker',
                active: speaker,
                onTap: live
                    ? () => _toggle(() async {
                          await engine.setSpeaker(!speaker);
                          setState(() => speaker = !speaker);
                        })
                    : null,
              ),
            ]),
            const SizedBox(height: 32),
            FloatingActionButton.large(
              key: const Key('hangUp'),
              heroTag: 'hangUp',
              backgroundColor: Colors.red.shade700,
              tooltip: live ? 'Hang up' : 'Close',
              onPressed: () async {
                if (live) await _toggle(engine.hangUp);
                if (context.mounted) Navigator.pop(context);
              },
              child: Icon(live ? Icons.call_end : Icons.close, color: Colors.white, size: 36),
            ),
            const SizedBox(height: 24),
          ]),
        ),
      ),
    );
  }

  Widget _round({required Key key, required IconData icon, required String label, required bool active, VoidCallback? onTap}) => Column(children: [
        IconButton.filled(
          key: key,
          iconSize: 32,
          style: IconButton.styleFrom(backgroundColor: active ? gold : Colors.white24, foregroundColor: active ? navyDark : Colors.white),
          onPressed: onTap,
          icon: Icon(icon),
          tooltip: label,
        ),
        const SizedBox(height: 6),
        Text(label, style: const TextStyle(color: Colors.white70)),
      ]);
}
