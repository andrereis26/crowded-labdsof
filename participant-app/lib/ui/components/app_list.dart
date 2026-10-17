import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

/// White rounded group of [AppListTile]s separated by dividers
/// ("Como chegar / Detalhes do bilhete / Histórico de alterações" in P5).
class AppListGroup extends StatelessWidget {
  const AppListGroup({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surface,
      borderRadius: const BorderRadius.all(AppRadii.panel),
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xxs),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (final (index, child) in children.indexed) ...[
              if (index > 0)
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: AppSpacing.xl),
                  child: Divider(),
                ),
              child,
            ],
          ],
        ),
      ),
    );
  }
}

/// Row with a leading icon, title, optional subtitle and a chevron.
class AppListTile extends StatelessWidget {
  const AppListTile({
    super.key,
    required this.title,
    this.icon,
    this.iconColor = AppColors.primary,
    this.subtitle,
    this.trailing,
    this.onTap,
    this.showChevron = true,
  });

  final String title;
  final IconData? icon;
  final Color iconColor;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;
  final bool showChevron;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: onTap != null,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.xl,
            vertical: AppSpacing.md,
          ),
          child: Row(
            children: [
              if (icon != null) ...[
                Icon(icon, size: 22, color: iconColor),
                const SizedBox(width: AppSpacing.md),
              ],
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  spacing: 2,
                  children: [
                    Text(
                      title,
                      style: AppTextStyles.body.copyWith(
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    if (subtitle != null)
                      Text(subtitle!, style: AppTextStyles.caption),
                  ],
                ),
              ),
              if (trailing != null) ...[
                const SizedBox(width: AppSpacing.sm),
                trailing!,
              ],
              if (showChevron && onTap != null) ...[
                const SizedBox(width: AppSpacing.sm),
                const Icon(
                  Icons.chevron_right,
                  size: 20,
                  color: AppColors.inkSecondary,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
