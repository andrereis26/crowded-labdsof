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

/// The WCAG 2.1 AA checks Flutter can run: every tap target has a label, and
/// text has enough contrast.
Future<void> _expectAccessible(WidgetTester tester) async {
  await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
  await expectLater(tester, meetsGuideline(textContrastGuideline));
}

Future<void> _pump(
  WidgetTester tester,
  Size size,
  MockClientHandler handler,
) async {
  tester.view.physicalSize = size;
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
  await tester.pumpWidget(CrowdedApp(store: store, initialLocation: '/import'));
  await tester.pumpAndSettle();
}

void main() {
  setUp(() {
    ticketPdfPicker = () async => (bytes: Uint8List(3), name: 'bilhete.pdf');
  });

  for (final size in [const Size(390, 844), const Size(1440, 900)]) {
    testWidgets('P2 meets the accessibility guidelines at $size', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final answer = Completer<http.Response>();
      await _pump(tester, size, (_) => answer.future);

      await _expectAccessible(tester);

      // While Core reads the ticket.
      await tester.tap(find.text('PDF do bilhete'));
      await tester.pump();
      await tester.pump();
      await _expectAccessible(tester);

      // The ticket has no QR code.
      answer.complete(http.Response('{"code":"QR_CODE_NOT_FOUND"}', 400));
      await tester.pumpAndSettle();
      await _expectAccessible(tester);

      semantics.dispose();
    });
  }

  for (final size in [const Size(390, 844), const Size(1440, 900)]) {
    testWidgets('P2a and the refusals meet the accessibility guidelines at '
        '$size', (tester) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        size,
        (_) async => http.Response(
          '{"status":"CONNECTED","ticketId":"t-1","connectionId":"t-1",'
          '"event":{"id":"e-1","name":"Festival Margens",'
          '"startDateTime":"2026-11-27T22:00:00Z",'
          '"doorsOpenAt":"2026-11-27T20:00:00Z","venueName":"Cais"},'
          '"ticket":{"gate":"A","sector":"2","seat":"14"}}',
          200,
        ),
      );

      // The ticket was added.
      await tester.tap(find.text('PDF do bilhete'));
      await tester.pumpAndSettle();
      expect(find.text('Adicionaste o evento Festival Margens'), findsOne);
      await _expectAccessible(tester);

      // Another account has the ticket, on a fresh app.
      await tester.pumpWidget(const SizedBox());
      await _pump(
        tester,
        size,
        (_) async => http.Response('{"code":"TICKET_ALREADY_IMPORTED"}', 409),
      );
      await tester.tap(find.text('PDF do bilhete'));
      await tester.pumpAndSettle();
      expect(find.text('Este bilhete já está ligado a outra conta'), findsOne);
      await _expectAccessible(tester);

      semantics.dispose();
    });
  }

  testWidgets('P2 announces that the ticket is being read', (tester) async {
    final semantics = tester.ensureSemantics();
    final answer = Completer<http.Response>();
    await _pump(tester, const Size(390, 844), (_) => answer.future);

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pump();
    await tester.pump();

    final reading = tester.getSemantics(find.byType(AppLoadingState));
    expect(reading.label, 'A ler o bilhete');
    expect(reading.flagsCollection.isLiveRegion, isTrue);

    answer.complete(http.Response('{"code":"QR_CODE_NOT_FOUND"}', 400));
    await tester.pumpAndSettle();
    semantics.dispose();
  });

  testWidgets('P2 says why an import failed in text, not only in colour', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await _pump(
      tester,
      const Size(390, 844),
      (_) async => http.Response('{"code":"RATE_LIMITED"}', 429),
    );

    await tester.tap(find.text('PDF do bilhete'));
    await tester.pumpAndSettle();

    expect(
      find.bySemanticsLabel(
        RegExp('Importaste demasiados bilhetes na última hora'),
      ),
      findsOneWidget,
    );
    semantics.dispose();
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
