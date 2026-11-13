import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P7 · Detalhe da alteração oficial: what changed, what stayed the same and
/// whether the ticket is still valid.
class ChangeDetailScreen extends StatelessWidget {
  const ChangeDetailScreen({
    super.key,
    required this.eventId,
    required this.changeId,
  });

  final String eventId;
  final String changeId;

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final event = store.eventById(eventId);
    final change = event?.changes.where((c) => c.id == changeId).firstOrNull;
    if (event == null || change == null) {
      return AppPage(
        leadingLabel: 'Fechar',
        showBackChevron: false,
        children: [AppErrorState(message: 'Não encontrámos esta alteração')],
      );
    }
    final now = store.now;
    final ticket = store.ticketFor(event.id);
    final from = change.previousStart;
    final to = change.newStart;
    final dateChanged = from != null && to != null;

    return AppPage(
      leadingLabel: 'Fechar',
      showBackChevron: false,
      background: AppColors.surface,
      showReportButton: true,
      maxWidth: 560,
      children: [
        Column(
          spacing: 10,
          children: [
            Container(
              padding: const EdgeInsets.all(AppSpacing.xl),
              decoration: const BoxDecoration(
                color: AppColors.warningSoft,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.calendar_today_outlined,
                size: 32,
                color: AppColors.warning,
              ),
            ),
            const AppTag(
              label: 'ALTERAÇÃO OFICIAL',
              tone: AppTagTone.warning,
              size: AppTagSize.large,
            ),
            Semantics(
              header: true,
              child: Text(
                dateChanged ? '${event.name} foi adiada' : change.title,
                textAlign: TextAlign.center,
                style: AppTextStyles.sheetTitle.copyWith(height: 1.15),
              ),
            ),
            Text(
              event.place,
              textAlign: TextAlign.center,
              style: AppTextStyles.body.copyWith(color: AppColors.inkSecondary),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.xxs),
        Container(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.xl,
            vertical: AppSpacing.xxs,
          ),
          decoration: const BoxDecoration(
            color: AppColors.background,
            borderRadius: BorderRadius.all(AppRadii.panel),
          ),
          child: Column(
            children: [
              if (dateChanged) ...[
                _CompareRow(
                  label: 'Data',
                  before: isSameDay(from, to) ? null : formatWeekdayDate(from),
                  after: formatWeekdayDate(to),
                ),
                const Divider(),
                _CompareRow(
                  label: 'Hora',
                  before: formatTime(from) == formatTime(to)
                      ? null
                      : formatTime(from),
                  after: formatTime(to),
                  unchanged: formatTime(from) == formatTime(to),
                ),
                const Divider(),
              ],
              _CompareRow(label: 'Local', after: event.venue, unchanged: true),
            ],
          ),
        ),
        if (change.refund case final refund?)
          AppListGroup(
            children: [
              AppListTile(
                icon: Icons.payments_outlined,
                title: 'Reembolso, se não puderes ir',
                subtitle:
                    'Até ${formatDayMonth(refund.until)} em ${refund.where} · '
                    'informação da organização',
                showChevron: false,
              ),
            ],
          ),
        if (ticket != null && change.ticketStillValid)
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.lg,
              vertical: 10,
            ),
            decoration: BoxDecoration(
              borderRadius: const BorderRadius.all(AppRadii.panel),
              border: Border.all(color: AppColors.border),
            ),
            child: Row(
              spacing: AppSpacing.md,
              children: [
                const Icon(
                  Icons.confirmation_number_outlined,
                  size: 22,
                  color: AppColors.primary,
                ),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    spacing: 2,
                    children: [
                      Text(
                        'O teu bilhete continua válido',
                        style: AppTextStyles.body.copyWith(
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      Text(
                        [
                          'Para ${formatWeekdayDate(event.start)}',
                          if (ticket.gate != null) 'Porta ${ticket.gate}',
                          if (ticket.seat != null) 'Lugar ${ticket.seat}',
                        ].join(' · '),
                        style: AppTextStyles.caption,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        Container(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.md,
            vertical: 10,
          ),
          decoration: const BoxDecoration(
            color: AppColors.primarySoft,
            borderRadius: BorderRadius.all(AppRadii.field),
          ),
          child: Row(
            spacing: AppSpacing.sm,
            children: [
              const Icon(
                Icons.verified_user_outlined,
                size: 18,
                color: AppColors.primary,
              ),
              Expanded(
                child: Text(
                  'Fonte: organização · publicado às '
                  '${formatTime(change.detectedAt)} · '
                  '${formatTimeAgo(change.detectedAt, now: now)}',
                  style: AppTextStyles.caption.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ],
          ),
        ),
        AppButton(
          label: 'Ver evento',
          onPressed: () => context.go('/event/${event.id}'),
        ),
        if (change.refund case final refund?)
          AppButton.secondary(
            label: 'Pedir reembolso até ${formatDayMonth(refund.until)}',
            icon: Icons.open_in_new,
            onPressed: () => showMessage(context, refund.where),
          ),
      ],
    );
  }
}

/// "Data   ~~SÁB 25 OUT~~ → SÁB 8 NOV" or "Hora   21:30  Sem alteração".
class _CompareRow extends StatelessWidget {
  const _CompareRow({
    required this.label,
    required this.after,
    this.before,
    this.unchanged = false,
  });

  final String label;
  final String? before;
  final String after;
  final bool unchanged;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: before != null
          ? '$label: era $before, passa a $after'
          : '$label: $after${unchanged ? ', sem alteração' : ''}',
      excludeSemantics: true,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
        child: Row(
          spacing: 10,
          children: [
            SizedBox(
              width: 52,
              child: Text(
                label,
                style: AppTextStyles.label.copyWith(
                  color: AppColors.inkSecondary,
                ),
              ),
            ),
            if (before != null) ...[
              Text(
                before!,
                style: AppTextStyles.body.copyWith(
                  color: AppColors.inkSecondary,
                  decoration: TextDecoration.lineThrough,
                ),
              ),
              const Icon(
                Icons.arrow_forward,
                size: 16,
                color: AppColors.inkSecondary,
              ),
            ],
            Flexible(
              child: Text(
                after,
                style: before != null
                    ? AppTextStyles.bodyStrong
                    : AppTextStyles.body.copyWith(fontWeight: FontWeight.w500),
              ),
            ),
            if (unchanged) const AppTag(label: 'Sem alteração'),
          ],
        ),
      ),
    );
  }
}
