import 'dart:async';
import 'dart:convert';

import 'package:crowded/app.dart';
import 'package:crowded/data/app_store.dart';
import 'package:crowded/data/auth_api.dart';
import 'package:crowded/data/connections_api.dart';
import 'package:crowded/data/notifications_api.dart';
import 'package:crowded/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

final _now = DateTime(2026, 11, 18, 15);

String _time(DateTime time) => time.toUtc().toIso8601String();

Map<String, Object?> _cancellation() => {
  'id': 'ch-1',
  'type': 'CANCELLATION',
  'severity': 'CRITICAL',
  'previousValue': <String, Object?>{},
  'newValue': <String, Object?>{},
  'ticketStillValid': false,
  'refundInfo': null,
  'message': 'Cancelado.',
  'provenance': {
    'sourceType': 'ORGANIZATION',
    'sourceName': 'Margens',
    'lastUpdatedAt': _time(_now),
    'confirmed': true,
  },
};

Map<String, Object?> _alert(String severity) => {
  'id': 'a-$severity',
  'type': 'ENTRANCE_CLOSURE',
  'severity': severity,
  'message': 'Entrada Sul fechada.',
  'sentAt': _time(_now),
};

/// One connection as `GET /me/connections` gives it; the doors open two
/// hours before the start.
Map<String, Object?> _connection(
  String id,
  String name, {
  required DateTime start,
  String type = 'TICKET',
  String status = 'SCHEDULED',
  Map<String, Object?>? change,
  List<Map<String, Object?>> alerts = const [],
  Map<String, Object?>? ticket,
}) => {
  'connectionId': 'c-$id',
  'type': type,
  'event': {
    'id': id,
    'name': name,
    'status': status,
    'startDateTime': _time(start),
    'doorsOpenAt': _time(start.subtract(const Duration(hours: 2))),
    'venueName': 'Cais da Ribeira',
    'latestChange': change,
    'activeAlerts': alerts,
  },
  'ticket': ticket,
};

/// The events of the list, in Core's order: those not over first.
List<Map<String, Object?>> _events() => [
  _connection(
    'e-1',
    'Concerto no Cais',
    start: DateTime(2026, 11, 20, 21),
    alerts: [_alert('WARNING')],
    ticket: {'gate': 'A', 'sector': null, 'seat': '14', 'hasEntryCode': true},
  ),
  _connection(
    'e-2',
    'Festival Margens',
    start: DateTime(2026, 11, 27, 22),
    status: 'CANCELLED',
    change: _cancellation(),
    ticket: {'gate': 'B', 'sector': null, 'seat': '3', 'hasEntryCode': true},
  ),
  _connection(
    'e-3',
    'Arraial de São João',
    start: DateTime(2026, 12, 3, 20),
    type: 'ACCESS_CODE',
  ),
];

