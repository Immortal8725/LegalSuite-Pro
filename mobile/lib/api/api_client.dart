import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'token_store.dart';

class ApiException implements Exception {
  ApiException(this.message, {this.status});
  final String message;
  final int? status;
  @override
  String toString() => message;
}

/// Thrown when the session cannot be renewed. The app returns to sign-in.
class SessionExpired extends ApiException {
  SessionExpired() : super('Your session has ended. Please sign in again.', status: 401);
}

/// Result of a sign-in attempt. [needsTotp] means the firm requires an authenticator code.
class LoginResult {
  LoginResult({required this.needsTotp, this.user, this.tenant});
  final bool needsTotp;
  final Map<String, dynamic>? user;
  final Map<String, dynamic>? tenant;
}

class ApiClient {
  ApiClient({required this.baseUrl, TokenStore? store, http.Client? client, this.timeout = const Duration(seconds: 60)})
      
      : store = store ?? SecureTokenStore(),
        _http = client ?? http.Client();

  static const accessKey = 'owlaw_access';
  static const refreshKey = 'owlaw_refresh';

  String baseUrl;
  final TokenStore store;
  final http.Client _http;
  final Duration timeout;
  void Function()? onSessionExpired;
  Map<String, dynamic>? user;
  Map<String, dynamic>? tenant;

  static const roleKey = 'owlaw_role';

  /// True for the Owlaw platform operator (superadmin). Known after sign-in, or from the saved session.
  Future<bool> isOperator() async => user != null ? user!['role'] == 'superadmin' : (await store.read(roleKey)) == 'superadmin';

  /// Starts authenticator enrolment: returns the setup key and otpauth link.
  Future<Map<String, dynamic>> totpStart() async => await post('/api/v1/auth/totp/start') as Map<String, dynamic>;

  /// Confirms the first code. The server answers with fresh tokens (carrying the 2FA claim) and 10 recovery codes.
  Future<List<String>> totpConfirm(String code) async {
    final d = await post('/api/v1/auth/totp/confirm', {'code': code.trim()}) as Map<String, dynamic>;
    if (d['accessToken'] != null) await store.write(accessKey, d['accessToken'] as String);
    if (d['refreshToken'] != null) await store.write(refreshKey, d['refreshToken'] as String);
    if (d['user'] is Map<String, dynamic>) user = d['user'] as Map<String, dynamic>;
    return [for (final c in (d['recoveryCodes'] as List? ?? const [])) '$c'];
  }

  Future<bool> hasSession() async => (await store.read(accessKey)) != null;

  Future<LoginResult> login({required String firmSlug, required String email, required String password, String? totpCode}) async {
    final data = await _raw('POST', '/api/v1/auth/login', {
      'firmSlug': firmSlug.trim(),
      'email': email.trim(),
      'password': password,
      if (totpCode != null && totpCode.trim().isNotEmpty) 'totpCode': totpCode.trim(),
    }, auth: false) as Map<String, dynamic>;
    if (data['requiresTotp'] == true) return LoginResult(needsTotp: true);
    await store.write(accessKey, data['accessToken'] as String);
    if (data['refreshToken'] != null) await store.write(refreshKey, data['refreshToken'] as String);
    user = data['user'] as Map<String, dynamic>?;
    tenant = data['tenant'] as Map<String, dynamic>?;
    await store.write(roleKey, '${user?['role'] ?? ''}');
    return LoginResult(needsTotp: false, user: user, tenant: tenant);
  }

  Future<Map<String, dynamic>> me() async {
    final d = await get('/api/v1/auth/me') as Map<String, dynamic>;
    user = d['user'] as Map<String, dynamic>?;
    tenant = d['tenant'] as Map<String, dynamic>?;
    return d;
  }

  Future<void> logout() async {
    try {
      if (await hasSession()) await _raw('POST', '/api/v1/auth/logout', {}, auth: true);
    } catch (_) {
      // Signing out locally still matters if the server cannot be reached.
    }
    await clearSession();
  }

  Future<void> clearSession() async {
    await store.delete(accessKey);
    await store.delete(refreshKey);
    await store.delete(roleKey);
    user = null;
    tenant = null;
  }

  Future<dynamic> get(String path) => _authed('GET', path, null);
  Future<dynamic> post(String path, [Map<String, dynamic>? body]) => _authed('POST', path, body ?? {});
  Future<dynamic> patch(String path, [Map<String, dynamic>? body]) => _authed('PATCH', path, body ?? {});
  Future<dynamic> put(String path, [Map<String, dynamic>? body]) => _authed('PUT', path, body ?? {});

