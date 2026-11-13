import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';
import 'event_widgets.dart';
import 'map_preview.dart';

/// P18 · Como chegar ao local: route to the entrance and live transport
/// options.
class DirectionsScreen extends StatelessWidget {
  const DirectionsScreen({super.key, required this.eventId});

  final String eventId;

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final event = store.eventById(eventId);
    if (event == null) {
      return AppPage(
        children: [
          AppErrorState(
            message: 'Não encontrámos este evento',
            retryLabel: 'Voltar aos meus eventos',
            onRetry: () => context.go('/events'),
          ),
        ],
      );
    }
    final directions = event.directions;
    final entrance = directions?.entrance;
    final venue = AppListGroup(
      children: [
        AppListTile(
          leading: const AppIconBadge(
            icon: Icons.location_on_outlined,
            iconSize: 20,
            dark: true,
          ),
          title: entrance == null ? event.venue : '${event.venue} · $entrance',
          subtitle: [
            ?directions?.entranceDetail,
            if (event.doorsOpen != null)
              'portas abrem às ${formatTime(event.doorsOpen!)}'
            else
              event.city,
          ].join(' · '),
          strong: true,
        ),
      ],
    );
    final options = directions == null
        ? const MutedNotice(
            icon: Icons.directions_outlined,
            title: 'Sem informação de transportes',
            message: 'Usa o mapa para ver o melhor caminho até ao recinto.',
          )
        : Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            spacing: AppSpacing.md,
            children: [
              AppPanel(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.xl,
                  vertical: 2,
                ),
                spacing: 0,
                children: [
                  for (final (i, option) in directions.options.indexed)
                    _TransportRow(
                      option: option,
                      divider: i < directions.options.length - 1,
                    ),
                ],
              ),
              SourceLabel(
                source: '${directions.operators} (horários GTFS)',
                updatedAt: directions.updatedAt,
                now: store.now,
              ),
            ],
          );
    final openMap = AppButton.secondary(
      label: 'Abrir no mapa',
      trailingIcon: Icons.open_in_new,
      onPressed: () => showComingSoon(context),
    );

    if (isDesktopLayout(context)) {
      return Scaffold(
        appBar: const CrowdedWebHeader(currentIndex: Sections.wallet),
        body: ListView(
          padding: const EdgeInsets.symmetric(vertical: 40),
          children: [
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1200),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 40),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    spacing: AppSpacing.xxl,
                    children: [
                      AppBreadcrumbs(
                        items: [
                          AppBreadcrumb(
                            'Os meus eventos',
                            onTap: () => context.go('/events'),
                          ),
                          AppBreadcrumb(
                            event.name,
                            onTap: () => context.go('/event/${event.id}'),
                          ),
                          const AppBreadcrumb('Como chegar'),
                        ],
                      ),
                      Semantics(
                        header: true,
                        child: Text(
                          'Como chegar ao ${event.name}',
                          style: AppTextStyles.title.copyWith(
                            fontSize: 28,
                            letterSpacing: -0.28,
                          ),
                        ),
                      ),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        spacing: 24,
                        children: [
                          Expanded(
                            flex: 680,
                            child: MapPreview(
                              entranceLabel: entrance == null
                                  ? event.venue
                                  : '$entrance · '
                                        '${directions!.entranceDetail.toLowerCase()}',
                              originLabel: 'Estás aqui',
                              height: 560,
                              radius: const Radius.circular(18),
                            ),
                          ),
                          Expanded(
                            flex: 416,
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              spacing: AppSpacing.lg,
                              children: [venue, options, openMap],
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

    return AppPage(
      title: 'Como chegar',
      showReportButton: true,
      children: [
        MapPreview(entranceLabel: entrance ?? event.venue),
        venue,
        options,
        openMap,
      ],
    );
  }
}

/// "Metro + 8 min a pé · Próximo às 21:12 · 26 min".
class _TransportRow extends StatelessWidget {
  const _TransportRow({required this.option, required this.divider});

  final TransportOption option;
  final bool divider;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 13),
      decoration: divider
          ? const BoxDecoration(
              border: Border(bottom: BorderSide(color: AppColors.border)),
            )
          : null,
      child: Row(
        spacing: AppSpacing.md,
        children: [
          AppIconBadge(
            icon: option.mode.icon,
            iconSize: 20,
            tone: option.disrupted ? AppTagTone.warning : option.mode.tone,
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: 3,
              children: [
                Wrap(
                  spacing: AppSpacing.xs,
                  runSpacing: AppSpacing.xxs,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(option.title, style: AppTextStyles.bodyStrong),
                    if (option.disrupted)
                      const AppTag(
                        label: 'Perturbação',
                        tone: AppTagTone.warning,
                        icon: Icons.warning_amber_rounded,
                      ),
                  ],
                ),
                Text(
                  option.detail,
                  style: AppTextStyles.caption.copyWith(
                    height: 1.35,
                    color: option.disrupted ? AppColors.warning : null,
                  ),
                ),
              ],
            ),
          ),
          Text('${option.minutes} min', style: AppTextStyles.bodyLarge),
        ],
      ),
    );
  }
}
