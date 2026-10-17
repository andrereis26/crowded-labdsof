import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../report/confirmation_request.dart';
import '../report/report_flow.dart';
import '../shared/shell.dart';
import '../shared/ticket_card.dart';
import 'event_widgets.dart';

/// P5 · Página do evento: the "opened ticket". On the event day it shows the
/// entry code, organizer alerts, crowd level, queues and weather; on other
/// days the schedule and forecast. Offline, it shows what was saved.
class EventScreen extends StatefulWidget {
  const EventScreen({super.key, required this.eventId, this.confirmationId});

  final String eventId;

  /// Opens this confirmation request (P13) over the page, e.g. from a
  /// notification.
  final String? confirmationId;

  @override
  State<EventScreen> createState() => _EventScreenState();
}

class _EventScreenState extends State<EventScreen> {
  @override
  void initState() {
    super.initState();
    _askToConfirm(widget.confirmationId);
  }

  @override
  void didUpdateWidget(EventScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.confirmationId != oldWidget.confirmationId) {
      _askToConfirm(widget.confirmationId);
    }
  }

  void _askToConfirm(String? id) {
    if (id == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final request = AppStore.read(context).confirmationRequest(id);
      if (request != null) showConfirmationRequest(context, request);
    });
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final event = store.eventById(widget.eventId);
    if (event == null) {
      return AppPage(
        leadingLabel: 'Fechar',
        showBackChevron: false,
        children: [
          AppErrorState(
            message: 'Não encontrámos este evento',
            retryLabel: 'Voltar à carteira',
            onRetry: () => context.go('/carteira'),
          ),
        ],
      );
    }
    final page = _EventPageData(store: store, event: event);
    return isDesktopLayout(context)
        ? _DesktopEventPage(page: page)
        : _MobileEventPage(page: page);
  }
}

/// What both layouts need, worked out once.
class _EventPageData {
  _EventPageData({required this.store, required this.event})
    : ticket = store.ticketFor(event.id),
      now = store.now;

  final AppStore store;
  final Event event;
  final Ticket? ticket;
  final DateTime now;

  bool get offline => store.offline;
  bool get isEventDay => isSameDay(event.start, now);
  int get daysToGo => daysBetween(now, event.start);
  bool get hasEntryCode => ticket?.hasEntryCode ?? false;

  /// Estimates shown to participants: not low confidence (FR-058).
  List<ParticipantEstimate> get visibleEstimates => isEventDay && !offline
      ? event.estimates
            .where((e) => e.confidence != ConfidenceLevel.low)
            .toList()
      : const [];

  int get requestCount =>
      store.yesNoRequestsFor(event.id).length +
      store.pollsFor(event.id).length +
      store.ideasFor(event.id).length;

  void openEntryCode(BuildContext context) =>
      context.push('/evento/${event.id}/codigo');

  void openDirections(BuildContext context) =>
      context.push('/evento/${event.id}/como-chegar');

  /// "Fonte: IPMA · atualizado há 30 min", or "guardado às 22:14" offline.
  String source(String name, DateTime? updatedAt) {
    if (offline) {
      return 'Fonte: $name · guardado às ${formatTime(store.savedAt!)}';
    }
    if (updatedAt == null) return 'Fonte: $name';
    return 'Fonte: $name · atualizado ${formatTimeAgo(updatedAt, now: now)}';
  }

  String alertOrigin(EventAlert alert) => offline
      ? '${alert.publisher} · enviado às ${formatTime(alert.sentAt)}'
      : formatSource(
          alert.publisher,
          alert.sentAt,
          now: now,
          withPrefix: false,
        );

  String get ticketSummary => [
    ticket!.origin.label,
    if (ticket!.gate != null) 'Porta ${ticket!.gate}',
    ?ticket!.seat,
  ].join(' · ');

