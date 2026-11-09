import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_spacing.dart';
import '../theme/app_text_styles.dart';

/// Width from which screens use the desktop layout (web header instead of
/// the bottom tab bar).
const desktopBreakpoint = 900.0;

bool isDesktopLayout(BuildContext context) =>
    MediaQuery.sizeOf(context).width >= desktopBreakpoint;

/// A main section of the app, shown in [AppTabBar] and [AppWebHeader].
class AppDestination {
  const AppDestination({
    required this.label,
    required this.icon,
    this.showInWebHeader = true,
  });

  final String label;
  final IconData icon;

  /// The web header shows the account as an avatar instead of a nav item.
  final bool showInWebHeader;

  static const defaults = [
    AppDestination(label: 'Os meus eventos', icon: Icons.credit_card),
    AppDestination(
      label: 'Conta',
      icon: Icons.person_outline,
      showInWebHeader: false,
    ),
  ];
}

/// iOS-style top bar: leading action ("Voltar" with chevron, or a text
/// action such as "Cancelar" / "Fechar"), centred title, trailing actions.
class AppTopBar extends StatelessWidget implements PreferredSizeWidget {
  const AppTopBar({
    super.key,
    this.title,
    this.leadingLabel,
    this.onLeading,
    this.showBackChevron = false,
    this.actions = const [],
  });

  /// "Voltar" with a chevron.
  const AppTopBar.back({
    super.key,
    this.title,
    required this.onLeading,
    this.actions = const [],
  }) : leadingLabel = 'Voltar',
       showBackChevron = true;

  final String? title;
  final String? leadingLabel;
  final VoidCallback? onLeading;
  final bool showBackChevron;
  final List<Widget> actions;

  @override
  Size get preferredSize => const Size.fromHeight(44);

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: SizedBox(
        height: preferredSize.height,
        child: NavigationToolbar(
          middleSpacing: AppSpacing.sm,
          leading: leadingLabel == null
              ? null
              : Semantics(
                  button: true,
                  child: InkWell(
                    onTap: onLeading,
                    borderRadius: const BorderRadius.all(AppRadii.sm),
                    child: Padding(
                      padding: EdgeInsets.only(
                        left: showBackChevron ? 5 : AppSpacing.screen,
                        right: AppSpacing.sm,
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (showBackChevron)
                            const Icon(
                              Icons.chevron_left,
                              size: 28,
                              color: AppColors.primary,
                            ),
                          Text(leadingLabel!, style: AppTextStyles.navAction),
                        ],
                      ),
                    ),
                  ),
                ),
          middle: title == null
              ? null
              : Semantics(
                  header: true,
                  child: Text(
                    title!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: AppTextStyles.headline,
                  ),
                ),
          trailing: actions.isEmpty
              ? null
              : Padding(
                  padding: const EdgeInsets.only(right: AppSpacing.sm),
                  child: Row(mainAxisSize: MainAxisSize.min, children: actions),
                ),
        ),
      ),
    );
  }
}

/// One step of [AppBreadcrumbs]; the last one has no [onTap].
class AppBreadcrumb {
  const AppBreadcrumb(this.label, {this.onTap});

  final String label;
  final VoidCallback? onTap;
}

/// "Os meus eventos › Festival Margens › Como chegar" above desktop pages.
class AppBreadcrumbs extends StatelessWidget {
  const AppBreadcrumbs({super.key, required this.items});

  final List<AppBreadcrumb> items;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: AppSpacing.xs,
      runSpacing: AppSpacing.xxs,
      children: [
        for (final (index, item) in items.indexed) ...[
          if (index > 0)
            const Icon(
              Icons.chevron_right,
              size: 14,
              color: AppColors.inkSecondary,
            ),
          if (item.onTap == null)
            Text(
              item.label,
              style: AppTextStyles.label.copyWith(fontWeight: FontWeight.w500),
            )
          else
            Semantics(
              link: true,
              child: InkWell(
                onTap: item.onTap,
                borderRadius: const BorderRadius.all(AppRadii.sm),
                child: Text(
                  item.label,
                  style: AppTextStyles.label.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w400,
                  ),
                ),
              ),
            ),
        ],
      ],
    );
  }
}

