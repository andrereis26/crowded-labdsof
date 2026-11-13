import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P6 · Histórico de alterações of an event.
class ChangeHistoryScreen extends StatelessWidget {
  const ChangeHistoryScreen({super.key, required this.eventId});

  final String eventId;

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final event = store.eventById(eventId);
    final now = store.now;
    if (event == null) {
      return AppPage(
        title: 'Histórico',
        children: [AppErrorState(message: 'Não encontrámos este evento')],
      );
    }
    final dateChange = event.status == EventStatus.postponed
        ? event.latestDateChange
        : null;

    return AppPage(
      title: 'Histórico',
      leadingLabel: 'Evento',
      showReportButton: true,
      maxWidth: 600,
      children: [
        AppPanel(
          padding: const EdgeInsets.all(AppSpacing.md),
          children: [
            Row(
              spacing: AppSpacing.md,
              children: [
                EventThumbnail(gradient: event.art.gradient, size: 48),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    spacing: 2,
                    children: [
                      Text(event.name, style: AppTextStyles.bodyLarge),
                      Text(event.place, style: AppTextStyles.caption),
                    ],
                  ),
                ),
                _StatusTag(status: event.status),
              ],
            ),
          ],
        ),
        if (dateChange != null)
          _CurrentStatus(event: event, change: dateChange),
        const SizedBox(height: AppSpacing.xxs),
        Text('Alterações', style: AppTextStyles.bodyStrong),
        if (event.changes.isEmpty)
          const AppEmptyState(
            icon: Icons.history,
            message: 'Ainda não há alterações',
          )
        else
          AppPanel(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.xl,
              vertical: AppSpacing.xs,
            ),
            spacing: 0,
            children: [
              for (final (index, change) in event.changes.indexed)
                _TimelineEntry(
                  change: change,
                  now: now,
                  latest: index == 0 && change.kind != ChangeKind.published,
                  last: index == event.changes.length - 1,
                  onTap: change.kind != ChangeKind.published
                      ? () => context.push(
                          '/event/${event.id}/change/${change.id}',
                        )
                      : null,
                ),
            ],
          ),
      ],
    );
  }
}

class _StatusTag extends StatelessWidget {
  const _StatusTag({required this.status});

  final EventStatus status;

  @override
  Widget build(BuildContext context) {
    return switch (status) {
      EventStatus.postponed => const AppTag(
        label: 'Adiado',
        tone: AppTagTone.warning,
      ),
      EventStatus.cancelled => const AppTag(
        label: 'Cancelado',
        tone: AppTagTone.danger,
      ),
      EventStatus.scheduled => const AppTag(
        label: 'Confirmado',
        tone: AppTagTone.success,
      ),
    };
  }
}

/// "ESTADO ATUAL · Adiado para SÁB 8 NOV · 21:30".
class _CurrentStatus extends StatelessWidget {
  const _CurrentStatus({required this.event, required this.change});

  final Event event;
  final EventChange change;

  @override
  Widget build(BuildContext context) {
    final previous = change.previousStart;
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.lg,
        vertical: AppSpacing.md,
      ),
      decoration: BoxDecoration(
        color: AppColors.warningSoft,
        borderRadius: const BorderRadius.all(AppRadii.panel),
        border: Border.all(color: AppColors.warningBorder),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: AppSpacing.md,
        children: [
          const Icon(
            Icons.calendar_today_outlined,
            size: 22,
            color: AppColors.warning,
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: 3,
              children: [
                Text(
                  'ESTADO ATUAL',
                  style: AppTextStyles.overline.copyWith(
                    color: AppColors.warning,
                    letterSpacing: 0.44,
                  ),
                ),
                Text(
                  'Adiado para ${formatWeekdayDate(event.start)} · '
                  '${formatTime(event.start)}',
                  style: AppTextStyles.bodyStrong,
                ),
                if (previous != null)
                  Text(
                    'Era ${formatWeekdayDate(previous)} · '
                    '${formatTime(previous)}. ${change.description}',
                    style: AppTextStyles.footnote.copyWith(
                      color: AppColors.ink,
                    ),
                  ),
                Text(
                  'Fonte: organização · publicado às '
                  '${formatTime(change.detectedAt)}',
                  style: AppTextStyles.source.copyWith(
                    color: AppColors.warning,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _TimelineEntry extends StatelessWidget {
  const _TimelineEntry({
    required this.change,
    required this.now,
    required this.latest,
    required this.last,
    this.onTap,
  });

  final EventChange change;
  final DateTime now;

  /// Highlighted dot for the change that set the current state.
  final bool latest;
  final bool last;
  final VoidCallback? onTap;

  String get _when =>
      '${isSameDay(change.detectedAt, now) ? 'HOJE' : formatDayMonth(change.detectedAt)}'
      ' · ${formatTime(change.detectedAt)}';

  String get _description {
    final from = change.previousStart;
    final to = change.newStart;
    if (from == null || to == null) return change.description;
    return '${formatWeekdayDate(from)} · ${formatTime(from)}  →  '
        '${formatWeekdayDate(to)} · ${formatTime(to)}';
  }

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          spacing: AppSpacing.lg,
          children: [
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: Column(
                spacing: 4,
                children: [
                  Container(
                    width: 12,
                    height: 12,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: latest
                          ? AppColors.crowdModerate
                          : AppColors.borderStrong,
                    ),
                  ),
                  if (!last)
                    Expanded(
                      child: Container(width: 2, color: AppColors.border),
                    ),
                ],
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(top: 12, bottom: 18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  spacing: 4,
                  children: [
                    Text(
                      _when,
                      style: AppTextStyles.caption.copyWith(
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    Text(change.title, style: AppTextStyles.bodyStrong),
                    Text(
                      _description,
                      style: AppTextStyles.footnote.copyWith(
                        color: AppColors.ink,
                      ),
                    ),
                    Row(
                      spacing: AppSpacing.xs,
                      children: [
                        const AppTag(
                          label: 'Organização',
                          tone: AppTagTone.info,
                        ),
                        Flexible(
                          child: Text(
                            change.publisher,
                            style: AppTextStyles.source,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            if (onTap != null)
              const Center(
                child: Icon(
                  Icons.chevron_right,
                  size: 20,
                  color: AppColors.inkSecondary,
                ),
              ),
          ],
        ),
      ),
    );
  }
}
