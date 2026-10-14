import 'package:flutter/material.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'api/api_client.dart';
import 'config.dart';
import 'screens/login.dart';
import 'screens/shell.dart';
import 'ui/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('en_ZA');
  final api = ApiClient(baseUrl: await AppConfig.loadBaseUrl());
  runApp(OwlawApp(api: api, signedIn: await api.hasSession()));
}

class OwlawApp extends StatefulWidget {
  const OwlawApp({super.key, required this.api, this.signedIn = false});
  final ApiClient api;
  final bool signedIn;
  @override
  State<OwlawApp> createState() => _OwlawAppState();
}

class _OwlawAppState extends State<OwlawApp> {
  final _messenger = GlobalKey<ScaffoldMessengerState>();
  late bool signedIn = widget.signedIn;

  @override
  void initState() {
    super.initState();
    widget.api.onSessionExpired = () {
      if (!mounted) return;
      setState(() => signedIn = false);
      _messenger.currentState?.showSnackBar(const SnackBar(content: Text('Your session has ended. Please sign in again.')));
    };
  }

  Future<void> _logout() async {
    await widget.api.logout();
    if (mounted) setState(() => signedIn = false);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Owlaw',
      debugShowCheckedModeBanner: false,
      scaffoldMessengerKey: _messenger,
      theme: owlawTheme(),
      home: signedIn
          ? HomeShell(api: widget.api, onLogout: _logout)
          : LoginScreen(api: widget.api, onSignedIn: () => setState(() => signedIn = true)),
    );
  }
}
