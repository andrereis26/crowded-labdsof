import 'package:flutter/material.dart';

import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';
import 'app_tag.dart';

/// "agora", "há 5 min", "há 2 h", "há 3 dias".
String formatTimeAgo(DateTime time, {DateTime? now}) {
  final elapsed = (now ?? DateTime.now()).difference(time);
  if (elapsed.inMinutes < 1) return 'agora';
  if (elapsed.inHours < 1) return 'há ${elapsed.inMinutes} min';
  if (elapsed.inDays < 1) return 'há ${elapsed.inHours} h';
  final days = elapsed.inDays;
  return days == 1 ? 'há 1 dia' : 'há $days dias';
}

/// Like [formatTimeAgo], but "ontem" for the day before (requests in P17).
String formatSentAgo(DateTime sent, {DateTime? now}) {
  final reference = now ?? DateTime.now();
  final days = DateTime(
    reference.year,
    reference.month,
    reference.day,
  ).difference(DateTime(sent.year, sent.month, sent.day)).inDays;
  return days == 1 ? 'ontem' : formatTimeAgo(sent, now: reference);
}

/// "Fonte: organização · há 5 min", or "Organização Margens · agora"
/// when [withPrefix] is false (alerts name the publisher directly). With
/// [updated] it reads "Fonte: organização · atualizado há 5 min".
String formatSource(
  String source,
  DateTime updatedAt, {
  DateTime? now,
  bool withPrefix = true,
  bool updated = false,
}) {
  final who = withPrefix ? 'Fonte: $source' : source;
  final ago = formatTimeAgo(updatedAt, now: now);
  return '$who · ${updated ? 'atualizado ' : ''}$ago';
}

/// Source and last-update time shown next to every piece of information.
/// A tag says when it is out of date or AI-generated and unconfirmed, with
/// words and an icon, never by colour alone. The app does not decide that: the
/// caller passes what Core decided.
class SourceLabel extends StatelessWidget {
  const SourceLabel({
    super.key,
    required this.source,
    required this.updatedAt,
    this.now,
    this.stale = false,
    this.generatedByAi = false,
    this.updated = false,
    this.color,
  });

  final String source;
  final DateTime updatedAt;

  /// Reference time; defaults to the current time. Useful in tests.
  final DateTime? now;

  /// Out of date: shows the "Desatualizado" tag.
  final bool stale;

  /// AI-generated and not confirmed: shows the "Gerado por IA" tag.
  final bool generatedByAi;

  /// Words the time as "atualizado há 5 min".
  final bool updated;

  /// Text colour; defaults to the secondary grey. Use white on photos.
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final text = Text(
      formatSource(source, updatedAt, now: now, updated: updated),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: AppTextStyles.source.copyWith(color: color),
    );
    if (!stale && !generatedByAi) return text;

    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.xs,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        text,
        if (generatedByAi)
          const AppTag(
            label: 'Gerado por IA',
            tone: AppTagTone.info,
            icon: Icons.auto_awesome,
          ),
        if (stale)
          const AppTag(
            label: 'Desatualizado',
            tone: AppTagTone.warning,
            icon: Icons.schedule,
          ),
      ],
    );
  }
}
