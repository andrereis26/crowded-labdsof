import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';
import 'import_screen.dart';

void _toMyEvents(BuildContext context) => context.go('/eventos');

void _uploadAnother(BuildContext context) => context.go('/importar');

/// P2a · Bilhete adicionado: the event code matched a registered event, so
/// the ticket was added without a review step (FR-003). Shows the event
/// data the organization registered.
class TicketAddedScreen extends StatelessWidget {
  const TicketAddedScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final ticket = store.lastImported;
    if (ticket == null) {
      return AppPage(
        children: [
          AppErrorState(
            message: 'Não há nenhum bilhete importado.',
            retryLabel: 'Importar bilhete',
            onRetry: () => _uploadAnother(context),
          ),
        ],
      );
    }
    final event = ticket.event;
    final hours = event.end == null
        ? formatTime(event.start)
        : '${formatTime(event.start)} · fim às ${formatTime(event.end!)}';
    final ticketDetails = [
      if (ticket.gate != null) 'Porta ${ticket.gate}',
      if (ticket.seat != null) 'Lugar ${ticket.seat}',
      'código QR guardado',
    ].join(' · ');

    return AppPage(
      leadingLabel: 'Fechar',
      showBackChevron: false,
      onLeading: () => _toMyEvents(context),
      maxWidth: 560,
      children: [
        const SizedBox(height: AppSpacing.md),
        AppResultHeader(
          icon: Icons.calendar_today_outlined,
          title: 'Adicionaste o evento ${event.name}',
          message: 'Está agora nos teus eventos.',
        ),
        AppPanel(
          color: AppColors.surface,
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.lg,
            vertical: AppSpacing.sm,
          ),
          spacing: 0,
          children: [
            _DetailRow('Data', formatWeekdayDate(event.start)),
            _DetailRow('Hora', hours),
            if (event.doorsOpen != null)
              _DetailRow('Portas', formatTime(event.doorsOpen!)),
            _DetailRow('Local', event.place, last: true),
          ],
        ),
        AppListGroup(
          children: [
            AppListTile(
              icon: Icons.confirmation_number_outlined,
              title: 'O teu bilhete',
              subtitle: ticketDetails,
              showChevron: false,
            ),
          ],
        ),
        Text(
          'Fonte: organização · dados do evento registado',
          style: AppTextStyles.source,
        ),
        AppButton(
          label: 'Ver evento',
          onPressed: () => context.go('/evento/${event.id}'),
        ),
        AppButton.secondary(
          label: 'Não é o teu evento? Remover',
          onPressed: () {
            AppStore.read(context).undoImport();
            _toMyEvents(context);
          },
        ),
      ],
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow(this.label, this.value, {this.last = false});

  final String label;
  final String value;
  final bool last;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
      decoration: BoxDecoration(
        border: last
            ? null
            : const Border(bottom: BorderSide(color: AppColors.border)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 72,
            child: Text(label, style: AppTextStyles.footnote),
          ),
          Expanded(child: Text(value, style: AppTextStyles.bodyStrong)),
        ],
      ),
    );
  }
}

/// P2b · Código do evento: the QR code was read but the event code was not,
/// or the code that was read matches no event. The participant types the
/// code printed on the ticket (FR-004); it is pre-filled with what was read.
class EventCodeScreen extends StatefulWidget {
  const EventCodeScreen({super.key});

  @override
  State<EventCodeScreen> createState() => _EventCodeScreenState();
}

class _EventCodeScreenState extends State<EventCodeScreen> {
  late final TextEditingController _code;
  String? _error;

  @override
  void initState() {
    super.initState();
    _code = TextEditingController(
      text: AppStore.read(context).pendingImport?.readCode ?? '',
    );
  }

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  void _submit() {
    if (_code.text.trim().isEmpty) {
      setState(() => _error = 'Escreve o código do evento.');
      return;
    }
    final outcome = AppStore.read(context).submitEventCode(_code.text);
    context.pushReplacement(ImportScreen.pathFor(outcome));
  }