  Widget header(BuildContext context) {
    if (ticket != null) {
      return TicketCard(
        ticket: ticket!,
        now: now,
        savedAt: offline ? store.savedAt : null,
      );
    }
    final following = store.isFollowing(event.id);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.md,
      children: [
        WalletCard(
          eventName: event.name,
          dateLabel: formatRelativeDay(event.start, now),
          venue: event.place,
          time: formatTime(event.start),
          statusLabel: following ? 'A seguir' : 'Sem bilhete',
          source: event.sourceUpdatedAt == null
              ? 'Fonte: agenda da bilheteira'
              : formatSource(
                  'agenda da bilheteira',
                  event.sourceUpdatedAt!,
                  now: now,
                ),
          gradient: event.art.gradient,
        ),
        AppButton.secondary(
          label: following ? 'Deixar de seguir' : 'Seguir evento',
          icon: following ? Icons.check : Icons.add,
          onPressed: () => store.toggleFollow(event.id),
        ),
      ],
    );
  }

  List<Widget> notices(BuildContext context) => [
    if (offline)
      MutedNotice(
        icon: Icons.wifi_off,
        iconColor: AppColors.warning,
        background: AppColors.segmentTrack,
        title: 'Sem rede',
        message:
            'A mostrar o que guardámos às ${formatTime(store.savedAt!)}. '
            'O bilhete e o código de entrada funcionam na mesma.',
      ),
    if (event.latestDateChange case final change?
        when event.status == EventStatus.postponed)
      AlertBanner(
        kind: AlertBannerKind.officialChange,
        message: postponedMessage(event),
        origin: formatSource('bilheteira oficial', change.detectedAt, now: now),
        onTap: () => context.push('/evento/${event.id}/alteracao/${change.id}'),
      ),
  ];

  List<Widget> alerts() => [
    if (isEventDay)
      for (final alert in event.alerts)
        AlertBanner(
          kind: alert.urgent
              ? AlertBannerKind.urgent
              : AlertBannerKind.organizationAlert,
          message: alert.message,
          origin: alertOrigin(alert),
        ),
  ];

  Widget countdown() => MutedNotice(
    icon: Icons.schedule,
    iconColor: AppColors.primary,
    background: AppColors.primarySoft,
    title: daysToGo == 1 ? 'É amanhã' : 'Faltam $daysToGo dias',
    message:
        'No dia do evento vais ver aqui a lotação, as filas e os alertas da '
        'organização.',
  );

  Widget schedule() => schedulePanel(
    event,
    source: source('bilheteira oficial', event.sourceUpdatedAt),
    large: isEventDay,
  );

  Widget? live() {
    if (!isEventDay) return null;
    if (offline) {
      return const MutedNotice(
        icon: Icons.people_outline,
        title: 'Lotação e filas',
        message: 'Voltam a aparecer quando tiveres rede.',
      );
    }
    final status = event.venueStatus;
    return status == null ? null : LiveVenuePanel(status: status, now: now);
  }

  Widget? weather() {
    final w = event.weather;
    if (w == null) return null;
    return InfoPanel(
      icon: weatherIcon(w.icon),
      iconColor: AppColors.crowdModerate,
      title: isEventDay
          ? w.summary
          : 'Previsão para ${formatDayMonth(event.start)} · ${w.summary}',
      subtitle: w.detail,
      source: source('IPMA', w.updatedAt),
      large: isEventDay,
    );
  }

  List<Widget> links(BuildContext context, {bool withSchedule = false}) => [
    if (withSchedule)
      AppListTile(
        icon: Icons.schedule,
        iconColor: AppColors.ink,
        title: event.doorsOpen != null
            ? 'Portas abrem às ${formatTime(event.doorsOpen!)}'
            : 'Começa às ${formatTime(event.start)}',
        subtitle:
            'Início às ${formatTime(event.start)} · Fonte: bilheteira oficial',
      ),
    if (!withSchedule)
      AppListTile(
        icon: Icons.directions_outlined,
        title: 'Como chegar',
        subtitle: event.place,
        onTap: () => openDirections(context),
      ),
    if (!withSchedule && visibleEstimates.isNotEmpty)
      AppListTile(
        icon: Icons.people_outline,
        title: 'Estimativas de participantes',
        subtitle: visibleEstimates.length == 1
            ? '1 confirmada por quem está no recinto'
            : '${visibleEstimates.length} confirmadas por quem está no recinto',
        onTap: () => context.push('/evento/${event.id}/estimativas'),
      ),
    if (requestCount > 0)
      AppListTile(
        icon: Icons.chat_bubble_outline,
        title: 'Pedidos da organização',
        subtitle: requestCount == 1 ? '1 pedido' : '$requestCount pedidos',
        onTap: () => context.push('/evento/${event.id}/pedidos'),
      ),
    if (ticket != null)
      AppListTile(
        icon: Icons.confirmation_number_outlined,
        title: 'Detalhes do bilhete',
        subtitle: ticketSummary,
        onTap: () => openEntryCode(context),
      ),
    AppListTile(
      icon: Icons.history,
      title: 'Histórico de alterações',
      subtitle: changeSummary(event),
      onTap: () => context.push('/evento/${event.id}/historico'),
    ),
  ];

  Widget? reportButton(BuildContext context, {bool expand = true}) {
    if (!isEventDay || ticket == null) return null;
    return AppButton(
      label: 'Reportar algo no recinto',
      icon: Icons.flag_outlined,
      expand: expand,
      onPressed: offline ? null : () => startReport(context, event),
    );
  }
}

class _MobileEventPage extends StatelessWidget {
  const _MobileEventPage({required this.page});

