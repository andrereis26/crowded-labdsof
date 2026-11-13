import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:go_router/go_router.dart';

import 'data/app_store.dart';
import 'dev/component_gallery.dart';
import 'data/models.dart';
import 'screens/account/account_screen.dart';
import 'screens/account/delete_account_screen.dart';
import 'screens/account/notification_preferences_screen.dart';
import 'screens/auth/sign_in_screen.dart';
import 'screens/account/contributions_screen.dart';
import 'screens/event/change_detail_screen.dart';
import 'screens/event/change_history_screen.dart';
import 'screens/event/directions_screen.dart';
import 'screens/event/entry_code_screen.dart';
import 'screens/event/estimates_screen.dart';
import 'screens/event/event_screen.dart';
import 'screens/event/left_event_screen.dart';
import 'screens/import/access_code_screen.dart';
import 'screens/import/import_result_screens.dart';
import 'screens/import/import_screen.dart';
import 'screens/notifications/live_banner.dart';
import 'screens/notifications/notifications_screen.dart';
import 'screens/report/recognition_screen.dart';
import 'screens/report/report_result_screen.dart';
import 'screens/report/report_screen.dart';
import 'screens/requests/requests_screen.dart';
import 'screens/shared/shell.dart';
import 'screens/wallet/wallet_screen.dart';
import 'ui/ui.dart';

/// Routes of the participant app. Screen codes (P1…) refer to the mockups.
GoRouter buildRouter({
  required AppStore store,
  String initialLocation = '/events',
}) => GoRouter(
  initialLocation: initialLocation,
  refreshListenable: store,
  redirect: (context, state) {
    // Signed-out users only see C1; signed-in users skip it.
    final atSignIn = state.matchedLocation == '/sign-in';
    if (!store.signedIn) return atSignIn ? null : '/sign-in';
    if (atSignIn) return '/events';
    // Event pages are only shown to connected participants.
    final eventId = state.pathParameters['id'];
    if (state.matchedLocation.startsWith('/event/') &&
        eventId != null &&
        store.eventById(eventId) != null &&
        !store.isConnected(eventId)) {
      return '/disconnected/$eventId';
    }
    return null;
  },
  routes: [
    GoRoute(
      path: '/sign-in',
      builder: (context, state) => const SignInScreen(), // C1
    ),
    StatefulShellRoute.indexedStack(
      builder: (context, state, shell) => AppShell(navigationShell: shell),
      branches: [
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/events',
              builder: (context, state) => const WalletScreen(), // P1
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/account',
              builder: (context, state) => const AccountScreen(), // C0
              routes: [
                GoRoute(
                  path: 'contributions',
                  builder: (context, state) =>
                      const ContributionsScreen(), // P20
                ),
              ],
            ),
          ],
        ),
      ],
    ),
    GoRoute(
      path: '/account/notifications',
      builder: (context, state) => const NotificationPreferencesScreen(), // C2
    ),
    GoRoute(
      path: '/account/delete',
      builder: (context, state) => const DeleteAccountScreen(), // C3
    ),
    GoRoute(
      path: '/notifications',
      builder: (context, state) => const NotificationsScreen(), // P8
    ),
    GoRoute(
      path: '/import',
      builder: (context, state) => const ImportScreen(), // P2
      routes: [
        GoRoute(
          path: 'added',
          builder: (context, state) => const TicketAddedScreen(), // P2a
        ),
        GoRoute(
          path: 'code',
          builder: (context, state) => const EventCodeScreen(), // P2b
        ),
        GoRoute(
          path: 'error/:reason',
          builder: (context, state) => ImportErrorScreen(
            outcome: ImportErrorScreen.outcomeFor(
              state.pathParameters['reason']!,
            ),
          ), // P2b
        ),
      ],
    ),
    GoRoute(
      path: '/access-code',
      builder: (context, state) => const AccessCodeScreen(), // P21
    ),
    GoRoute(
      path: '/disconnected/:id',
      builder: (context, state) =>
          LeftEventScreen(eventId: state.pathParameters['id']!), // P8
    ),
    // "Reportar" in the tab bar, the header or the floating button: the
    // report form of the event in progress, or of the next one today.
    GoRoute(
      path: '/report',
      redirect: (context, state) {
        final event = store.eventsToday.firstOrNull;
        return event == null ? '/events' : '/event/${event.id}/report';
      },
    ),
    GoRoute(
      path: '/event/:id',
      builder: (context, state) => EventScreen(
        eventId: state.pathParameters['id']!,
        confirmationId: state.uri.queryParameters['confirm'],
      ), // P5, P10, P13
      routes: [
        GoRoute(
          path: 'report',
          builder: (context, state) => ReportScreen(
            eventId: state.pathParameters['id']!,
            category: ReportCategory.values
                .asNameMap()[state.uri.queryParameters['category']],
          ), // P11
        ),
        GoRoute(
          path: 'report-result',
          builder: (context, state) => ReportResultScreen(
            eventId: state.pathParameters['id']!,
            away: state.uri.queryParameters['status'] == 'away',
          ), // P12
        ),
        GoRoute(
          path: 'estimates',
          builder: (context, state) =>
              EstimatesScreen(eventId: state.pathParameters['id']!), // P14
        ),
        GoRoute(
          path: 'requests',
          builder: (context, state) =>
              RequestsScreen(eventId: state.pathParameters['id']!), // P17
        ),
        GoRoute(
          path: 'directions',
          builder: (context, state) =>
              DirectionsScreen(eventId: state.pathParameters['id']!), // P18
        ),
        GoRoute(
          path: 'code',
          builder: (context, state) =>
              EntryCodeScreen(eventId: state.pathParameters['id']!), // P19
        ),
        GoRoute(
          path: 'history',
          builder: (context, state) =>
              ChangeHistoryScreen(eventId: state.pathParameters['id']!), // P6
        ),
        GoRoute(
          path: 'change/:changeId',
          builder: (context, state) => ChangeDetailScreen(
            eventId: state.pathParameters['id']!,
            changeId: state.pathParameters['changeId']!,
          ), // P7
        ),
      ],
    ),
    GoRoute(
      path: '/recognition/:id',
      builder: (context, state) =>
          RecognitionScreen(recognitionId: state.pathParameters['id']!), // P15
    ),
    GoRoute(
      path: '/dev/components',
      builder: (context, state) => const ComponentGallery(),
    ),
  ],
);

class CrowdedApp extends StatefulWidget {
  const CrowdedApp({super.key, this.store, this.initialLocation = '/events'});

  final AppStore? store;
  final String initialLocation;

  @override
  State<CrowdedApp> createState() => _CrowdedAppState();
}

class _CrowdedAppState extends State<CrowdedApp> {
  late final AppStore _store = widget.store ?? AppStore();
  late final GoRouter _router = buildRouter(
    store: _store,
    initialLocation: widget.initialLocation,
  );

  @override
  void dispose() {
    _router.dispose();
    if (widget.store == null) _store.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AppStoreScope(
      store: _store,
      child: MaterialApp.router(
        title: 'Crowded',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.light(),
        locale: const Locale('pt', 'PT'),
        supportedLocales: const [Locale('pt', 'PT')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        routerConfig: _router,
        // Live banners show above every page (P8).
        builder: (context, child) => LiveBannerHost(
          onOpen: (location) => _router.push(location),
          child: child ?? const SizedBox.shrink(),
        ),
      ),
    );
  }
}
