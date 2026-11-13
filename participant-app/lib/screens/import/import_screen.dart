import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';

/// P2 · Importar bilhete: from the ticket PDF or a screenshot or photo of
/// it. The QR code and the event code are read from the file; events
/// without tickets are joined with an access code (P21).
class ImportScreen extends StatelessWidget {
  const ImportScreen({super.key});

  /// Screen that shows [outcome]: P2a when the ticket was added, P2b
  /// otherwise.
  static String pathFor(ImportOutcome outcome) => switch (outcome) {
    ImportOutcome.added => '/import/added',
    ImportOutcome.needsEventCode => '/import/code',
    ImportOutcome.noQrCode => '/import/error/no-qr',
    ImportOutcome.eventNotOnCrowded => '/import/error/not-on-crowded',
    ImportOutcome.linkedToAnotherAccount => '/import/error/other-account',
  };

  void _import(BuildContext context, TicketOrigin origin) {
    final outcome = AppStore.read(context).importFile(origin);
    context.push(pathFor(outcome));
  }

  @override
  Widget build(BuildContext context) {
    return AppPage(
      title: 'Importar bilhete',
      leadingLabel: 'Cancelar',
      showBackChevron: false,
      maxWidth: 560,
      children: [
        Text(
          'Carrega o bilhete do evento. Lemos o código QR e o código do '
          'evento para o juntar aos teus eventos.',
          style: AppTextStyles.bodySecondary.copyWith(fontSize: 15),
        ),
        AppNavigationCard(
          icon: Icons.description_outlined,
          title: 'PDF do bilhete',
          subtitle: 'O ficheiro que recebeste quando compraste o bilhete.',
          onTap: () => _import(context, TicketOrigin.pdf),
        ),
        AppNavigationCard(
          icon: Icons.image_outlined,
          title: 'Captura de ecrã ou foto',
          subtitle: isDesktopLayout(context)
              ? 'PNG ou JPG, com o código QR visível.'
              : 'Uma imagem nítida, com o código QR visível.',
          onTap: () => _import(context, TicketOrigin.image),
        ),
        Center(
          child: AppInlineLink(
            label: 'Evento sem bilhete? Inserir código de acesso',
            icon: Icons.qr_code_scanner,
            onPressed: () => context.push('/access-code'),
          ),
        ),
      ],
    );
  }
}