  final _EventPageData page;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppTopBar(
        leadingLabel: 'Fechar',
        onLeading: () => AppPage.goBack(context),
        actions: [
          if (page.hasEntryCode && !page.isEventDay) ...[
            AppCircleButton(
              icon: Icons.qr_code_2,
              semanticLabel: 'Mostrar código de entrada',
              onPressed: () => page.openEntryCode(context),
            ),
            const SizedBox(width: AppSpacing.sm),
          ],
          AppCircleButton(
            icon: Icons.more_horiz,
            semanticLabel: 'Mais opções',
            onPressed: () => showComingSoon(context),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.screen,
          AppSpacing.md,
          AppSpacing.screen,
          AppSpacing.xxl,
        ),
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            spacing: AppSpacing.md,
            children: [
              page.header(context),
              const SizedBox(height: AppSpacing.xs),
              ...page.notices(context),
              if (page.isEventDay && page.hasEntryCode)
                EntryCodeButton(onTap: () => page.openEntryCode(context)),
              ...page.alerts(),
              if (!page.isEventDay && page.daysToGo > 0) page.countdown(),
              page.schedule(),
              ?page.live(),
              ?page.weather(),
              AppListGroup(children: page.links(context)),
              ?page.reportButton(context),
            ],
          ),
        ],
      ),
    );
  }
}

class _DesktopEventPage extends StatelessWidget {
  const _DesktopEventPage({required this.page});

  final _EventPageData page;

  @override
  Widget build(BuildContext context) {
    final event = page.event;
    final estimates = page.visibleEstimates;
    final weather = page.weather();
    final report = page.reportButton(context, expand: false);

    return Scaffold(
      appBar: const CrowdedWebHeader(currentIndex: Sections.wallet),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 32),
        children: [
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1112),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 40),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  spacing: AppSpacing.xl,
                  children: [
                    Row(
                      children: [
                        AppInlineLink(
                          label: 'Carteira',
                          onPressed: () => context.go('/carteira'),
                        ),
                        const Icon(
                          Icons.chevron_right,
                          size: 18,
                          color: AppColors.inkSecondary,
                        ),
                        Text(
                          event.name,
                          style: AppTextStyles.label.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      spacing: AppSpacing.xl,
                      children: [
                        SizedBox(
                          width: 400,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            spacing: AppSpacing.md,
                            children: [
                              page.header(context),
                              if (page.hasEntryCode)
                                AppButton(
                                  label: 'Mostrar código de entrada',
                                  icon: Icons.qr_code_2,
                                  onPressed: () => page.openEntryCode(context),
                                ),
                              AppListGroup(
                                children: page.links(
                                  context,
                                  withSchedule: true,
                                ),
                              ),
                            ],
                          ),
                        ),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            spacing: AppSpacing.md,
                            children: [
                              ...page.notices(context),
                              ...page.alerts(),
                              if (!page.isEventDay && page.daysToGo > 0)
                                page.countdown(),
                              ?page.live(),
                              if (estimates.isNotEmpty)
                                _EstimatesPanel(
                                  estimates: estimates,
                                  now: page.now,
                                  onSeeAll: () => context.push(
                                    '/evento/${event.id}/estimativas',
                                  ),
                                ),
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                spacing: AppSpacing.md,
                                children: [
                                  if (weather != null) Expanded(child: weather),
                                  Expanded(
                                    child: AppListGroup(
                                      children: [
                                        AppListTile(
                                          icon: Icons.directions_outlined,
                                          title: 'Como chegar',
                                          subtitle: event.place,
                                          onTap: () =>
                                              page.openDirections(context),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    if (report != null) Center(child: report),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Participant estimates with their confidence (desktop event page).
class _EstimatesPanel extends StatelessWidget {
  const _EstimatesPanel({
    required this.estimates,
    required this.now,
    required this.onSeeAll,
  });

  final List<ParticipantEstimate> estimates;
  final DateTime now;
  final VoidCallback onSeeAll;

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      spacing: AppSpacing.md,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                'Estimativas de participantes',
                style: AppTextStyles.bodyStrong,
              ),
            ),
            AppInlineLink(label: 'Ver todas', onPressed: onSeeAll),
          ],
        ),
        Text(
          'Reportes confirmados por quem está no recinto. Podem não refletir '
          'a realidade.',
          style: AppTextStyles.caption,
        ),
        for (final estimate in estimates)
          Row(
            spacing: AppSpacing.md,
            children: [
              Icon(
                estimate.category.icon,
                size: 22,
                color: AppColors.crowdModerate,
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  spacing: 2,
                  children: [
                    Text(
                      estimate.title,
                      style: AppTextStyles.body.copyWith(
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    Text(
                      '${estimate.category.label} · '
                      '${formatTimeAgo(estimate.reportedAt, now: now)}',
                      style: AppTextStyles.caption,
                    ),
                  ],
                ),
              ),
              ConfidenceIndicator(level: estimate.confidence),
              AppTag(
                label:
                    '${estimate.confirmed} confirmaram · '
                    '${estimate.denied} ${estimate.denied == 1 ? 'negou' : 'negaram'}',
              ),
            ],
          ),
      ],
    );
  }
}
