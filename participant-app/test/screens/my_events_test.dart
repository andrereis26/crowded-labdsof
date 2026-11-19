import 'dart:async';

import 'package:crowded/app.dart';
import 'package:crowded/data/app_store.dart';
import 'package:crowded/data/auth_api.dart';
import 'package:crowded/data/connections_api.dart';
import 'package:crowded/data/models.dart';
import 'package:crowded/data/notifications_api.dart';
import 'package:crowded/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

final _now = DateTime(2026, 11, 18, 15);
const _art = EventArt(Color(0xFF2E4A8C), Color(0xFF0F1626));

Event _event(
  String id,
  String name, {
  required DateTime start,
  EventStatus status = EventStatus.scheduled,
  List<EventChange> changes = const [],
  List<EventAlert> alerts = const [],
  bool byCode = false,
}) => Event(
  id: id,
  name: name,
  venue: 'Cais da Ribeira',
  city: '',
  start: start,
  doorsOpen: start.subtract(const Duration(hours: 2)),
  art: _art,
  status: status,
  changes: changes,
  alerts: alerts,
  joinedWithAccessCode: byCode,
);

EventChange _cancellation() => EventChange(
  id: 'ch-1',
  kind: ChangeKind.cancelled,
  title: 'Evento cancelado',
  description: 'Cancelado pela organização.',
  detectedAt: _now,
  publisher: 'Margens',
  severity: Severity.critical,
);

EventAlert _alert(Severity severity) => EventAlert(
  message: 'Entrada Sul fechada.',
  publisher: 'Organização',
  sentAt: _now,
  severity: severity,
);

/// The events as the list of Core gives them: those not over first.
List<ConnectedEvent> _events() {
  final soon = _event(
    'e-1',
    'Concerto no Cais',
    start: DateTime(2026, 11, 20, 21),
    alerts: [_alert(Severity.warning)],
  );
  final cancelled = _event(
    'e-2',
    'Festival Margens',
    start: DateTime(2026, 11, 27, 22),
    status: EventStatus.cancelled,
    changes: [_cancellation()],
  );
  final byCode = _event(
    'e-3',
    'Arraial de São João',
    start: DateTime(2026, 12, 3, 20),
    byCode: true,
  );
  return [
    ConnectedEvent(
      event: soon,
      ticket: Ticket(id: 't-1', event: soon, gate: 'A', seat: '14'),
    ),
    ConnectedEvent(
      event: cancelled,
      ticket: Ticket(id: 't-2', event: cancelled, gate: 'B', seat: '3'),
    ),
    ConnectedEvent(event: byCode),
  ];
}

class _FakeConnectionsApi implements ConnectionsApi {
  _FakeConnectionsApi(this.answer);

  Future<MyConnections> Function() answer;
  int calls = 0;

  @override
  Future<MyConnections> myConnections() {
    calls++;
    return answer();
  }

  @override
  Future<JoinedEvent> joinWithAccessCode(String accessCode) =>
      throw UnimplementedError();
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
  _FakeConnectionsApi api, {
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
      accessToken: 'token',
      expiresAt: _now.add(const Duration(hours: 1)),
      userId: 'u-1',
      role: 'PARTICIPANT',
      email: 'ana@crowded.test',
    ),
    connectionsApi: api,
  );
  await tester.pumpWidget(CrowdedApp(store: store, initialLocation: '/events'));
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump();
  }
  return store;
}

MyConnections _list(List<ConnectedEvent> events) =>
    MyConnections(events: events, syncedAt: _now);

