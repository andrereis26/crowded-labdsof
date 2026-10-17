import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../event/event_widgets.dart';
import '../shared/shell.dart';
import '../shared/ticket_card.dart';

/// P1 · Os meus eventos: the ticket wallet and the events the participant
/// follows.
class WalletScreen extends StatelessWidget {
  const WalletScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    return isDesktopLayout(context)
        ? _DesktopWallet(store: store)
        : _MobileWallet(store: store);
  }
}

String _summary(AppStore store, {bool withFollowed = false}) {
  final count = store.tickets.length;
  final changes = store.changedTicketCount;
  final followed = store.followedEvents.length;
  return [
    count == 1 ? '1 bilhete' : '$count bilhetes',
    if (changes > 0) changes == 1 ? '1 alteração' : '$changes alterações',
    if (withFollowed && followed > 0)
      followed == 1 ? '1 evento que segues' : '$followed eventos que segues',
  ].join(' · ');
}

void _openEvent(BuildContext context, Event event) =>
    context.push('/evento/${event.id}');

class _MobileWallet extends StatelessWidget {
  const _MobileWallet({required this.store});

  final AppStore store;

  @override
  Widget build(BuildContext context) {
    final tickets = store.tickets;
    final followed = store.followedEvents;

    return SafeArea(
      bottom: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.screen,
          AppSpacing.sm,
          AppSpacing.screen,
          AppSpacing.xxl,
        ),
        children: [
          SectionHeader(
            title: 'Carteira',
            subtitle: tickets.isEmpty ? null : _summary(store),
            actions: [
              AppCircleButton(
                icon: Icons.notifications_none,
                semanticLabel: 'Notificações',
                badgeCount: store.unreadCount,
                onPressed: () => context.push('/notificacoes'),
              ),
              AppCircleButton(
                icon: Icons.add,
                semanticLabel: 'Adicionar bilhete',
                dark: true,
                onPressed: () => context.push('/importar'),
              ),
            ],
          ),
          if (tickets.isEmpty)
            AppEmptyState(
              icon: Icons.credit_card,
              message: 'Ainda sem bilhetes',
              actionLabel: 'Importar',
              onAction: () => context.push('/importar'),
            )
          else
            TicketStack(
              tickets: tickets,
              now: store.now,
              onOpen: (t) => _openEvent(context, t.event),
            ),
          if (followed.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.xxl),
            Text(
              'A seguir',
              style: AppTextStyles.headline.copyWith(fontSize: 20),
            ),
            const SizedBox(height: AppSpacing.md),
            for (final event in followed) ...[
              FollowedEventRow(
                event: event,
                onTap: () => _openEvent(context, event),
              ),
              const SizedBox(height: AppSpacing.sm),
            ],
          ],
        ],
      ),
    );
  }
}

/// Wallet cards stacked like the iPhone wallet: the soonest ticket in front,
/// later ones behind it showing only their header.
class TicketStack extends StatelessWidget {
  const TicketStack({
    super.key,
    required this.tickets,
    required this.now,
    required this.onOpen,
  });

  /// Soonest first.
  final List<Ticket> tickets;
  final DateTime now;
  final ValueChanged<Ticket> onOpen;

  @override
  Widget build(BuildContext context) {
    final back = tickets.reversed.toList();
    const step = WalletCard.collapsedHeight;
    return SizedBox(
      height: (back.length - 1) * step + WalletCard.height,
      child: Stack(
        children: [
          for (final (index, ticket) in back.indexed)
            Positioned(
              top: index * step,
              left: 0,
              right: 0,
              child: TicketCard(
                ticket: ticket,
                now: now,
                onTap: () => onOpen(ticket),
              ),
            ),
        ],
      ),
    );
  }
}

/// White row for an event followed without a ticket.
class FollowedEventRow extends StatelessWidget {
  const FollowedEventRow({
    super.key,
    required this.event,
    required this.onTap,
    this.showThumbnail = false,
  });