http.Response _list(List<Map<String, Object?>> connections) => http.Response(
  jsonEncode({'connections': connections, 'syncedAt': _time(_now)}),
  200,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

/// Core's side of the list: what it answers, and the requests it got.
class _Core {
  _Core(this.answer);

  Future<http.Response> Function() answer;
  final requests = <http.Request>[];

  Future<http.Response> handle(http.Request request) {
    requests.add(request);
    return answer();
  }
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

Future<AppStore> _pump(
  WidgetTester tester,
  _Core core, {
  Size size = const Size(390, 4000),
  bool settle = true,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final store = AppStore(
    clock: () => _now,
    notifications: _QuietNotificationsApi(),
    session: AuthSession(
      accessToken: 'token-123',
      expiresAt: _now.add(const Duration(hours: 1)),
      userId: 'u-1',
      role: 'PARTICIPANT',
      email: 'ana@crowded.test',
    ),
    connectionsApi: HttpConnectionsApi(
      token: () => 'token-123',
      client: MockClient(core.handle),
      baseUrl: 'https://crowded.test/api',
    ),
  );
  await tester.pumpWidget(CrowdedApp(store: store, initialLocation: '/events'));
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump();
  }
  return store;
}

void main() {
  group('P1 with the events of Core', () {
    testWidgets('asks Core for the events of the signed-in participant', (
      tester,
    ) async {
      final core = _Core(() async => _list(_events()));
      await _pump(tester, core);

      final request = core.requests.single;
      expect(request.method, 'GET');
      expect(request.url.toString(), 'https://crowded.test/api/me/connections');
      expect(request.headers['Authorization'], 'Bearer token-123');
    });

    testWidgets('lists each event with its time, doors, venue, change and '
        'alerts', (tester) async {
      await _pump(tester, _Core(() async => _list(_events())));

      expect(find.text('Concerto no Cais'), findsOneWidget);
      expect(find.text('21:00'), findsOneWidget);
      expect(find.text('19:00'), findsOneWidget);
      expect(find.text('ABERTURA'), findsNWidgets(3));
      expect(find.text('Cais da Ribeira'), findsNWidgets(3));
      expect(find.text('Atenção · 1 alerta ativo'), findsOneWidget);
      expect(find.text('Importante · Evento cancelado'), findsOneWidget);
      expect(find.text('Bilhete'), findsOneWidget);
      expect(find.text('Público'), findsOneWidget);
      expect(find.text('A'), findsOneWidget);
      expect(find.text('14'), findsOneWidget);
    });

    testWidgets('keeps the order of Core, the events not over first', (
      tester,
    ) async {
      await _pump(tester, _Core(() async => _list(_events())));

      double top(String name) => tester.getTopLeft(find.text(name)).dy;
      expect(top('Concerto no Cais'), lessThan(top('Festival Margens')));
      expect(top('Festival Margens'), lessThan(top('Arraial de São João')));
    });

    testWidgets('highlights a cancelled event with words and an icon, not '
        'colour alone', (tester) async {
      final semantics = tester.ensureSemantics();
      await _pump(tester, _Core(() async => _list(_events())));

      expect(find.text('CANCELADO'), findsOneWidget);
      expect(find.text('Cancelado'), findsOneWidget);
      expect(find.byIcon(Icons.error_outline), findsOneWidget);
      expect(
        find.bySemanticsLabel(
          RegExp('Festival Margens.*Cancelado.*Importante · Evento cancelado'),
        ),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel(
          RegExp('Concerto no Cais.*Atenção · 1 alerta ativo'),
        ),
        findsOneWidget,
      );
      semantics.dispose();
    });

    testWidgets('counts the active alerts and names the most serious', (
      tester,
    ) async {
      final busy = _connection(
        'e-9',
        'Feira',
        start: DateTime(2026, 11, 22, 10),
        type: 'ACCESS_CODE',
        alerts: [_alert('INFO'), _alert('CRITICAL')],
      );
      await _pump(tester, _Core(() async => _list([busy])));

      expect(find.text('Importante · 2 alertas ativos'), findsOneWidget);
    });

    testWidgets('shows only the options to import a ticket or enter an '
        'access code when there are no events', (tester) async {
      await _pump(tester, _Core(() async => _list(const [])));

      expect(find.text('Ainda não tens eventos'), findsOneWidget);
      expect(find.text('Importar bilhete'), findsOneWidget);
      expect(find.text('Inserir código de acesso'), findsOneWidget);
      expect(find.byType(WalletCard), findsNothing);
      expect(find.byIcon(Icons.add), findsNothing);
    });

    testWidgets('shows that it is loading', (tester) async {
      final answer = Completer<http.Response>();
      await _pump(tester, _Core(() => answer.future), settle: false);
      await tester.pump();

      expect(find.text('A carregar os teus eventos'), findsOneWidget);
      expect(find.text('Ainda não tens eventos'), findsNothing);

      answer.complete(_list(_events()));
      await tester.pumpAndSettle();
      expect(find.text('A carregar os teus eventos'), findsNothing);
      expect(find.text('Concerto no Cais'), findsOneWidget);
    });

    testWidgets('shows an error and loads again on request', (tester) async {
      final core = _Core(() async => throw http.ClientException('offline'));
      await _pump(tester, core);

      expect(
        find.text('Não foi possível carregar os teus eventos'),
        findsOneWidget,
      );
      expect(find.text('Ainda não tens eventos'), findsNothing);

      core.answer = () async => _list(_events());
      await tester.tap(find.text('Tentar de novo'));
      await tester.pumpAndSettle();

      expect(core.requests, hasLength(2));
      expect(
        find.text('Não foi possível carregar os teus eventos'),
        findsNothing,
      );
      expect(find.text('Concerto no Cais'), findsOneWidget);
    });

    testWidgets('shows an error when Core answers with a problem', (
      tester,
    ) async {
      await _pump(
        tester,
        _Core(() async => http.Response('{"code":"BOOM"}', 500)),
      );

      expect(
        find.text('Não foi possível carregar os teus eventos'),
        findsOneWidget,
      );
    });

    testWidgets('keeps the events on screen when a later load fails', (
      tester,
    ) async {
      final core = _Core(() async => _list(_events()));
      final store = await _pump(tester, core);

      core.answer = () async => http.Response('', 502);
      await store.refreshConnections();
      await tester.pumpAndSettle();

      expect(store.connectionsStatus, ConnectionsStatus.failed);
      expect(
        find.text('Não foi possível carregar os teus eventos'),
        findsOneWidget,
      );
      expect(find.text('Concerto no Cais'), findsOneWidget);
    });

    testWidgets('a pull refreshes the list', (tester) async {
      final core = _Core(() async => _list(_events()));
      await _pump(tester, core, size: const Size(390, 844));

      await tester.fling(
        find.byType(ListView).first,
        const Offset(0, 400),
        1000,
      );
      await tester.pumpAndSettle();

      expect(core.requests, hasLength(2));
    });
  });

  for (final size in [
    const Size(375, 812),
    const Size(390, 844),
    const Size(1440, 900),
  ]) {
    testWidgets('P1 fits and meets the accessibility guidelines at $size', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pump(tester, _Core(() async => _list(_events())), size: size);

      expect(tester.takeException(), isNull);
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(textContrastGuideline));

      semantics.dispose();
    });

    testWidgets('P1 empty and failed meet the guidelines at $size', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pump(tester, _Core(() async => _list(const [])), size: size);
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(textContrastGuideline));

      await tester.pumpWidget(const SizedBox());
      await _pump(
        tester,
        _Core(() async => throw http.ClientException('offline')),
        size: size,
      );
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(textContrastGuideline));

      semantics.dispose();
    });
  }
}
