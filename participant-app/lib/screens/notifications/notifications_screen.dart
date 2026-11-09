import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P8 · Centro de notificações.
class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  NotificationFilter _filter = NotificationFilter.all;

  void _open(AppStore store, AppNotification n) {
    store.markRead(n);
    if (n.route != null) {
      context.push(n.route!);
    } else if (n.changeId != null) {
      context.push('/evento/${n.eventId}/alteracao/${n.changeId}');
    } else {
      context.push('/evento/${n.eventId}');
    }
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final now = store.now;
    final items = store.notifications.where((n) => n.matches(_filter));

    // HOJE / ESTA SEMANA / ANTERIORES.
    final groups = <String, List<AppNotification>>{};
    for (final n in items) {
      final days = daysBetween(n.receivedAt, now);
      final group = days == 0
          ? 'HOJE'
          : days < 7
          ? 'ESTA SEMANA'
          : 'ANTERIORES';
      groups.putIfAbsent(group, () => []).add(n);
    }

    return AppPage(
      leadingLabel: 'Os meus eventos',
      maxWidth: 640,
      showReportButton: true,
      children: [
        SectionHeader(
          title: 'Notificações',
          actions: [
            AppCircleButton(
              icon: Icons.settings_outlined,
              semanticLabel: 'Preferências de notificações',
              onPressed: () => showComingSoon(context),
            ),
          ],
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            spacing: AppSpacing.sm,
            children: [
              for (final filter in NotificationFilter.values)
                AppChip(
                  label: filter.label,
                  selected: filter == _filter,
                  onTap: () => setState(() => _filter = filter),
                ),
            ],
          ),
        ),
        if (groups.isEmpty)
          const AppEmptyState(
            icon: Icons.notifications_none,
            message: 'Sem notificações',
          ),
        for (final MapEntry(key: title, value: list) in groups.entries) ...[
          Padding(
            padding: const EdgeInsets.only(top: AppSpacing.xs),
            child: Text(
              title,
              style: AppTextStyles.overline.copyWith(
                color: AppColors.inkSecondary,
              ),
            ),
          ),
          Material(
            color: AppColors.surface,
            borderRadius: const BorderRadius.all(AppRadii.panel),
            clipBehavior: Clip.antiAlias,
            child: Column(
              children: [
                for (final (index, n) in list.indexed) ...[
                  if (index > 0)
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: AppSpacing.lg),
                      child: Divider(),
                    ),
                  _NotificationTile(
                    notification: n,
                    now: now,
                    connected:
                        store.isConnected(n.eventId) ||
                        store.eventById(n.eventId) == null,
                    onTap: () => _open(store, n),
                    onAnswer: (answer) => store.answerRequest(n, answer),
                  ),
                ],
              ],
            ),
          ),
        ],
      ],
    );
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({
    required this.notification,
    required this.now,
    required this.onTap,
    this.connected = true,
    required this.onAnswer,
  });

  final AppNotification notification;
  final DateTime now;
  final VoidCallback onTap;

  /// False for events the participant left: shown muted, and opening it
  /// explains that the page is no longer available (SR-020).
  final bool connected;
  final ValueChanged<bool> onAnswer;

  (IconData, Color, Color) get _icon => switch (notification.kind) {
    NotificationKind.alert => (
      Icons.campaign_outlined,
      AppColors.warningSoft,
      AppColors.warning,
    ),
    NotificationKind.request => (
      Icons.chat_bubble_outline,
      AppColors.primarySoft,
      AppColors.primary,
    ),
    NotificationKind.confirmation => (
      Icons.fact_check_outlined,
      AppColors.primarySoft,
      AppColors.primary,
    ),
    NotificationKind.recognition => (
      Icons.workspace_premium_outlined,
      AppColors.successSoft,
      AppColors.success,
    ),
    NotificationKind.officialChange => (
      Icons.calendar_today_outlined,
      AppColors.dangerSoft,
      AppColors.danger,
    ),
    NotificationKind.weather => (
      Icons.warning_amber_rounded,
      AppColors.orangeSoft,
      AppColors.orange,
    ),
  };

  /// Next to the event name only when it changes priority or trust; the
  /// icon already shows the type.
  String? get _tag => notification.urgent
      ? 'Urgente'
      : switch (notification.kind) {
          NotificationKind.officialChange => 'Organização',
          NotificationKind.weather => 'IPMA',
          _ => null,
        };

  String get _when {
    final at = notification.receivedAt;
    if (isSameDay(at, now)) return formatTimeAgo(at, now: now);
    return formatWeekdayDate(at).split(' ').first.toLowerCase();
  }

  @override
  Widget build(BuildContext context) {
    final n = notification;
    final (icon, background, foreground) = _icon;
    final isOpenRequest =
        connected && n.kind == NotificationKind.request && n.answer == null;

    return Semantics(
      button: true,
      label: n.read ? null : 'Não lida',
      child: Opacity(
        opacity: connected ? 1 : 0.6,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: AppSpacing.md,
              children: [
                Container(
                  padding: const EdgeInsets.all(AppSpacing.sm),
                  decoration: BoxDecoration(
                    color: background,
                    borderRadius: const BorderRadius.all(AppRadii.field),
                  ),
                  child: Icon(
                    icon,
                    size: 18,
                    color: foreground,
                    semanticLabel: n.typeLabel,
                  ),
                ),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    spacing: 3,
                    children: [
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        spacing: AppSpacing.xs,
                        children: [
                          Expanded(
                            child: Text(
                              [n.eventName, ?_tag].join(' · '),
                              style: AppTextStyles.caption.copyWith(
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                          Text(_when, style: AppTextStyles.caption),
                        ],
                      ),
                      Text(n.title, style: AppTextStyles.bodyStrong),
                      if (!connected)
                        Text(
                          'Já não estás ligado a este evento.',
                          style: AppTextStyles.footnote,
                        )
                      else if (n.body != null)
                        Text(
                          n.body!,
                          style: AppTextStyles.footnote.copyWith(
                            color: AppColors.ink,
                            height: 1.4,
                          ),
                        ),
                      if (isOpenRequest)
                        Padding(
                          padding: const EdgeInsets.only(top: AppSpacing.xs),
                          child: Row(
                            spacing: AppSpacing.sm,
                            children: [
                              AppButton.secondary(
                                label: 'Não',
                                size: AppButtonSize.small,
                                expand: false,
                                onPressed: () => onAnswer(false),
                              ),
                              AppButton(
                                label: 'Sim',
                                size: AppButtonSize.small,
                                expand: false,
                                onPressed: () => onAnswer(true),
                              ),
                            ],
                          ),
                        ),
                      if (n.answer != null)
                        Text(
                          'Respondeste: ${n.answer! ? 'Sim' : 'Não'}',
                          style: AppTextStyles.caption.copyWith(
                            color: AppColors.success,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                    ],
                  ),
                ),
                SizedBox(
                  width: 8,
                  child: n.read
                      ? null
                      : Container(
                          margin: const EdgeInsets.only(top: 4),
                          height: 8,
                          decoration: const BoxDecoration(
                            color: AppColors.primary,
                            shape: BoxShape.circle,
                          ),
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
