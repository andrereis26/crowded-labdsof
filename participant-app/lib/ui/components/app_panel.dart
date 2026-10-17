import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

/// White rounded block that groups content on the grey background
/// (form panel in P3, "Fila · detalhes" in P11).
class AppPanel extends StatelessWidget {
  const AppPanel({
    super.key,
    required this.children,
    this.title,
    this.padding = const EdgeInsets.all(AppSpacing.xl),
    this.spacing = AppSpacing.lg,
    this.color = AppColors.surface,
    this.borderColor,
  });

  final String? title;
  final List<Widget> children;
  final EdgeInsetsGeometry padding;
  final double spacing;

  /// Grey panels sit on white pages and dialogs (P12, P15).
  final Color color;

  /// Outlined panels on white cards (P15, P16) and urgent requests (P17).
  final Color? borderColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(
        color: color,
        borderRadius: const BorderRadius.all(AppRadii.panel),
        border: borderColor == null ? null : Border.all(color: borderColor!),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        spacing: spacing,
        children: [
          if (title != null) Text(title!, style: AppTextStyles.bodyStrong),
          ...children,
        ],
      ),
    );
  }
}

/// Heading block of a multi-step flow: "PASSO 2 DE 3", title and subtitle.
class AppStepHeading extends StatelessWidget {
  const AppStepHeading({
    super.key,
    required this.title,
    this.step,
    this.totalSteps,
    this.subtitle,
  }) : assert((step == null) == (totalSteps == null));

  final String title;
  final int? step;
  final int? totalSteps;
  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      spacing: AppSpacing.md,
      children: [
        if (step != null)
          Text(
            'PASSO $step DE $totalSteps',
            style: AppTextStyles.overline.copyWith(color: AppColors.primary),
          ),
        Semantics(header: true, child: Text(title, style: AppTextStyles.title)),
        if (subtitle != null)
          Text(subtitle!, style: AppTextStyles.bodySecondary),
      ],
    );
  }
}
