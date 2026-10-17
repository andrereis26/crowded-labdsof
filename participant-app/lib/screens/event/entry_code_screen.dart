import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// On the web the code shows for two minutes after the password (P19).
const _webRevealSeconds = 120;

/// P19 · Código de entrada (#91). The app shows it straight away, also
/// offline; the website asks for the password first and hides it again
/// after two minutes.
class EntryCodeScreen extends StatefulWidget {
  const EntryCodeScreen({super.key, required this.eventId});

  final String eventId;

  @override
  State<EntryCodeScreen> createState() => _EntryCodeScreenState();
}

class _EntryCodeScreenState extends State<EntryCodeScreen> {
  final _password = TextEditingController();
  Timer? _timer;
  int _secondsLeft = 0;
  String? _passwordError;

  @override
  void dispose() {
    _timer?.cancel();
    _password.dispose();
    super.dispose();
  }

  void _reveal() {
    if (_password.text.isEmpty) {
      setState(() => _passwordError = 'Introduz a tua palavra-passe.');
      return;
    }
    _password.clear();
    _timer?.cancel();
    setState(() {
      _passwordError = null;
      _secondsLeft = _webRevealSeconds;
    });
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      setState(() => _secondsLeft--);
      if (_secondsLeft <= 0) timer.cancel();
    });
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final ticket = store.ticketFor(widget.eventId);
    if (ticket == null) {
      return AppPage(
        leadingLabel: 'Fechar',
        showBackChevron: false,
        children: [
          AppErrorState(
            message: 'Não tens bilhete para este evento',
            retryLabel: 'Voltar à carteira',
            onRetry: () => context.go('/carteira'),
          ),
        ],
      );
    }

    if (!isDesktopLayout(context)) {
      return AppPage(
        title: 'Código de entrada',
        leadingLabel: 'Fechar',
        showBackChevron: false,
        background: AppColors.surface,
        maxWidth: 420,
        children: [
          _EntryCodeContent(
            ticket: ticket,
            holder: store.userName,
            now: store.now,
          ),
        ],
      );
    }

    final revealed = _secondsLeft > 0 || !ticket.hasEntryCode;
    final minutes = _secondsLeft ~/ 60;
    final seconds = (_secondsLeft % 60).toString().padLeft(2, '0');
    return Scaffold(
      appBar: const CrowdedWebHeader(currentIndex: Sections.wallet),
      body: ListView(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.screen,
          vertical: 40,
        ),
        children: [
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 560),
              child: AppFloatingCard(
                padding: const EdgeInsets.all(36),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  spacing: AppSpacing.xl,
                  children: [
                    Row(
                      spacing: AppSpacing.sm,
                      children: [
                        Expanded(
                          child: Wrap(
                            spacing: AppSpacing.sm,
                            runSpacing: AppSpacing.xs,
                            crossAxisAlignment: WrapCrossAlignment.center,
                            children: [
                              Text(
                                'Código de entrada',
                                style: AppTextStyles.bodyStrong.copyWith(
                                  color: AppColors.inkSecondary,
                                ),
                              ),
                              if (_secondsLeft > 0)
                                AppTag(
                                  label:
                                      'Confirmado · esconde-se em '
                                      '$minutes:$seconds',
                                  tone: AppTagTone.success,
                                  icon: Icons.lock_outline,
                                ),
                            ],
                          ),
                        ),
                        AppCircleButton(
                          icon: Icons.close,
                          semanticLabel: 'Fechar',
                          size: 32,
                          onPressed: () => AppPage.goBack(context),
                        ),
                      ],
                    ),
                    if (revealed)
                      _EntryCodeContent(
                        ticket: ticket,
                        holder: store.userName,
                        now: store.now,
                        desktop: true,
                      )
                    else
                      _PasswordStep(
                        controller: _password,
                        errorText: _passwordError,
                        onSubmit: _reveal,
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
}

/// Asks for the password before showing the code on the website.
class _PasswordStep extends StatelessWidget {
  const _PasswordStep({
    required this.controller,
    required this.errorText,
    required this.onSubmit,
  });

  final TextEditingController controller;
  final String? errorText;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.xl,
      children: [
        const AppResultHeader(
          icon: Icons.lock_outline,
          tone: AppTagTone.info,
          title: 'Confirma que és tu',
          message:
              'No site, o código só aparece depois da palavra-passe e '
              'durante 2 minutos. Na app aparece sempre, mesmo sem rede.',
        ),
        AppTextField(
          label: 'Palavra-passe',
          controller: controller,
          obscureText: true,
          autofillHints: const [AutofillHints.password],
          textInputAction: TextInputAction.done,
          errorText: errorText,
          onSubmitted: (_) => onSubmit(),
        ),
        AppButton(
          label: 'Mostrar código',
          icon: Icons.qr_code_2,
          onPressed: onSubmit,
        ),
      ],
    );
  }
}

class _EntryCodeContent extends StatelessWidget {
  const _EntryCodeContent({
    required this.ticket,
    required this.holder,
    required this.now,
    this.desktop = false,
  });

  final Ticket ticket;
  final String holder;
  final DateTime now;
  final bool desktop;

  String get _origin {
    final label = ticket.origin.label;
    final phrase = label[0].toLowerCase() + label.substring(1);
    return ticket.origin == TicketOrigin.email ? '$phrase (.eml)' : phrase;
  }

  @override
  Widget build(BuildContext context) {
    final event = ticket.event;
    final imported = formatDayMonth(ticket.importedAt);
    final fields = [
      if (ticket.gate != null) ('PORTA', ticket.gate!),
      if (ticket.seat != null) ('LUGAR', ticket.seat!),
      ('TITULAR', holder),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.xl,
      children: [
        Column(
          spacing: 4,
          children: [
            Text(
              event.name,
              textAlign: TextAlign.center,
              style: AppTextStyles.title.copyWith(fontSize: desktop ? 26 : 24),
            ),
            Text(
              '${formatRelativeDaySentence(event.start, now)} · '
              '${formatTime(event.start)} · ${event.place}',
              textAlign: TextAlign.center,
              style: AppTextStyles.bodySecondary.copyWith(height: 1.2),
            ),
          ],
        ),
        if (ticket.hasEntryCode) ...[
          Center(
            child: Container(
              padding: const EdgeInsets.all(AppSpacing.xxl),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: const BorderRadius.all(Radius.circular(20)),
                border: Border.all(color: AppColors.border),
              ),
              child: EntryCodeImage(seed: ticket.id, size: desktop ? 240 : 250),
            ),
          ),
          if (ticket.codeReference != null)
            Text(
              'Ref. •••• ${ticket.codeReference}',
              textAlign: TextAlign.center,
              style: AppTextStyles.label.copyWith(
                color: AppColors.inkSecondary,
              ),
            ),
          Container(
            padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
            decoration: const BoxDecoration(
              border: Border.symmetric(
                horizontal: BorderSide(color: AppColors.border),
              ),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                for (final (label, value) in fields)
                  if (desktop)
                    Expanded(child: _Field(label, value, centered: true))
                  else
                    _Field(label, value),
              ],
            ),
          ),
          if (desktop)
            Row(
              spacing: 10,
              children: [
                const Icon(
                  Icons.smartphone,
                  size: 18,
                  color: AppColors.inkSecondary,
                ),
                Expanded(
                  child: Text(
                    'Para entrar, mostra este código no telemóvel.',
                    style: AppTextStyles.footnote,
                  ),
                ),
              ],
            ),
          _OriginNote(
            'Código da bilheteira · $_origin em $imported',
            strong: true,
          ),
        ] else ...[
          Container(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.xxl,
              vertical: 28,
            ),
            decoration: const BoxDecoration(
              color: AppColors.background,
              borderRadius: BorderRadius.all(Radius.circular(20)),
            ),
            child: Column(
              spacing: AppSpacing.md,
              children: [
                const Icon(
                  Icons.qr_code_2,
                  size: 48,
                  color: AppColors.inkSecondary,
                ),
                Text(
                  'Código de entrada não disponível',
                  textAlign: TextAlign.center,
                  style: AppTextStyles.buttonLarge,
                ),
                Text(
                  _missingCodeMessage(ticket.origin),
                  textAlign: TextAlign.center,
                  style: AppTextStyles.bodySecondary.copyWith(height: 1.2),
                ),
              ],
            ),
          ),
          AppButton(
            label: 'Carregar captura do código',
            icon: Icons.file_upload_outlined,
            onPressed: () => showComingSoon(context),
          ),
          AppButton.link(
            label: 'Como encontrar o código no email ou na app da bilheteira?',
            onPressed: () => showComingSoon(context),
          ),
          _OriginNote('Bilhete $_origin em $imported · sem código'),
        ],
      ],
    );
  }

  static String _missingCodeMessage(TicketOrigin origin) {
    final where = switch (origin) {
      TicketOrigin.pdf => 'no PDF',
      TicketOrigin.email => 'no email',
      TicketOrigin.manual => 'nos dados',
    };
    return 'Não encontrámos um código $where que importaste. Usa o bilhete '
        'original ou carrega uma captura de ecrã do código.';
  }
}

