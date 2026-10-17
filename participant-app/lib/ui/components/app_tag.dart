import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

enum AppTagTone { neutral, success, warning, danger, info }

enum AppTagSize {
  /// 11 px ("No recinto", "Desatualizado", "Correspondência alta").
  small,

  /// 12 px ("PEDIDO DE CONFIRMAÇÃO").
  medium,

  /// 13 px ("Código de entrada encontrado").
  large,
}

/// Rounded pill with text and an optional icon. Always carries text, so the
/// colour is never the only cue.
class AppTag extends StatelessWidget {
  const AppTag({
    super.key,
    required this.label,
    this.tone = AppTagTone.neutral,
    this.size = AppTagSize.small,
    this.icon,
  });

  final String label;
  final AppTagTone tone;
  final AppTagSize size;
  final IconData? icon;

  static (Color background, Color foreground) colorsFor(AppTagTone tone) =>
      switch (tone) {
        AppTagTone.neutral => (AppColors.background, AppColors.inkSecondary),
        AppTagTone.success => (AppColors.successSoft, AppColors.success),
        AppTagTone.warning => (AppColors.warningSoft, AppColors.warning),
        AppTagTone.danger => (AppColors.dangerSoft, AppColors.danger),
        AppTagTone.info => (AppColors.primarySoft, AppColors.primary),
      };

  @override
  Widget build(BuildContext context) {
    final (background, foreground) = colorsFor(tone);
    final (padding, fontSize, iconSize) = switch (size) {
      AppTagSize.small => (
        const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        11.0,
        12.0,
      ),
      AppTagSize.medium => (
        const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        12.0,
        14.0,
      ),
      AppTagSize.large => (const EdgeInsets.fromLTRB(10, 6, 12, 6), 13.0, 14.0),
    };

    return Container(
      padding: padding,
      decoration: BoxDecoration(
        color: background,
        borderRadius: const BorderRadius.all(AppRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: iconSize, color: foreground),
            SizedBox(width: size == AppTagSize.small ? 4 : AppSpacing.xs),
          ],
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: AppTextStyles.tag.copyWith(
                fontSize: fontSize,
                color: foreground,
                letterSpacing: size == AppTagSize.medium ? 0.36 : null,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
