import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P4 · Associação ao evento do catálogo. A ticket can also be saved
/// without a link ("Nenhum destes").
class LinkEventScreen extends StatefulWidget {
  const LinkEventScreen({super.key});

  @override
  State<LinkEventScreen> createState() => _LinkEventScreenState();
}

class _LinkEventScreenState extends State<LinkEventScreen> {
  static const _none = '';
  String? _selected;

  void _save(AppStore store, TicketDraft draft, List<EventMatch> matches) {
    final match = matches.where((m) => m.event.id == _selected).firstOrNull;
    store.saveDraft(draft, event: match?.event);
    final messenger = ScaffoldMessenger.of(context);
    context.go('/carteira');
    messenger.showSnackBar(
      const SnackBar(content: Text('Bilhete guardado na carteira.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final draft = store.importDraft;
    if (draft == null) {
      // Opened directly: nothing to link yet.
      return AppPage(
        title: 'Associar evento',
        children: [
          AppEmptyState(
            icon: Icons.confirmation_number_outlined,
            message: 'Começa por importar um bilhete',
            actionLabel: 'Importar',
            onAction: () => context.go('/importar'),
          ),
        ],
      );
    }

    final matches = store.matchesFor(draft);
    final selected =
        _selected ??
        matches.where((m) => m.strong).firstOrNull?.event.id ??
        _none;

    return AppPage(
      title: 'Associar evento',
      maxWidth: 560,
      children: [
        const AppStepHeading(
          step: 3,
          totalSteps: 3,
          title: 'Qual destes eventos é o do teu bilhete?',
          subtitle:
              'Associa o bilhete ao evento para receberes alterações e '
              'alertas.',
        ),
        for (final match in matches)
          AppRadioCard(
            title: match.event.name,
            subtitle:
                '${formatWeekdayDate(match.event.start)} · '
                '${formatTime(match.event.start)} · ${match.event.place}',
            selected: selected == match.event.id,
            onTap: () => setState(() => _selected = match.event.id),
            leading: EventThumbnail(gradient: match.event.art.gradient),
            footer: AppTag(
              label: match.strong
                  ? 'Correspondência alta'
                  : 'Correspondência baixa',
              tone: match.strong ? AppTagTone.success : AppTagTone.neutral,
            ),
          ),
        AppRadioCard(
          title: 'Nenhum destes',
          subtitle:
              'Não recebes alterações oficiais deste evento. Podes '
              'associá-lo mais tarde.',
          selected: selected == _none,
          onTap: () => setState(() => _selected = _none),
        ),
        AppButton(
          label: selected == _none
              ? 'Guardar sem associar'
              : 'Associar e guardar',
          onPressed: () {
            _selected = selected;
            _save(store, draft, matches);
          },
        ),
        AppButton.link(
          label: 'Procurar outro evento',
          onPressed: () => context.go('/eventos'),
        ),
      ],
    );
  }
}
