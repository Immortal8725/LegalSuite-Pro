import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import 'common.dart';

/// Opens the phone dialler or mail app. Shows a message if the device cannot.
Future<void> openUri(BuildContext context, Uri uri) async {
  try {
    final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!ok && context.mounted) snack(context, 'This device cannot open ${uri.scheme} links.', error: true);
  } catch (_) {
    if (context.mounted) snack(context, 'This device cannot open ${uri.scheme} links.', error: true);
  }
}

Uri telUri(String phone) => Uri(scheme: 'tel', path: phone.replaceAll(RegExp(r'[^0-9+]'), ''));
Uri mailUri(String email, {String? subject}) =>
    Uri(scheme: 'mailto', path: email, query: subject == null ? null : 'subject=${Uri.encodeComponent(subject)}');
