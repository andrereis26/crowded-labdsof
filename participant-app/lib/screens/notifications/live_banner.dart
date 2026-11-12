import 'dart:async';

import 'package:flutter/material.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import 'notifications_screen.dart';

/// Shows the newest live notification as a banner at the top of every page
/// (FR-068). Opening it marks it read and goes to its event, change or
/// screen; it can be closed, and hides after [duration]. It stays in the
/// notification center either way.
class LiveBannerHost extends StatefulWidget {
  const LiveBannerHost({
    super.key,
    required this.child,
    required this.onOpen,
    this.duration = const Duration(seconds: 8),
  });

  final Widget child;

  /// Goes to a location of the app; the host sits above the router.
  final ValueChanged<String> onOpen;
  final Duration duration;

  @override
  State<LiveBannerHost> createState() => _LiveBannerHostState();
}

class _LiveBannerHostState extends State<LiveBannerHost> {
  Timer? _timer;
  String? _shownId;

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  /// Restarts the countdown whenever another notification is shown.
  void _schedule(AppStore store, AppNotification? banner) {
    if (banner?.id == _shownId) return;
    _shownId = banner?.id;
    _timer?.cancel();
    if (banner != null) _timer = Timer(widget.duration, store.dismissBanner);
  }

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final banner = store.banner;
    _schedule(store, banner);
    final reduceMotion = MediaQuery.disableAnimationsOf(context);

    return Stack(
      children: [
        widget.child,
        Positioned(
          top: 0,
          left: 0,
          right: 0,
          child: SafeArea(
            bottom: false,
            child: AnimatedSwitcher(
              duration: reduceMotion
                  ? Duration.zero
                  : const Duration(milliseconds: 200),
              transitionBuilder: (child, animation) => FadeTransition(
                opacity: animation,
                child: SlideTransition(
                  position: Tween(
                    begin: const Offset(0, -0.25),
                    end: Offset.zero,
                  ).animate(animation),
                  child: child,
                ),
              ),
              child: banner == null
                  ? const SizedBox.shrink()
                  : Padding(
                      key: ValueKey(banner.id),
                      padding: const EdgeInsets.all(AppSpacing.lg),
                      child: Center(
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 560),
                          child: LiveNotificationBanner(
                            notification: banner,
                            onOpen: () {
                              store.markRead(banner);
                              widget.onOpen(banner.target);
                            },
                            onClose: store.dismissBanner,
                          ),
                        ),
                      ),
                    ),
            ),
          ),
        ),
      ],
    );
  }
}

/// A new notification: its type, event, source and title, announced to
/// screen readers when it appears.
class LiveNotificationBanner extends StatelessWidget {
  const LiveNotificationBanner({
    super.key,
    required this.notification,
    required this.onOpen,
    required this.onClose,
  });

  final AppNotification notification;
  final VoidCallback onOpen;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final n = notification;
    final (icon, background, foreground) = notificationIcon(n.kind);
    const radius = BorderRadius.all(AppRadii.panel);
    return Semantics(
      liveRegion: true,
      container: true,
      child: Material(
        color: AppColors.surface,
        elevation: 6,
        shadowColor: AppColors.ink.withValues(alpha: 0.2),
        shape: RoundedRectangleBorder(
          borderRadius: radius,
          side: BorderSide(color: AppColors.ink.withValues(alpha: 0.08)),
        ),
        child: InkWell(
          onTap: onOpen,
          borderRadius: radius,
          child: Padding(
            padding: const EdgeInsets.only(
              left: AppSpacing.lg,
              top: AppSpacing.md,
              bottom: AppSpacing.md,
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: AppSpacing.md,
              children: [
                Container(
                  padding: const EdgeInsets.all(AppSpacing.sm),
                  decoration: BoxDecoration(
                    color: background,
                    borderRadius: const BorderRadius.all(AppRadii.field),
                  ),
                  child: Icon(
                    icon,
                    size: 18,
                    color: foreground,
                    semanticLabel: n.typeLabel,
                  ),
                ),
                Expanded(
                  child: Semantics(
                    button: true,
                    hint: 'Abrir notificação',
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      spacing: 3,
                      children: [
                        Text(
                          [n.eventName, ?notificationSource(n)].join(' · '),
                          style: AppTextStyles.caption.copyWith(
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                        Text(
                          n.title,
                          style: AppTextStyles.bodyStrong,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                ),
                // No tooltip: the banner sits above the router's overlay.
                IconButton(
                  key: const ValueKey('live-banner-close'),
                  onPressed: onClose,
                  constraints: const BoxConstraints(
                    minWidth: 44,
                    minHeight: 44,
                  ),
                  icon: const Icon(
                    Icons.close,
                    size: 20,
                    color: AppColors.ink,
                    semanticLabel: 'Fechar',
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