void main() {
  group('P1 with the events of Core', () {
    testWidgets('lists each event with its time, doors, venue, change and '
        'alerts', (tester) async {
      final api = _FakeConnectionsApi(() async => _list(_events()));
      await _pump(tester, api);

      expect(api.calls, 1);
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
      await _pump(tester, _FakeConnectionsApi(() async => _list(_events())));

      double top(String name) => tester.getTopLeft(find.text(name)).dy;
      expect(top('Concerto no Cais'), lessThan(top('Festival Margens')));
      expect(top('Festival Margens'), lessThan(top('Arraial de São João')));
    });

    testWidgets('highlights a cancelled event with words and an icon, not '
        'colour alone', (tester) async {
      final semantics = tester.ensureSemantics();
      await _pump(tester, _FakeConnectionsApi(() async => _list(_events())));

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
      final busy = _event(
        'e-9',
        'Feira',
        start: DateTime(2026, 11, 22, 10),
        alerts: [_alert(Severity.information), _alert(Severity.critical)],
      );
      await _pump(
        tester,
        _FakeConnectionsApi(() async => _list([ConnectedEvent(event: busy)])),
      );

      expect(find.text('Importante · 2 alertas ativos'), findsOneWidget);
    });

    testWidgets('shows only the options to import a ticket or enter an '
        'access code when there are no events', (tester) async {
      await _pump(tester, _FakeConnectionsApi(() async => _list(const [])));

      expect(find.text('Ainda não tens eventos'), findsOneWidget);
      expect(find.text('Importar bilhete'), findsOneWidget);
      expect(find.text('Inserir código de acesso'), findsOneWidget);
      expect(find.byType(WalletCard), findsNothing);
      expect(find.byIcon(Icons.add), findsNothing);
    });

    testWidgets('shows that it is loading', (tester) async {
      final answer = Completer<MyConnections>();
      await _pump(
        tester,
        _FakeConnectionsApi(() => answer.future),
        settle: false,
      );
      await tester.pump();

      expect(find.text('A carregar os teus eventos'), findsOneWidget);
      expect(find.text('Ainda não tens eventos'), findsNothing);

      answer.complete(_list(_events()));
      await tester.pumpAndSettle();
      expect(find.text('A carregar os teus eventos'), findsNothing);
      expect(find.text('Concerto no Cais'), findsOneWidget);
    });

    testWidgets('shows an error and loads again on request', (tester) async {
      final api = _FakeConnectionsApi(
        () async => throw const ConnectionsApiException('NETWORK'),
      );
      await _pump(tester, api);

      expect(
        find.text('Não foi possível carregar os teus eventos'),
        findsOneWidget,
      );
      expect(find.text('Ainda não tens eventos'), findsNothing);

      api.answer = () async => _list(_events());
      await tester.tap(find.text('Tentar de novo'));
      await tester.pumpAndSettle();

      expect(api.calls, 2);
      expect(
        find.text('Não foi possível carregar os teus eventos'),
        findsNothing,
      );
      expect(find.text('Concerto no Cais'), findsOneWidget);
    });

    testWidgets('keeps the events on screen when a later load fails', (
      tester,
    ) async {
      final api = _FakeConnectionsApi(() async => _list(_events()));
      final store = await _pump(tester, api);

      api.answer = () async => throw const ConnectionsApiException('NETWORK');
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
      final api = _FakeConnectionsApi(() async => _list(_events()));
      await _pump(tester, api, size: const Size(390, 844));

      await tester.fling(
        find.byType(ListView).first,
        const Offset(0, 400),
        1000,
      );
      await tester.pumpAndSettle();

      expect(api.calls, 2);
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
      await _pump(
        tester,
        _FakeConnectionsApi(() async => _list(_events())),
        size: size,
      );

      expect(tester.takeException(), isNull);
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(textContrastGuideline));

      semantics.dispose();
    });

    testWidgets('P1 empty and failed meet the guidelines at $size', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        _FakeConnectionsApi(() async => _list(const [])),
        size: size,
      );
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(textContrastGuideline));

      await tester.pumpWidget(const SizedBox());
      await _pump(
        tester,
        _FakeConnectionsApi(
          () async => throw const ConnectionsApiException('NETWORK'),
        ),
        size: size,
      );
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(textContrastGuideline));

      semantics.dispose();
    });
  }
}
