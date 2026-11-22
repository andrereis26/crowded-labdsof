import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../event/event_widgets.dart';
import '../shared/provenance_label.dart';
import '../shared/shell.dart';
import '../shared/ticket_card.dart';

/// P1 · Os meus eventos: the participant's events, tickets and public events
/// joined with an access code, those that have not ended first.
class WalletScreen extends StatefulWidget {
  const WalletScreen({super.key});

  @override
  State<WalletScreen> createState() => _WalletScreenState();
}

class _WalletScreenState extends State<WalletScreen> {
  bool _requested = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_requested) return;
    _requested = true;
    final store = AppStore.of(context);
    // Not while the screen is being built: loading notifies its listeners.
    Future.microtask(store.refreshConnections);
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    return isDesktopLayout(context)
        ? _DesktopWallet(store: store)
        : _MobileWallet(store: store);
  }
}

void _openEvent(BuildContext context, Event event) =>
    context.push('/event/${event.id}');

/// What the list says while it loads, when it fails and while it is empty;
/// null when the events are shown.
Widget? _listState(AppStore store, List<ConnectedEvent> connections) {
  final failed = store.connectionsStatus == ConnectionsStatus.failed;
  if (failed) {
    return AppErrorState(
      message: 'Não foi possível carregar os teus eventos',
      onRetry: store.refreshConnections,
    );
  }
  if (connections.isNotEmpty) return null;
  if (store.connectionsStatus == ConnectionsStatus.loading) {
    return const AppLoadingState(message: 'A carregar os teus eventos');
  }
  return const NoEventsState();
}

class _MobileWallet extends StatelessWidget {
  const _MobileWallet({required this.store});

  final AppStore store;

  @override
  Widget build(BuildContext context) {
    final connections = store.connections;
    final state = _listState(store, connections);

    return SafeArea(
      bottom: false,
      child: RefreshIndicator(
        onRefresh: store.refreshConnections,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.screen,
            AppSpacing.sm,
            AppSpacing.screen,
            AppSpacing.xxl,
          ),
          children: [
            SectionHeader(
              title: 'Os meus eventos',
              actions: [
                AppCircleButton(
                  icon: Icons.notifications_none,
                  semanticLabel: 'Notificações',
                  badgeCount: store.unreadCount,
                  onPressed: () => context.push('/notifications'),
                ),
                if (connections.isNotEmpty)
                  AppCircleButton(
                    icon: Icons.add,
                    semanticLabel: 'Adicionar evento',
                    dark: true,
                    onPressed: () => context.push('/import'),
                  ),
              ],
            ),
            ?state,
            if (state is AppErrorState && connections.isNotEmpty)
              const SizedBox(height: AppSpacing.md),
            if (connections.isNotEmpty && state is! AppLoadingState)
              for (final connection in connections) ...[
                ConnectionCard(
                  event: connection.event,
                  ticket: connection.ticket,
                  now: store.now,
                  onTap: () => _openEvent(context, connection.event),
                ),
                const SizedBox(height: AppSpacing.md),
              ],
          ],
        ),
      ),
    );
  }
}

/// P1 empty state: no tickets and no public events yet.
class NoEventsState extends StatelessWidget {
  const NoEventsState({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xxl),
      child: Column(
        spacing: AppSpacing.md,
        children: [
          const AppResultHeader(
            icon: Icons.confirmation_number_outlined,
            title: 'Ainda não tens eventos',
            message:
                'Importa o bilhete que compraste ou insere o código de acesso '
                'de um evento público.',
            tone: AppTagTone.info,
          ),
          const SizedBox(height: AppSpacing.sm),
          AppButton(
            label: 'Importar bilhete',
            onPressed: () => context.push('/import'),
          ),
          AppButton.secondary(
            label: 'Inserir código de acesso',
            onPressed: () => context.push('/access-code'),
          ),
        ],
      ),
    );
  }
}

/// White row for a public event joined with an access code.
class PublicEventRow extends StatelessWidget {
  const PublicEventRow({
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
                    for (final note in eventNotes(event))
                      Row(
                        spacing: 4,
                        children: [
                          Icon(
                            note.icon,
                            size: 14,
                            color: AppColors.inkSecondary,
                          ),
                          Expanded(
                            child: Text(
                              note.text,
                              style: AppTextStyles.footnote.copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                  ],
                ),
              ),
              const AppTag(label: 'Público'),
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
    final connections = store.connections;
    final tickets = [
      for (final c in connections)
        if (c.ticket != null) c.ticket!,
    ];
    final today = tickets.where((t) => isSameDay(t.event.start, now)).toList();
    final upcoming = tickets.where((t) => !today.contains(t)).toList();
    // Core's list has only the latest change, which may not be the date's.
    final changed = tickets
        .where(
          (t) =>
              t.event.status == EventStatus.postponed &&
              t.event.latestDateChange != null,
        )
        .toList();
    final publicEvents = [
      for (final c in connections)
        if (c.ticket == null) c.event,
    ];
    final state = _listState(store, connections);

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
                        child: Text(
                          'Os meus eventos',
                          style: AppTextStyles.largeTitle.copyWith(
                            fontSize: 28,
                          ),
                        ),
                      ),
                      AppButton.secondary(
                        label: 'Inserir código de acesso',
                        icon: Icons.qr_code_scanner,
                        expand: false,
                        onPressed: () => context.push('/access-code'),
                      ),
                      const SizedBox(width: AppSpacing.md),
                      AppButton(
                        label: 'Importar bilhete',
                        icon: Icons.add,
                        expand: false,
                        onPressed: () => context.push('/import'),
                      ),
                    ],
                  ),
                  for (final ticket in changed)
                    AlertBanner(
                      kind: AlertBannerKind.officialChange,
                      message: postponedMessage(ticket.event),
                      origin: formatProvenance(
                        ticket.event.latestDateChange!.source,
                        now: now,
                      ),
                      onTap: () => context.push(
                        '/event/${ticket.event.id}/change/'
                        '${ticket.event.latestDateChange!.id}',
                      ),
                    ),
                  if (state is NoEventsState)
                    const AppPanel(
                      children: [
                        Center(
                          child: SizedBox(width: 360, child: NoEventsState()),
                        ),
                      ],
                    )
                  else
                    ?state,
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
                  if (publicEvents.isNotEmpty) ...[
                    Text(
                      'Eventos públicos',
                      style: AppTextStyles.headline.copyWith(fontSize: 20),
                    ),
                    _Grid(
                      columns: 2,
                      children: [
                        for (final event in publicEvents)
                          PublicEventRow(
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
        if (status?.crowdLevel case final level?)
          CrowdLevelIndicator(level: level),
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
              // A summary: the event page lists every point.
              for (final queue in status.queues.take(3))
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
          // One label per source, so an estimate never reads as the
          // organization's.
          for (final source
              in status.queuesShareSource
                  ? [?status.queuesSource]
                  : status.queueSources)
            ProvenanceLabel(provenance: source, now: now),
        ],
        Row(
          spacing: AppSpacing.md,
          children: [
            if (ticket.hasEntryCode)
              Expanded(
                child: AppButton.secondary(
                  label: 'Mostrar código',
                  icon: Icons.qr_code_2,
                  onPressed: () => context.push('/event/${event.id}/code'),
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
