import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

enum AppButtonVariant {
  /// Dark filled button for the main action ("Confirmar e continuar").
  primary,

  /// White outlined button ("Introduzir dados manualmente", "Não").
  secondary,

  /// Blue text-only button ("Procurar outro evento", "Não consigo ver daqui").
  link,
}

enum AppButtonSize {
  regular,

  /// Taller button with larger text, for Yes/No answers (P13).
  large,
}

/// Button used across the participant app.
///
/// Fills the available width by default, as in the mockups. Pass
/// `expand: false` to size it to its content. A null [onPressed] disables it.
class AppButton extends StatelessWidget {
  const AppButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.variant = AppButtonVariant.primary,
    this.size = AppButtonSize.regular,
    this.expand = true,
  });

  const AppButton.secondary({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.size = AppButtonSize.regular,
    this.expand = true,
  }) : variant = AppButtonVariant.secondary;

  const AppButton.link({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.expand = true,
  }) : variant = AppButtonVariant.link,
       size = AppButtonSize.regular;

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final AppButtonVariant variant;
  final AppButtonSize size;
  final bool expand;

  @override
  Widget build(BuildContext context) {
    final isLarge = size == AppButtonSize.large;
    final (background, foreground, border) = switch (variant) {
      AppButtonVariant.primary => (AppColors.ink, Colors.white, null),
      AppButtonVariant.secondary => (
        AppColors.surface,
        AppColors.ink,
        Border.all(color: AppColors.border),
      ),
      AppButtonVariant.link => (Colors.transparent, AppColors.primary, null),
    };
    final textStyle =
        (isLarge ? AppTextStyles.buttonLarge : AppTextStyles.button).copyWith(
          color: foreground,
        );
    const radius = BorderRadius.all(AppRadii.button);

    final content = Row(
      mainAxisSize: expand ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (icon != null) ...[
          Icon(icon, size: isLarge ? 22 : 18, color: foreground),
          const SizedBox(width: AppSpacing.sm),
        ],
        Flexible(
          child: Text(
            label,
            style: textStyle,
            textAlign: TextAlign.center,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );

    return Semantics(
      button: true,
      enabled: onPressed != null,
      child: Opacity(
        opacity: onPressed == null ? 0.4 : 1,
        child: Material(
          color: background,
          shape: RoundedRectangleBorder(borderRadius: radius),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onPressed,
            borderRadius: radius,
            child: Container(
              decoration: BoxDecoration(border: border, borderRadius: radius),
              padding: EdgeInsets.symmetric(
                horizontal: AppSpacing.xxl,
                vertical: isLarge ? 18 : 14,
              ),
              child: content,
            ),
          ),
        ),
      ),
    );
  }
}

/// Small inline blue link with an optional leading icon
/// ("Como tratamos os teus dados", "Como guardar um email em .eml?").
class AppInlineLink extends StatelessWidget {
  const AppInlineLink({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      link: true,
      child: InkWell(
        onTap: onPressed,
        borderRadius: const BorderRadius.all(AppRadii.sm),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: AppSpacing.xxs),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[
                Icon(icon, size: 16, color: AppColors.primary),
                const SizedBox(width: AppSpacing.xs),
              ],
              Flexible(
                child: Text(
                  label,
                  style: AppTextStyles.label.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
