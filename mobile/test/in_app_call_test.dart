import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:owlaw_mobile/api/api_client.dart';
import 'package:owlaw_mobile/api/owlaw_api.dart';
import 'package:owlaw_mobile/api/token_store.dart';
import 'package:owlaw_mobile/screens/in_app_call.dart';
import 'package:owlaw_mobile/voice/voice_engine.dart';
import 'package:twilio_voice/twilio_voice.dart' show CallEvent;

class FakeEngine implements VoiceEngine {
  FakeEngine({this.supported = true});
  @override
  final bool supported;
  final ctrl = StreamController<CallState>.broadcast();
  final log = <String>[];
  Map<String, String>? params;
  @override
  Stream<CallState> get states => ctrl.stream;
  @override
  Future<void> place({required String accessToken, required String identity, required String to, required Map<String, String> params}) async {
    log.add('place $to as $identity with $accessToken');
    this.params = params;
    ctrl.add(CallState.ringing);
  }

  @override
  Future<void> setMuted(bool muted) async => log.add('mute $muted');
  @override
  Future<void> setSpeaker(bool on) async => log.add('speaker $on');
  @override
  Future<void> hangUp() async => log.add('hangup');
  @override
  Future<void> registerForIncoming({required String accessToken, String? deviceToken}) async => log.add('register');
}

Map? sent;
ApiClient api({bool fail = false}) => ApiClient(
      baseUrl: 'http://api.test',
      store: MemoryTokenStore()..values[ApiClient.accessKey] = 'A',
      client: MockClient((req) async {
        if (req.url.path == '/api/v1/voice/app/token') {
          return http.Response(jsonEncode({'success': true, 'data': {'token': 'JWT', 'identity': 'owlaw_u1', 'incomingReady': false}}), 200);
        }
        if (req.url.path == '/api/v1/calls/app') {
          sent = jsonDecode(req.body) as Map;
          if (fail) return http.Response(jsonEncode({'success': false, 'message': 'In-app calling is not set up on this server. Use the callback call.'}), 400);
          return http.Response(
              jsonEncode({'success': true, 'data': {'id': 'call1', 'toNumber': '+27825550199', 'bridgeToken': 'b' * 64, 'accessToken': 'JWT', 'identity': 'owlaw_u1'}}), 200);
        }
        return http.Response(jsonEncode({'success': true, 'data': []}), 200);
      }),
    );

void main() {
  setUp(() => sent = null);

  test('token fetch returns the Twilio token and identity', () async {
    final t = await api().voiceAppToken();
    expect(t['token'], 'JWT');
    expect(t['identity'], 'owlaw_u1');
  });

  test('Twilio events map to call states', () {
    expect(mapTwilioEvent(CallEvent.connected), CallState.connected);
    expect(mapTwilioEvent(CallEvent.callEnded), CallState.ended);
    expect(mapTwilioEvent(CallEvent.ringing), CallState.ringing);
    expect(mapTwilioEvent(CallEvent.log), isNull);
    expect(clock(const Duration(minutes: 2, seconds: 5)), '02:05');
  });

  testWidgets('places the call on the matter, then mute, speaker and hang up', (tester) async {
    final engine = FakeEngine();
    await tester.pumpWidget(MaterialApp(home: InAppCallScreen(api: api(), to: '082 555 0199', name: 'Thandi', caseId: 'c1', engine: engine)));
    await tester.pumpAndSettle();
    expect(sent, {'to': '082 555 0199', 'caseId': 'c1', 'recordingEnabled': false, 'recordingConsentGiven': false});
    expect(engine.log.first, 'place +27825550199 as owlaw_u1 with JWT');
    expect(engine.params, {'bridgeToken': 'b' * 64});
    expect(find.text('Ringing'), findsOneWidget);
    engine.ctrl.add(CallState.connected);
    await tester.pump();
    await tester.pump();
    expect(find.text('00:00'), findsOneWidget);
    await tester.pump(const Duration(seconds: 3));
    expect(find.text('00:03'), findsOneWidget);
    await tester.tap(find.byKey(const Key('muteButton')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('speakerButton')));
    await tester.pump();
    expect(find.text('Unmute'), findsOneWidget);
    await tester.tap(find.byKey(const Key('hangUp')));
    await tester.pumpAndSettle();
    expect(engine.log, containsAllInOrder(['mute true', 'speaker true', 'hangup']));
  });

  testWidgets('without in-app calling it offers the callback call', (tester) async {
    await tester.pumpWidget(MaterialApp(home: InAppCallScreen(api: api(), to: '0825550199', engine: FakeEngine(supported: false))));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('useCallback')), findsOneWidget);
    expect(sent, isNull);
  });

  testWidgets('a server refusal shows the reason and the fallback', (tester) async {
    await tester.pumpWidget(MaterialApp(home: InAppCallScreen(api: api(fail: true), to: '0825550199', engine: FakeEngine())));
    await tester.pumpAndSettle();
    expect(sent?['nonMatter'], true);
    expect(find.textContaining('not set up on this server'), findsOneWidget);
    expect(find.byKey(const Key('useCallback')), findsOneWidget);
  });
}
