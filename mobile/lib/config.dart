import 'package:shared_preferences/shared_preferences.dart';

/// API base URL. Build-time default comes from --dart-define=API_BASE_URL,
/// and the attorney can override it on the sign-in screen.
class AppConfig {
  static const String defaultBaseUrl =
      String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:18081');
  static const _key = 'owlaw_api_base_url';

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
    return normalise(saved == null || saved.isEmpty ? defaultBaseUrl : saved);
  }

  static Future<void> saveBaseUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, normalise(url));
  }
}
