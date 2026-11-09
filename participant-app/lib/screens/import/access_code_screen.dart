import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';

/// P21 · Código de acesso: joins a public event by scanning the QR code on
/// its posters or typing its access code (FR-085). An unknown code reveals
/// no event.
class AccessCodeScreen extends StatefulWidget {
  const AccessCodeScreen({super.key});

  @override
  State<AccessCodeScreen> createState() => _AccessCodeScreenState();
}

class _AccessCodeScreenState extends State<AccessCodeScreen> {
  final _code = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  void _submit() {
    if (_code.text.trim().isEmpty) {
      setState(() => _error = 'Escreve o código de acesso.');
      return;
    }
    final event = AppStore.read(context).joinWithAccessCode(_code.text);
    if (event == null) {
      setState(
        () => _error =
            'Código não encontrado. Confirma o código no cartaz ou junto da '
            'organização.',
      );
      return;
    }
    context.go('/evento/${event.id}');
  }

  @override
  Widget build(BuildContext context) {
    return AppPage(
      title: 'Código de acesso',
      leadingLabel: 'Cancelar',
      showBackChevron: false,
      maxWidth: 560,
      children: [
        Semantics(
          header: true,
          child: Text('Ligar a um evento público', style: AppTextStyles.title),
        ),
        Text(
          'Lê o código QR do cartaz ou escreve o código de acesso que a '
          'organização divulgou.',
          style: AppTextStyles.bodySecondary.copyWith(fontSize: 15),
        ),
        if (!isDesktopLayout(context)) ...[
          const _QrViewfinder(),
          Text(
            'ou escreve o código',
            textAlign: TextAlign.center,
            style: AppTextStyles.footnote,
          ),
        ],
        AppTextField(
          label: 'Código de acesso',
          controller: _code,
          prefixIcon: Icons.tag,
          textInputAction: TextInputAction.done,
          errorText: _error,
          onChanged: (_) {
            if (_error != null) setState(() => _error = null);
          },
          onSubmitted: (_) => _submit(),
        ),
        AppButton(label: 'Abrir evento', onPressed: _submit),
      ],
    );
  }
}

/// Camera preview with a frame for the QR code. A placeholder until the
/// camera plugin is added.
class _QrViewfinder extends StatelessWidget {
  const _QrViewfinder();

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Câmara para ler o código QR do cartaz',
      child: Container(
        height: 220,
        decoration: const BoxDecoration(
          color: AppColors.ink,
          borderRadius: BorderRadius.all(AppRadii.card),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          spacing: AppSpacing.md,
          children: [
            Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.white, width: 3),
                borderRadius: const BorderRadius.all(AppRadii.button),
              ),
              child: const Icon(
                Icons.qr_code_2,
                size: 56,
                color: Colors.white54,
              ),
            ),
            ExcludeSemantics(
              child: Text(
                'Aponta a câmara ao código QR do cartaz',
                style: AppTextStyles.footnote.copyWith(color: Colors.white),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
