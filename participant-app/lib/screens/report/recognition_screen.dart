import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P15 · Reconhecimento — o teu reporte ajudou. Opened from the
/// notification when the organizer marks a report as helpful.
class RecognitionScreen extends StatelessWidget {
  const RecognitionScreen({super.key, required this.recognitionId});

  final String recognitionId;

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final recognition = store.recognition(recognitionId);
    if (recognition == null) {
      return AppPage(
        leadingLabel: 'Fechar',
        showBackChevron: false,
        children: [
          AppErrorState(
            message: 'Não encontrámos este reconhecimento',
            retryLabel: 'Voltar aos meus eventos',
            onRetry: () => context.go('/events'),
          ),
        ],
      );
    }
    final eventName = store.eventById(recognition.eventId)?.name ?? 'evento';
    void openEvent() => context.go('/event/${recognition.eventId}');
    final header = AppResultHeader(
      icon: Icons.workspace_premium_outlined,
      title: 'O teu reporte ajudou!',
      message: 'A organização do $eventName marcou o teu reporte como útil.',
      large: true,
    );
    final summary = _ReportSummary(recognition: recognition, now: store.now);
    const thanks = 'Obrigado por ajudares quem está no evento contigo.';

    if (isDesktopLayout(context)) {
      return Scaffold(
        appBar: const CrowdedWebHeader(currentIndex: Sections.wallet),
        body: ListView(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.screen,
            vertical: 56,
          ),
          children: [
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 600),
                child: AppFloatingCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    spacing: 22,
                    children: [
                      header,
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        spacing: AppSpacing.lg,
                        children: [
                          Expanded(
                            child: _StatTile(
                              icon: Icons.check_circle_outline,
                              iconColor: AppColors.success,
                              value: formatDuration(recognition.resolvedAfter),
                              label: 'até o problema ser resolvido',
                            ),
                          ),
                          Expanded(
                            child: _StatTile(
                              icon: Icons.campaign_outlined,
                              iconColor: AppColors.primary,
                              value:
                                  '${formatCount(recognition.alertReach)} '
                                  'pessoas',
                              label: 'receberam o alerta da organização',
                            ),
                          ),
                        ],
                      ),
                      summary,
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        spacing: AppSpacing.md,
                        children: [
                          AppButton(
                            label: 'Ver evento',
                            expand: false,
                            onPressed: openEvent,
                          ),
                          AppButton.secondary(
                            label: 'Fechar',
                            expand: false,
                            onPressed: () => AppPage.goBack(context),
                          ),
                        ],
                      ),
                      Text(
                        thanks,
                        textAlign: TextAlign.center,
                        style: AppTextStyles.caption,
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
      leadingLabel: 'Fechar',
      showBackChevron: false,
      background: AppColors.surface,
      children: [
        header,
        const SizedBox(height: AppSpacing.xxs),
        AppPanel(
          color: AppColors.background,
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.xl,
            vertical: AppSpacing.lg,
          ),
          spacing: AppSpacing.md,
          children: [
            Text(
              'IMPACTO',
              style: AppTextStyles.overline.copyWith(
                color: AppColors.inkSecondary,
              ),
            ),
            _ImpactRow(
              icon: Icons.check_circle_outline,
              iconColor: AppColors.success,
              title:
                  'Problema resolvido em '
                  '${formatDuration(recognition.resolvedAfter)}',
              subtitle: recognition.resolution,
            ),
            _ImpactRow(
              icon: Icons.campaign_outlined,
              iconColor: AppColors.primary,
              title:
                  'Alerta enviado a ${formatCount(recognition.alertReach)} '
                  'pessoas',
              subtitle: '“${recognition.alertMessage}”',
            ),
          ],
        ),
        summary,
        const SizedBox(height: AppSpacing.xxs),
        Text(
          thanks,
          textAlign: TextAlign.center,
          style: AppTextStyles.body.copyWith(fontWeight: FontWeight.w500),
        ),
        const SizedBox(height: AppSpacing.xxs),
        AppButton(label: 'Ver evento', onPressed: openEvent),
      ],
    );
  }
}

class _ImpactRow extends StatelessWidget {
  const _ImpactRow({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final Color iconColor;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Row(
      spacing: AppSpacing.md,
      children: [
        Icon(icon, size: 24, color: iconColor),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            spacing: 2,
            children: [
              Text(title, style: AppTextStyles.bodyStrong),
              Text(subtitle, style: AppTextStyles.caption),
            ],
          ),
        ),
      ],
    );
  }
}

/// "18 min · até o problema ser resolvido" (desktop).
class _StatTile extends StatelessWidget {
  const _StatTile({
    required this.icon,
    required this.iconColor,
    required this.value,
    required this.label,
  });

  final IconData icon;
  final Color iconColor;
  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return AppPanel(
      color: AppColors.background,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
      spacing: AppSpacing.xs,
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: Icon(icon, size: 24, color: iconColor),
        ),
        Text(value, style: AppTextStyles.title.copyWith(fontSize: 24)),
        Text(label, style: AppTextStyles.footnote),
      ],
    );
  }
}

/// "O TEU REPORTE · Falta de água no bar principal".
class _ReportSummary extends StatelessWidget {
  const _ReportSummary({required this.recognition, required this.now});

  final Recognition recognition;
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    final at = recognition.reportedAt;
    return AppPanel(
      borderColor: AppColors.border,
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.xl,
        vertical: AppSpacing.lg,
      ),
      spacing: AppSpacing.xs,
      children: [
        Text(
          'O TEU REPORTE',
          style: AppTextStyles.overline.copyWith(color: AppColors.inkSecondary),
        ),
        Row(
          spacing: 10,
          children: [
            Icon(recognition.category.icon, size: 20, color: AppColors.ink),
            Expanded(
              child: Text(
                recognition.reportTitle,
                style: AppTextStyles.bodyStrong,
              ),
            ),
          ],
        ),
        Text(
          '${formatRelativeDaySentence(at, now)} · ${formatTime(at)} · '
          '${recognition.category.label}',
          style: AppTextStyles.caption,
        ),
      ],
    );
  }
}
