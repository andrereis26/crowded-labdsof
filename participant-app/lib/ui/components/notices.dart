import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

enum AlertBannerKind {
  organizationAlert(
    'ALERTA DA ORGANIZAÇÃO',
    Icons.warning_amber_rounded,
    AppColors.warningSoft,
    AppColors.warningBorder,
    AppColors.warning,
  ),
  officialChange(
    'ALTERAÇÃO OFICIAL',
    Icons.calendar_today_outlined,
    AppColors.warningSoft,
    AppColors.warningBorder,
    AppColors.warning,
  ),

  /// Red, only for people's safety.
  urgent(
    'URGENTE',
    Icons.campaign_outlined,
    AppColors.dangerSoft,
    AppColors.dangerBorder,
    AppColors.danger,
  );

  const AlertBannerKind(
    this.label,
    this.icon,
    this.background,
    this.border,
    this.foreground,
  );

  final String label;
  final IconData icon;
  final Color background;
  final Color border;
  final Color foreground;
}

/// Alert or official change banner: icon, label, message and origin (#41).
class AlertBanner extends StatelessWidget {
  const AlertBanner({
    super.key,
    required this.kind,
    required this.message,
    required this.origin,
    this.onTap,
  });

  final AlertBannerKind kind;
  final String message;

  /// Who published it and when, e.g. built with `formatSource`.
  final String origin;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    const radius = BorderRadius.all(AppRadii.panel);
    return Semantics(
      liveRegion: kind == AlertBannerKind.urgent,
      container: true,
      child: Material(
        color: kind.background,
        shape: RoundedRectangleBorder(
          borderRadius: radius,
          side: BorderSide(color: kind.border),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.lg,
              vertical: AppSpacing.md,
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(kind.icon, size: 22, color: kind.foreground),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    spacing: 3,
                    children: [
                      Text(
                        kind.label,
                        style: AppTextStyles.overline.copyWith(
                          color: kind.foreground,
                          letterSpacing: 0.44,
                        ),
                      ),
                      Text(message, style: AppTextStyles.bodyStrong),
                      Text(
                        origin,
                        style: AppTextStyles.source.copyWith(
                          color: kind.foreground,
                        ),
                      ),
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

/// Notice that always accompanies participant estimates (FR-059).
class EstimateNotice extends StatelessWidget {
  const EstimateNotice({
    super.key,
    this.title = 'Estimativas de participantes',
    this.message =
        'Reportes de quem está no recinto, confirmados por outros '
        'participantes. Podem não refletir a realidade.',
  });

  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.lg,
        vertical: AppSpacing.md,
      ),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: const BorderRadius.all(AppRadii.panel),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.info_outline,
            size: 20,
            color: AppColors.inkSecondary,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              spacing: 2,
              children: [
                Text(title, style: AppTextStyles.subhead),
                Text(message, style: AppTextStyles.caption),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
