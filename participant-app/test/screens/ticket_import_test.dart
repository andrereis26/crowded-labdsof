import 'dart:async';
import 'dart:typed_data';

import 'package:crowded/app.dart';
import 'package:crowded/data/app_store.dart';
import 'package:crowded/data/auth_api.dart';
import 'package:crowded/data/notifications_api.dart';
import 'package:crowded/data/tickets_api.dart';
import 'package:crowded/screens/import/import_screen.dart';
import 'package:crowded/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

final _now = DateTime(2026, 10, 17, 15);

/// P2 with Core's `POST /tickets`, through a fake HTTP client.
void main() {
  setUp(() {
    ticketPdfPicker = () async => (bytes: Uint8List(3), name: 'bilhete.pdf');
  });

  Future<AppStore> pump(WidgetTester tester, MockClientHandler handler) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final store = AppStore(
      clock: () => _now,
      notifications: _QuietNotificationsApi(),
      session: AuthSession(
        accessToken: 'token',
        expiresAt: _now.add(const Duration(hours: 1)),
        userId: 'u-1',
        role: 'PARTICIPANT',
        email: 'ana@crowded.test',
      ),
      ticketsApi: HttpTicketsApi(
        token: () => 'token',
        client: MockClient(handler),
        baseUrl: 'https://crowded.test/api',
      ),
    );
    await tester.pumpWidget(
      CrowdedApp(store: store, initialLocation: '/import'),
    );
    await tester.pumpAndSettle();
    return store;
  }

  testWidgets('asks for the event code when Core keeps the import pending', (
    tester,
  ) async {
    final store = await pump(
      tester,
      (_) async => http.Response(
        '{"status":"NEEDS_EVENT_CODE","importId":"i-1","ticket":{}}',
        200,
      ),
    );

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pumpAndSettle();

    expect(
      find.text('Não conseguimos associar o bilhete a um evento'),
      findsOneWidget,
    );
    expect(store.pendingImport?.importId, 'i-1');
  });

  const connected =
      '{"status":"CONNECTED","ticketId":"t-1","connectionId":"t-1",'
      '"event":{"id":"e-1","name":"Festival Margens",'
      '"startDateTime":"2026-11-27T22:00:00Z",'
      '"doorsOpenAt":"2026-11-27T20:00:00Z","venueName":"Cais da Ribeira"},'
      '"ticket":{"gate":"A","sector":"2","seat":"14"}}';

  testWidgets('adds the ticket at once with the registered event', (
    tester,
  ) async {
    final store = await pump(
      tester,
      (_) async => http.Response(connected, 200),
    );

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pumpAndSettle();

    expect(find.text('Adicionaste o evento Festival Margens'), findsOneWidget);
    expect(find.text('Cais da Ribeira'), findsOneWidget);
    expect(
      find.text('Porta A · Lugar 14 · código QR guardado'),
      findsOneWidget,
    );
    expect(
      find.text('Fonte: organização · dados do evento registado'),
      findsOneWidget,
    );
    expect(find.text('Não é o teu evento? Remover'), findsOneWidget);
    expect(store.pendingImport, isNull);
    expect(store.ticketFor('e-1')?.event.name, 'Festival Margens');
    expect(store.isConnected('e-1'), isTrue);
  });

  testWidgets('says that the ticket is already in use by another account', (
    tester,
  ) async {
    final store = await pump(
      tester,
      (_) async => http.Response('{"code":"TICKET_ALREADY_IMPORTED"}', 409),
    );

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pumpAndSettle();

    expect(
      find.text('Este bilhete já está ligado a outra conta'),
      findsOneWidget,
    );
    expect(store.pendingImport, isNull);
    expect(store.ticketFor('e-1'), isNull);
  });

  testWidgets('shows that the ticket has no QR code', (tester) async {
    final store = await pump(
      tester,
      (_) async => http.Response('{"code":"QR_CODE_NOT_FOUND"}', 400),
    );

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pumpAndSettle();

    expect(find.text('Não encontrámos o código QR do bilhete'), findsOneWidget);
    expect(store.pendingImport, isNull);
  });

  testWidgets('shows that the ticket is being read while Core works', (
    tester,
  ) async {
    final answer = Completer<http.Response>();
    await pump(tester, (_) => answer.future);

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pump(); // the file is chosen
    await tester.pump(); // the upload starts

    expect(find.byType(AppLoadingState), findsOneWidget);
    expect(find.text('A ler o bilhete'), findsOneWidget);
    expect(find.text('PDF do bilhete'), findsNothing);

    answer.complete(http.Response('{"code":"QR_CODE_NOT_FOUND"}', 400));
    await tester.pumpAndSettle();
  });

  testWidgets('says so when Core cannot be reached', (tester) async {
    await pump(tester, (_) async => throw http.ClientException('offline'));

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pumpAndSettle();

    expect(
      find.text('Não foi possível importar o bilhete. Tenta outra vez.'),
      findsOneWidget,
    );
    expect(find.text('PDF do bilhete'), findsOneWidget);
  });

  testWidgets('says so when too many tickets were imported', (tester) async {
    await pump(
      tester,
      (_) async => http.Response('{"code":"RATE_LIMITED"}', 429),
    );

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Importaste demasiados bilhetes na última hora. Tenta mais tarde.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('does nothing when no file is chosen', (tester) async {
    ticketPdfPicker = () async => null;
    var calls = 0;
    await pump(tester, (_) async {
      calls++;
      return http.Response('{}', 500);
    });

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pumpAndSettle();

    expect(calls, 0);
    expect(find.text('PDF do bilhete'), findsOneWidget);
  });
}

/// Notifications that stay quiet, so the signed-in store opens no connection.
class _QuietNotificationsApi implements NotificationsApi {
  @override
  Future<NotificationPage> list(String token) async =>
      const NotificationPage(notifications: [], unread: 0);

  @override
  Future<void> markRead(String token, String notificationId) async {}

  @override
  Stream<LiveEvent> live(String token) => StreamController<LiveEvent>().stream;
}
