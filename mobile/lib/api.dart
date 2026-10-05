import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class LegalSuiteApi {
  LegalSuiteApi({this.baseUrl = 'http://127.0.0.1:18081'});

  final String baseUrl;

  Future<String?> get token async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString('ls_access');
  }

  Future<Map<String, dynamic>> login({
    required String firmSlug,
    required String email,
    required String password,
  }) async {
    final res = await http.post(
      Uri.parse('$baseUrl/api/v1/auth/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'firmSlug': firmSlug,
        'email': email,
        'password': password,
      }),
    );
    final body = jsonDecode(res.body) as Map<String, dynamic>;
    if (res.statusCode >= 400 || body['success'] == false) {
      throw Exception(body['message'] ?? 'Login failed');
    }
    final data = body['data'] as Map<String, dynamic>;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('ls_access', data['accessToken'] as String);
    await prefs.setString('ls_user', jsonEncode(data['user']));
    await prefs.setString('ls_tenant', jsonEncode(data['tenant']));
    return data;
  }

  Future<void> logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('ls_access');
  }

  Future<dynamic> get(String path) async {
    return _send('GET', path, null);
  }

  Future<dynamic> post(String path, Map<String, dynamic> body) async {
    return _send('POST', path, body);
  }

  Future<dynamic> _send(String method, String path, Map<String, dynamic>? body) async {
    final t = await token;
    final uri = Uri.parse('$baseUrl$path');
    final headers = {
      'Content-Type': 'application/json',
      if (t != null) 'Authorization': 'Bearer $t',
    };
    final res = method == 'POST'
        ? await http.post(uri, headers: headers, body: jsonEncode(body ?? {}))
        : await http.get(uri, headers: headers);
    final decoded = jsonDecode(res.body) as Map<String, dynamic>;
    if (res.statusCode >= 400 || decoded['success'] == false) {
      throw Exception(decoded['message'] ?? 'Request failed');
    }
    return decoded['data'];
  }
}
