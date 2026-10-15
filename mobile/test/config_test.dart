import 'package:flutter_test/flutter_test.dart';
import 'package:owlaw_mobile/config.dart';

void main() {
  test('release builds refuse plain HTTP API addresses', () {
    expect(AppConfig.checkBaseUrl('http://10.0.2.2:18081', release: true), isNotNull);
    expect(AppConfig.checkBaseUrl('https://app.example.co.za', release: true), isNull);
  });

  test('debug builds allow a local HTTP API', () {
    expect(AppConfig.checkBaseUrl('http://10.0.2.2:18081', release: false), isNull);
  });

  test('incomplete addresses are refused', () {
    expect(AppConfig.checkBaseUrl('app.example.co.za', release: false), isNotNull);
    expect(AppConfig.checkBaseUrl('ftp://x.co.za', release: false), isNotNull);
  });
}
