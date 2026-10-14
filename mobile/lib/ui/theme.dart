import 'package:flutter/material.dart';

const navy = Color(0xFF1A365D);
const navyDark = Color(0xFF10233F);
const gold = Color(0xFFC9A04F);

ThemeData owlawTheme() {
  final scheme = ColorScheme.fromSeed(seedColor: navy, primary: navy, secondary: gold, surface: Colors.white);
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: const Color(0xFFF6F7FA),
    appBarTheme: const AppBarTheme(backgroundColor: navy, foregroundColor: Colors.white, centerTitle: false),
    navigationBarTheme: NavigationBarThemeData(
      indicatorColor: gold.withValues(alpha: 0.35),
      labelTextStyle: WidgetStateProperty.all(const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
    ),
    inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(backgroundColor: gold, foregroundColor: navyDark, minimumSize: const Size.fromHeight(48)),
    ),
    cardTheme: const CardThemeData(color: Colors.white, elevation: 0.5, margin: EdgeInsets.symmetric(horizontal: 12, vertical: 5)),
  );
}

/// The OwLaw wordmark: "Ow" in the given colour, "Law" in gold.
class Wordmark extends StatelessWidget {
  const Wordmark({super.key, this.size = 22, this.color = Colors.white});
  final double size;
  final Color color;
  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'OwLaw',
      excludeSemantics: true,
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Container(
          width: size * 1.5,
          height: size * 1.5,
          decoration: BoxDecoration(color: gold, borderRadius: BorderRadius.circular(size * 0.4)),
          child: Icon(Icons.visibility_outlined, color: navyDark, size: size),
        ),
        SizedBox(width: size * 0.4),
        Text('Ow', style: TextStyle(fontSize: size, fontWeight: FontWeight.w800, color: color)),
        Text('Law', style: TextStyle(fontSize: size, fontWeight: FontWeight.w800, color: gold)),
      ]),
    );
  }
}
