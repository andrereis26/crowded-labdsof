import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../report/confirmation_request.dart';
import '../report/report_flow.dart';
import '../shared/provenance_label.dart';
import '../shared/shell.dart';
import '../shared/shell.dart' as shell show reportAction;
import '../shared/ticket_card.dart';
import 'event_loading.dart';
import 'event_widgets.dart';

/// P5 · Página do evento: the "opened ticket". On the event day it shows the
/// entry code, organizer alerts, IPMA warnings, crowd level, queues and
/// weather; on other days the schedule and forecast. Offline, or when Core
/// cannot be reached, it shows what was saved and when. Every piece of
/// information comes from the organization or IPMA (ADR 0015).
class EventScreen extends StatefulWidget {
  const EventScreen({super.key, required this.eventId, this.confirmationId});

  final String eventId;

  /// Opens this confirmation request (P13) over the page, e.g. from a
  /// notification.
  final String? confirmationId;

  @override
  State<EventScreen> createState() => _EventScreenState();
}

class _EventScreenState extends State<EventScreen>
    with RefreshesEvent<EventScreen> {
  @override
  String eventIdOf(EventScreen widget) => widget.eventId;

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
        children: [missingEventState(context, store, widget.eventId)],
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

  /// Offline, or the last load from Core failed: the page shows saved data.
  bool get offline => store.showsSavedData(event.id);

  /// When the data shown was saved, while [offline].
  DateTime get savedAt => store.savedAtFor(event.id)!;
  bool get isEventDay => event.isToday(now);
  int get daysToGo => daysBetween(now, event.start);
  bool get hasEntryCode => ticket?.hasEntryCode ?? false;

  /// Estimates shown to participants: not low confidence.
  List<ParticipantEstimate> get visibleEstimates => isEventDay && !offline
      ? event.estimates
            .where((e) => e.confidence != ConfidenceLevel.low)
            .toList()
      : const [];

  int get requestCount =>
      store.yesNoRequestsFor(event.id).length + store.ideasFor(event.id).length;

  /// "Reportar": this event when it takes place today, otherwise the event
  /// in progress; disabled offline or with no event today.
  VoidCallback? reportAction(BuildContext context) {
    if (offline) return null;
    if (store.eventsToday.any((e) => e.id == event.id)) {
      return () => startReport(context, event);
    }
    return shell.reportAction(context);
  }

  /// "Remover bilhete" or "Sair do evento".
  Future<void> showMoreOptions(BuildContext context) async {
    final label = ticket != null ? 'Remover bilhete' : 'Sair do evento';
    final leave = await showAppBottomSheet<bool>(
      context: context,
      builder: (context) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: AppSpacing.md,
        children: [
          Text(event.name, style: AppTextStyles.sheetTitle),
          Text(
            ticket != null
                ? 'Deixas de ver a página do evento e de receber as suas '
                      'alterações. Podes importar o bilhete de novo.'
                : 'Deixas de ver a página do evento e de receber as suas '
                      'alterações. Podes voltar com o código de acesso.',
            style: AppTextStyles.bodySecondary,
          ),
          AppButton.danger(
            label: label,
            onPressed: () => Navigator.pop(context, true),
          ),
          AppButton.secondary(
            label: 'Cancelar',
            onPressed: () => Navigator.pop(context, false),
          ),
        ],
      ),
    );
    if (leave != true || !context.mounted) return;
    store.leaveEvent(event.id);
    context.go('/events');
  }

  void openEntryCode(BuildContext context) =>
      context.push('/event/${event.id}/code');

  void openDirections(BuildContext context) =>
      context.push('/event/${event.id}/directions');

  /// "Fonte: IPMA · atualizado há 30 min", or "guardado às 22:14" offline.
  String source(String name, DateTime? updatedAt) {
    if (offline) {
      return 'Fonte: $name · guardado às ${formatTime(savedAt)}';
    }
    if (updatedAt == null) return 'Fonte: $name';
    return 'Fonte: $name · atualizado ${formatTimeAgo(updatedAt, now: now)}';
  }

  /// Only the time: the banner's overline already says who sent it.
  String alertOrigin(EventAlert alert) {
    final when = offline
        ? 'enviado às ${formatTime(alert.sentAt)}'
        : formatTimeAgo(alert.sentAt, now: now);
    return alert.source.isUnconfirmedAi ? 'Gerado por IA · $when' : when;
  }

  Widget header(BuildContext context) {
    if (ticket != null) {
      return TicketCard(
        ticket: ticket!,
        now: now,
        savedAt: offline ? savedAt : null,
      );
    }
    final (dateLabel, statusLabel, tone) = eventCardStatus(
      event,
      now,
      scheduledLabel: event.isPublic ? 'Público' : 'Bilhete',
    );
    return WalletCard(
      eventName: event.name,
      dateLabel: dateLabel,
      venue: event.place,
      time: formatTime(event.start),
      statusLabel: statusLabel,
      statusTone: tone,
      source: switch (event.source) {
        final origin? when offline =>
          'Fonte: ${sourceName(origin)} · guardado às ${formatTime(savedAt)}',
        _ when offline =>
          'Fonte: organização · guardado às ${formatTime(savedAt)}',
        final origin? => formatProvenance(origin, now: now),
        null => 'Fonte: organização',
      },
      gradient: event.art.gradient,
    );
  }

  List<Widget> notices(BuildContext context) => [
    if (offline)
      MutedNotice(
        icon: Icons.wifi_off,
        iconColor: AppColors.warning,
        background: AppColors.segmentTrack,
        title: store.offline || (store.syncOf(event.id)?.unreachable ?? false)
            ? 'Sem rede'
            : 'Sem ligação ao Crowded',
        message:
            'A mostrar o que guardámos às ${formatTime(savedAt)}.'
            '${ticket == null ? '' : ' O bilhete e o código de entrada funcionam na mesma.'}',
      ),
    if (event.latestDateChange case final change?
        when event.status == EventStatus.postponed)
      AlertBanner(
        kind: AlertBannerKind.officialChange,
        message: postponedMessage(event),
        origin: formatProvenance(change.source, now: now),
        onTap: () => context.push('/event/${event.id}/change/${change.id}'),
      ),
  ];

  List<Widget> alerts() => [
    for (final alert in event.alerts)
      AlertBanner(
        kind: alert.urgent
            ? AlertBannerKind.urgent
            : AlertBannerKind.organizationAlert,
        message: alert.message,
        origin: alertOrigin(alert),
      ),
    for (final warning in event.warnings)
      AlertBanner(
        kind: switch (warning.level) {
          WarningLevel.yellow => AlertBannerKind.weatherYellow,
          WarningLevel.orange => AlertBannerKind.weatherOrange,
          WarningLevel.red => AlertBannerKind.weatherRed,
        },
        message: warning.detail == null
            ? warning.title
            : '${warning.title} · ${warning.detail}',
        origin: warningOrigin(warning),
      ),
  ];

  /// "Fonte: IPMA · emitido às 14:00 · até 23:59".
  String warningOrigin(WeatherWarning warning) {
    final until = isSameDay(warning.until, warning.issuedAt)
        ? formatTime(warning.until)
        : '${formatDayMonth(warning.until)}, ${formatTime(warning.until)}';
    return 'Fonte: IPMA · emitido às ${formatTime(warning.issuedAt)} · '
        'até $until';
  }

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
    source: source(
      event.source == null ? 'organização' : sourceName(event.source!),
      event.updatedAt,
    ),
    large: isEventDay,
  );

  Widget? live(BuildContext context) {
    if (!isEventDay) return null;
    if (offline) {
      return const MutedNotice(
        icon: Icons.people_outline,
        title: 'Lotação e filas',
        message: 'Voltam a aparecer quando tiveres rede.',
      );
    }
    final status = event.venueStatus;
    if (status == null) {
      return MutedNotice(
        icon: Icons.people_outline,
        title: 'Ainda sem informação do recinto',
        message: event.alerts.isEmpty
            ? 'A organização ainda não publicou a lotação, as filas nem '
                  'alertas. Aparecem aqui assim que forem publicados.'
            : 'A organização ainda não publicou a lotação nem as filas. '
                  'Aparecem aqui assim que forem publicadas.',
      );
    }
    final estimates = visibleEstimates;
    return LiveVenuePanel(
      status: status,
      now: now,
      footer: estimates.isEmpty || isDesktopLayout(context)
          ? null
          : AppListTile(
              icon: Icons.people_outline,
              iconColor: AppColors.ink,
              title: 'Estimativas de participantes · ${estimates.length}',
              subtitle: estimates.first.title,
              onTap: () => context.push('/event/${event.id}/estimates'),
            ),
    );
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
      source: offline
          ? source('IPMA', w.updatedAt)
          : 'Previsão IPMA · distrito do ${event.district} · atualizada às '
                '${formatTime(w.updatedAt)}',
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
        subtitle: 'Início às ${formatTime(event.start)} · Fonte: organização',
      ),
    if (!withSchedule)
      AppListTile(
        icon: Icons.directions_outlined,
        title: 'Como chegar',
        onTap: () => openDirections(context),
      ),
    if (requestCount > 0)
      AppListTile(
        icon: Icons.chat_bubble_outline,
        title: 'Pedidos da organização',
        subtitle: requestCount == 1 ? '1 pedido' : '$requestCount pedidos',
        onTap: () => context.push('/event/${event.id}/requests'),
      ),
    if (ticket != null)
      AppListTile(
        icon: Icons.confirmation_number_outlined,
        title: 'Detalhes do bilhete',
        onTap: () => openEntryCode(context),
      ),
    AppListTile(
      icon: Icons.history,
      title: 'Histórico de alterações',
      subtitle: changeSummary(event),
      onTap: () => context.push('/event/${event.id}/history'),
    ),
  ];
}

class _MobileEventPage extends StatelessWidget {
  const _MobileEventPage({required this.page});

  final _EventPageData page;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: page.offline
          ? null
          : AppReportButton(onPressed: page.reportAction(context)),
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
            onPressed: () => page.showMoreOptions(context),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.screen,
          AppSpacing.md,
          AppSpacing.screen,
          96,
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
              ?page.live(context),
              ?page.weather(),
              AppListGroup(children: page.links(context)),
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
                          label: 'Os meus eventos',
                          onPressed: () => context.go('/events'),
                        ),
                        const Icon(
                          Icons.chevron_right,
                          size: 18,
                          color: AppColors.inkSecondary,
                        ),
                        Expanded(
                          child: Text(
                            event.name,
                            style: AppTextStyles.label.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                        AppInlineLink(
                          label: page.ticket != null
                              ? 'Remover bilhete'
                              : 'Sair do evento',
                          onPressed: () => page.showMoreOptions(context),
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
                              ?page.live(context),
                              if (estimates.isNotEmpty)
                                _EstimatesPanel(
                                  estimates: estimates,
                                  now: page.now,
                                  onSeeAll: () => context.push(
                                    '/event/${event.id}/estimates',
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
