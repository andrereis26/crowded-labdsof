import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P20 · As tuas contribuições: the participant's reports and confirmations
/// and what happened to each one (FR-038). Only the participant sees it.
class ContributionsScreen extends StatefulWidget {
  const ContributionsScreen({super.key});

  @override
  State<ContributionsScreen> createState() => _ContributionsScreenState();
}

class _ContributionsScreenState extends State<ContributionsScreen> {
  ContributionFilter _filter = ContributionFilter.all;

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final now = store.now;
    final all = store.contributions;
    final items = all.where((c) => c.matches(_filter));

    // One group per event and day: "FESTIVAL MARGENS · HOJE".
    final groups = <String, List<Contribution>>{};
    for (final c in items) {
      final key =
          '${c.eventName.toUpperCase()} · ${formatRelativeDay(c.at, now)}';
      groups.putIfAbsent(key, () => []).add(c);
    }
    final desktop = isDesktopLayout(context);
    final list = [
      if (groups.isEmpty)
        const AppEmptyState(
          icon: Icons.flag_outlined,
          message: 'Ainda sem contribuições',
        ),
      for (final MapEntry(key: title, value: group) in groups.entries) ...[
        Text(
          title,
          style: AppTextStyles.overline.copyWith(color: AppColors.inkSecondary),
        ),
        AppPanel(
          padding: EdgeInsets.symmetric(horizontal: desktop ? 20 : 14),
          spacing: 0,
          children: [
            for (final (i, c) in group.indexed)
              _ContributionRow(
                contribution: c,
                now: now,
                showEvent: desktop,
                divider: i < group.length - 1,
                onTap: _routeFor(store, c) == null
                    ? null
                    : () => context.push(_routeFor(store, c)!),
              ),
          ],
        ),
      ],
    ];

    if (desktop) {
      return ListView(
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
                    Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            spacing: 4,
                            children: [
                              Semantics(
                                header: true,
                                child: Text(
                                  'As tuas contribuições',
                                  style: AppTextStyles.title.copyWith(
                                    fontSize: 28,
                                    letterSpacing: -0.28,
                                  ),
                                ),
                              ),
                              Text(
                                'Os reportes e confirmações que fizeste e o '
                                'que aconteceu a cada um.',
                                style: AppTextStyles.bodySecondary,
                              ),
                            ],
                          ),
                        ),
                        AppButton.secondary(
                          label: 'Preferências',
                          icon: Icons.settings_outlined,
                          size: AppButtonSize.small,
                          expand: false,
                          onPressed: () => showComingSoon(context),
                        ),
                      ],
                    ),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      spacing: 24,
                      children: [
                        SizedBox(
                          width: 260,
                          child: _FilterList(
                            selected: _filter,
                            counts: {
                              for (final f in ContributionFilter.values)
                                f: all.where((c) => c.matches(f)).length,
                            },
                            onSelect: (f) => setState(() => _filter = f),
                          ),
                        ),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            spacing: AppSpacing.md,
                            children: list,
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
      );
    }

    return SafeArea(
      bottom: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.screen,
          0,
          AppSpacing.screen,
          AppSpacing.xxl,
        ),
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: AppInlineLink(
              label: 'Conta',
              icon: Icons.chevron_left,
              onPressed: () => context.go(Sections.paths[Sections.account]),
            ),
          ),
          const SectionHeader(title: 'Contribuições'),
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            spacing: AppSpacing.md,
            children: [
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  spacing: AppSpacing.sm,
                  children: [
                    for (final filter in const [
                      ContributionFilter.all,
                      ContributionFilter.reports,
                      ContributionFilter.confirmations,
                    ])
                      AppChip(
                        label: filter.label,
                        selected: filter == _filter,
                        onTap: () => setState(() => _filter = filter),
                      ),
                  ],
                ),
              ),
              ...list,
            ],
          ),
        ],
      ),
    );
  }

  /// Helpful reports open the recognition; others their event, if known.
  static String? _routeFor(AppStore store, Contribution c) =>
      c.route ??
      (store.eventById(c.eventId) == null ? null : '/evento/${c.eventId}');
}

class _ContributionRow extends StatelessWidget {
  const _ContributionRow({
    required this.contribution,
    required this.now,
    required this.showEvent,
    required this.divider,
    this.onTap,
  });

  final Contribution contribution;
  final DateTime now;

  /// "Festival Margens · Reporte" instead of "Reporte" (desktop).
  final bool showEvent;
  final bool divider;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = contribution;
    final when = isSameDay(c.at, now)
        ? formatTimeAgo(c.at, now: now)
        : formatDayMonth(c.at).toLowerCase();
    return Semantics(
      button: onTap != null,
      child: InkWell(
        onTap: onTap,
        child: Container(
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
              AppIconBadge(icon: c.kind.icon, tone: c.tone),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  spacing: 3,
                  children: [
                    Row(
                      spacing: AppSpacing.xs,
                      children: [
                        Expanded(
                          child: Text(
                            showEvent
                                ? '${c.eventName} · ${c.kind.label}'
                                : c.kind.label,
                            style: AppTextStyles.caption.copyWith(
                              fontWeight: FontWeight.w500,
                              height: 1.2,
                            ),
                          ),
                        ),
                        Text(
                          when,
                          style: AppTextStyles.caption.copyWith(height: 1.2),
                        ),
                      ],
                    ),
                    Text(c.title, style: AppTextStyles.bodyStrong),
                    Text(
                      c.status,
                      style: AppTextStyles.footnote.copyWith(
                        color: AppColors.ink,
                        height: 1.4,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Filters with counts on the left of the desktop page.
class _FilterList extends StatelessWidget {
  const _FilterList({
    required this.selected,
    required this.counts,
    required this.onSelect,
  });

  final ContributionFilter selected;
  final Map<ContributionFilter, int> counts;
  final ValueChanged<ContributionFilter> onSelect;

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      padding: const EdgeInsets.all(AppSpacing.sm),
      spacing: 2,
      children: [
        for (final filter in ContributionFilter.values)
          _FilterItem(
            filter: filter,
            count: counts[filter] ?? 0,
            selected: filter == selected,
            onTap: () => onSelect(filter),
          ),
      ],
    );
  }
}

class _FilterItem extends StatelessWidget {
  const _FilterItem({
    required this.filter,
    required this.count,
    required this.selected,
    required this.onTap,
  });

  final ContributionFilter filter;
  final int count;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? AppColors.primary : AppColors.ink;
    const radius = BorderRadius.all(AppRadii.field);
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: selected ? AppColors.primarySoft : Colors.transparent,
        borderRadius: radius,
        child: InkWell(
          onTap: onTap,
          borderRadius: radius,
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.md,
              vertical: 10,
            ),
            child: Row(
              spacing: 10,
              children: [
                Icon(filter.icon, size: 18, color: color),
                Expanded(
                  child: Text(
                    filter.label,
                    style: AppTextStyles.subhead.copyWith(
                      color: color,
                      fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                    ),
                  ),
                ),
                Text(
                  '$count',
                  style: AppTextStyles.caption.copyWith(
                    color: selected
                        ? AppColors.primary
                        : AppColors.inkSecondary,
                    fontWeight: FontWeight.w600,
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
