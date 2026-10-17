import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';
import 'account_layout.dart';

/// Conta: profile, links to the account settings (C2, C3), sign out and the
/// demo switches.
class AccountScreen extends StatelessWidget {
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final account = store.account;

    void signOut() {
      store.signOut();
      context.go('/entrar');
    }

    final profile = AppPanel(
      children: [
        Row(
          spacing: AppSpacing.md,
          children: [
            CircleAvatar(
              radius: 26,
              backgroundColor: AppColors.avatar,
              child: Text(
                account?.initials ?? '',
                style: AppTextStyles.bodyStrong.copyWith(
                  color: AppColors.primary,
                ),
              ),
            ),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                spacing: 2,
                children: [
                  Text(account?.name ?? '', style: AppTextStyles.bodyLarge),
                  Text(account?.email ?? '', style: AppTextStyles.footnote),
                ],
              ),
            ),
          ],
        ),
      ],
    );

    final demo = [
      Text(
        'DEMONSTRAÇÃO',
        style: AppTextStyles.overline.copyWith(color: AppColors.inkSecondary),
      ),
      AppListGroup(
        children: [
          AppListTile(
            icon: Icons.wifi_off,
            iconColor: AppColors.ink,
            title: 'Simular sem rede',
            subtitle: 'Mostra a página do evento como sem rede.',
            showChevron: false,
            trailing: Switch(value: store.offline, onChanged: store.setOffline),
          ),
          AppListTile(
            icon: Icons.widgets_outlined,
            title: 'Componentes',
            onTap: () => context.push('/dev/componentes'),
          ),
        ],
      ),
    ];

    final signOutButton = AppButton.secondary(
      label: 'Terminar sessão',
      icon: Icons.logout,
      onPressed: signOut,
    );

    if (isDesktopLayout(context)) {
      return AccountDesktopFrame(
        section: AccountSection.profile,
        title: 'Perfil',
        children: [
          profile,
          Align(alignment: Alignment.centerLeft, child: signOutButton),
          const SizedBox(height: AppSpacing.sm),
          ...demo,
        ],
      );
    }

    return SafeArea(
      bottom: false,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.screen,
          AppSpacing.sm,
          AppSpacing.screen,
          AppSpacing.xxl,
        ),
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            spacing: AppSpacing.md,
            children: [
              const SectionHeader(title: 'Conta'),
              profile,
              AppListGroup(
                children: [
                  for (final section in AccountSection.values.skip(1))
                    AppListTile(
                      icon: section.icon,
                      title: section.label,
                      subtitle: section == AccountSection.privacy
                          ? 'Descarregar ou apagar os teus dados'
                          : null,
                      onTap: () => section.path == null
                          ? showComingSoon(context)
                          : context.push(section.path!),
                    ),
                ],
              ),
              signOutButton,
              const SizedBox(height: AppSpacing.sm),
              ...demo,
            ],
          ),
        ],
      ),
    );
  }
}