  final Event event;
  final VoidCallback onTap;
  final bool showThumbnail;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surface,
      borderRadius: const BorderRadius.all(AppRadii.button),
      child: InkWell(
        onTap: onTap,
        borderRadius: const BorderRadius.all(AppRadii.button),
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: showThumbnail ? 10 : AppSpacing.lg,
            vertical: showThumbnail ? 10 : AppSpacing.md,
          ),
          child: Row(
            spacing: AppSpacing.md,
            children: [
              if (showThumbnail)
                EventThumbnail(gradient: event.art.gradient, size: 40),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  spacing: 2,
                  children: [
                    Text(event.name, style: AppTextStyles.bodyStrong),
                    Text(
                      '${formatDayMonth(event.start)} · ${event.venue}',
                      style: AppTextStyles.footnote,
                    ),
                  ],
                ),
              ),
              const AppTag(label: 'Sem bilhete'),
              if (showThumbnail)
                const Icon(
                  Icons.chevron_right,
                  size: 20,
                  color: AppColors.inkSecondary,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DesktopWallet extends StatelessWidget {
  const _DesktopWallet({required this.store});

  final AppStore store;

  @override
  Widget build(BuildContext context) {
    final now = store.now;
    final tickets = store.tickets;
    final today = tickets.where((t) => isSameDay(t.event.start, now)).toList();
    final upcoming = tickets.where((t) => !today.contains(t)).toList();
    final changed = tickets
        .where((t) => t.event.status == EventStatus.postponed)
        .toList();
    final followed = store.followedEvents;

    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 40),
      children: [
        Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 1112),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 40),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                spacing: AppSpacing.xxl,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          spacing: 4,
                          children: [
                            Text(
                              'Carteira',
                              style: AppTextStyles.largeTitle.copyWith(
                                fontSize: 28,
                              ),
                            ),
                            Text(
                              _summary(store, withFollowed: true),
                              style: AppTextStyles.footnote,
                            ),
                          ],
                        ),
                      ),
                      AppButton.secondary(
                        label: 'Seguir um evento',
                        icon: Icons.search,
                        expand: false,
                        onPressed: () => context.go('/eventos'),
                      ),
                      const SizedBox(width: AppSpacing.md),
                      AppButton(
                        label: 'Importar bilhete',
                        icon: Icons.add,
                        expand: false,
                        onPressed: () => context.push('/importar'),
                      ),
                    ],
                  ),
                  for (final ticket in changed)
                    AlertBanner(
                      kind: AlertBannerKind.officialChange,
                      message: postponedMessage(ticket.event),
                      origin: formatSource(
                        'bilheteira oficial',
                        ticket.event.latestDateChange!.detectedAt,
                        now: now,
                      ),
                      onTap: () => context.push(
                        '/evento/${ticket.event.id}/alteracao/'
                        '${ticket.event.latestDateChange!.id}',
                      ),
                    ),
                  if (tickets.isEmpty)
                    AppEmptyState(
                      icon: Icons.credit_card,
                      message: 'Ainda sem bilhetes',
                      actionLabel: 'Importar',
                      onAction: () => context.push('/importar'),
                    ),
                  for (final ticket in today) ...[
                    Text(
                      'Hoje',
                      style: AppTextStyles.headline.copyWith(fontSize: 20),
                    ),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      spacing: AppSpacing.xl,
                      children: [
                        SizedBox(
                          width: 480,
                          child: TicketCard(
                            ticket: ticket,
                            now: now,
                            onTap: () => _openEvent(context, ticket.event),
                          ),
                        ),
                        Expanded(
                          child: _TodayPanel(ticket: ticket, now: now),
                        ),
                      ],
                    ),
                  ],
                  if (upcoming.isNotEmpty) ...[
                    Text(
                      'Próximos bilhetes',
                      style: AppTextStyles.headline.copyWith(fontSize: 20),
                    ),
                    _Grid(
                      columns: 3,
                      children: [
                        for (final ticket in upcoming)
                          TicketCard(
                            ticket: ticket,
                            now: now,
                            onTap: () => _openEvent(context, ticket.event),
                          ),
                      ],
                    ),
                  ],
                  if (followed.isNotEmpty) ...[
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            'A seguir',
                            style: AppTextStyles.headline.copyWith(
                              fontSize: 20,
                            ),
                          ),
                        ),
                        AppInlineLink(
                          label: 'Ver eventos perto de ti',
                          onPressed: () => context.go('/eventos'),
                        ),
                      ],
                    ),
                    _Grid(
                      columns: 2,
                      children: [
                        for (final event in followed)
                          FollowedEventRow(
                            event: event,
                            showThumbnail: true,
                            onTap: () => _openEvent(context, event),
                          ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Live summary next to today's ticket on desktop.
class _TodayPanel extends StatelessWidget {
  const _TodayPanel({required this.ticket, required this.now});

  final Ticket ticket;
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    final event = ticket.event;
    final status = event.venueStatus;
    final alert = event.alerts.firstOrNull;
    return AppPanel(
      spacing: AppSpacing.md,
      children: [
        Row(
          children: [
            Expanded(
              child: Text('Agora no recinto', style: AppTextStyles.headline),
            ),
            if (status != null) const LiveTag(),
          ],
        ),
        if (status != null) CrowdLevelIndicator(level: status.crowdLevel),
        if (alert != null)
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.md,
              vertical: 10,
            ),
            decoration: const BoxDecoration(
              color: AppColors.warningSoft,
              borderRadius: BorderRadius.all(AppRadii.field),
            ),
            child: Row(
              spacing: AppSpacing.sm,
              children: [
                const Icon(
                  Icons.warning_amber_rounded,
                  size: 18,
                  color: AppColors.warning,
                ),
                Expanded(
                  child: Text(
                    alert.message,
                    style: AppTextStyles.label.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
        if (status != null) ...[
          Row(
            spacing: AppSpacing.sm,
            children: [
              for (final queue in status.queues)
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: const BoxDecoration(
                      color: AppColors.background,
                      borderRadius: BorderRadius.all(AppRadii.field),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      spacing: 2,
                      children: [
                        Text(queue.place, style: AppTextStyles.caption),
                        Text(
                          '~${queue.minutes} min',
                          style: AppTextStyles.bodyStrong,
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
          Text(
            'Fonte: organização e contador de entradas · '
            '${formatTimeAgo(status.queuesUpdatedAt, now: now)}',
            style: AppTextStyles.source,
          ),
        ],
        Row(
          spacing: AppSpacing.md,
          children: [
            if (ticket.hasEntryCode)
              Expanded(
                child: AppButton.secondary(
                  label: 'Mostrar código',
                  icon: Icons.qr_code_2,
                  onPressed: () => showComingSoon(context),
                ),
              ),
            Expanded(
              child: AppButton(
                label: 'Ver evento',
                onPressed: () => _openEvent(context, event),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// Simple grid whose items keep their natural height.
class _Grid extends StatelessWidget {
  const _Grid({required this.columns, required this.children});

  final int columns;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    const gap = AppSpacing.xl;
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = (constraints.maxWidth - gap * (columns - 1)) / columns;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [
            for (final child in children) SizedBox(width: width, child: child),
          ],
        );
      },
    );
  }
}