  /// Sends a file as multipart form data, the way the web app uploads documents and proofs.
  Future<dynamic> upload(String path, {required List<int> bytes, required String filename, Map<String, String> fields = const {}}) async {
    Future<dynamic> once() async {
      final req = http.MultipartRequest('POST', Uri.parse('$baseUrl$path'));
      final t = await store.read(accessKey);
      if (t != null) req.headers['Authorization'] = 'Bearer $t';
      req.headers['Accept'] = 'application/json';
      fields.forEach((k, v) {
        if (v.trim().isNotEmpty) req.fields[k] = v.trim();
      });
      req.files.add(http.MultipartFile.fromBytes('file', bytes, filename: filename));
      return _send(req);
    }

    try {
      return await once();
    } on ApiException catch (e) {
      if (e.status != 401) rethrow;
      if (await _refresh()) return once();
      await clearSession();
      onSessionExpired?.call();
      throw SessionExpired();
    }
  }

  /// Downloads a file (a document or a proof of payment) as raw bytes.
  Future<List<int>> download(String path) async {
    Future<List<int>> once() async {
      final req = http.Request('GET', Uri.parse('$baseUrl$path'));
      final t = await store.read(accessKey);
      if (t != null) req.headers['Authorization'] = 'Bearer $t';
      http.Response res;
      try {
        res = await http.Response.fromStream(await _http.send(req).timeout(timeout));
      } catch (_) {
        throw ApiException('Cannot reach the Owlaw server at $baseUrl. Check the server address and your connection.');
      }
      if (res.statusCode >= 400) throw ApiException('Download failed (${res.statusCode}).', status: res.statusCode);
      return res.bodyBytes;
    }

    try {
      return await once();
    } on ApiException catch (e) {
      if (e.status != 401) rethrow;
      if (await _refresh()) return once();
      await clearSession();
      onSessionExpired?.call();
      throw SessionExpired();
    }
  }

  Future<dynamic> _authed(String method, String path, Map<String, dynamic>? body) async {
    try {
      return await _raw(method, path, body, auth: true);
    } on ApiException catch (e) {
      if (e.status != 401) rethrow;
      if (await _refresh()) return _raw(method, path, body, auth: true);
      await clearSession();
      onSessionExpired?.call();
      throw SessionExpired();
    }
  }

  Future<bool> _refresh() async {
    final r = await store.read(refreshKey);
    if (r == null) return false;
    try {
      final d = await _raw('POST', '/api/v1/auth/refresh', {'refreshToken': r}, auth: false) as Map<String, dynamic>;
      final a = d['accessToken'] as String?;
      if (a == null) return false;
      await store.write(accessKey, a);
      if (d['refreshToken'] != null) await store.write(refreshKey, d['refreshToken'] as String);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<dynamic> _raw(String method, String path, Map<String, dynamic>? body, {required bool auth}) async {
    final uri = Uri.parse('$baseUrl$path');
    final headers = <String, String>{'Content-Type': 'application/json', 'Accept': 'application/json'};
    if (auth) {
      final t = await store.read(accessKey);
      if (t != null) headers['Authorization'] = 'Bearer $t';
    }
    final req = http.Request(method, uri)..headers.addAll(headers);
    if (body != null) req.body = jsonEncode(body);
    return _send(req);
  }

  Future<dynamic> _send(http.BaseRequest req) async {
    http.Response res;
    try {
      res = await http.Response.fromStream(await _http.send(req).timeout(timeout));
    } on TimeoutException {
      throw ApiException('The server took too long to answer. Check your connection and try again.');
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException('Cannot reach the Owlaw server at $baseUrl. Check the server address and your connection.');
    }
    Map<String, dynamic>? decoded;
    try {
      final j = res.body.isEmpty ? null : jsonDecode(utf8.decode(res.bodyBytes));
      if (j is Map<String, dynamic>) decoded = j;
    } catch (_) {
      decoded = null;
    }
    if (res.statusCode >= 400 || decoded?['success'] == false) {
      final msg = decoded?['message']?.toString() ?? decoded?['error']?.toString();
      throw ApiException(
        msg ?? (res.statusCode == 401 ? 'Please sign in again.' : 'Request failed (${res.statusCode}).'),
        status: res.statusCode,
      );
    }
    return decoded?['data'];
  }
}
