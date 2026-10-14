import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:owlaw_mobile/api/api_client.dart';
import 'package:owlaw_mobile/api/token_store.dart';
import 'package:owlaw_mobile/main.dart';
import 'package:owlaw_mobile/screens/matters.dart';
import 'package:owlaw_mobile/screens/time.dart';
import 'package:owlaw_mobile/ui/common.dart';
import 'package:shared_preferences/shared_preferences.dart';

final cases = [
  {'id': 'c1', 'caseNumber': 'C-1048', 'title': 'Taylor Contract Dispute', 'clientName': 'Taylor Holdings', 'practiceArea': 'Contract', 'status': 'open', 'docketClocks': []},
  {'id': 'c2', 'caseNumber': 'C-2002', 'title': 'Van der Merwe v City', 'clientName': 'P van der Merwe', 'practiceArea': 'Delict', 'status': 'open', 'docketClocks': [{'daysLeft': 3}]},
];

ApiClient fakeApi({bool signedIn = true}) {
  final store = MemoryTokenStore();
  if (signedIn) store.values[ApiClient.accessKey] = 'A';
  return ApiClient(
    baseUrl: 'http://api.test',
    store: store,
    client: MockClient((req) async {
      Object? data;
      switch (req.url.path) {
        case '/api/v1/auth/login':
          data = {'accessToken': 'A', 'refreshToken': 'R', 'user': {'firstName': 'John'}, 'tenant': {}};
        case '/api/v1/dashboard':
          data = {'activeCases': 2, 'outstanding': 1200, 'hoursBilled': 5, 'unreadNotifications': 1, 'currency': 'ZAR', 'docket': [], 'upcomingEvents': []};
        case '/api/v1/cases':
          data = cases;
        default:
          data = [];
      }
      return http.Response(jsonEncode({'success': true, 'data': data}), 200);
    }),
  );
}

void main() {
  setUpAll(() async => initializeDateFormatting('en_ZA'));
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('matter search matches number, client and words in any order', () {
    final all = cases.map((e) => Map<String, dynamic>.from(e)).toList();
    expect(filterMatters(all, '').length, 2);
    expect(filterMatters(all, 'c-2002').single['id'], 'c2');
    expect(filterMatters(all, 'holdings taylor').single['id'], 'c1');
    expect(filterMatters(all, 'nothing'), isEmpty);
  });

  test('time parser understands minutes, h:mm and hours', () {
    expect(parseMinutes('45'), 45);
    expect(parseMinutes('1:30'), 90);
    expect(parseMinutes('1.5h'), 90);
    expect(parseMinutes('2,25h'), 135);
    expect(parseMinutes('soon'), isNull);
    expect(parseMinutes('0'), isNull);
  });

  testWidgets('sign-in asks for all fields before calling the server', (tester) async {
    await tester.pumpWidget(OwlawApp(api: fakeApi(signedIn: false)));
    expect(find.text('Attorney sign-in'), findsOneWidget);
    await tester.tap(find.byKey(const Key('signIn')));
    await tester.pump();
    expect(find.text('Enter your firm, email and password.'), findsOneWidget);
  });

  testWidgets('signing in opens the dashboard with bottom navigation', (tester) async {
    await tester.pumpWidget(OwlawApp(api: fakeApi(signedIn: false)));
    await tester.enterText(find.byKey(const Key('firm')), 'smith-associates');
    await tester.enterText(find.byKey(const Key('email')), 'john@smithlaw.com');
    await tester.enterText(find.byKey(const Key('password')), 'password');
    await tester.tap(find.byKey(const Key('signIn')));
    await tester.pumpAndSettle();
    expect(find.text('Welcome back, John'), findsOneWidget);
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.text(money(1200, 'ZAR')), findsOneWidget);
  });

  testWidgets('matters tab lists and filters matters', (tester) async {
    await tester.pumpWidget(OwlawApp(api: fakeApi(), signedIn: true));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Matters').last);
    await tester.pumpAndSettle();
    expect(find.textContaining('Taylor Contract Dispute'), findsOneWidget);
    expect(find.text('In 3 days'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('matterSearch')), 'van der');
    await tester.pumpAndSettle();
    expect(find.textContaining('Taylor Contract Dispute'), findsNothing);
    expect(find.textContaining('Van der Merwe v City'), findsOneWidget);
  });
}