/// Bottom tab bar of the mobile app: "Os meus eventos", the raised
/// "Reportar" button in the middle when [onReport] is given, and "Conta".
class AppTabBar extends StatelessWidget {
  const AppTabBar({
    super.key,
    required this.currentIndex,
    required this.onTap,
    this.destinations = AppDestination.defaults,
    this.onReport,
    this.reportEnabled = true,
  });

  final int currentIndex;
  final ValueChanged<int> onTap;
  final List<AppDestination> destinations;

  /// Opens the report form. The button is shown when this is set.
  final VoidCallback? onReport;

  /// False when the participant has no event that day.
  final bool reportEnabled;

  @override
  Widget build(BuildContext context) {
    final items = <Widget>[
      for (final (index, destination) in destinations.indexed)
        Expanded(
          child: _TabItem(
            destination: destination,
            selected: index == currentIndex,
            onTap: () => onTap(index),
          ),
        ),
    ];
    if (onReport != null) {
      items.insert(
        items.length ~/ 2,
        Expanded(
          child: _ReportTab(onTap: onReport!, enabled: reportEnabled),
        ),
      );
    }
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.tabBar,
        border: Border(top: BorderSide(color: AppColors.tabBarBorder)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(height: 58, child: Row(children: items)),
      ),
    );
  }
}

/// The middle tab: [AppReportButton] raised over the top edge of the bar,
/// with its label aligned with the other tabs.
class _ReportTab extends StatelessWidget {
  const _ReportTab({required this.onTap, required this.enabled});

  final VoidCallback onTap;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return Stack(
      clipBehavior: Clip.none,
      alignment: Alignment.topCenter,
      children: [
        Positioned.fill(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              ExcludeSemantics(
                child: Text(
                  'Reportar',
                  style: AppTextStyles.tabLabel.copyWith(
                    color: enabled ? AppColors.ink : AppColors.inkSecondary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              const SizedBox(height: 7),
            ],
          ),
        ),
        Positioned(
          top: -26,
          child: DecoratedBox(
            decoration: const BoxDecoration(
              color: AppColors.tabBar,
              shape: BoxShape.circle,
            ),
            child: Padding(
              padding: const EdgeInsets.all(4),
              child: AppReportButton(onPressed: enabled ? onTap : null),
            ),
          ),
        ),
      ],
    );
  }
}

/// Round "Reportar" button: in the middle of the tab bar, or floating in the
/// bottom-right corner when the bar is hidden or the screen has none.
/// Disabled (no [onPressed]) when the participant has no event that day.
class AppReportButton extends StatelessWidget {
  const AppReportButton({super.key, required this.onPressed, this.size = 52});

  final VoidCallback? onPressed;
  final double size;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null;
    return Semantics(
      button: true,
      enabled: enabled,
      label: enabled ? 'Reportar' : 'Reportar, sem eventos hoje',
      excludeSemantics: true,
      child: Material(
        color: enabled ? AppColors.ink : AppColors.disabled,
        shape: const CircleBorder(),
        elevation: enabled ? 6 : 0,
        shadowColor: const Color(0x66000000),
        child: InkWell(
          onTap: onPressed,
          customBorder: const CircleBorder(),
          child: SizedBox.square(
            dimension: size,
            child: Icon(
              Icons.flag_outlined,
              size: 24,
              color: enabled ? Colors.white : AppColors.inkSecondary,
            ),
          ),
        ),
      ),
    );
  }
}

class _TabItem extends StatelessWidget {
  const _TabItem({
    required this.destination,
    required this.selected,
    required this.onTap,
  });

