import 'api_client.dart';
import 'owlaw_api.dart';

/// What the signed-in person may see, mirroring the web app:
/// a page shows only if its module is enabled for the firm, and
/// firm administration (team, website, product billing, settings) stays on the web.
class Access {
  Access({required this.role, required Set<String> modules, this.modulesKnown = true}) : _modules = modules;

  final String role;
  final Set<String> _modules;
  final bool modulesKnown;

  static final everything = Access(role: 'attorney', modules: const {}, modulesKnown: false);

  /// The access for the signed-in person, set by the home shell after sign-in.
  static Access current = everything;

  bool get isClient => role == 'client';
  bool get isFirmAdmin => const {'owner', 'partner', 'director'}.contains(role);

  /// Staff can change fee invoices and trust. Client portal users cannot (the API refuses them too).
  bool get isStaff => !isClient && role.isNotEmpty;

  /// If the module list could not be loaded, show the page and let the API decide.
  bool module(String slug) => !modulesKnown || _modules.contains(slug);

  bool get billing => isStaff && module('billing');
  bool get trust => isStaff && module('trust');
  bool get expenses => isStaff && module('expenses');
  bool get documents => module('documents');
  bool get esign => module('esignatures');
  bool get tasks => module('tasks');
  bool get calendar => module('calendar');
  bool get ai => module('ai');
  bool get messages => isStaff;
  bool get conflicts => module('conflicts');
  bool get clients => module('clients');

  static Access from(String? role, List<Json> modules) => Access(
        role: (role ?? '').toLowerCase(),
        modules: modules.where((m) => m['enabled'] == true || m['core'] == true).map((m) => '${m['slug']}').toSet(),
      );

  static Future<Access> load(ApiClient api) async {
    try {
      if (api.user == null || api.tenant == null) await api.me();
      final role = api.user?['role']?.toString();
      final tid = api.tenant?['id']?.toString();
      if (tid == null) return Access(role: (role ?? '').toLowerCase(), modules: const {}, modulesKnown: false);
      try {
        return from(role, await api.tenantModules(tid));
      } catch (_) {
        return Access(role: (role ?? '').toLowerCase(), modules: const {}, modulesKnown: false);
      }
    } catch (_) {
      return everything;
    }
  }
}
