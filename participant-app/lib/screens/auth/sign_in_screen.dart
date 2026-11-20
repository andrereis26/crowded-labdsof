import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/password_strength.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';

enum _Mode { signIn, register }

/// C1 · Entrar / registar.
class SignInScreen extends StatefulWidget {
  const SignInScreen({super.key});

  @override
  State<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends State<SignInScreen> {
  _Mode _mode = _Mode.signIn;
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _confirmation = TextEditingController();
  bool _showPassword = false;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _confirmation.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    final store = AppStore.of(context);
    setState(() {
      _busy = true;
      _error = null;
    });
    final error = _mode == _Mode.signIn
        ? await store.signIn(email: _email.text, password: _password.text)
        : await store.register(
            email: _email.text,
            password: _password.text,
            confirmation: _confirmation.text,
          );
    if (!mounted) return;
    setState(() {
      _busy = false;
      _error = error;
    });
    if (error == null) context.go('/events');
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final form = _Form(
      mode: _mode,
      email: _email,
      password: _password,
      confirmation: _confirmation,
      showPassword: _showPassword,
      busy: _busy,
      error: _error ?? store.organizationNotice,
      onSignOut: store.organizationNotice == null ? null : store.signOut,
      onModeChanged: (m) => setState(() {
        _mode = m;
        _error = null;
      }),
      onTogglePassword: () => setState(() => _showPassword = !_showPassword),
      onSubmit: _submit,
    );

    if (isDesktopLayout(context)) {
      return Scaffold(
        backgroundColor: AppColors.surface,
        body: Row(
          children: [
            const Expanded(child: _Hero(large: true)),
            Expanded(
              child: Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(40),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 400),
                    child: form,
                  ),
                ),
              ),
            ),
          ],
        ),
      );
    }

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light,
      child: Scaffold(
        backgroundColor: AppColors.surface,
        body: ListView(
          padding: EdgeInsets.zero,
          children: [
            const SizedBox(height: 300, child: _Hero()),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.screen,
                AppSpacing.xl,
                AppSpacing.screen,
                AppSpacing.xxl,
              ),
              child: form,
            ),
          ],
        ),
      ),
    );
  }
}

/// Dark picture with the logo and the tagline.
class _Hero extends StatelessWidget {
  const _Hero({this.large = false});

