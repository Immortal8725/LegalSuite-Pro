import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'theme.dart';

/// Formats an amount in the firm's currency. South African firms use rand.
String money(dynamic v, [String? currency]) {
  final symbol = switch (currency) { 'USD' => r'$ ', 'GBP' => '£ ', 'EUR' => '€ ', _ => 'R ' };
  return NumberFormat.currency(locale: 'en_ZA', symbol: symbol, decimalDigits: 2).format(v is num ? v : num.tryParse('$v') ?? 0);
}

String niceDate(dynamic iso, {bool withTime = false}) {
  if (iso == null) return '';
  final d = DateTime.tryParse('$iso');
  if (d == null) return '$iso';
  final l = d.toLocal();
  return DateFormat(withTime ? 'd MMM yyyy, HH:mm' : 'd MMM yyyy', 'en_ZA').format(l);
}

String hoursMinutes(dynamic minutes) {
  final m = minutes is num ? minutes.toInt() : int.tryParse('$minutes') ?? 0;
  return m >= 60 ? '${m ~/ 60} h ${m % 60} min' : '$m min';
}

String cleanError(Object e) => e.toString().replaceFirst('Exception: ', '');

/// Loads data, shows a spinner, an error with retry, an empty message,
/// or the content, and supports pull-to-refresh.
class Loader<T> extends StatefulWidget {
  const Loader({super.key, required this.load, required this.builder, this.isEmpty, this.emptyText = 'Nothing here yet.', this.emptyIcon = Icons.inbox_outlined});
  final Future<T> Function() load;
  final Widget Function(BuildContext context, T data, Future<void> Function() reload) builder;
  final bool Function(T data)? isEmpty;
  final String emptyText;
  final IconData emptyIcon;
  @override
  State<Loader<T>> createState() => LoaderState<T>();
}

class LoaderState<T> extends State<Loader<T>> {
  T? data;
  Object? error;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    reload();
  }

  Future<void> reload() async {
    setState(() {
      loading = data == null;
      error = null;
    });
    try {
      final d = await widget.load();
      if (!mounted) return;
      setState(() {
        data = d;
        loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        error = e;
        loading = false;
      });
    }
  }

  Widget _scrollable(Widget child) => RefreshIndicator(
        onRefresh: reload,
        child: LayoutBuilder(
          builder: (c, box) => SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            child: ConstrainedBox(constraints: BoxConstraints(minHeight: box.maxHeight), child: Center(child: child)),
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    if (loading) return const Center(child: CircularProgressIndicator());
    if (error != null && data == null) {
      return _scrollable(Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Icon(Icons.cloud_off, size: 40, color: Colors.redAccent),
          const SizedBox(height: 12),
          Text(cleanError(error!), textAlign: TextAlign.center),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: reload, child: const Text('Try again')),
        ]),
      ));
    }
    final d = data as T;
    if (widget.isEmpty?.call(d) ?? false) {
      return _scrollable(Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(widget.emptyIcon, size: 40, color: Colors.black38),
          const SizedBox(height: 12),
          Text(widget.emptyText, textAlign: TextAlign.center, style: const TextStyle(color: Colors.black54)),
        ]),
      ));
    }
    return RefreshIndicator(onRefresh: reload, child: widget.builder(context, d, reload));
  }
}

class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key});
  final String text;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 6),
        child: Text(text.toUpperCase(),
            style: const TextStyle(fontSize: 12, letterSpacing: 1.1, fontWeight: FontWeight.w700, color: navy)),
      );
}

Color urgencyColour(dynamic daysLeft) {
  final d = daysLeft is num ? daysLeft : num.tryParse('$daysLeft') ?? 999;
  if (d < 0) return Colors.red.shade700;
  if (d <= 7) return Colors.orange.shade800;
  if (d <= 30) return Colors.amber.shade800;
  return Colors.green.shade700;
}

String daysLabel(dynamic daysLeft) {
  final d = daysLeft is num ? daysLeft.toInt() : int.tryParse('$daysLeft');
  if (d == null) return '';
  if (d < 0) return '${-d} days overdue';
  if (d == 0) return 'Today';
  if (d == 1) return 'Tomorrow';
  return 'In $d days';
}

void snack(BuildContext context, String text, {bool error = false}) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text), backgroundColor: error ? Colors.red.shade700 : navy));
}
