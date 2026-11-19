import 'dart:async';
import 'dart:convert';

import 'package:crowded/app.dart';
import 'package:crowded/data/app_store.dart';
import 'package:crowded/data/auth_api.dart';
import 'package:crowded/data/connections_api.dart';
import 'package:crowded/data/events_api.dart';
import 'package:crowded/data/notifications_api.dart';
import 'package:crowded/screens/import/access_code_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

final _now = DateTime(2026, 6, 20, 15);
const _event = '30000000-0000-4000-8000-0000000000aa';
const _joined =
    '{"connectionId":"c-1","event":{"id":"$_event","name":"Arraial",'
    '"startDateTime":"2026-06-23T20:00:00Z","doorsOpenAt":null,'
    '"venueName":"Fontainhas"}}';
const _mobile = Size(390, 844);
const _desktop = Size(1440, 900);

/// P21 with Core's `POST /connections`, through a fake HTTP client.
void main() {
  // No camera in widget tests: a button stands in for a scanned QR code.
  setUp(() {
    qrScannerBuilder = (context, onCode) => Center(
      child: TextButton(
        onPressed: () => onCode('SAOJOAO26'),
        // White on the dark viewfinder, as the camera hint is.
        child: const Text(
          'Ler código QR',
          style: TextStyle(color: Colors.white),
        ),
      ),
    );
  });

  /// The codes sent: the list that follows a connection is not one.
  List<http.Request> posted(List<http.Request> sent) =>
      sent.where((request) => request.method == 'POST').toList();

  Future<(AppStore, List<http.Request>)> pump(
    WidgetTester tester,
    Future<http.Response> Function(http.Request) handler, {
    Size size = _mobile,
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final sent = <http.Request>[];
    final store = AppStore(
      clock: () => _now,
      notifications: _QuietNotificationsApi(),
      events: _OneEventApi(),
      session: AuthSession(
        accessToken: 'token',
        expiresAt: _now.add(const Duration(hours: 1)),
        userId: 'u-1',
        role: 'PARTICIPANT',
        email: 'ana@crowded.test',
      ),
      connectionsApi: HttpConnectionsApi(
        token: () => 'token',
        client: MockClient((request) {
          sent.add(request);
          return handler(request);
        }),
        baseUrl: 'https://crowded.test/api',
      ),
    );
    await tester.pumpWidget(
      CrowdedApp(store: store, initialLocation: '/access-code'),
    );
    await tester.pumpAndSettle();
    return (store, sent);
  }

  testWidgets('connects with the QR code and opens the event page', (
    tester,
  ) async {
    final (store, sent) = await pump(
      tester,
      (_) async => http.Response(_joined, 201),
    );

    await tester.tap(find.text('Ler código QR'));
    await tester.pumpAndSettle();

    expect(jsonDecode(posted(sent).single.body), {'accessCode': 'SAOJOAO26'});
    expect(store.isConnected(_event), isTrue);
    expect(find.text('Arraial'), findsWidgets);
    expect(find.byType(AccessCodeScreen), findsNothing);
  });

  testWidgets('connects with the typed code in the same way', (tester) async {
    final (store, sent) = await pump(
      tester,
      (_) async => http.Response(_joined, 200),
    );

    await tester.enterText(find.byType(TextField), ' saojoao26 ');
    await tester.tap(find.text('Abrir evento'));
    await tester.pumpAndSettle();

    expect(jsonDecode(posted(sent).single.body), {'accessCode': 'saojoao26'});
    expect(store.isConnected(_event), isTrue);
    expect(find.text('Arraial'), findsWidgets);
  });

  testWidgets('says the code was not found, and stays on the screen', (
    tester,
  ) async {
    final (store, _) = await pump(
      tester,
      (_) async => http.Response('{"code":"ACCESS_CODE_NOT_FOUND"}', 404),
    );

    await tester.enterText(find.byType(TextField), 'NOPE2026');
    await tester.tap(find.text('Abrir evento'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Código não encontrado.'), findsOneWidget);
    expect(find.byType(AccessCodeScreen), findsOneWidget);
    expect(store.isConnected(_event), isFalse);
  });

  testWidgets('says when Core cannot be reached', (tester) async {
    await pump(tester, (_) async => throw http.ClientException('offline'));

    await tester.enterText(find.byType(TextField), 'SAOJOAO26');
    await tester.tap(find.text('Abrir evento'));
    await tester.pumpAndSettle();

    expect(
      find.text('Não foi possível abrir o evento. Tenta outra vez.'),
      findsOneWidget,
    );
    expect(find.byType(AccessCodeScreen), findsOneWidget);
  });

  testWidgets('says when the session ended', (tester) async {
    await pump(tester, (_) async => http.Response('', 401));

    await tester.enterText(find.byType(TextField), 'SAOJOAO26');
    await tester.tap(find.text('Abrir evento'));
    await tester.pumpAndSettle();

    expect(
      find.text('A tua sessão terminou. Entra outra vez.'),
      findsOneWidget,
    );
    expect(find.byType(AccessCodeScreen), findsOneWidget);
  });

  testWidgets('forgets the events joined through Core on sign-out', (
    tester,
  ) async {
    final (store, _) = await pump(
      tester,
      (_) async => http.Response(_joined, 201),
    );
    await tester.enterText(find.byType(TextField), 'SAOJOAO26');
    await tester.tap(find.text('Abrir evento'));
    await tester.pumpAndSettle();
    expect(store.isConnected(_event), isTrue);

    store.signOut();
    await tester.pumpAndSettle();

    expect(store.isConnected(_event), isFalse);
  });

  testWidgets('sends one request while Core answers', (tester) async {
    final answer = Completer<http.Response>();
    final (_, sent) = await pump(tester, (_) => answer.future);

    await tester.enterText(find.byType(TextField), 'SAOJOAO26');
    await tester.tap(find.text('Abrir evento'));
    await tester.pump();
    await tester.tap(find.text('A abrir o evento'));
    await tester.tap(find.text('Ler código QR'));
    await tester.pump();

    expect(posted(sent), hasLength(1));
    answer.complete(http.Response(_joined, 201));
    await tester.pumpAndSettle();
    expect(find.text('Arraial'), findsWidgets);
  });

  group('accessibility', () {
    /// The WCAG 2.1 AA checks Flutter can run: every tap target has a label,
    /// and text has enough contrast.
    Future<void> expectAccessible(WidgetTester tester) async {
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(textContrastGuideline));
    }

    for (final size in [_mobile, _desktop]) {
      testWidgets('P21 meets the guidelines at $size', (tester) async {
        final semantics = tester.ensureSemantics();
        final answer = Completer<http.Response>();
        await pump(tester, (_) => answer.future, size: size);
        await expectAccessible(tester);

        // While Core answers.
        await tester.enterText(find.byType(TextField), 'NOPE2026');
        await tester.tap(find.text('Abrir evento'));
        await tester.pump();
        await expectAccessible(tester);

        // The code was not found.
        answer.complete(http.Response('{"code":"ACCESS_CODE_NOT_FOUND"}', 404));
        await tester.pumpAndSettle();
        await expectAccessible(tester);
        semantics.dispose();
      });
    }

    testWidgets('P21 says why the code failed in text, not only in colour', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await pump(
        tester,
        (_) async => http.Response('{"code":"ACCESS_CODE_NOT_FOUND"}', 404),
      );

      await tester.enterText(find.byType(TextField), 'NOPE2026');
      await tester.tap(find.text('Abrir evento'));
      await tester.pumpAndSettle();

      expect(
        find.bySemanticsLabel(RegExp('Código não encontrado')),
        findsOneWidget,
      );
      semantics.dispose();
    });
  });
}

class _QuietNotificationsApi implements NotificationsApi {
  @override
  Future<NotificationPage> list(String token) async =>
      const NotificationPage(notifications: [], unread: 0);

  @override
  Future<void> markRead(String token, String notificationId) async {}

  @override
  Stream<LiveEvent> live(String token) => StreamController<LiveEvent>().stream;
}

/// Core's page of the event the code connects to.
class _OneEventApi implements EventsApi {
  EventPage _page() => eventPageFromJson({
    'id': _event,
    'name': 'Arraial',
    'status': 'SCHEDULED',
    'startDateTime': '2026-06-23T20:00:00Z',
    'venue': {'name': 'Fontainhas', 'district': 'Porto'},
    'organization': {'id': 'o-1', 'name': 'Arraiais do Porto'},
  })!;

  @override
  Future<EventPage> page(String token, String eventId) async => _page();

  @override
  Future<List<CoreChange>> changes(String token, String eventId) async => [];

  @override
  Stream<EventPage> live(String token, String eventId) =>
      StreamController<EventPage>().stream;
}
