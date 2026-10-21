import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import 'api_client.dart';

/// Time entries recorded without signal are kept on the phone and sent when the server can be reached.
class OfflineDrafts {
  static const _key = 'owlaw_offline_time_drafts';

  static bool isOffline(Object e) {
    final m = e.toString();
    return m.contains('Cannot reach the Owlaw server') || m.contains('took too long');
  }

  static Future<List<Map<String, dynamic>>> all() async {
    final p = await SharedPreferences.getInstance();
    return (p.getStringList(_key) ?? const []).map((s) => (jsonDecode(s) as Map).cast<String, dynamic>()).toList();
  }

  static Future<void> add(Map<String, dynamic> entry) async {
    final p = await SharedPreferences.getInstance();
    await p.setStringList(_key, [...(p.getStringList(_key) ?? const []), jsonEncode(entry)]);
  }

  /// Sends waiting drafts. Returns how many were sent; stops at the first network failure.
  static Future<int> sync(ApiClient api) async {
    final p = await SharedPreferences.getInstance();
    final left = [...(p.getStringList(_key) ?? const <String>[])];
    var sent = 0;
    while (left.isNotEmpty) {
      try {
        await api.post('/api/v1/time-entries', (jsonDecode(left.first) as Map).cast<String, dynamic>());
      } catch (e) {
        if (isOffline(e)) break;
        // The server refused it (for example a closed matter): drop it rather than retry forever.
      }
      left.removeAt(0);
      sent++;
      await p.setStringList(_key, left);
    }
    return sent;
  }
}