  final bool large;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(gradient: AppColors.walletFallbackPhoto),
      child: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0x80000000), Color(0xD9000000)],
          ),
        ),
        child: SafeArea(
          bottom: false,
          child: Padding(
            padding: EdgeInsets.all(large ? 48 : AppSpacing.xxl),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  spacing: AppSpacing.sm,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(AppSpacing.xs),
                      decoration: const BoxDecoration(
                        color: AppColors.surface,
                        borderRadius: BorderRadius.all(AppRadii.sm),
                      ),
                      child: const Icon(
                        Icons.people_outline,
                        size: 18,
                        color: AppColors.ink,
                      ),
                    ),
                    Text(
                      'Crowded',
                      style: AppTextStyles.headline.copyWith(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.36,
                        color: Colors.white,
                      ),
                    ),
                  ],
                ),
                const Spacer(),
                Semantics(
                  header: true,
                  child: Text(
                    'Sabe o que se passa nos teus eventos',
                    style: AppTextStyles.largeTitle.copyWith(
                      fontSize: large ? 40 : 28,
                      height: 1.15,
                      color: Colors.white,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Form extends StatelessWidget {
  const _Form({
    required this.mode,
    required this.email,
    required this.password,
    required this.confirmation,
    required this.showPassword,
    required this.busy,
    required this.error,
    this.onSignOut,
    required this.onModeChanged,
    required this.onTogglePassword,
    required this.onSubmit,
  });

  final _Mode mode;
  final TextEditingController email;
  final TextEditingController password;
  final TextEditingController confirmation;
  final bool showPassword;
  final bool busy;
  final String? error;

  /// Ends the organization's session the browser holds, when there is one.
  final VoidCallback? onSignOut;
  final ValueChanged<_Mode> onModeChanged;
  final VoidCallback onTogglePassword;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    final registering = mode == _Mode.register;
    return AutofillGroup(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: AppSpacing.lg,
        children: [
          AppSegmentedControl<_Mode>(
            selected: mode,
            onChanged: onModeChanged,
            segments: const [
              AppSegment(value: _Mode.signIn, label: 'Entrar'),
              AppSegment(value: _Mode.register, label: 'Criar conta'),
            ],
          ),

          AppTextField(
            label: 'Email',
            controller: email,
            prefixIcon: Icons.mail_outline,
            keyboardType: TextInputType.emailAddress,
            textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.email],
          ),
          AppTextField(
            label: 'Palavra-passe',
            controller: password,
            prefixIcon: Icons.lock_outline,
            obscureText: !showPassword,
            autofillHints: [
              registering ? AutofillHints.newPassword : AutofillHints.password,
            ],
            // Registering, Enter moves on to the confirmation.
            textInputAction: registering
                ? TextInputAction.next
                : TextInputAction.done,
            onSubmitted: registering ? null : (_) => onSubmit(),
            suffix: IconButton(
              tooltip: showPassword
                  ? 'Esconder palavra-passe'
                  : 'Mostrar palavra-passe',
              icon: Icon(
                showPassword
                    ? Icons.visibility_off_outlined
                    : Icons.visibility_outlined,
                size: 18,
                color: AppColors.inkSecondary,
              ),
              onPressed: onTogglePassword,
            ),
          ),
          if (registering) _StrengthMeter(password: password),
          if (registering)
            AppTextField(
              label: 'Confirmar palavra-passe',
              controller: confirmation,
              prefixIcon: Icons.lock_outline,
              obscureText: !showPassword,
              autofillHints: const [AutofillHints.newPassword],
              onSubmitted: (_) => onSubmit(),
            ),
          if (error != null)
            Semantics(
              liveRegion: true,
              child: Text(
                error!,
                style: AppTextStyles.caption.copyWith(color: AppColors.danger),
              ),
            ),
          if (onSignOut != null)
            Align(
              alignment: Alignment.centerLeft,
              child: AppInlineLink(
                label: 'Terminar sessão',
                onPressed: onSignOut!,
              ),
            ),
          if (!registering)
            Align(
              alignment: Alignment.centerLeft,
              child: AppInlineLink(
                label: 'Esqueci-me da palavra-passe',
                onPressed: () => showComingSoon(context),
              ),
            ),
          AppButton(
            label: registering ? 'Criar conta' : 'Entrar',
            // Disabled while Core answers, so a double tap sends one request.
            onPressed: busy ? null : onSubmit,
          ),
          Wrap(
            alignment: WrapAlignment.center,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: AppSpacing.xs,
            children: [
              Text('Organizas eventos?', style: AppTextStyles.bodySecondary),
              AppInlineLink(
                label: 'Fala com a nossa equipa',
                onPressed: () => showComingSoon(context),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// The password's strength, five bars and a label shown once the participant
/// starts typing, and the list of requirements, each ticked once it is met.
class _StrengthMeter extends StatelessWidget {
  const _StrengthMeter({required this.password});

  final TextEditingController password;

  static Color _color(PasswordStrength strength) => switch (strength) {
    PasswordStrength.veryWeak => AppColors.danger,
    PasswordStrength.weak => AppColors.orange,
    PasswordStrength.moderate => AppColors.warning,
    PasswordStrength.strong => AppColors.success,
    PasswordStrength.veryStrong => AppColors.walletStatus,
  };

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: password,
      builder: (context, value, _) {
        final typed = value.text.isNotEmpty;
        final strength = PasswordStrength.of(value.text);
        final missing = PasswordStrength.missing(value.text);
        final color = _color(strength);
        return Semantics(
          container: true,
          label: [
            if (typed) 'Força da palavra-passe: ${strength.label}.',
            if (missing.isEmpty)
              'Cumpre todos os requisitos.'
            else
              'Requisitos em falta: ${_join(missing)}.',
          ].join(' '),
          child: ExcludeSemantics(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: AppSpacing.xs,
              children: [
                if (typed) ...[
                  Row(
                    spacing: AppSpacing.xs,
                    children: [
                      for (final level in PasswordStrength.values)
                        Expanded(
                          child: Container(
                            height: 4,
                            decoration: BoxDecoration(
                              color: level.index <= strength.index
                                  ? color
                                  : AppColors.border,
                              borderRadius: const BorderRadius.all(
                                Radius.circular(2),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                  Text(
                    'Força: ${strength.label}',
                    style: AppTextStyles.caption.copyWith(
                      color: color,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
                Text(
                  'A palavra-passe deve conter:',
                  style: AppTextStyles.caption,
                ),
                for (final requirement in PasswordRequirement.values)
                  _RequirementRow(
                    label: requirement.label,
                    met: !missing.contains(requirement),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  /// "a, b e c", as Portuguese lists are written.
  static String _join(List<PasswordRequirement> items) {
    final labels = [
      for (final item in items)
        item.label[0].toLowerCase() + item.label.substring(1),
    ];
    if (labels.length == 1) return labels.first;
    final head = labels.sublist(0, labels.length - 1).join(', ');
    return '$head e ${labels.last}';
  }
}

/// One requirement of the password, ticked once it is met.
class _RequirementRow extends StatelessWidget {
  const _RequirementRow({required this.label, required this.met});

  final String label;
  final bool met;

  @override
  Widget build(BuildContext context) {
    final color = met ? AppColors.success : AppColors.inkSecondary;
    return Row(
      spacing: AppSpacing.xs,
      children: [
        Icon(
          met ? Icons.check_circle : Icons.radio_button_unchecked,
          size: 14,
          color: color,
        ),
        Expanded(
          child: Text(
            label,
            style: AppTextStyles.caption.copyWith(color: color),
          ),
        ),
      ],
    );
  }
}
