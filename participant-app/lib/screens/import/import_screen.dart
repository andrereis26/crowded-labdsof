import 'dart:typed_data';

import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../data/tickets_api.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';

/// A ticket file chosen by the participant.
typedef PickedFile = ({Uint8List bytes, String name});

/// Opens the system file picker for a ticket PDF, or returns null when the
/// participant cancels. Widget tests replace it, since they have no picker.
typedef TicketPdfPicker = Future<PickedFile?> Function();

TicketPdfPicker ticketPdfPicker = _pickPdf;

Future<PickedFile?> _pickPdf() async {
  final file = await openFile(
    acceptedTypeGroups: const [
      XTypeGroup(
        label: 'PDF',
        extensions: ['pdf'],
        mimeTypes: ['application/pdf'],
        uniformTypeIdentifiers: ['com.adobe.pdf'],
      ),
    ],
  );
  if (file == null) return null;
  return (bytes: await file.readAsBytes(), name: file.name);
}

/// P2 · Importar bilhete: from the ticket PDF or a screenshot or photo of
/// it. The QR code and the event code are read from the file; events
/// without tickets are joined with an access code (P21).
class ImportScreen extends StatefulWidget {
  const ImportScreen({super.key});

  /// Screen that shows [outcome]: P2a when the ticket was added, P2b
  /// otherwise.
  static String pathFor(ImportOutcome outcome) => switch (outcome) {
    ImportOutcome.added => '/import/added',
    ImportOutcome.needsEventCode => '/import/code',
    ImportOutcome.noQrCode => '/import/error/no-qr',
    ImportOutcome.eventNotOnCrowded => '/import/error/not-on-crowded',
    ImportOutcome.linkedToAnotherAccount => '/import/error/other-account',
    ImportOutcome.importExpired => '/import/error/expired',
  };

  @override
  State<ImportScreen> createState() => _ImportScreenState();
}

class _ImportScreenState extends State<ImportScreen> {
  bool _reading = false;

  Future<void> _importPdf() async {
    final file = await ticketPdfPicker();
    if (file == null || !mounted) return;
    setState(() => _reading = true);
    try {
      final outcome = await AppStore.read(context)
          .importPdf(file.bytes, file.name);
      if (mounted) context.push(ImportScreen.pathFor(outcome));
    } on TicketsApiException catch (e) {
      if (mounted) {
        showMessage(
          context,
          e.tooManyRequests
              ? 'Importaste demasiados bilhetes na última hora. '
                    'Tenta mais tarde.'
              : 'Não foi possível importar o bilhete. Tenta outra vez.',
        );
      }
    } finally {
      if (mounted) setState(() => _reading = false);
    }
  }

  void _importImage() {
    final outcome = AppStore.read(context).importImage();
    context.push(ImportScreen.pathFor(outcome));
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
        if (_reading)
          // A node of its own, so screen readers announce it alone.
          Semantics(
            container: true,
            child: const AppLoadingState(message: 'A ler o bilhete'),
          )
        else ...[
          AppNavigationCard(
            icon: Icons.description_outlined,
            title: 'PDF do bilhete',
            subtitle: 'O ficheiro que recebeste quando compraste o bilhete.',
            onTap: _importPdf,
          ),
          AppNavigationCard(
            icon: Icons.image_outlined,
            title: 'Captura de ecrã ou foto',
            subtitle: isDesktopLayout(context)
                ? 'PNG ou JPG, com o código QR visível.'
                : 'Uma imagem nítida, com o código QR visível.',
            onTap: _importImage,
          ),
        ],
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
