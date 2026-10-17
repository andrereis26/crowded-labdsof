import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

/// Confidence of a participant estimate (FR-058).
enum ConfidenceLevel {
  low('Confiança baixa', 1, AppColors.background, AppColors.inkSecondary),
  medium('Confiança média', 2, AppColors.warningSoft, AppColors.warning),
  high('Confiança alta', 3, AppColors.successSoft, AppColors.success);

  const ConfidenceLevel(
    this.label,
    this.bars,
    this.background,
    this.foreground,
  );

  final String label;
  final int bars;
  final Color background;
  final Color foreground;
}

/// Pill with signal bars and text: never colour alone (#77).
///
/// Low-confidence estimates are not shown to participants; screens should
/// hide the estimate instead of showing [ConfidenceLevel.low].
class ConfidenceIndicator extends StatelessWidget {
  const ConfidenceIndicator({super.key, required this.level});

  final ConfidenceLevel level;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: level.label,
      excludeSemantics: true,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          color: level.background,
          borderRadius: const BorderRadius.all(AppRadii.pill),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              spacing: 2,
              children: [
                for (final (index, height) in const [6.0, 9.0, 12.0].indexed)
                  Container(
                    width: 3,
                    height: height,
                    decoration: BoxDecoration(
                      color: index < level.bars
                          ? level.foreground
                          : AppColors.inactive,
                      borderRadius: const BorderRadius.all(Radius.circular(1)),
                    ),
                  ),
              ],
            ),
            const SizedBox(width: AppSpacing.xs),
            Text(
              level.label,
              style: AppTextStyles.tag.copyWith(color: level.foreground),
            ),
          ],
        ),
      ),
    );
  }
}

/// Four crowd levels (FR-018).
enum CrowdLevel {
  low('Baixa', AppColors.crowdLow),
  moderate('Moderada', AppColors.crowdModerate),
  high('Elevada', AppColors.crowdHigh),
  full('Lotado', AppColors.crowdFull);

  const CrowdLevel(this.label, this.color);

  final String label;
  final Color color;

  /// 1 to 4.
  int get step => index + 1;
}

/// "Lotação Moderada · 2 de 4" with a four-segment meter: text, level
/// number and meter, so colour is never the only cue (#51).
class CrowdLevelIndicator extends StatelessWidget {
  const CrowdLevelIndicator({
    super.key,
    required this.level,
    this.title = 'Lotação',
  });

  final CrowdLevel level;
  final String title;

  @override
  Widget build(BuildContext context) {
    final total = CrowdLevel.values.length;
    return Semantics(
      label: '$title ${level.label}, nível ${level.step} de $total',
      excludeSemantics: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            spacing: AppSpacing.xs,
            children: [
              Text(title, style: AppTextStyles.footnote),
              Text(level.label, style: AppTextStyles.bodyStrong),
              Text('· ${level.step} de $total', style: AppTextStyles.footnote),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Row(
            spacing: 4,
            children: [
              for (var i = 0; i < total; i++)
                Expanded(
                  child: Container(
                    height: 6,
                    decoration: BoxDecoration(
                      color: i < level.step ? level.color : AppColors.border,
                      borderRadius: const BorderRadius.all(Radius.circular(3)),
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
