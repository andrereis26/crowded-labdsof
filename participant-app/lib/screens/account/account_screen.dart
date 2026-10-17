import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';

/// Conta. Placeholder until C2 and C3 are built; holds the demo switches.
class AccountScreen extends StatelessWidget {
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
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
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                spacing: AppSpacing.md,
                children: [
                  const SectionHeader(title: 'Conta'),
                  AppListGroup(
                    children: [
                      AppListTile(
                        icon: Icons.notifications_none,
                        title: 'Preferências de notificações',
                        onTap: () => showComingSoon(context),
                      ),
                      AppListTile(
                        icon: Icons.person_outline,
                        title: 'Conta e dados',
                        onTap: () => showComingSoon(context),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  Text(
                    'DEMONSTRAÇÃO',
                    style: AppTextStyles.overline.copyWith(
                      color: AppColors.inkSecondary,
                    ),
                  ),
                  AppListGroup(
                    children: [
                      AppListTile(
                        icon: Icons.wifi_off,
                        iconColor: AppColors.ink,
                        title: 'Simular sem rede',
                        subtitle: 'Mostra a página do evento como sem rede.',
                        showChevron: false,
                        trailing: Switch(
                          value: store.offline,
                          onChanged: store.setOffline,
                        ),
                      ),
                      AppListTile(
                        icon: Icons.widgets_outlined,
                        title: 'Componentes',
                        onTap: () => context.push('/dev/componentes'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