  @override
  Widget build(BuildContext context) {
    final pending = AppStore.of(context).pendingImport;
    if (pending == null) {
      return AppPage(
        children: [
          AppErrorState(
            message: 'Não há nenhum bilhete a importar.',
            retryLabel: 'Importar bilhete',
            onRetry: () => _uploadAnother(context),
          ),
        ],
      );
    }
    final read = pending.readCode;
    return AppPage(
      title: 'Importar bilhete',
      leadingLabel: 'Cancelar',
      showBackChevron: false,
      onLeading: () => _toMyEvents(context),
      maxWidth: 560,
      children: [
        Semantics(
          header: true,
          child: Text(
            read == null
                ? 'Não conseguimos associar o bilhete a um evento'
                : 'O código lido não corresponde a nenhum evento',
            style: AppTextStyles.title,
          ),
        ),
        Text(
          read == null
              ? 'Escreve o código do evento impresso no bilhete. Serve só '
                    'para completar este bilhete.'
              : 'Preenchemos o código com o que lemos no bilhete. Compara-o '
                    'com o impresso e corrige o que for preciso.',
          style: AppTextStyles.bodySecondary.copyWith(fontSize: 15),
        ),
        AppListGroup(
          children: [
            AppListTile(
              icon: pending.origin == TicketOrigin.image
                  ? Icons.image_outlined
                  : Icons.description_outlined,
              iconColor: AppColors.danger,
              title: pending.fileName,
              subtitle: read == null
                  ? 'Código QR lido · falta o código do evento'
                  : 'Lido: $read · sem evento',
              showChevron: false,
            ),
          ],
        ),
        AppTextField(
          label: 'Código do evento',
          controller: _code,
          prefixIcon: Icons.tag,
          textInputAction: TextInputAction.done,
          errorText: _error,
          borderColor: read != null && _code.text == read
              ? AppColors.attention
              : null,
          onChanged: (_) => setState(() => _error = null),
          onSubmitted: (_) => _submit(),
        ),
        if (read != null && _error == null)
          Text(
            'Lido do bilhete. Confirma letras e números parecidos, como O e 0.',
            style: AppTextStyles.footnote,
          ),
        AppButton(label: 'Associar bilhete', onPressed: _submit),
        AppButton.link(
          label: 'Carregar outro ficheiro',
          onPressed: () => _uploadAnother(context),
        ),
      ],
    );
  }
}

/// P2b · errors: no QR code (FR-070), event not on Crowded (FR-083) or the
/// ticket is linked to another account (FR-084). Nothing is stored.
class ImportErrorScreen extends StatelessWidget {
  const ImportErrorScreen({super.key, required this.outcome});

  final ImportOutcome outcome;

  static ImportOutcome outcomeFor(String slug) => switch (slug) {
    'sem-qr' => ImportOutcome.noQrCode,
    'outra-conta' => ImportOutcome.linkedToAnotherAccount,
    _ => ImportOutcome.eventNotOnCrowded,
  };

  @override
  Widget build(BuildContext context) {
    final (title, message, tone) = switch (outcome) {
      ImportOutcome.noQrCode => (
        'Não encontrámos o código QR do bilhete',
        'Sem o código QR, o bilhete não é guardado. Carrega o PDF original '
            'ou uma imagem nítida com o código QR visível.',
        AppTagTone.warning,
      ),
      ImportOutcome.linkedToAnotherAccount => (
        'Este bilhete já está ligado a outra conta',
        'Cada bilhete só pode estar ligado a uma conta. Se o bilhete é teu, '
            'contacta a organização do evento.',
        AppTagTone.danger,
      ),
      _ => (
        'Este evento ainda não usa o Crowded',
        'Não guardámos o bilhete nem o ficheiro. Quando a organização aderir '
            'ao Crowded, podes importá-lo de novo.',
        AppTagTone.danger,
      ),
    };
    final uploadFirst = outcome == ImportOutcome.noQrCode;
    final upload = uploadFirst
        ? AppButton(
            label: 'Carregar outro ficheiro',
            onPressed: () => _uploadAnother(context),
          )
        : AppButton.secondary(
            label: 'Carregar outro ficheiro',
            onPressed: () => _uploadAnother(context),
          );
    final back = uploadFirst
        ? AppButton.secondary(
            label: 'Voltar aos meus eventos',
            onPressed: () => _toMyEvents(context),
          )
        : AppButton(
            label: 'Voltar aos meus eventos',
            onPressed: () => _toMyEvents(context),
          );
    return AppPage(
      leadingLabel: 'Fechar',
      showBackChevron: false,
      onLeading: () => _toMyEvents(context),
      background: AppColors.surface,
      maxWidth: 560,
      children: [
        const SizedBox(height: AppSpacing.xl),
        AppResultHeader(
          icon: Icons.warning_amber_rounded,
          title: title,
          message: message,
          tone: tone,
        ),
        const SizedBox(height: AppSpacing.sm),
        ...uploadFirst ? [upload, back] : [back, upload],
      ],
    );
  }
}
