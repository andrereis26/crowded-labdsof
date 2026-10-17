import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import 'app_tag.dart';

/// Icon on a tinted rounded square, at the start of list rows (estimates in
/// P14, transport options in P18, contributions in P20).
class AppIconBadge extends StatelessWidget {
  const AppIconBadge({
    super.key,
    required this.icon,
    this.tone = AppTagTone.neutral,
    this.iconSize = 18,
    this.dark = false,
  });

  final IconData icon;

  /// Same colours as [AppTag]; neutral is grey with a dark icon.
  final AppTagTone tone;
  final double iconSize;

  /// Black square with a white icon ("Parque da Cidade · Porta A").
  final bool dark;

  @override
  Widget build(BuildContext context) {
    final (background, foreground) = dark
        ? (AppColors.ink, Colors.white)
        : tone == AppTagTone.neutral
        ? (AppColors.background, AppColors.ink)
        : AppTag.colorsFor(tone);
    return ExcludeSemantics(
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.sm),
        decoration: BoxDecoration(
          color: background,
          borderRadius: const BorderRadius.all(AppRadii.field),
        ),
        child: Icon(icon, size: iconSize, color: foreground),
      ),
    );
  }
}
