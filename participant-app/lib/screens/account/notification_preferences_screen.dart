import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../shared/shell.dart';
import 'account_layout.dart';

/// C2 · Preferências de notificações.
class NotificationPreferencesScreen extends StatelessWidget {
  const NotificationPreferencesScreen({super.key});

  Future<void> _pickLevel(
    BuildContext context,
    AppStore store,
    Event event,
  ) async {
    final current = store.levelFor(event.id);
    final picked = await showAppBottomSheet<EventNotificationLevel>(
      context: context,
      builder: (context) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        spacing: AppSpacing.sm,
        children: [
          Text(event.name, style: AppTextStyles.headline),
          for (final level in EventNotificationLevel.values)
            AppRadioCard(
              title: level.label,
              selected: level == current,
              onTap: () => Navigator.of(context).pop(level),
            ),
        ],
      ),
    );
    if (picked != null) {
      store.updatePreferences((p) => p.perEvent[event.id] = picked);
    }
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final prefs = store.preferences;
    void update(void Function(NotificationPreferences p) change) =>
        store.updatePreferences(change);

    final children = <Widget>[
      PreferencePanel(
        overline: 'PARA TODOS OS EVENTOS',
        children: [
          PreferenceSwitchTile(
            title: 'Alterações oficiais',
            subtitle:
                'Cancelamentos, adiamentos e mudanças de local. Recomendado.',
            value: prefs.officialChanges,
            onChanged: (v) => update((p) => p.officialChanges = v),
          ),
          PreferenceSwitchTile(
            title: 'Alertas da organização',
            subtitle: 'Ex.: portas fechadas, mudanças no recinto.',
            value: prefs.organizerAlerts,
            onChanged: (v) => update((p) => p.organizerAlerts = v),
          ),
          PreferenceSwitchTile(
            title: 'Alertas de meteorologia',
            subtitle: 'Só quando a previsão passa um limite de risco.',
            value: prefs.weatherAlerts,
            onChanged: (v) => update((p) => p.weatherAlerts = v),
          ),
          PreferenceSwitchTile(
            title: 'Reconhecimentos',
            subtitle: 'Quando um reporte teu ajuda a organização.',
            value: prefs.recognitions,
            onChanged: (v) => update((p) => p.recognitions = v),
          ),
        ],
      ),
      PreferencePanel(
        children: [
          PreferenceSwitchTile(
            icon: Icons.mail_outline,
            title: 'Alterações oficiais também por email',
            subtitle:
                'Cancelamentos, adiamentos e mudanças de local chegam por '
                'email, mesmo sem a app instalada.',
            value: prefs.officialChangesByEmail,
            onChanged: (v) => update((p) => p.officialChangesByEmail = v),
          ),
        ],
      ),
      AppPanel(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.xl,
          vertical: AppSpacing.xl,
        ),
        spacing: 10,
        children: [
          Text('Pedidos da organização', style: AppTextStyles.bodyStrong),
          AppSegmentedControl<RequestNotificationLevel>(
            selected: prefs.requests,
            onChanged: (v) => update((p) => p.requests = v),
            segments: [
              for (final level in RequestNotificationLevel.values)
                AppSegment(value: level, label: level.label),
            ],
          ),
        ],
      ),
      PreferencePanel(
        children: [
          PreferenceSwitchTile(
            title: 'Pedidos de confirmação',
            subtitle:
                'No máximo 3 por evento, por hora, e só quando estás perto do '
                'recinto.',
            value: prefs.confirmationPrompts,
            onChanged: (v) => update((p) => p.confirmationPrompts = v),
          ),
        ],
      ),
      if (store.connectedEvents.isNotEmpty)
        PreferencePanel(
          overline: 'POR EVENTO',
          children: [
            for (final event in store.connectedEvents)
              InkWell(
                onTap: () => _pickLevel(context, store, event),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
                  child: Row(
                    spacing: 10,
                    children: [
                      Expanded(
                        flex: 3,
                        child: Text(
                          event.name,
                          style: AppTextStyles.body.copyWith(
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                      Expanded(
                        flex: 2,
                        child: Text(
                          store.levelFor(event.id).label,
                          textAlign: TextAlign.end,
                          style: AppTextStyles.footnote,
                        ),
                      ),
                      const Icon(
                        Icons.chevron_right,
                        size: 18,
                        color: AppColors.inkSecondary,
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      // Browsers get no push notifications (ADR 0010).
      if (kIsWeb)
        Container(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.lg,
            vertical: AppSpacing.md,
          ),
          decoration: const BoxDecoration(
            color: AppColors.primarySoft,
            borderRadius: BorderRadius.all(AppRadii.button),
          ),
          child: Row(
            spacing: 10,
            children: [
              const Icon(Icons.smartphone, size: 18, color: AppColors.primary),
              Expanded(
                child: Text(
                  'No browser, só recebes notificações com o site aberto. '
                  'Instala a app para as receberes sempre.',
                  style: AppTextStyles.label.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ],
          ),
        ),
    ];

    return AppPage(
      title: 'Notificações',
      leadingLabel: 'Conta',
      section: Sections.account,
      desktopBody: AccountDesktopFrame(
        section: AccountSection.notifications,
        title: 'Notificações',
        children: children,
      ),
      children: children,
    );
  }
}
