import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:owlaw_mobile/api/api_client.dart';
import 'package:owlaw_mobile/api/token_store.dart';

http.Response ok(Object? data, {int status = 200}) =>
    http.Response(jsonEncode({'success': true, 'data': data}), status, headers: {'content-type': 'application/json'});
http.Response fail(String msg, int status) =>
    http.Response(jsonEncode({'success': false, 'message': msg}), status, headers: {'content-type': 'application/json'});

void main() {
  test('login stores tokens and sends the bearer token afterwards', () async {
    final store = MemoryTokenStore();
    String? auth;
    final api = ApiClient(
      baseUrl: 'http://api.test',
      store: store,
      client: MockClient((req) async {
        if (req.url.path == '/api/v1/auth/login') {
          final body = jsonDecode(req.body) as Map;
          expect(body['firmSlug'], 'smith-associates');
          expect(body.containsKey('totpCode'), isFalse);
          return ok({'accessToken': 'A1', 'refreshToken': 'R1', 'user': {'firstName': 'John'}, 'tenant': {'firmName': 'Smith'}});
        }
        auth = req.headers['Authorization'];
        return ok([1, 2, 3]);
      }),
    );
    final r = await api.login(firmSlug: ' smith-associates ', email: 'john@smithlaw.com', password: 'password');
    expect(r.needsTotp, isFalse);
    expect(store.values[ApiClient.accessKey], 'A1');
    expect(store.values[ApiClient.refreshKey], 'R1');
    expect(api.user?['firstName'], 'John');
    expect(await api.get('/api/v1/cases'), [1, 2, 3]);
    expect(auth, 'Bearer A1');
  });

  test('login reports an authenticator challenge without storing a token', () async {
    final store = MemoryTokenStore();
    final api = ApiClient(baseUrl: 'http://api.test', store: store, client: MockClient((_) async => ok({'requiresTotp': true, 'email': 'a@b.co.za'})));
    final r = await api.login(firmSlug: 'f', email: 'a@b.co.za', password: 'x');
    expect(r.needsTotp, isTrue);
    expect(store.values, isEmpty);
  });

  test('server error message is shown to the attorney', () async {
    final api = ApiClient(baseUrl: 'http://api.test', store: MemoryTokenStore(), client: MockClient((_) async => fail('Invalid email or password', 401)));
    expect(
      () => api.login(firmSlug: 'f', email: 'a@b.co.za', password: 'bad'),
      throwsA(isA<ApiException>().having((e) => e.message, 'message', 'Invalid email or password')),
    );
  });

  test('a 401 refreshes the token once and retries', () async {
    final store = MemoryTokenStore()
      ..values[ApiClient.accessKey] = 'old'
      ..values[ApiClient.refreshKey] = 'R1';
    var calls = 0;
    final api = ApiClient(
      baseUrl: 'http://api.test',
      store: store,
      client: MockClient((req) async {
        if (req.url.path == '/api/v1/auth/refresh') return ok({'accessToken': 'new', 'refreshToken': 'R2'});
        calls++;
        return req.headers['Authorization'] == 'Bearer new' ? ok({'fine': true}) : fail('expired', 401);
      }),
    );
    expect(await api.get('/api/v1/dashboard'), {'fine': true});
    expect(calls, 2);
    expect(store.values[ApiClient.refreshKey], 'R2');
  });

  test('a failed refresh clears the session and tells the app', () async {
    final store = MemoryTokenStore()
      ..values[ApiClient.accessKey] = 'old'
      ..values[ApiClient.refreshKey] = 'R1';
    var expired = false;
    final api = ApiClient(baseUrl: 'http://api.test', store: store, client: MockClient((_) async => fail('nope', 401)))
      ..onSessionExpired = () => expired = true;
    await expectLater(api.get('/api/v1/cases'), throwsA(isA<SessionExpired>()));
    expect(expired, isTrue);
    expect(store.values, isEmpty);
  });

  test('network failure gives a readable message', () async {
    final api = ApiClient(baseUrl: 'http://api.test', store: MemoryTokenStore(), client: MockClient((_) async => throw Exception('socket')));
    await expectLater(api.get('/x'), throwsA(isA<ApiException>().having((e) => e.message, 'm', contains('Cannot reach the Owlaw server'))));
  });

  test('logout clears tokens even when the server is down', () async {
    final store = MemoryTokenStore()..values[ApiClient.accessKey] = 'A';
    final api = ApiClient(baseUrl: 'http://api.test', store: store, client: MockClient((_) async => throw Exception('down')));
    await api.logout();
    expect(await api.hasSession(), isFalse);
  });
}
