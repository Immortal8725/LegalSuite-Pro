import 'dart:io' show File;

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import 'common.dart';
import 'theme.dart';

class PickedFile {
  PickedFile(this.bytes, this.name);
  final List<int> bytes;
  final String name;
}

/// Asks where the file comes from (camera, photos or files) and returns its bytes.
/// Returns null when the attorney cancels.
Future<PickedFile?> pickAnyFile(BuildContext context) async {
  final source = await showModalBottomSheet<String>(
    context: context,
    builder: (c) => SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        if (!kIsWeb)
          ListTile(leading: const Icon(Icons.photo_camera_outlined, color: navy), title: const Text('Take a photo'), onTap: () => Navigator.pop(c, 'camera')),
        ListTile(leading: const Icon(Icons.photo_library_outlined, color: navy), title: const Text('Choose a photo'), onTap: () => Navigator.pop(c, 'gallery')),
        ListTile(leading: const Icon(Icons.attach_file, color: navy), title: const Text('Choose a file'), onTap: () => Navigator.pop(c, 'file')),
      ]),
    ),
  );
  if (source == null) return null;
  try {
    if (source == 'file') {
      final r = await FilePicker.pickFiles(withData: true, type: FileType.custom, allowedExtensions: const ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'odt', 'ods', 'rtf', 'txt', 'csv', 'jpg', 'jpeg', 'png', 'heic', 'heif', 'eml', 'msg', 'zip']);
      final f = r?.files.singleOrNull;
      if (f == null) return null;
      final bytes = f.bytes ?? (f.path != null ? await File(f.path!).readAsBytes() : null);
      if (bytes == null) return null;
      return PickedFile(bytes, f.name);
    }
    final x = await ImagePicker().pickImage(source: source == 'camera' ? ImageSource.camera : ImageSource.gallery, imageQuality: 85, maxWidth: 2400);
    if (x == null) return null;
    return PickedFile(await x.readAsBytes(), x.name);
  } catch (e) {
    if (context.mounted) snack(context, 'Could not open that file: ${cleanError(e)}', error: true);
    return null;
  }
}

/// Opens downloaded bytes in the phone's viewer. On the web build it opens a browser tab.
Future<void> openBytes(BuildContext context, List<int> bytes, String name) async {
  try {
    if (kIsWeb) {
      await launchUrl(Uri.dataFromBytes(bytes, mimeType: 'application/octet-stream'), webOnlyWindowName: '_blank');
      return;
    }
    final dir = await getTemporaryDirectory();
    final safe = name.replaceAll(RegExp(r'[^A-Za-z0-9._ -]'), '_');
    final f = File('${dir.path}/$safe');
    await f.writeAsBytes(bytes, flush: true);
    final r = await OpenFilex.open(f.path);
    if (r.type != ResultType.done && context.mounted) {
      await SharePlus.instance.share(ShareParams(files: [XFile(f.path)], subject: name));
    }
  } catch (e) {
    if (context.mounted) snack(context, 'Could not open $name: ${cleanError(e)}', error: true);
  }
}

Future<void> shareText(BuildContext context, String text, {String? subject}) async {
  try {
    await SharePlus.instance.share(ShareParams(text: text, subject: subject));
  } catch (e) {
    if (context.mounted) snack(context, 'Could not share: ${cleanError(e)}', error: true);
  }
}

/// Saves bytes to a temporary file and opens the phone's share sheet (WhatsApp, email, Drive and so on).
Future<void> shareBytes(BuildContext context, List<int> bytes, String name, {String? text}) async {
  try {
    if (kIsWeb) {
      await launchUrl(Uri.dataFromBytes(bytes, mimeType: 'application/octet-stream'), webOnlyWindowName: '_blank');
      return;
    }
    final dir = await getTemporaryDirectory();
    final safe = name.replaceAll(RegExp(r'[^A-Za-z0-9._ -]'), '_');
    final f = File('${dir.path}/$safe');
    await f.writeAsBytes(bytes, flush: true);
    await SharePlus.instance.share(ShareParams(files: [XFile(f.path)], subject: name, text: text));
  } catch (e) {
    if (context.mounted) snack(context, 'Could not share $name: ${cleanError(e)}', error: true);
  }
}

/// Lets the user open or share a generated file.
Future<void> openOrShare(BuildContext context, List<int> bytes, String name) async {
  final pick = await showModalBottomSheet<String>(
    context: context,
    builder: (c) => SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        ListTile(title: Text(name, style: const TextStyle(fontWeight: FontWeight.bold))),
        ListTile(key: const Key('openFile'), leading: const Icon(Icons.open_in_new, color: navy), title: const Text('Open'), onTap: () => Navigator.pop(c, 'open')),
        ListTile(key: const Key('shareFile'), leading: const Icon(Icons.share_outlined, color: navy), title: const Text('Share or save'), onTap: () => Navigator.pop(c, 'share')),
      ]),
    ),
  );
  if (!context.mounted || pick == null) return;
  if (pick == 'open') {
    await openBytes(context, bytes, name);
  } else {
    await shareBytes(context, bytes, name);
  }
}

/// Picks a CSV or text file and returns its text.
Future<PickedFile?> pickCsv(BuildContext context) async {
  try {
    final r = await FilePicker.pickFiles(withData: true, type: FileType.custom, allowedExtensions: const ['csv', 'txt']);
    final f = r?.files.singleOrNull;
    if (f == null) return null;
    final bytes = f.bytes ?? (f.path != null ? await File(f.path!).readAsBytes() : null);
    return bytes == null ? null : PickedFile(bytes, f.name);
  } catch (e) {
    if (context.mounted) snack(context, 'Could not open that file: ${cleanError(e)}', error: true);
    return null;
  }
}
