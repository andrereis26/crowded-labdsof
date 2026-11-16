import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../../data/app_store.dart';
import '../../data/connections_api.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';

/// P21 · Código de acesso: joins a public event by scanning the QR code on
/// its posters or typing its access code. An unknown code reveals
/// no event.
class AccessCodeScreen extends StatefulWidget {
  const AccessCodeScreen({super.key});

  @override
  State<AccessCodeScreen> createState() => _AccessCodeScreenState();
}

class _AccessCodeScreenState extends State<AccessCodeScreen> {
  final _code = TextEditingController();
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  /// A QR code read by the camera: the poster's QR code holds the access
  /// code.
  void _onScanned(String value) {
    if (!mounted || _busy || _code.text == value) return;
    _code.text = value;
    _submit();
  }

  Future<void> _submit() async {
    if (_busy) return;
    if (_code.text.trim().isEmpty) {
      setState(() => _error = 'Escreve o código de acesso.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    String? error;
    String? eventId;
    try {
      eventId = await AppStore.read(context).joinWithAccessCode(_code.text);
      if (eventId == null) {
        error =
            'Código não encontrado. Confirma o código no cartaz ou junto da '
            'organização.';
      }
    } on ConnectionsApiException catch (e) {
      error = e.sessionEnded
          ? 'A tua sessão terminou. Entra outra vez.'
          : 'Não foi possível abrir o evento. Tenta outra vez.';
    }
    if (!mounted) return;
    setState(() {
      _busy = false;
      _error = error;
    });
    if (eventId != null) context.go('/event/$eventId');
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
          _QrViewfinder(onCode: _onScanned),
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
        AppButton(
          label: _busy ? 'A abrir o evento' : 'Abrir evento',
          // Disabled while Core answers, so a double tap or a QR code read
          // twice sends one request.
          onPressed: _busy ? null : _submit,
        ),
      ],
    );
  }
}

/// Builds the camera preview that reads QR codes and calls `onCode` with
/// the text of each one. Widget tests replace it, since they have no camera.
typedef QrScannerBuilder = Widget Function(
  BuildContext context,
  ValueChanged<String> onCode,
);

QrScannerBuilder qrScannerBuilder = _cameraScanner;

Widget _cameraScanner(BuildContext context, ValueChanged<String> onCode) =>
    MobileScanner(
      controller: MobileScannerController(
        formats: const [BarcodeFormat.qrCode],
      ),
      onDetect: (capture) {
        final value = capture.barcodes.firstOrNull?.rawValue;
        if (value != null && value.isNotEmpty) onCode(value);
      },
      // The frame shows only while the camera is running.
      overlayBuilder: (context, constraints) => const _ScanFrame(),
      errorBuilder: (context, error) => Center(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            spacing: AppSpacing.md,
            children: [
              const Icon(Icons.no_photography_outlined, color: Colors.white),
              Text(
                'Não foi possível usar a câmara. Escreve o código abaixo.',
                textAlign: TextAlign.center,
                style: AppTextStyles.footnote.copyWith(color: Colors.white),
              ),
            ],
          ),
        ),
      ),
    );

/// Frame and hint drawn over the camera preview.
class _ScanFrame extends StatelessWidget {
  const _ScanFrame();

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        spacing: AppSpacing.md,
        children: [
          Container(
            width: 150,
            height: 150,
            decoration: BoxDecoration(
              border: Border.all(color: Colors.white, width: 3),
              borderRadius: const BorderRadius.all(AppRadii.button),
            ),
          ),
          ExcludeSemantics(
            child: Text(
              'Aponta a câmara ao código QR do cartaz',
              style: AppTextStyles.footnote.copyWith(
                color: Colors.white,
                shadows: const [Shadow(blurRadius: 4)],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Camera preview that reads the QR code on the poster (P21).
class _QrViewfinder extends StatelessWidget {
  const _QrViewfinder({required this.onCode});

  final ValueChanged<String> onCode;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Câmara para ler o código QR do cartaz',
      child: ClipRRect(
        borderRadius: const BorderRadius.all(AppRadii.card),
        child: Container(
          height: 240,
          color: AppColors.ink,
          child: qrScannerBuilder(context, onCode),
        ),
      ),
    );
  }
}
