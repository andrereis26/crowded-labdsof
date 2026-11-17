import 'package:flutter/material.dart';

import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';

/// "• Ao vivo" pill.
class LiveTag extends StatelessWidget {
  const LiveTag({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: const BoxDecoration(
        color: AppColors.primarySoft,
        borderRadius: BorderRadius.all(AppRadii.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        spacing: 5,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: const BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
            ),
          ),
          Text(
            'Ao vivo',
            style: AppTextStyles.tag.copyWith(color: AppColors.primary),
          ),
        ],
      ),
    );
  }
}

/// White panel with an icon, a title, a subtitle and a source line
/// (schedule and weather on the event page).
class InfoPanel extends StatelessWidget {
  const InfoPanel({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.source,
    this.iconColor = AppColors.inkSecondary,
    this.large = true,
  });

  final IconData icon;
  final Color iconColor;
  final String title;
  final String? subtitle;
  final String? source;

  /// 17 px title on the event day, 15 px otherwise.
  final bool large;

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.xl,
        vertical: AppSpacing.lg,
      ),
      spacing: 10,
      children: [
        Row(
          spacing: AppSpacing.md,
          children: [
            Icon(icon, size: 28, color: iconColor),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                spacing: 2,
                children: [
                  Text(
                    title,
                    style: large
                        ? AppTextStyles.headline
                        : AppTextStyles.bodyStrong,
                  ),
                  if (subtitle != null)
                    Text(subtitle!, style: AppTextStyles.footnote),
                ],
              ),
            ),
          ],
        ),
        if (source != null) Text(source!, style: AppTextStyles.source),
      ],
    );
  }
}

/// "Portas abrem às 20:00 · Início às 22:00 · fim às 02:00".
InfoPanel schedulePanel(
  Event event, {
  required String source,
  bool large = true,
}) {
  final doors = event.doorsOpen;
  final startLine = [
    'Início às ${formatTime(event.start)}',
    if (event.end != null) 'fim às ${formatTime(event.end!)}',
  ].join(' · ');
  return InfoPanel(
    icon: Icons.schedule,
    title: doors != null
        ? 'Portas abrem às ${formatTime(doors)}'
        : 'Começa às ${formatTime(event.start)}',
    subtitle: doors != null ? startLine : null,
    source: source,
    large: large,
  );
}

IconData weatherIcon(WeatherIcon icon) => switch (icon) {
  WeatherIcon.sun => Icons.wb_sunny_outlined,
  WeatherIcon.cloud => Icons.cloud_outlined,
  WeatherIcon.rain => Icons.water_drop_outlined,
};

/// Crowd level and queue times on the event day ("Agora no recinto").
class LiveVenuePanel extends StatelessWidget {
  const LiveVenuePanel({
    super.key,
    required this.status,
    required this.now,
    this.footer,
  });

  final VenueStatus status;
  final DateTime now;

  /// Shown under a divider: the link to the participant estimates (P14).
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.xl,
        vertical: AppSpacing.lg,
      ),
      spacing: 10,
      children: [
        Row(
          children: [
            Expanded(
              child: Text('Agora no recinto', style: AppTextStyles.headline),
            ),
            const LiveTag(),
          ],
        ),
        Row(
          spacing: AppSpacing.md,
          children: [
            const Icon(Icons.people_outline, size: 26, color: AppColors.ink),
            Expanded(child: CrowdLevelIndicator(level: status.crowdLevel)),
          ],
        ),
        if (status.queues.isNotEmpty) ...[
          const Divider(),
          Text('Filas', style: AppTextStyles.footnote),
          for (final queue in status.queues) QueueRow(queue: queue),
        ],
        Text(
          'Fonte: organização · atualizado '
          '${formatTimeAgo(status.updatedAt, now: now)}',
          style: AppTextStyles.source,
        ),
        if (footer != null) ...[const Divider(), footer!],
      ],
    );
  }
}

/// "Porta C   a aumentar  ~25 min".
class QueueRow extends StatelessWidget {
  const QueueRow({super.key, required this.queue});

  final QueueTime queue;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label:
          '${queue.place}, cerca de ${queue.minutes} minutos'
          '${queue.increasing ? ', a aumentar' : ''}',
      excludeSemantics: true,
      child: Row(
        children: [
          Expanded(
            child: Text(
              queue.place,
              style: AppTextStyles.body.copyWith(fontWeight: FontWeight.w500),
            ),
          ),
          if (queue.increasing) ...[
            Text(
              'a aumentar',
              style: AppTextStyles.tag.copyWith(
                color: AppColors.warning,
                fontWeight: FontWeight.w500,
              ),
            ),
            const SizedBox(width: AppSpacing.xs),
          ],
          Text('~${queue.minutes} min', style: AppTextStyles.bodyStrong),
        ],
      ),
    );
  }
}

/// Big call to action for the entry code on the event day.
class EntryCodeButton extends StatelessWidget {
  const EntryCodeButton({super.key, required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    const radius = BorderRadius.all(AppRadii.panel);
    return Semantics(
      button: true,
      child: Material(
        color: AppColors.surface,
        shape: const RoundedRectangleBorder(
          borderRadius: radius,
          side: BorderSide(color: AppColors.primary, width: 1.5),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.xl,
              vertical: AppSpacing.lg,
            ),
            child: Row(
              spacing: AppSpacing.md,
              children: [
                const Icon(Icons.qr_code_2, size: 26, color: AppColors.primary),
                Expanded(
                  child: Text(
                    'Mostrar código de entrada',
                    style: AppTextStyles.bodyLarge.copyWith(
                      color: AppColors.primary,
                    ),
                  ),
                ),
                const Icon(
                  Icons.chevron_right,
                  size: 20,
                  color: AppColors.primary,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Grey card with an icon, title and text ("Sem rede", "Lotação e filas").
class MutedNotice extends StatelessWidget {
  const MutedNotice({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.iconColor = AppColors.ink,
    this.background = AppColors.surface,
  });

  final IconData icon;
  final Color iconColor;
  final Color background;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.xl,
        vertical: AppSpacing.lg,
      ),
      decoration: BoxDecoration(
        color: background,
        borderRadius: const BorderRadius.all(AppRadii.panel),
      ),
      child: Row(
        spacing: AppSpacing.md,
        children: [
          Icon(icon, size: 26, color: iconColor),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: 2,
              children: [
                Text(title, style: AppTextStyles.headline),
                Text(message, style: AppTextStyles.footnote),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Number of changes after publication, for "Histórico de alterações", or
/// null when there are none.
String? changeSummary(Event event) {
  final count = event.changes
      .where((c) => c.kind != ChangeKind.published)
      .length;
  return switch (count) {
    0 => null,
    1 => '1 alteração',
    _ => '$count alterações',
  };
}

/// "A Tempestade foi adiada para SÁB 8 NOV · 21:30".
String postponedMessage(Event event) =>
    '${event.name} foi adiada para ${formatWeekdayDate(event.start)} · '
    '${formatTime(event.start)}';
