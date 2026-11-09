import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../ui/ui.dart';
import '../report/report_flow.dart';

/// Root sections, in the order of the tab bar.
abstract final class Sections {
  static const wallet = 0;
  static const account = 1;

  static const paths = ['/eventos', '/conta'];
}

/// Opens the report form (P11) with the event in progress chosen, or null
/// when the participant has no event today and "Reportar" is disabled.
VoidCallback? reportAction(BuildContext context) {
  final event = AppStore.of(context).eventsToday.firstOrNull;
  if (event == null) return null;
  return () => startReport(context, event);
}

/// Website header wired to the app routes.
class CrowdedWebHeader extends StatelessWidget implements PreferredSizeWidget {
  const CrowdedWebHeader({super.key, required this.currentIndex});

  final int currentIndex;

  @override
  Size get preferredSize => const Size.fromHeight(64);

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    return AppWebHeader(
      currentIndex: currentIndex,
      onNavigate: (i) => context.go(Sections.paths[i]),
      userInitials: store.account?.initials ?? '',
      unreadNotifications: store.unreadCount,
      onReport: reportAction(context),
      onNotifications: () => context.push('/notificacoes'),
      onAccount: () => context.go(Sections.paths[Sections.account]),
    );
  }
}

/// Scaffold of the root sections: bottom tab bar on mobile, web header on
/// desktop. On mobile the bar hides while scrolling down; "Reportar" then
/// floats in the corner until the bar comes back.
class AppShell extends StatefulWidget {
  const AppShell({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  bool _barVisible = true;

  void _onTap(int index) {
    setState(() => _barVisible = true);
    widget.navigationShell.goBranch(
      index,
      initialLocation: index == widget.navigationShell.currentIndex,
    );
  }

  bool _onScroll(UserScrollNotification notification) {
    if (notification.depth != 0) return false;
    final visible = switch (notification.direction) {
      ScrollDirection.reverse => false,
      ScrollDirection.forward => true,
      ScrollDirection.idle => _barVisible,
    };
    if (visible != _barVisible) setState(() => _barVisible = visible);
    return false;
  }

  @override
  Widget build(BuildContext context) {
    final shell = widget.navigationShell;
    if (isDesktopLayout(context)) {
      return Scaffold(
        appBar: CrowdedWebHeader(currentIndex: shell.currentIndex),
        body: shell,
      );
    }
    final onReport = reportAction(context);
    return Scaffold(
      body: NotificationListener<UserScrollNotification>(
        onNotification: _onScroll,
        child: shell,
      ),
      floatingActionButton: _barVisible
          ? null
          : AppReportButton(
              key: const Key('floating-report'),
              onPressed: onReport,
            ),
      bottomNavigationBar: AnimatedSize(
        duration: const Duration(milliseconds: 200),
        child: _barVisible
            ? AppTabBar(
                currentIndex: shell.currentIndex,
                onTap: _onTap,
                onReport: onReport ?? () {},
                reportEnabled: onReport != null,
              )
            : const SizedBox(width: double.infinity),
      ),
    );
  }
}

/// Scaffold of screens opened on top of a section (import flow, event page,
/// notifications): iOS-style top bar on mobile, web header and a back link
/// above a centred column on desktop.
class AppPage extends StatelessWidget {
  const AppPage({
    super.key,
    required this.children,
    this.title,
    this.leadingLabel = 'Voltar',
    this.showBackChevron = true,
    this.onLeading,
    this.actions = const [],
    this.section = Sections.wallet,
    this.maxWidth = 640,
    this.background = AppColors.background,
    this.desktopBody,
    this.bottom,
    this.showReportButton = false,
  });

  final String? title;

  /// "Voltar", "Cancelar", "Fechar" or the previous screen's name.
  final String leadingLabel;
  final bool showBackChevron;

  /// Defaults to going back.
  final VoidCallback? onLeading;
  final List<Widget> actions;

  /// Section highlighted in the web header.
  final int section;
  final double maxWidth;
  final Color background;
  final List<Widget> children;

  /// Replaces the centred column on desktop.
  final Widget? desktopBody;

  /// Pinned under the content, e.g. a primary button.
  final Widget? bottom;

  /// Shows the floating "Reportar" button on mobile. Off in task flows
  /// (import, access code, the report form), the entry code and settings.
  final bool showReportButton;

  static void goBack(BuildContext context) {
    if (context.canPop()) {
      context.pop();
    } else {
      context.go(Sections.paths[Sections.wallet]);
    }
  }

  @override
  Widget build(BuildContext context) {
    final leading = onLeading ?? () => goBack(context);
    if (isDesktopLayout(context)) {
      return Scaffold(
        backgroundColor: background,
        appBar: CrowdedWebHeader(currentIndex: section),
        body:
            desktopBody ??
            ListView(
              padding: const EdgeInsets.symmetric(vertical: 32),
              children: [
                Center(
                  child: ConstrainedBox(
                    constraints: BoxConstraints(maxWidth: maxWidth),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: AppSpacing.screen,
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        spacing: AppSpacing.md,
                        children: [
                          Row(
                            children: [
                              AppInlineLink(
                                label: leadingLabel,
                                icon: showBackChevron
                                    ? Icons.chevron_left
                                    : Icons.close,
                                onPressed: leading,
                              ),
                              const Spacer(),
                              ...actions,
                            ],
                          ),
                          if (title != null)
                            Text(title!, style: AppTextStyles.title),
                          ...children,
                          ?bottom,
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
      );
    }

    return Scaffold(
      backgroundColor: background,
      floatingActionButton: showReportButton
          ? AppReportButton(onPressed: reportAction(context))
          : null,
      appBar: AppTopBar(
        title: title,
        leadingLabel: leadingLabel,
        showBackChevron: showBackChevron,
        onLeading: leading,
        actions: actions,
      ),
      body: ListView(
        padding: EdgeInsets.fromLTRB(
          AppSpacing.screen,
          AppSpacing.md,
          AppSpacing.screen,
          // Room for the floating "Reportar" over the last item.
          showReportButton ? 96 : AppSpacing.xl,
        ),
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            spacing: AppSpacing.md,
            children: children,
          ),
        ],
      ),
      bottomNavigationBar: bottom == null
          ? null
          : SafeArea(
              minimum: const EdgeInsets.fromLTRB(
                AppSpacing.screen,
                AppSpacing.sm,
                AppSpacing.screen,
                AppSpacing.md,
              ),
              child: bottom!,
            ),
    );
  }
}

/// Large title header of a root section on mobile ("Os meus eventos").
class SectionHeader extends StatelessWidget {
  const SectionHeader({
    super.key,
    required this.title,
    this.subtitle,
    this.actions = const [],
  });

  final String title;
  final String? subtitle;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.sm, bottom: AppSpacing.md),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Semantics(
                  header: true,
                  child: Text(
                    title,
                    style: AppTextStyles.largeTitle.copyWith(fontSize: 28),
                  ),
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: 4),
                  Text(subtitle!, style: AppTextStyles.footnote),
                ],
              ],
            ),
          ),
          ...[
            for (final (i, action) in actions.indexed) ...[
              if (i > 0) const SizedBox(width: AppSpacing.sm),
              action,
            ],
          ],
        ],
      ),
    );
  }
}

/// Square event picture: a gradient until the catalog has photos.
class EventThumbnail extends StatelessWidget {
  const EventThumbnail({
    super.key,
    required this.gradient,
    this.size = 52,
    this.radius = 10,
  });

  final Gradient gradient;
  final double size;
  final double radius;

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          gradient: gradient,
          borderRadius: BorderRadius.circular(radius),
        ),
      ),
    );
  }
}

/// Feedback for actions whose screen is not built yet.
void showComingSoon(BuildContext context) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(const SnackBar(content: Text('Disponível em breve.')));
}

void showMessage(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}
