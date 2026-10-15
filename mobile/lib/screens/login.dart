import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../config.dart';
import '../ui/theme.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.api, required this.onSignedIn});
  final ApiClient api;
  final VoidCallback onSignedIn;
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final firm = TextEditingController();
  final email = TextEditingController();
  final password = TextEditingController();
  final code = TextEditingController();
  late final server = TextEditingController(text: widget.api.baseUrl);
  bool needsCode = false;
  bool busy = false;
  bool showServer = false;
  String? error;

  @override
  void dispose() {
    for (final c in [firm, email, password, code, server]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _submit() async {
    if (firm.text.trim().isEmpty || email.text.trim().isEmpty || password.text.isEmpty) {
      setState(() => error = 'Enter your firm, email and password.');
      return;
    }
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final url = AppConfig.normalise(server.text);
      final problem = AppConfig.checkBaseUrl(url);
      if (problem != null) {
        setState(() => error = problem);
        return;
      }
      if (url != widget.api.baseUrl) {
        widget.api.baseUrl = url;
        await AppConfig.saveBaseUrl(url);
      }
      final r = await widget.api.login(
        firmSlug: firm.text,
        email: email.text,
        password: password.text,
        totpCode: needsCode ? code.text : null,
      );
      if (!mounted) return;
      if (r.needsTotp) {
        setState(() => needsCode = true);
      } else {
        widget.onSignedIn();
      }
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: navy,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                const Center(child: Wordmark(size: 30)),
                const SizedBox(height: 8),
                const Text('Eyes on every matter.', textAlign: TextAlign.center, style: TextStyle(color: Colors.white70)),
                const SizedBox(height: 24),
                Card(
                  margin: EdgeInsets.zero,
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: AutofillGroup(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                        const Text('Attorney sign-in', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: navy)),
                        const SizedBox(height: 16),
                        TextField(
                          key: const Key('firm'),
                          controller: firm,
                          enabled: !needsCode,
                          decoration: const InputDecoration(labelText: 'Firm ID', hintText: 'e.g. ndlovu-partners'),
                          textInputAction: TextInputAction.next,
                          autocorrect: false,
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          key: const Key('email'),
                          controller: email,
                          enabled: !needsCode,
                          keyboardType: TextInputType.emailAddress,
                          autofillHints: const [AutofillHints.email],
                          decoration: const InputDecoration(labelText: 'Email'),
                          textInputAction: TextInputAction.next,
                          autocorrect: false,
                        ),
                        const SizedBox(height: 12),
                        TextField(
                          key: const Key('password'),
                          controller: password,
                          enabled: !needsCode,
                          obscureText: true,
                          autofillHints: const [AutofillHints.password],
                          decoration: const InputDecoration(labelText: 'Password'),
                          onSubmitted: (_) => _submit(),
                        ),
                        if (needsCode) ...[
                          const SizedBox(height: 12),
                          TextField(
                            key: const Key('totp'),
                            controller: code,
                            keyboardType: TextInputType.number,
                            autofillHints: const [AutofillHints.oneTimeCode],
                            decoration: const InputDecoration(labelText: 'Authenticator code', helperText: 'Your firm requires a 6-digit code.'),
                            onSubmitted: (_) => _submit(),
                          ),
                        ],
                        if (error != null) ...[
                          const SizedBox(height: 12),
                          Text(error!, key: const Key('loginError'), style: TextStyle(color: Colors.red.shade700)),
                        ],
                        const SizedBox(height: 16),
                        FilledButton(
                          key: const Key('signIn'),
                          onPressed: busy ? null : _submit,
                          child: busy
                              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                              : Text(needsCode ? 'Verify and sign in' : 'Sign in'),
                        ),
                        TextButton.icon(
                          onPressed: () => setState(() => showServer = !showServer),
                          icon: const Icon(Icons.dns_outlined, size: 18),
                          label: const Text('Server settings'),
                        ),
                        if (showServer)
                          TextField(
                            key: const Key('server'),
                            controller: server,
                            keyboardType: TextInputType.url,
                            autocorrect: false,
                            decoration: const InputDecoration(labelText: 'Owlaw server address', hintText: 'https://api.owlaw.co.za'),
                          ),
                      ]),
                    ),
                  ),
                ),
              ]),
            ),
          ),
        ),
      ),
    );
  }
}
