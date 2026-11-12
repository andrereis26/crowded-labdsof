import 'package:flutter/material.dart';

import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';
import 'app_tag.dart';

/// Information older than this shows "Desatualizado".
const staleAfter = Duration(minutes: 20);

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
/// when [withPrefix] is false (alerts name the publisher directly).
String formatSource(
  String source,
  DateTime updatedAt, {
  DateTime? now,
  bool withPrefix = true,
}) {
  final who = withPrefix ? 'Fonte: $source' : source;
  return '$who · ${formatTimeAgo(updatedAt, now: now)}';
}

bool isStale(DateTime updatedAt, {DateTime? now}) =>
    (now ?? DateTime.now()).difference(updatedAt) >= staleAfter;

/// Source and last-update time shown next to every piece of information,
/// with a "Desatualizado" tag after 20 minutes.
class SourceLabel extends StatelessWidget {
  const SourceLabel({
    super.key,
    required this.source,
    required this.updatedAt,
    this.now,
    this.showStale = true,
    this.color,
  });

  final String source;
  final DateTime updatedAt;

  /// Reference time; defaults to the current time. Useful in tests.
  final DateTime? now;

  /// Set to false where the data is not expected to change often
  /// (e.g. ticket details on the wallet card).
  final bool showStale;

  /// Text colour; defaults to the secondary grey. Use white on photos.
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final text = Text(
      formatSource(source, updatedAt, now: now),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: AppTextStyles.source.copyWith(color: color),
    );
    if (!showStale || !isStale(updatedAt, now: now)) return text;

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Flexible(child: text),
        const SizedBox(width: AppSpacing.sm),
        const AppTag(label: 'Desatualizado', tone: AppTagTone.warning),
      ],
    );
  }
}
