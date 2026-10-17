import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../report/report_flow.dart';
import '../shared/event_context_row.dart';
import '../shared/shell.dart';

const _rules = [
  'Várias pessoas perto do recinto confirmaram-na.',
  'Respostas Não e o passar do tempo fazem-na desaparecer.',
  'Sem confirmações suficientes, não é mostrada.',
];

/// P14 · Estimativas de participantes: reports confirmed by people at the
/// venue (#77). Low-confidence estimates are not shown (FR-058).
class EstimatesScreen extends StatelessWidget {
  const EstimatesScreen({super.key, required this.eventId});

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
            retryLabel: 'Voltar à carteira',
            onRetry: () => context.go('/carteira'),
          ),
        ],
      );
    }
    final now = store.now;
    final estimates = event.estimates
        .where((e) => e.confidence != ConfidenceLevel.low)
        .toList();
    final canReport =
        isSameDay(event.start, now) &&
        store.ticketFor(event.id) != null &&
        !store.offline;
    final list = estimates.isEmpty
        ? const AppEmptyState(
            icon: Icons.people_outline,
            message: 'Ainda sem estimativas confirmadas',
          )
        : null;

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
                            'Carteira',
                            onTap: () => context.go('/carteira'),
                          ),
                          AppBreadcrumb(
                            event.name,
                            onTap: () => context.go('/evento/${event.id}'),
                          ),
                          const AppBreadcrumb('Estimativas de participantes'),
                        ],
                      ),
                      Row(
                        spacing: AppSpacing.xl,
                        children: [
                          EventThumbnail(
                            gradient: event.art.gradient,
                            size: 64,
                            radius: 12,
                          ),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              spacing: 4,
                              children: [
                                Semantics(
                                  header: true,
                                  child: Text(
                                    'Estimativas de participantes',
                                    style: _pageTitle,
                                  ),
                                ),
                                Text(
                                  '${event.name} · ${event.venue} · reportes '
                                  'confirmados por quem está no recinto',
                                  style: AppTextStyles.bodySecondary,
                                ),
                              ],
                            ),
                          ),
                          if (canReport)
                            AppButton(
                              label: 'Reportar algo',
                              icon: Icons.flag_outlined,
                              expand: false,
                              onPressed: () => startReport(context, event),
                            ),
                        ],
                      ),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        spacing: 24,
                        children: [
                          Expanded(
                            child: AppPanel(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 24,
                                vertical: AppSpacing.xl,
                              ),
                              spacing: AppSpacing.xxs,
                              children: [
                                Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        'Ativas agora',
                                        style: AppTextStyles.headline,
                                      ),
                                    ),
                                    Text(
                                      estimates.length == 1
                                          ? '1 estimativa'
                                          : '${estimates.length} estimativas',
                                      style: AppTextStyles.label.copyWith(
                                        color: AppColors.primary,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: AppSpacing.sm),
                                const _InlineNotice(
                                  'Podem não refletir a realidade.',
                                ),
                                ?list,
                                for (final (i, estimate) in estimates.indexed)
                                  _EstimateRow(
                                    estimate: estimate,
                                    now: now,
                                    divider: i < estimates.length - 1,
                                  ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 400, child: _RulesPanel()),
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
      title: 'Estimativas',
      children: [
        EventContextRow(event: event, subtitle: '${event.venue} · agora'),
        const EstimateNotice(
          message:
              'Confirmadas por outros participantes no recinto. Podem não '
              'refletir a realidade.',
        ),
        ?list,
        if (estimates.isNotEmpty)
          AppPanel(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.xl,
              vertical: 2,
            ),
            spacing: 0,
            children: [
              for (final (i, estimate) in estimates.indexed)
                _EstimateRow(
                  estimate: estimate,
                  now: now,
                  divider: i < estimates.length - 1,
                ),
            ],
          ),
        AppListGroup(
          children: [
            AppListTile(
              title: 'Como funcionam as estimativas',
              onTap: () => showAppBottomSheet<void>(
                context: context,
                builder: (context) => const _RulesPanel(sheet: true),
              ),
            ),
          ],
        ),
        if (canReport)
          AppButton.secondary(
            label: 'Reportar algo',
            icon: Icons.flag_outlined,
            onPressed: () => startReport(context, event),
          ),
      ],
    );
  }
}

final _pageTitle = AppTextStyles.title.copyWith(
  fontSize: 28,
  letterSpacing: -0.28,
);

/// "FILA · Fila nos WC junto ao palco · 15–20 min · há 4 min" with the
/// confirmation bar.
class _EstimateRow extends StatelessWidget {
  const _EstimateRow({
    required this.estimate,
    required this.now,
    required this.divider,
  });

  final ParticipantEstimate estimate;
  final DateTime now;
  final bool divider;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
      decoration: divider
          ? const BoxDecoration(
              border: Border(bottom: BorderSide(color: AppColors.border)),
            )
          : null,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: AppSpacing.md,
        children: [
          AppIconBadge(
            icon: estimate.category.icon,
            tone: estimate.category.tone,
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              spacing: 5,
              children: [
                Text(
                  estimate.category.label.toUpperCase(),
                  style: AppTextStyles.overline.copyWith(
                    color: AppColors.inkSecondary,
                    letterSpacing: 0.44,
                  ),
                ),
                Text(estimate.title, style: AppTextStyles.bodyStrong),
                Text(
                  formatTimeAgo(estimate.reportedAt, now: now),
                  style: AppTextStyles.caption.copyWith(height: 1.2),
                ),
                ConfirmationBar(
                  confirmed: estimate.confirmed,
                  denied: estimate.denied,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Grey strip with an info icon ("Podem não refletir a realidade.").
class _InlineNotice extends StatelessWidget {
  const _InlineNotice(this.message);

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.md,
        vertical: AppSpacing.sm,
      ),
      decoration: const BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.all(AppRadii.field),
      ),
      child: Row(
        spacing: AppSpacing.sm,
        children: [
          const Icon(
            Icons.info_outline,
            size: 16,
            color: AppColors.inkSecondary,
          ),
          Expanded(
            child: Text(
              message,
              style: AppTextStyles.caption.copyWith(
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// "Quando aparece uma estimativa": a side panel on desktop, a bottom sheet
/// on mobile.
class _RulesPanel extends StatelessWidget {
  const _RulesPanel({this.sheet = false});

  final bool sheet;

  @override
  Widget build(BuildContext context) {
    final children = [
      Text(
        'Quando aparece uma estimativa',
        style: sheet ? AppTextStyles.headline : AppTextStyles.bodyStrong,
      ),
      for (final rule in _rules)
        Text(
          rule,
          style: AppTextStyles.label.copyWith(fontWeight: FontWeight.w400),
        ),
    ];
    if (sheet) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        spacing: AppSpacing.md,
        children: children,
      );
    }
    return AppPanel(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.xl,
        vertical: AppSpacing.lg,
      ),
      spacing: AppSpacing.md,
      children: children,
    );
  }
}
