import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

/// White rounded group of [AppListTile]s separated by dividers
/// ("Como chegar / Detalhes do bilhete / Histórico de alterações" in P5).
class AppListGroup extends StatelessWidget {
  const AppListGroup({
    super.key,
    required this.children,
    this.title,
    this.color = AppColors.surface,
  });

  final List<Widget> children;

  /// Small grey heading inside the group ("O que acontece a seguir").
  final String? title;

  /// Grey groups sit on white pages (P12).
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color,
      borderRadius: const BorderRadius.all(AppRadii.panel),
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xxs),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (title != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.xl,
                  AppSpacing.xs,
                  AppSpacing.xl,
                  0,
                ),
                child: Text(
                  title!,
                  style: AppTextStyles.label.copyWith(
                    color: AppColors.inkSecondary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
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
    this.leading,
    this.subtitle,
    this.subtitleColor,
    this.trailing,
    this.onTap,
    this.showChevron = true,
    this.strong = false,
  });

  final String title;
  final IconData? icon;
  final Color iconColor;

  /// Replaces [icon], e.g. an [AppIconBadge].
  final Widget? leading;
  final String? subtitle;

  /// Defaults to the secondary grey; warning text uses [AppColors.warning].
  final Color? subtitleColor;
  final Widget? trailing;
  final VoidCallback? onTap;
  final bool showChevron;

  /// Semibold title, for rows that carry the main information (P12, P18).
  final bool strong;

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
              if (leading != null) ...[
                leading!,
                const SizedBox(width: AppSpacing.md),
              ] else if (icon != null) ...[
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
                        fontWeight: strong ? FontWeight.w600 : FontWeight.w500,
                      ),
                    ),
                    if (subtitle != null)
                      Text(
                        subtitle!,
                        style: AppTextStyles.caption.copyWith(
                          color: subtitleColor,
                        ),
                      ),
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
