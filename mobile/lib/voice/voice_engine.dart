import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:twilio_voice/twilio_voice.dart';

/// What the call screen shows. Mapped from the Twilio SDK events.
enum CallState { idle, connecting, ringing, connected, reconnecting, ended, failed }

/// The in-app calling surface the screens use. Kept small so tests can supply a fake.
abstract class VoiceEngine {
  bool get supported;
  Stream<CallState> get states;
  Future<void> place({required String accessToken, required String identity, required String to, required Map<String, String> params});
  Future<void> setMuted(bool muted);
  Future<void> setSpeaker(bool on);
  Future<void> hangUp();

  /// Lets the phone ring for incoming in-app calls. Needs a push token from FCM or APNs to work
  /// while the app is closed; see mobile/README.md.
  Future<void> registerForIncoming({required String accessToken, String? deviceToken});

  /// In-app calling is on unless the build turns it off with --dart-define=OWLAW_IN_APP_CALLS=false.
  static const enabledByBuild = bool.fromEnvironment('OWLAW_IN_APP_CALLS', defaultValue: true);

  /// Incoming in-app calls need FCM (Android) or APNs (iOS) push keys. Off until the build sets
  /// --dart-define=OWLAW_INCOMING_CALLS=true and the server has TWILIO_PUSH_CREDENTIAL_SID.
  static const incomingByBuild = bool.fromEnvironment('OWLAW_INCOMING_CALLS', defaultValue: false);

  static VoiceEngine? _instance;
  static VoiceEngine get instance => _instance ??= TwilioVoiceEngine();
  static set instance(VoiceEngine e) => _instance = e;
}

CallState? mapTwilioEvent(CallEvent e) => switch (e) {
      CallEvent.ringing => CallState.ringing,
      CallEvent.connected || CallEvent.reconnected || CallEvent.answer => CallState.connected,
      CallEvent.reconnecting => CallState.reconnecting,
      CallEvent.callEnded || CallEvent.declined || CallEvent.missedCall => CallState.ended,
      _ => null,
    };

class TwilioVoiceEngine implements VoiceEngine {
  final _states = StreamController<CallState>.broadcast();
  StreamSubscription<CallEvent>? _sub;

  /// Android and iOS use the native Twilio Voice SDK. The web build is only for screenshots and tests.
  @override
  bool get supported => VoiceEngine.enabledByBuild && !kIsWeb;

  @override
  Stream<CallState> get states => _states.stream;

  @override
  Future<void> place({required String accessToken, required String identity, required String to, required Map<String, String> params}) async {
    final tv = TwilioVoicePlatform.instance;
    _sub ??= tv.callEventsListener.listen((e) {
      final s = mapTwilioEvent(e);
      if (s != null) _states.add(s);
    });
    if (!await tv.hasMicAccess()) {
      await tv.requestMicAccess();
      if (!await tv.hasMicAccess()) throw Exception('Owlaw needs the microphone to make a call. Allow it in the phone settings.');
    }
    await tv.requestReadPhoneStatePermission();
    await tv.requestCallPhonePermission();
    await tv.requestManageOwnCallsPermission();
    await tv.registerPhoneAccount();
    await tv.setTokens(accessToken: accessToken);
    _states.add(CallState.connecting);
    final ok = await tv.call.place(from: identity, to: to, extraOptions: params);
    if (ok == false) throw Exception('The phone did not start the call.');
  }

  @override
  Future<void> registerForIncoming({required String accessToken, String? deviceToken}) async {
    final tv = TwilioVoicePlatform.instance;
    await tv.registerPhoneAccount();
    await tv.setTokens(accessToken: accessToken, deviceToken: deviceToken);
  }

  @override
  Future<void> setMuted(bool muted) async => TwilioVoicePlatform.instance.call.toggleMute(muted);

  @override
  Future<void> setSpeaker(bool on) async => TwilioVoicePlatform.instance.call.toggleSpeaker(on);

  @override
  Future<void> hangUp() async {
    await TwilioVoicePlatform.instance.call.hangUp();
    _states.add(CallState.ended);
  }
}
