import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';
import 'account_layout.dart';

/// C3 · Conta: apagar conta e dados. The participant types APAGAR
/// and the password before the button is enabled.
class DeleteAccountScreen extends StatefulWidget {
  const DeleteAccountScreen({super.key});

  static const confirmationWord = 'APAGAR';

  @override
  State<DeleteAccountScreen> createState() => _DeleteAccountScreenState();
}

class _DeleteAccountScreenState extends State<DeleteAccountScreen> {
  final _confirmation = TextEditingController();
  final _password = TextEditingController();

  @override
  void initState() {
    super.initState();
    _confirmation.addListener(() => setState(() {}));
    _password.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _confirmation.dispose();
    _password.dispose();
    super.dispose();
  }

  bool get _confirmed =>
      _confirmation.text.trim() == DeleteAccountScreen.confirmationWord;

  void _delete(AppStore store) {
    store.deleteAccount();
    final messenger = ScaffoldMessenger.of(context);
    context.go('/entrar');
    messenger.showSnackBar(
      const SnackBar(content: Text('A tua conta foi apagada.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final tickets = store.tickets.length;
    final publicEvents = store.publicEvents.length;

    Widget item(IconData icon, String text) => Row(
      spacing: 10,
      children: [
        Icon(icon, size: 18, color: AppColors.danger),
        Expanded(
          child: Text(
            text,
            style: AppTextStyles.subhead.copyWith(fontWeight: FontWeight.w500),
          ),
        ),
      ],
    );

    final children = <Widget>[
      Container(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.lg,
          vertical: AppSpacing.md,
        ),
        decoration: BoxDecoration(
          color: AppColors.dangerSoft,
          borderRadius: const BorderRadius.all(AppRadii.panel),
          border: Border.all(color: AppColors.dangerBorder),
        ),
        child: Row(
          spacing: AppSpacing.md,
          children: [
            const Icon(
              Icons.warning_amber_rounded,
              size: 22,
              color: AppColors.danger,
            ),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                spacing: 2,
                children: [
                  Text(
                    'Esta ação não pode ser desfeita',
                    style: AppTextStyles.bodyStrong,
                  ),
                  Text(
                    'Apagamos a tua conta e os teus dados pessoais.',
                    style: AppTextStyles.footnote.copyWith(
                      color: AppColors.ink,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      AppPanel(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        spacing: 10,
        title: 'O que vai ser apagado',
        children: [
          item(
            Icons.confirmation_number_outlined,
            tickets == 1
                ? '1 bilhete e o respetivo código de entrada'
                : '$tickets bilhetes e os respetivos códigos de entrada',
          ),
          item(
            Icons.calendar_today_outlined,
            publicEvents == 1
                ? '1 evento público a que estás ligado'
                : '$publicEvents eventos públicos a que estás ligado',
          ),
          item(
            Icons.notifications_none,
            'Preferências e histórico de notificações',
          ),
          item(Icons.key_outlined, 'Conta e dados de acesso'),
        ],
      ),
      AppPanel(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        children: [
          Row(
            spacing: 10,
            children: [
              const Icon(
                Icons.flag_outlined,
                size: 18,
                color: AppColors.inkSecondary,
              ),
              Expanded(
                child: Text(
                  'Os reportes que fizeste ficam anónimos, sem ligação à tua '
                  'conta.',
                  style: AppTextStyles.label.copyWith(
                    fontWeight: FontWeight.w400,
                    height: 1.4,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
      Align(
        alignment: Alignment.centerLeft,
        child: AppInlineLink(
          label: 'Descarregar os meus dados antes de apagar',
          icon: Icons.download,
          onPressed: () => showComingSoon(context),
        ),
      ),
      AppPanel(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        spacing: AppSpacing.md,
        children: [
          AppTextField(
            label:
                'Escreve ${DeleteAccountScreen.confirmationWord} para confirmar',
            controller: _confirmation,
            textInputAction: TextInputAction.next,
            suffix: _confirmed
                ? const Icon(Icons.check, size: 18, color: AppColors.success)
                : null,
          ),
          AppTextField(
            label: 'Palavra-passe',
            controller: _password,
            prefixIcon: Icons.lock_outline,
            obscureText: true,
            autofillHints: const [AutofillHints.password],
          ),
        ],
      ),
      AppButton.danger(
        label: 'Apagar conta definitivamente',
        icon: Icons.delete_outline,
        onPressed: _confirmed && _password.text.isNotEmpty
            ? () => _delete(store)
            : null,
      ),
    ];

    return AppPage(
      title: 'Apagar conta',
      leadingLabel: 'Conta',
      section: Sections.account,
      desktopBody: AccountDesktopFrame(
        section: AccountSection.privacy,
        title: 'Apagar conta',
        children: children,
      ),
      children: children,
    );
  }
}
