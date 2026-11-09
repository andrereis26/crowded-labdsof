import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../ui/ui.dart';
import '../shared/shell.dart';

/// Sections of the account area, shown as a side menu on desktop.
enum AccountSection {
  profile(
    'Perfil',
    Icons.person_outline,
    '/conta',
    'Nome, email e palavra-passe',
  ),
  notifications(
    'Notificações',
    Icons.notifications_none,
    '/conta/notificacoes',
    'Alterações, alertas e avisos',
  ),
  location('Localização', Icons.my_location, null, 'Permissão de localização'),
  contributions(
    'Contribuições',
    Icons.flag_outlined,
    '/conta/contribuicoes',
    'Os teus reportes e confirmações',
  ),
  privacy(
    'Privacidade e dados',
    Icons.shield_outlined,
    '/conta/apagar',
    'Descarregar ou apagar os teus dados',
  );

  const AccountSection(this.label, this.icon, this.path, this.description);

  final String label;
  final IconData icon;

  /// Line under the label in the mobile menu (C0).
  final String description;

  /// Null while the section has no screen yet.
  final String? path;
}

/// Desktop layout of the account area: "Conta" title, side menu and the
/// selected section (C2 desktop).
class AccountDesktopFrame extends StatelessWidget {
  const AccountDesktopFrame({
    super.key,
    required this.section,
    required this.title,
    required this.children,
  });

  final AccountSection section;
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 40),
      children: [
        Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 1112),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 40),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                spacing: AppSpacing.xxl,
                children: [
                  Text(
                    'Conta',
                    style: AppTextStyles.largeTitle.copyWith(fontSize: 28),
                  ),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    spacing: AppSpacing.xxl,
                    children: [
                      SizedBox(width: 260, child: _SideMenu(selected: section)),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          spacing: AppSpacing.md,
                          children: [
                            Text(
                              title,
                              style: AppTextStyles.headline.copyWith(
                                fontSize: 20,
                              ),
                            ),
                            ...children,
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _SideMenu extends StatelessWidget {
  const _SideMenu({required this.selected});

  final AccountSection selected;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.sm),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.all(AppRadii.panel),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: 2,
        children: [
          for (final section in AccountSection.values)
            _SideMenuItem(
              section: section,
              selected: section == selected,
              onTap: () => section.path == null
                  ? showComingSoon(context)
                  : context.go(section.path!),
            ),
        ],
      ),
    );
  }
}

class _SideMenuItem extends StatelessWidget {
  const _SideMenuItem({
    required this.section,
    required this.selected,
    required this.onTap,
  });

  final AccountSection section;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? AppColors.primary : AppColors.ink;
    return Semantics(
      button: true,
      selected: selected,
      child: Material(
        color: selected ? AppColors.primarySoft : Colors.transparent,
        borderRadius: const BorderRadius.all(AppRadii.sm),
        child: InkWell(
          onTap: onTap,
          borderRadius: const BorderRadius.all(AppRadii.sm),
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.md,
              vertical: 10,
            ),
            child: Row(
              spacing: AppSpacing.md,
              children: [
                Icon(section.icon, size: 18, color: color),
                Text(
                  section.label,
                  style: AppTextStyles.subhead.copyWith(
                    color: color,
                    fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
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

/// Row with a title, an explanation and a switch (C2).
class PreferenceSwitchTile extends StatelessWidget {
  const PreferenceSwitchTile({
    super.key,
    required this.title,
    required this.value,
    required this.onChanged,
    this.subtitle,
    this.icon,
  });

  final String title;
  final String? subtitle;
  final IconData? icon;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: InkWell(
        onTap: () => onChanged(!value),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
          child: Row(
            spacing: AppSpacing.md,
            children: [
              if (icon != null) Icon(icon, size: 22, color: AppColors.primary),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  spacing: 2,
                  children: [
                    Text(title, style: AppTextStyles.bodyStrong),
                    if (subtitle != null)
                      Text(
                        subtitle!,
                        style: AppTextStyles.caption.copyWith(height: 1.35),
                      ),
                  ],
                ),
              ),
              Switch(value: value, onChanged: onChanged),
            ],
          ),
        ),
      ),
    );
  }
}

/// White panel with an optional overline title and rows separated by
/// dividers (C2).
class PreferencePanel extends StatelessWidget {
  const PreferencePanel({super.key, required this.children, this.overline});

  final String? overline;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.xl,
        vertical: AppSpacing.xs,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.all(AppRadii.panel),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (overline != null)
            Padding(
              padding: const EdgeInsets.only(top: AppSpacing.sm),
              child: Text(
                overline!,
                style: AppTextStyles.overline.copyWith(
                  color: AppColors.inkSecondary,
                ),
              ),
            ),
          for (final (index, child) in children.indexed) ...[
            if (index > 0) const Divider(),
            child,
          ],
        ],
      ),
    );
  }
}
