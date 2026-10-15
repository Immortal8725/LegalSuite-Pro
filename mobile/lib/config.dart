import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// API base URL. Build-time default comes from --dart-define=API_BASE_URL,
/// and the attorney can override it on the sign-in screen.
class AppConfig {
  /// Release builds must be given --dart-define=API_BASE_URL=https://... (the Gradle build stops otherwise).
  /// https://app.owlaw.co.za is only a placeholder. Debug builds default to the emulator's view of a local API.
  static const String _define = String.fromEnvironment('API_BASE_URL');
  static const String releasePlaceholder = 'https://app.owlaw.co.za';
  static String get defaultBaseUrl =>
      _define.isNotEmpty ? _define : (kReleaseMode ? releasePlaceholder : 'http://10.0.2.2:18081');

  /// Release builds only talk HTTPS. Returns a message when the address is not allowed, otherwise null.
  static String? checkBaseUrl(String url, {bool release = kReleaseMode}) {
    final u = Uri.tryParse(url.trim());
    if (u == null || !u.hasScheme || u.host.isEmpty) return 'Enter the full address, for example https://app.example.co.za';
    if (u.scheme != 'https' && u.scheme != 'http') return 'The address must start with https://';
    if (release && u.scheme != 'https') return 'This build only connects over HTTPS.';
    return null;
  }
  static const _key = 'owlaw_api_base_url';

  /// Where the Owlaw web app lives, for links a client opens (e-sign).
  /// Set with --dart-define=WEB_BASE_URL. Without it the API address is used, which suits
  /// a single-host deployment where the web app and API share an origin.
  static const String webBaseUrlDefine = String.fromEnvironment('WEB_BASE_URL');

  static String webLink(String apiBase, String path) {
    final base = normalise(webBaseUrlDefine.isNotEmpty ? webBaseUrlDefine : apiBase);
    return '$base${path.startsWith('/') ? path : '/$path'}';
  }

  static String normalise(String url) {
    var u = url.trim();
    while (u.endsWith('/')) {
      u = u.substring(0, u.length - 1);
    }
    return u;
  }

  static Future<String> loadBaseUrl() async {
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_key);
    final url = normalise(saved == null || saved.isEmpty ? defaultBaseUrl : saved);
    return checkBaseUrl(url) == null ? url : normalise(defaultBaseUrl);
  }

  static Future<void> saveBaseUrl(String url) async {
    final problem = checkBaseUrl(url);
    if (problem != null) throw ArgumentError(problem);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, normalise(url));
  }
}
