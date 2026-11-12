import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

/// Confidence of a participant estimate.
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

/// Pill with signal bars and text: never colour alone.
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

/// Four crowd levels.
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

/// "Lotação Moderada" with a four-segment meter: the level name and the
/// number of filled segments, so colour is never the only cue.
/// Screen readers also hear the level number.
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
          // One paragraph, so it wraps instead of overflowing.
          Text.rich(
            TextSpan(
              style: AppTextStyles.footnote,
              children: [
                TextSpan(text: '$title '),
                TextSpan(text: level.label, style: AppTextStyles.bodyStrong),
              ],
            ),
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

/// "9 confirmaram · 1 negou" with a green and red bar, so the split is
/// readable as text and as a shape (P14).
class ConfirmationBar extends StatelessWidget {
  const ConfirmationBar({
    super.key,
    required this.confirmed,
    required this.denied,
  });

  final int confirmed;
  final int denied;

  @override
  Widget build(BuildContext context) {
    final total = confirmed + denied;
    final share = total == 0 ? 0.0 : confirmed / total;
    final label =
        '$confirmed ${confirmed == 1 ? 'confirmou' : 'confirmaram'} · '
        '$denied ${denied == 1 ? 'negou' : 'negaram'}';
    return Semantics(
      label: label,
      excludeSemantics: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        spacing: 5,
        children: [
          Text(label, style: AppTextStyles.caption.copyWith(height: 1.2)),
          ClipRRect(
            borderRadius: const BorderRadius.all(Radius.circular(4)),
            child: SizedBox(
              height: 6,
              child: LinearProgressIndicator(
                value: share,
                color: AppColors.crowdLow,
                backgroundColor: AppColors.deniedSoft,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
