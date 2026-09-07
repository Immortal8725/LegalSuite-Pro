import 'package:flutter/material.dart';
import 'package:legalsuite_mobile/api.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.api, required this.onLoggedIn});

  final LegalSuiteApi api;
  final VoidCallback onLoggedIn;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final slug = TextEditingController(text: 'smith-associates');
  final email = TextEditingController(text: 'john@smithlaw.com');
  final password = TextEditingController(text: 'password');
  String? error;
  bool busy = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF1A365D),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 32),
              const Text('LegalSuite Pro',
                  style: TextStyle(color: Color(0xFFC6A052), fontSize: 14, fontWeight: FontWeight.bold, letterSpacing: 2)),
              const SizedBox(height: 8),
              const Text('The docket in your pocket.',
                  style: TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.w800)),
              const SizedBox(height: 32),
              _field(slug, 'Firm slug'),
              _field(email, 'Email'),
              _field(password, 'Password', obscure: true),
              if (error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(error!, style: const TextStyle(color: Colors.orangeAccent)),
                ),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: busy
                      ? null
                      : () async {
                          setState(() {
                            busy = true;
                            error = null;
                          });
                          try {
                            await widget.api.login(
                              firmSlug: slug.text.trim(),
                              email: email.text.trim(),
                              password: password.text,
                            );
                            widget.onLoggedIn();
                          } catch (e) {
                            setState(() => error = e.toString());
                          } finally {
                            if (mounted) setState(() => busy = false);
                          }
                        },
                  style: FilledButton.styleFrom(backgroundColor: const Color(0xFFC6A052), foregroundColor: const Color(0xFF1A365D)),
                  child: Text(busy ? 'Signing in…' : 'Sign in'),
                ),
              ),
              const Spacer(),
              const Text('Point API_BASE at your Spring Boot host. Demo: smith-associates / john@smithlaw.com / password',
                  style: TextStyle(color: Colors.white54, fontSize: 12)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _field(TextEditingController c, String label, {bool obscure = false}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        controller: c,
        obscureText: obscure,
        style: const TextStyle(color: Colors.white),
        decoration: InputDecoration(
          labelText: label,
          labelStyle: const TextStyle(color: Colors.white70),
          enabledBorder: const OutlineInputBorder(borderSide: BorderSide(color: Colors.white24)),
          focusedBorder: const OutlineInputBorder(borderSide: BorderSide(color: Color(0xFFC6A052))),
        ),
      ),
    );
  }
}
