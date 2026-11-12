import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';
import 'app_tag.dart';

const _boxRadius = BorderRadius.all(AppRadii.button);

/// Empty list with an icon, a message and an optional action
/// ("Ainda sem bilhetes" + "Importar").
class AppEmptyState extends StatelessWidget {
  const AppEmptyState({
    super.key,
    required this.icon,
    required this.message,
    this.actionLabel,
    this.actionIcon = Icons.add,
    this.onAction,
  });

  final IconData icon;
  final String message;
  final String? actionLabel;
  final IconData actionIcon;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: const BoxDecoration(
        color: AppColors.background,
        borderRadius: _boxRadius,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        spacing: AppSpacing.sm,
        children: [
          Icon(icon, size: 24, color: AppColors.ink),
          Text(
            message,
            textAlign: TextAlign.center,
            style: AppTextStyles.label.copyWith(fontWeight: FontWeight.w600),
          ),
          if (actionLabel != null)
            Material(
              color: AppColors.ink,
              borderRadius: const BorderRadius.all(AppRadii.sm),
              child: InkWell(
                onTap: onAction,
                borderRadius: const BorderRadius.all(AppRadii.sm),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: AppSpacing.xs,
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    spacing: 4,
                    children: [
                      Icon(actionIcon, size: 14, color: Colors.white),
                      Text(
                        actionLabel!,
                        style: AppTextStyles.tag.copyWith(
                          fontSize: 12,
                          color: Colors.white,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// Skeleton placeholder while content loads.
class AppLoadingState extends StatelessWidget {
  const AppLoadingState({super.key, this.message = 'A carregar'});

  final String message;

  @override
  Widget build(BuildContext context) {
    Widget bar(double width) => Container(
      width: width,
      height: 10,
      decoration: const BoxDecoration(
        color: AppColors.skeleton,
        borderRadius: BorderRadius.all(Radius.circular(5)),
      ),
    );

    return Semantics(
      label: message,
      liveRegion: true,
      excludeSemantics: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: const BoxDecoration(
          color: AppColors.background,
          borderRadius: _boxRadius,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          spacing: AppSpacing.sm,
          children: [
            bar(120),
            bar(150),
            bar(90),
            Text(
              message,
              style: AppTextStyles.caption.copyWith(
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Load failure with a retry action.
class AppErrorState extends StatelessWidget {
  const AppErrorState({
    super.key,
    this.message = 'Não foi possível carregar',
    this.retryLabel = 'Tentar de novo',
    this.onRetry,
  });

  final String message;
  final String retryLabel;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: const BoxDecoration(
        color: AppColors.dangerSoft,
        borderRadius: _boxRadius,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        spacing: AppSpacing.sm,
        children: [
          const Icon(Icons.cancel_outlined, size: 24, color: AppColors.danger),
          Text(
            message,
            textAlign: TextAlign.center,
            style: AppTextStyles.tag.copyWith(
              fontSize: 12,
              color: AppColors.danger,
            ),
          ),
          if (onRetry != null)
            InkWell(
              onTap: onRetry,
              borderRadius: const BorderRadius.all(AppRadii.sm),
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.xxs),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  spacing: 4,
                  children: [
                    const Icon(
                      Icons.refresh,
                      size: 14,
                      color: AppColors.primary,
                    ),
                    Text(
                      retryLabel,
                      style: AppTextStyles.tag.copyWith(
                        fontSize: 12,
                        color: AppColors.primary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// Thin strip that says the app is offline or running in degraded mode and
/// how old the data is.
class AppStatusStrip extends StatelessWidget {
  /// "Sem rede · a mostrar dados de há 12 min".
  const AppStatusStrip.offline({super.key, required this.message})
    : _icon = Icons.wifi_off,
      _background = AppColors.ink,
      _foreground = Colors.white;

  /// "Bilheteira indisponível · última sincronização há 18 min".
  const AppStatusStrip.degraded({super.key, required this.message})
    : _icon = Icons.warning_amber_rounded,
      _background = AppColors.warningSoft,
      _foreground = AppColors.warning;

  final String message;
  final IconData _icon;
  final Color _background;
  final Color _foreground;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      liveRegion: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: 10,
        ),
        decoration: BoxDecoration(
          color: _background,
          borderRadius: const BorderRadius.all(AppRadii.field),
        ),
        child: Row(
          spacing: AppSpacing.sm,
          children: [
            Icon(_icon, size: 16, color: _foreground),
            Expanded(
              child: Text(
                message,
                style: AppTextStyles.tag.copyWith(
                  fontSize: 12,
                  color: _foreground,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Outcome of an action: a tinted round icon, a title and a message
/// ("Reporte enviado", "Não estás perto do recinto" in P12, P15).
class AppResultHeader extends StatelessWidget {
  const AppResultHeader({
    super.key,
    required this.icon,
    required this.title,
    this.message,
    this.tone = AppTagTone.success,
    this.large = false,
  });

  final IconData icon;
  final String title;
  final String? message;

  /// Colours of the circle, as in [AppTag].
  final AppTagTone tone;

  /// Bigger icon and title (recognition in P15).
  final bool large;

  @override
  Widget build(BuildContext context) {
    final (background, foreground) = AppTag.colorsFor(tone);
    return Column(
      mainAxisSize: MainAxisSize.min,
      spacing: AppSpacing.md,
      children: [
        Container(
          padding: EdgeInsets.all(large ? AppSpacing.xxl : 18),
          decoration: BoxDecoration(color: background, shape: BoxShape.circle),
          child: Icon(icon, size: large ? 40 : 36, color: foreground),
        ),
        Semantics(
          header: true,
          child: Text(
            title,
            textAlign: TextAlign.center,
            style: AppTextStyles.sheetTitle.copyWith(
              fontSize: large ? 28 : null,
            ),
          ),
        ),
        if (message != null)
          Text(
            message!,
            textAlign: TextAlign.center,
            style: AppTextStyles.bodySecondary.copyWith(fontSize: 15),
          ),
      ],
    );
  }
}
