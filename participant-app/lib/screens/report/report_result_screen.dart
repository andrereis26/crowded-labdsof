import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';
import 'report_flow.dart';

/// P12 · Reporte enviado / recusado (mobile page). On desktop the same
/// content opens in a dialog (see `startReport`).
class ReportResultScreen extends StatelessWidget {
  const ReportResultScreen({
    super.key,
    required this.eventId,
    this.away = false,
  });

  final String eventId;

  /// "Não estás perto do recinto" instead of "Reporte enviado".
  final bool away;

  void _backToEvent(BuildContext context) {
    if (context.canPop()) {
      context.pop();
    } else {
      context.go('/evento/$eventId');
    }
  }

  void _retry(BuildContext context, Event event) {
    final store = AppStore.read(context);
    if (store.isNearVenue(event)) {
      context.pushReplacement('/evento/$eventId/reportar');
    } else {
      showMessage(context, 'Continuas longe do recinto.');
    }
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final event = store.eventById(eventId);
    final report = store.lastReport;
    return AppPage(
      leadingLabel: 'Fechar',
      showBackChevron: false,
      background: AppColors.surface,
      maxWidth: 500,
      onLeading: () => _backToEvent(context),
      children: [
        const SizedBox(height: AppSpacing.sm),
        if (event == null)
          AppErrorState(
            message: 'Não encontrámos este evento',
            retryLabel: 'Voltar à carteira',
            onRetry: () => context.go('/carteira'),
          )
        else if (away)
          ReportAwayView(
            event: event,
            onBack: () => _backToEvent(context),
            onRetry: () => _retry(context, event),
            onLostAndFound: () => context.pushReplacement(
              '/evento/$eventId/reportar?categoria='
              '${ReportCategory.lostAndFound.name}',
            ),
          )
        else
          ReportSentView(
            event: event,
            report: report?.eventId == eventId ? report : null,
            onDone: () => _backToEvent(context),
          ),
      ],
    );
  }
}

/// "Reporte enviado" with what happens next.
class ReportSentView extends StatelessWidget {
  const ReportSentView({
    super.key,
    required this.event,
    required this.report,
    required this.onDone,
  });

  final Event event;

  /// Null when the page is opened without a report just sent.
  final ParticipantReport? report;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final report = this.report;
    final lost = report?.category == ReportCategory.lostAndFound;
    final about = report == null
        ? ''
        : ' sobre ${report.category.subject} ${report.place.withPreposition}';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 18,
      children: [
        AppResultHeader(
          icon: Icons.check,
          title: 'Reporte enviado',
          message:
              'A organização do ${event.name} já recebeu o teu reporte$about.',
        ),
        AppListGroup(
          title: 'O que acontece a seguir',
          color: AppColors.background,
          children: [
            const AppListTile(
              icon: Icons.check_circle_outline,
              iconColor: AppColors.success,
              title: 'Enviado à organização',
              subtitle: 'Agora mesmo',
              strong: true,
            ),
            if (lost) ...const [
              AppListTile(
                icon: Icons.schedule,
                iconColor: AppColors.warning,
                title: 'A organização vê se alguém o entregou',
                subtitle: 'Na zona de perdidos e achados',
                strong: true,
              ),
              AppListTile(
                icon: Icons.notifications_none,
                iconColor: AppColors.inkSecondary,
                title: 'Avisamos-te se for encontrado',
                subtitle: 'Com o local onde o podes levantar',
                strong: true,
              ),
            ] else ...const [
              AppListTile(
                icon: Icons.schedule,
                iconColor: AppColors.warning,
                title: 'A pedir confirmação a participantes perto de ti',
                subtitle: 'Normalmente demora poucos minutos',
                strong: true,
              ),
              AppListTile(
                icon: Icons.people_outline,
                iconColor: AppColors.inkSecondary,
                title:
                    'Se for confirmado, aparece como estimativa na página do '
                    'evento',
                subtitle: 'Com o número de pessoas que o confirmaram',
                strong: true,
              ),
            ],
          ],
        ),
        AppButton(label: 'Voltar ao evento', onPressed: onDone),
      ],
    );
  }
}

/// "Não estás perto do recinto": reports need the participant at the venue,
/// except lost items.
class ReportAwayView extends StatelessWidget {
  const ReportAwayView({
    super.key,
    required this.event,
    required this.onBack,
    required this.onRetry,
    required this.onLostAndFound,
    this.desktop = false,
  });

  final Event event;
  final VoidCallback onBack;
  final VoidCallback onRetry;
  final VoidCallback onLostAndFound;

  /// "Voltar" and "Tentar novamente" side by side.
  final bool desktop;

  @override
  Widget build(BuildContext context) {
    final retry = AppButton(
      label: 'Tentar novamente',
      icon: Icons.refresh,
      onPressed: onRetry,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: desktop ? AppSpacing.xl : 18,
      children: [
        AppResultHeader(
          icon: Icons.gps_fixed,
          tone: AppTagTone.warning,
          title: 'Não estás perto do recinto',
          message:
              'Para reportar o que se passa no ${event.name}, tens de estar a '
              'menos de $reportRadiusMeters m do recinto.',
        ),
        if (desktop)
          Row(
            spacing: AppSpacing.md,
            children: [
              Expanded(
                child: AppButton.secondary(label: 'Voltar', onPressed: onBack),
              ),
              Expanded(child: retry),
            ],
          )
        else ...[
          retry,
          AppButton.secondary(label: 'Voltar ao evento', onPressed: onBack),
        ],
        AppListGroup(
          color: AppColors.background,
          children: [
            AppListTile(
              icon: Icons.shopping_bag_outlined,
              iconColor: AppColors.ink,
              title: 'Perdeste alguma coisa?',
              subtitle: 'Reporta-o daqui, mesmo longe do recinto.',
              strong: true,
              onTap: onLostAndFound,
            ),
          ],
        ),
      ],
    );
  }
}
