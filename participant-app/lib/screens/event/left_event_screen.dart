import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P8 · Já não estás ligado a este evento: shown instead of the event page
/// when the participant removed its ticket or left the public event (FR-008,
/// SR-020), for instance from an old notification.
class LeftEventScreen extends StatelessWidget {
  const LeftEventScreen({super.key, required this.eventId});

  final String eventId;

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final event = store.eventById(eventId);
    final leftAt = store.leftAt(eventId);
    final subtitle = event == null
        ? null
        : leftAt == null
        ? event.name
        : '${event.name} · saíste a ${formatDayMonth(leftAt)}';
    final publicEvent = event?.isPublic ?? false;

    return AppPage(
      leadingLabel: 'Notificações',
      onLeading: () => context.go('/notificacoes'),
      background: AppColors.surface,
      maxWidth: 560,
      children: [
        const SizedBox(height: AppSpacing.xl),
        AppResultHeader(
          icon: Icons.link_off,
          title: 'Já não estás ligado a este evento',
          message: subtitle,
          tone: AppTagTone.neutral,
        ),
        Text(
          publicEvent
              ? 'Para voltares a ver as alterações e os alertas da '
                    'organização, insere de novo o código de acesso do evento.'
              : 'Para voltares a ver as alterações e os alertas da '
                    'organização, importa de novo o teu bilhete.',
          textAlign: TextAlign.center,
          style: AppTextStyles.bodySecondary.copyWith(fontSize: 15),
        ),
        const SizedBox(height: AppSpacing.sm),
        AppButton(
          label: 'Voltar às notificações',
          onPressed: () => context.go('/notificacoes'),
        ),
        AppButton.secondary(
          label: publicEvent ? 'Inserir código de acesso' : 'Importar bilhete',
          onPressed: () =>
              context.push(publicEvent ? '/codigo-de-acesso' : '/importar'),
        ),
      ],
    );
  }
}
