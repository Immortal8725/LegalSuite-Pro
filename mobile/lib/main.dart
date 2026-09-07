import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:legalsuite_mobile/api.dart';
import 'package:legalsuite_mobile/screens/home.dart';
import 'package:legalsuite_mobile/screens/login.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const LegalSuiteApp());
}

class LegalSuiteApp extends StatelessWidget {
  const LegalSuiteApp({super.key});

  @override
  Widget build(BuildContext context) {
    const navy = Color(0xFF1A365D);
    const gold = Color(0xFFC6A052);
    return MaterialApp(
      title: 'LegalSuite Pro',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: navy, primary: navy, secondary: gold),
        textTheme: GoogleFonts.sourceSans3TextTheme(),
        useMaterial3: true,
      ),
      home: const Gate(),
    );
  }
}

class Gate extends StatefulWidget {
  const Gate({super.key});

  @override
  State<Gate> createState() => _GateState();
}

class _GateState extends State<Gate> {
  final api = LegalSuiteApi();
  bool ready = false;
  bool signedIn = false;

  @override
  void initState() {
    super.initState();
    SharedPreferences.getInstance().then((p) {
      setState(() {
        signedIn = p.getString('ls_access') != null;
        ready = true;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    if (!ready) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return signedIn
        ? HomeScreen(api: api, onLogout: () => setState(() => signedIn = false))
        : LoginScreen(
            api: api,
            onLoggedIn: () => setState(() => signedIn = true),
          );
  }
}
