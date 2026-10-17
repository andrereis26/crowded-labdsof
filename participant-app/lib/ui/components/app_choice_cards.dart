import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

BoxDecoration _cardDecoration({required bool selected, bool outlined = false}) {
  return BoxDecoration(
    color: selected ? AppColors.primarySoft : AppColors.surface,
    borderRadius: const BorderRadius.all(AppRadii.panel),
    border: selected
        ? Border.all(color: AppColors.primary, width: 2)
        : outlined
        ? Border.all(color: AppColors.border)
        : null,
  );
}

/// Selectable tile with an icon and a label, laid out in a two-column grid
/// (report categories in P11).
class AppCategoryTile extends StatelessWidget {
  const AppCategoryTile({
    super.key,
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: const BorderRadius.all(AppRadii.panel),
          child: Container(
            // Keeps the box the same size when the 2 px border appears.
            padding: EdgeInsets.all(selected ? 13 : 14),
            decoration: _cardDecoration(selected: selected, outlined: true),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  padding: const EdgeInsets.all(AppSpacing.sm),
                  decoration: BoxDecoration(
                    color: selected ? AppColors.surface : AppColors.background,
                    borderRadius: const BorderRadius.all(AppRadii.field),
                  ),
                  child: Icon(
                    icon,
                    size: 20,
                    color: selected ? AppColors.primary : AppColors.ink,
                  ),
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(label, style: AppTextStyles.subhead),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Radio circle drawn as in the mockups (22 px).
class AppRadio extends StatelessWidget {
  const AppRadio({super.key, required this.selected});

  final bool selected;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 150),
      width: 22,
      height: 22,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: selected ? AppColors.primary : AppColors.surface,
        border: selected
            ? null
            : Border.all(color: AppColors.borderStrong, width: 1.5),
      ),
      alignment: Alignment.center,
      child: selected
          ? Container(
              width: 8,
              height: 8,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                color: AppColors.surface,
              ),
            )
          : null,
    );
  }
}

/// Card with a radio, optional image, title, subtitle and extra content
/// below (event match in P4, attendance poll options in P16).
class AppRadioCard extends StatelessWidget {
  const AppRadioCard({
    super.key,
    required this.title,
    required this.selected,
    required this.onTap,
    this.subtitle,
    this.leading,
    this.footer,
  });

  final String title;
  final String? subtitle;
  final bool selected;
  final VoidCallback? onTap;

  /// Usually a 52 px rounded event image.
  final Widget? leading;

  /// Extra content under the subtitle, e.g. an [AppTag].
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      inMutuallyExclusiveGroup: true,
      checked: selected,
      button: true,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: const BorderRadius.all(AppRadii.panel),
          child: Container(
            padding: EdgeInsets.all(selected ? 12 : 14),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: const BorderRadius.all(AppRadii.panel),
              border: selected
                  ? Border.all(color: AppColors.primary, width: 2)
                  : null,
            ),
            child: Row(
              children: [
                AppRadio(selected: selected),
                const SizedBox(width: AppSpacing.md),
                if (leading != null) ...[
                  leading!,
                  const SizedBox(width: AppSpacing.md),
                ],
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    spacing: 4,
                    children: [
                      Text(title, style: AppTextStyles.bodyStrong),
                      if (subtitle != null)
                        Text(
                          subtitle!,
                          style: AppTextStyles.caption.copyWith(height: 1.35),
                        ),
                      ?footer,
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Bordered answer with a radio, tinted when selected (poll options in P16).
class AppOptionTile extends StatelessWidget {
  const AppOptionTile({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    const radius = BorderRadius.all(AppRadii.button);
    return Semantics(
      inMutuallyExclusiveGroup: true,
      checked: selected,
      button: true,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: Container(
            // Keeps the box the same size when the 2 px border appears.
            padding: EdgeInsets.symmetric(
              horizontal: selected ? 13 : 14,
              vertical: selected ? 12 : 13,
            ),
            decoration: BoxDecoration(
              color: selected ? AppColors.primarySoft : AppColors.surface,
              borderRadius: radius,
              border: selected
                  ? Border.all(color: AppColors.primary, width: 2)
                  : Border.all(color: AppColors.border),
            ),
            child: Row(
              spacing: AppSpacing.md,
              children: [
                AppRadio(selected: selected),
                Expanded(
                  child: Text(
                    label,
                    style: AppTextStyles.body.copyWith(
                      fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Tappable row card with a tinted icon, title, subtitle and a chevron
/// (import options in P2).
class AppNavigationCard extends StatelessWidget {
  const AppNavigationCard({
    super.key,
    required this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.footer,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final VoidCallback? onTap;

  /// Shown under a divider inside the card, e.g. an [AppInlineLink].
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    final row = Semantics(
      button: true,
      child: InkWell(
        onTap: onTap,
        borderRadius: const BorderRadius.all(AppRadii.button),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: const BoxDecoration(
                color: AppColors.primarySoft,
                borderRadius: BorderRadius.all(AppRadii.button),
              ),
              child: Icon(icon, size: 24, color: AppColors.primary),
            ),
            const SizedBox(width: AppSpacing.lg),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                spacing: 3,
                children: [
                  Text(title, style: AppTextStyles.bodyLarge),
                  if (subtitle != null)
                    Text(subtitle!, style: AppTextStyles.footnote),
                ],
              ),
            ),
            const SizedBox(width: AppSpacing.sm),
            const Icon(
              Icons.chevron_right,
              size: 20,
              color: AppColors.inkSecondary,
            ),
          ],
        ),
      ),
    );

    return Material(
      color: AppColors.surface,
      borderRadius: const BorderRadius.all(AppRadii.panel),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            row,
            if (footer != null) ...[
              const SizedBox(height: AppSpacing.md),
              const Divider(),
              const SizedBox(height: AppSpacing.md),
              Align(alignment: Alignment.centerLeft, child: footer),
            ],
          ],
        ),
      ),
    );
  }
}
