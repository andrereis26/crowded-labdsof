import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';

/// P2 · Importar bilhete: from the ticket PDF or the confirmation email
/// file, or typed in by hand.
class ImportScreen extends StatelessWidget {
  const ImportScreen({super.key});

  void _start(BuildContext context, TicketOrigin origin) {
    AppStore.of(context).startImport(origin);
    context.push('/importar/rever');
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
          'Carrega o bilhete que compraste noutra plataforma.',
          style: AppTextStyles.bodySecondary.copyWith(fontSize: 15),
        ),
        AppNavigationCard(
          icon: Icons.description_outlined,
          title: 'PDF do bilhete',
          subtitle: 'O ficheiro que recebeste da bilheteira.',
          onTap: () => _start(context, TicketOrigin.pdf),
        ),
        AppNavigationCard(
          icon: Icons.mail_outline,
          title: 'Email de confirmação (.eml)',
          subtitle: 'Guarda o email como ficheiro .eml e carrega-o aqui.',
          onTap: () => _start(context, TicketOrigin.email),
          footer: AppInlineLink(
            label: 'Como guardar um email em .eml?',
            icon: Icons.info_outline,
            onPressed: () => showComingSoon(context),
          ),
        ),
        const SizedBox(height: AppSpacing.xxs),
        AppButton.secondary(
          label: 'Introduzir dados manualmente',
          icon: Icons.edit_outlined,
          onPressed: () => _start(context, TicketOrigin.manual),
        ),
        Center(
          child: AppInlineLink(
            label: 'Como tratamos os teus dados',
            icon: Icons.shield_outlined,
            onPressed: () => showComingSoon(context),
          ),
        ),
      ],
    );
  }
}
