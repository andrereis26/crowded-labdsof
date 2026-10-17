import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

enum WalletCardStatusTone {
  /// "Confirmado".
  confirmed(AppColors.walletStatusSoft, AppColors.walletStatus),

  /// "Adiado · 8 Nov", "Hora alterada".
  changed(AppColors.warningSoft, AppColors.warning),

  /// "Cancelado".
  cancelled(AppColors.dangerSoft, AppColors.danger);

  const WalletCardStatusTone(this.background, this.foreground);

  final Color background;
  final Color foreground;
}

/// Ticket card in the wallet (P1), styled like an iPhone wallet pass.
///
/// The event photo fills the card under a shade that keeps the white text
/// readable. When stacked, only the header (about 62 px) of the cards behind
/// shows. The status always has text, never colour alone.
class WalletCard extends StatelessWidget {
  const WalletCard({
    super.key,
    required this.eventName,
    required this.dateLabel,
    required this.venue,
    required this.statusLabel,
    required this.source,
    this.statusTone = WalletCardStatusTone.confirmed,
    this.time,
    this.gate,
    this.seat,
    this.image,
    this.gradient,
    this.onTap,
  });

  final String eventName;

  /// "HOJE", "SÁB 8 NOV".
  final String dateLabel;
  final String venue;
  final String? time;
  final String? gate;
  final String? seat;
  final String statusLabel;
  final WalletCardStatusTone statusTone;

  /// Source and update time, e.g. built with `formatSource`.
  final String source;
  final ImageProvider? image;

  /// Shown when there is no [image]; defaults to a dark blue gradient.
  final Gradient? gradient;
  final VoidCallback? onTap;

  static const height = 224.0;
  static const collapsedHeight = 62.0;
  static const _radius = BorderRadius.all(AppRadii.card);

  @override
  Widget build(BuildContext context) {
    final fields = [
      if (time != null) ('HORA', time!),
      if (gate != null) ('PORTA', gate!),
      if (seat != null) ('LUGAR', seat!),
    ];

    return Semantics(
      button: onTap != null,
      label: [
        eventName,
        dateLabel,
        venue,
        for (final (label, value) in fields) '$label $value',
        statusLabel,
      ].join(', '),
      excludeSemantics: true,
      child: Container(
        height: height,
        decoration: const BoxDecoration(
          borderRadius: _radius,
          boxShadow: [
            BoxShadow(
              color: Color(0x2E000000),
              offset: Offset(0, -2),
              blurRadius: 10,
            ),
          ],
        ),
        child: Material(
          borderRadius: _radius,
          clipBehavior: Clip.antiAlias,
          child: Ink(
            decoration: BoxDecoration(
              gradient: image == null
                  ? gradient ?? AppColors.walletFallbackPhoto
                  : null,
              image: image == null
                  ? null
                  : DecorationImage(image: image!, fit: BoxFit.cover),
            ),
            child: InkWell(
              onTap: onTap,
              child: DecoratedBox(
                decoration: const BoxDecoration(
                  gradient: AppColors.walletShade,
                ),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(18, 18, 18, 16),
                  child: _content(fields),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _content(List<(String, String)> fields) {
    final white = AppTextStyles.body.copyWith(color: Colors.white);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                eventName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: white.copyWith(
                  fontSize: 19,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.19,
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Text(
              dateLabel,
              style: white.copyWith(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.78,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          venue,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: white.copyWith(
            fontSize: 13,
            color: Colors.white.withValues(alpha: 0.85),
          ),
        ),
        const Spacer(),
        Row(
          spacing: 28,
          children: [
            for (final (label, value) in fields)
              Flexible(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  spacing: 2,
                  children: [
                    Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: white.copyWith(
                        fontSize: 10,
                        fontWeight: FontWeight.w500,
                        letterSpacing: 0.8,
                        color: Colors.white.withValues(alpha: 0.75),
                      ),
                    ),
                    Text(
                      value,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: white.copyWith(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ),
        const SizedBox(height: 10),
        Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: statusTone.background,
                borderRadius: const BorderRadius.all(AppRadii.pill),
              ),
              child: Text(
                statusLabel,
                style: AppTextStyles.tag.copyWith(
                  fontSize: 12,
                  color: statusTone.foreground,
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Text(
                source,
                textAlign: TextAlign.end,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: AppTextStyles.source.copyWith(
                  color: Colors.white.withValues(alpha: 0.8),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}