class _Field extends StatelessWidget {
  const _Field(this.label, this.value, {this.centered = false});

  final String label;
  final String value;
  final bool centered;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: centered
          ? CrossAxisAlignment.center
          : CrossAxisAlignment.start,
      spacing: 2,
      children: [
        Text(
          label,
          style: AppTextStyles.tabLabel.copyWith(
            color: AppColors.inkSecondary,
            letterSpacing: 0.6,
          ),
        ),
        Text(value, style: AppTextStyles.bodyLarge),
      ],
    );
  }
}

/// Grey note on where the code came from.
class _OriginNote extends StatelessWidget {
  const _OriginNote(this.text, {this.strong = false});

  final String text;
  final bool strong;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.lg,
        vertical: AppSpacing.md,
      ),
      decoration: const BoxDecoration(
        color: AppColors.background,
        borderRadius: BorderRadius.all(AppRadii.button),
      ),
      child: Text(
        text,
        style: strong
            ? AppTextStyles.caption.copyWith(
                color: AppColors.ink,
                fontWeight: FontWeight.w600,
                height: 1.2,
              )
            : AppTextStyles.caption.copyWith(height: 1.2),
      ),
    );
  }
}

/// Square entry code drawn from the ticket. A stand-in with the look of a
/// QR code until the ticketing simulator supplies the real code.
class EntryCodeImage extends StatelessWidget {
  const EntryCodeImage({super.key, required this.seed, this.size = 250});

