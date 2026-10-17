import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P9 · Eventos perto de mim / hoje.
class EventsScreen extends StatefulWidget {
  const EventsScreen({super.key});

  @override
  State<EventsScreen> createState() => _EventsScreenState();
}

class _EventsScreenState extends State<EventsScreen> {
  DiscoverFilter _filter = DiscoverFilter.today;

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final now = store.now;
    final events = store.discover(_filter);
    final desktop = isDesktopLayout(context);

    final content = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.md,
      children: [
        SectionHeader(
          title: 'Eventos',
          actions: [
            if (!desktop)
              AppCircleButton(
                icon: Icons.notifications_none,
                semanticLabel: 'Notificações',
                badgeCount: store.unreadCount,
                onPressed: () => context.push('/notificacoes'),
              ),
            AppCircleButton(
              icon: Icons.search,
              semanticLabel: 'Procurar eventos',
              onPressed: () => showComingSoon(context),
            ),
          ],
        ),
        AppSegmentedControl<DiscoverFilter>(
          selected: _filter,
          onChanged: (f) => setState(() => _filter = f),
          segments: [
            for (final f in DiscoverFilter.values)
              AppSegment(value: f, label: f.label),
          ],
        ),
        Row(
          spacing: AppSpacing.xs,
          children: [
            const Icon(Icons.my_location, size: 16, color: AppColors.primary),
            Expanded(
              child: Text(
                'Perto de Porto · localização aproximada',
                style: AppTextStyles.label,
              ),
            ),
            AppInlineLink(
              label: 'Alterar',
              onPressed: () => showComingSoon(context),
            ),
          ],
        ),
        if (events.isEmpty)
          const AppEmptyState(
            icon: Icons.event_busy_outlined,
            message: 'Sem eventos para mostrar',
          ),
        for (final event in events)
          _EventRow(
            event: event,
            now: now,
            hasTicket: store.ticketFor(event.id) != null,
            following: store.isFollowing(event.id),
            onTap: () => context.push('/evento/${event.id}'),
            onFollow: () => store.toggleFollow(event.id),
          ),
        Text(
          'Fonte: agenda da bilheteira · atualizado há 10 min',
          style: AppTextStyles.source,
        ),
      ],
    );

    return SafeArea(
      bottom: false,
      child: ListView(
        padding: EdgeInsets.fromLTRB(
          AppSpacing.screen,
          desktop ? 32 : AppSpacing.sm,
          AppSpacing.screen,
          AppSpacing.xxl,
        ),
        children: [
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 720),
              child: content,
            ),
          ),
        ],
      ),
    );
  }
}

class _EventRow extends StatelessWidget {
  const _EventRow({
    required this.event,
    required this.now,
    required this.hasTicket,
    required this.following,
    required this.onTap,
    required this.onFollow,
  });

  final Event event;
  final DateTime now;
  final bool hasTicket;
  final bool following;
  final VoidCallback onTap;
  final VoidCallback onFollow;

  String get _when {
    final time = event.end != null && isSameDay(event.start, event.end!)
        ? '${formatTime(event.start)}–${formatTime(event.end!)}'
        : formatTime(event.start);
    return '${formatRelativeDaySentence(event.start, now)} · $time · '
        '${event.venue}';
  }

  @override
  Widget build(BuildContext context) {
    final Widget trailing;
    if (hasTicket) {
      trailing = const AppTag(label: 'Tens bilhete', tone: AppTagTone.success);
    } else if (following) {
      trailing = Semantics(
        button: true,
        label: 'A seguir. Deixar de seguir',
        excludeSemantics: true,
        child: GestureDetector(
          onTap: onFollow,
          child: const AppTag(
            label: 'A seguir',
            tone: AppTagTone.info,
            icon: Icons.check,
          ),
        ),
      );
    } else {
      trailing = AppButton.secondary(
        label: 'Seguir',
        icon: Icons.add,
        size: AppButtonSize.small,
        expand: false,
        onPressed: onFollow,
      );
    }

    return Material(
      color: AppColors.surface,
      borderRadius: const BorderRadius.all(AppRadii.panel),
      child: InkWell(
        onTap: onTap,
        borderRadius: const BorderRadius.all(AppRadii.panel),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(10, 10, 12, 10),
          child: Row(
            spacing: AppSpacing.md,
            children: [
              EventThumbnail(
                gradient: event.art.gradient,
                size: 68,
                radius: 12,
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  spacing: 3,
                  children: [
                    Text(event.name, style: AppTextStyles.bodyStrong),
                    Text(
                      _when,
                      style: AppTextStyles.caption.copyWith(height: 1.35),
                    ),
                    if (event.distanceKm != null)
                      Row(
                        spacing: 4,
                        children: [
                          const Icon(
                            Icons.place_outlined,
                            size: 13,
                            color: AppColors.inkSecondary,
                          ),
                          Text(
                            formatDistance(event.distanceKm!),
                            style: AppTextStyles.caption.copyWith(
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ],
                      ),
                  ],
                ),
              ),
              trailing,
            ],
          ),
        ),
      ),
    );
  }
}
