import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
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
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _showPassword = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  void _submit() {
    final store = AppStore.of(context);
    final error = _mode == _Mode.signIn
        ? store.signIn(email: _email.text, password: _password.text)
        : store.register(
            name: _name.text,
            email: _email.text,
            password: _password.text,
          );
    setState(() => _error = error);
    if (error == null) context.go('/carteira');
  }

  @override
  Widget build(BuildContext context) {
    final form = _Form(
      mode: _mode,
      name: _name,
      email: _email,
      password: _password,
      showPassword: _showPassword,
      error: _error,
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
    required this.name,
    required this.email,
    required this.password,
    required this.showPassword,
    required this.error,
    required this.onModeChanged,
    required this.onTogglePassword,
    required this.onSubmit,
  });

  final _Mode mode;
  final TextEditingController name;
  final TextEditingController email;
  final TextEditingController password;
  final bool showPassword;
  final String? error;
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
          if (registering)
            AppTextField(
              label: 'Nome',
              controller: name,
              prefixIcon: Icons.person_outline,
              textInputAction: TextInputAction.next,
              autofillHints: const [AutofillHints.name],
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
            hint: registering ? 'Pelo menos 8 caracteres' : null,
            obscureText: !showPassword,
            autofillHints: [
              registering ? AutofillHints.newPassword : AutofillHints.password,
            ],
            onSubmitted: (_) => onSubmit(),
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
          if (error != null)
            Semantics(
              liveRegion: true,
              child: Text(
                error!,
                style: AppTextStyles.caption.copyWith(color: AppColors.danger),
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
            onPressed: onSubmit,
          ),
          Wrap(
            alignment: WrapAlignment.center,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: AppSpacing.xs,
            children: [
              Text('És uma organização?', style: AppTextStyles.bodySecondary),
              AppInlineLink(
                label: 'Registar organização',
                onPressed: () => showComingSoon(context),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