  final AppDestination destination;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? AppColors.primary : AppColors.inkSecondary;
    return Semantics(
      button: true,
      selected: selected,
      child: InkWell(
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          spacing: 3,
          children: [
            Icon(destination.icon, size: 24, color: color),
            Text(
              destination.label,
              style: AppTextStyles.tabLabel.copyWith(
                color: color,
                fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Header of the website (desktop layout): logo, sections, the "Reportar"
/// button, notifications and the account avatar.
class AppWebHeader extends StatelessWidget implements PreferredSizeWidget {
  const AppWebHeader({
    super.key,
    required this.currentIndex,
    required this.onNavigate,
    required this.userInitials,
    this.destinations = AppDestination.defaults,
    this.unreadNotifications = 0,
    this.onReport,
    this.onNotifications,
    this.onAccount,
  });

  final int currentIndex;
  final ValueChanged<int> onNavigate;
  final List<AppDestination> destinations;
  final String userInitials;
  final int unreadNotifications;

  /// Opens the report form; the button is disabled when null.
  final VoidCallback? onReport;
  final VoidCallback? onNotifications;
  final VoidCallback? onAccount;

  @override
  Size get preferredSize => const Size.fromHeight(64);

  @override
  Widget build(BuildContext context) {
    return Container(
      height: preferredSize.height,
      padding: const EdgeInsets.symmetric(horizontal: 40),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(bottom: BorderSide(color: AppColors.border)),
      ),
      child: Row(
        children: [
          const _Logo(),
          const SizedBox(width: 40),
          Expanded(
            child: Row(
              spacing: 4,
              children: [
                for (final (index, destination) in destinations.indexed)
                  if (destination.showInWebHeader)
                    _WebNavItem(
                      destination: destination,
                      selected: index == currentIndex,
                      onTap: () => onNavigate(index),
                    ),
              ],
            ),
          ),
          _HeaderReportButton(onPressed: onReport),
          const SizedBox(width: AppSpacing.xl),
          _NotificationsButton(
            unread: unreadNotifications,
            onTap: onNotifications,
          ),
          const SizedBox(width: AppSpacing.xl),
          Semantics(
            button: true,
            label: 'Conta',
            child: InkWell(
              onTap: onAccount,
              customBorder: const CircleBorder(),
              child: CircleAvatar(
                radius: 17,
                backgroundColor: AppColors.avatar,
                child: Text(
                  userInitials,
                  style: AppTextStyles.label.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Logo extends StatelessWidget {
  const _Logo();

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      spacing: AppSpacing.sm,
      children: [
        Container(
          padding: const EdgeInsets.all(AppSpacing.xs),
          decoration: const BoxDecoration(
            color: AppColors.ink,
            borderRadius: BorderRadius.all(AppRadii.sm),
          ),
          child: const Icon(
            Icons.people_outline,
            size: 18,
            color: Colors.white,
          ),
        ),
        Text(
          'Crowded',
          style: AppTextStyles.headline.copyWith(
            fontSize: 18,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.36,
          ),
        ),
      ],
    );
  }
}

class _WebNavItem extends StatelessWidget {
  const _WebNavItem({
    required this.destination,
    required this.selected,
    required this.onTap,
  });

  final AppDestination destination;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? AppColors.primary : AppColors.inkSecondary;
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
              vertical: AppSpacing.sm,
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              spacing: AppSpacing.sm,
              children: [
                Icon(destination.icon, size: 18, color: color),
                Text(
                  destination.label,
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

class _HeaderReportButton extends StatelessWidget {
  const _HeaderReportButton({required this.onPressed});

  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null;
    final color = enabled ? Colors.white : AppColors.inkSecondary;
    return Semantics(
      button: true,
      enabled: enabled,
      label: enabled ? 'Reportar' : 'Reportar, sem eventos hoje',
      excludeSemantics: true,
      child: Material(
        color: enabled ? AppColors.ink : AppColors.disabled,
        borderRadius: const BorderRadius.all(AppRadii.sm),
        child: InkWell(
          onTap: onPressed,
          borderRadius: const BorderRadius.all(AppRadii.sm),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 8, 16, 8),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              spacing: AppSpacing.sm,
              children: [
                Icon(Icons.flag_outlined, size: 16, color: color),
                Text(
                  'Reportar',
                  style: AppTextStyles.subhead.copyWith(
                    color: color,
                    fontWeight: FontWeight.w600,
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

class _NotificationsButton extends StatelessWidget {
  const _NotificationsButton({required this.unread, required this.onTap});

  final int unread;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: unread > 0 ? 'Notificações, $unread por ler' : 'Notificações',
      excludeSemantics: true,
      child: InkWell(
        onTap: onTap,
        borderRadius: const BorderRadius.all(AppRadii.field),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.sm),
          child: Badge(
            isLabelVisible: unread > 0,
            backgroundColor: AppColors.counter,
            label: Text('$unread'),
            child: const Icon(
              Icons.notifications_none,
              size: 20,
              color: AppColors.ink,
            ),
          ),
        ),
      ),
    );
  }
}