  final String seed;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Código de entrada',
      image: true,
      child: CustomPaint(size: Size.square(size), painter: _CodePainter(seed)),
    );
  }
}

class _CodePainter extends CustomPainter {
  _CodePainter(this.seed);

  final String seed;

  static const _modules = 25;

  @override
  void paint(Canvas canvas, Size size) {
    final cell = size.width / _modules;
    final paint = Paint()..color = AppColors.ink;
    final random = Random(
      seed.codeUnits.fold<int>(7, (h, c) => (h * 31 + c) & 0x7fffffff),
    );

    bool inFinder(int x, int y) {
      bool near(int fx, int fy) =>
          x >= fx - 1 && x <= fx + 7 && y >= fy - 1 && y <= fy + 7;
      return near(0, 0) || near(_modules - 7, 0) || near(0, _modules - 7);
    }

    for (var y = 0; y < _modules; y++) {
      for (var x = 0; x < _modules; x++) {
        if (inFinder(x, y) || random.nextDouble() > 0.45) continue;
        canvas.drawRect(Rect.fromLTWH(x * cell, y * cell, cell, cell), paint);
      }
    }

    for (final (fx, fy) in [(0, 0), (_modules - 7, 0), (0, _modules - 7)]) {
      final origin = Offset(fx * cell, fy * cell);
      canvas
        ..drawRect(origin & Size.square(cell * 7), paint)
        ..drawRect(
          (origin + Offset(cell, cell)) & Size.square(cell * 5),
          Paint()..color = AppColors.surface,
        )
        ..drawRect(
          (origin + Offset(cell * 2, cell * 2)) & Size.square(cell * 3),
          paint,
        );
    }
  }

  @override
  bool shouldRepaint(_CodePainter oldDelegate) => oldDelegate.seed != seed;
}
