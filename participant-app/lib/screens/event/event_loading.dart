import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../ui/ui.dart';

/// Loads the page's event from Core when it opens, and again when it shows
/// another event, then follows its live stream until the page closes.
mixin RefreshesEvent<T extends StatefulWidget> on State<T> {
  /// The event [widget] shows.
  String eventIdOf(T widget);

  AppStore? _store;
  String? _followed;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  @override
  void didUpdateWidget(T oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (eventIdOf(oldWidget) != eventIdOf(widget)) _refresh();
  }

  @override
  void dispose() {
    _unfollow();
    super.dispose();
  }

  // After the frame: the load tells the store's listeners at once.
  void _refresh() => WidgetsBinding.instance.addPostFrameCallback((_) {
    if (!mounted) return;
    final store = AppStore.read(context);
    final eventId = eventIdOf(widget);
    store.refreshEvent(eventId);
    if (_followed == eventId && _store == store) return;
    _unfollow();
    store.followEvent(eventId);
    _store = store;
    _followed = eventId;
  });

  void _unfollow() {
    final eventId = _followed;
    if (eventId != null) _store?.unfollowEvent(eventId);
    _followed = null;
  }
}

/// What a page shows while its event is not there: loading, or why it could
/// not be loaded.
Widget missingEventState(BuildContext context, AppStore store, String eventId) {
  final sync = store.syncOf(eventId);
  void backToEvents() => context.go('/events');
  if (sync == null) {
    return AppErrorState(
      message: 'Não encontrámos este evento',
      retryLabel: 'Voltar aos meus eventos',
      onRetry: backToEvents,
    );
  }
  if (sync.loading) {
    return const AppLoadingState(message: 'A carregar o evento');
  }
  return switch (sync.failure) {
    'NOT_CONNECTED' => AppErrorState(
      message: 'Já não estás ligado a este evento',
      retryLabel: 'Voltar aos meus eventos',
      onRetry: backToEvents,
    ),
    'EVENT_NOT_FOUND' => AppErrorState(
      message: 'Não encontrámos este evento',
      retryLabel: 'Voltar aos meus eventos',
      onRetry: backToEvents,
    ),
    _ => AppErrorState(
      message: sync.unreachable
          ? 'Sem rede. Não foi possível carregar o evento'
          : 'Não foi possível carregar o evento',
      onRetry: () => store.refreshEvent(eventId),
    ),
  };
}
