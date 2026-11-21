import 'dart:typed_data';

import 'package:crowded/app.dart';
import 'package:crowded/data/app_store.dart';
import 'package:crowded/data/auth_api.dart';

import 'dart:async';

import 'package:crowded/data/events_api.dart';
import 'package:crowded/data/models.dart';
import 'package:crowded/data/notifications_api.dart';
import 'package:crowded/screens/event/entry_code_screen.dart';
import 'package:crowded/screens/import/access_code_screen.dart';
import 'package:crowded/screens/import/import_screen.dart';
import 'package:crowded/ui/ui.dart';
import 'package:crowded/utils/pt_format.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

/// Saturday 17 October 2026, 15:00.
final _now = DateTime(2026, 10, 17, 15);

const _email = 'ines.martins@email.pt';

AuthSession _session(String email, {String role = 'PARTICIPANT'}) =>
    AuthSession(accessToken: 'token', userId: 'u-1', role: role, email: email);

/// Core's registration and sign-in, without a network: one account, whose
/// password is `palavra-secreta`, plus the ones registered in the test.
class _FakeAuthApi implements AuthApi {
  final accounts = {_email: 'palavra-secreta', 'equipa@ribeira-live.test': 'x'};
  String? failWith;

  @override
  Future<void> register({
    required String email,
    required String password,
  }) async {
    if (failWith != null) throw AuthException(failWith!);
    if (accounts.containsKey(email)) {
      throw const AuthException('EMAIL_ALREADY_REGISTERED');
    }
    accounts[email] = password;
  }

  @override
  Future<AuthSession> signIn({
    required String email,
    required String password,
  }) async {
    if (failWith != null) throw AuthException(failWith!);
    if (accounts[email] != password) {
      throw const AuthException('INVALID_CREDENTIALS');
    }
    return _session(
      email,
      role: email.endsWith('ribeira-live.test')
          ? 'ORGANIZATION_MEMBER'
          : 'PARTICIPANT',
    );
  }

  /// Who `GET /me` answers at start-up; nobody unless a test sets it.
  AuthSession? restored;
  int signOuts = 0;
  void Function()? ended;

  /// Holds the answer of `GET /me` until the test completes it.
  final restoreAnswered = Completer<void>();

  @override
  Future<AuthSession?> restore() async {
    await restoreAnswered.future;
    return restored;
  }

  @override
  Future<void> signOut() async => signOuts++;

  @override
  http.Client client() => http.Client();

  @override
  set onEnded(void Function()? callback) => ended = callback;
}

AppNotification _notification(
  String id,
  NotificationKind kind, {
  required String title,
  required Duration ago,
  String eventId = 'festival-margens',
  String eventName = 'Festival Margens',
  String? body,
  String? changeId,
  bool read = false,
}) => AppNotification(
  id: id,
  kind: kind,
  eventId: eventId,
  eventName: eventName,
  typeLabel: kind.typeLabel,
  title: title,
  body: body,
  receivedAt: _now.subtract(ago),
  changeId: changeId,
  read: read,
);

/// The participant's notifications in Notifications, without a network.
/// Each call to [live] opens a stream the test drives with [send], [drop]
/// and [refuse].
class _FakeNotificationsApi implements NotificationsApi {
  _FakeNotificationsApi([List<AppNotification>? notifications])
    : stored =
          notifications ??
          [
            _notification(
              'n-alert',
              NotificationKind.alert,
              title: 'Porta B fechada. Use a Porta C.',
              ago: const Duration(minutes: 3),
            ),
            _notification(
              'n-change',
              NotificationKind.officialChange,
              eventId: 'a-tempestade',
              eventName: 'A Tempestade',
              title: 'Adiado para SÁB 8 NOV · 21:30',
              body: 'Era SÁB 25 OUT.\nTraz o bilhete.',
              changeId: 'tempestade-date',
              ago: const Duration(hours: 2),
            ),
            _notification(
              'n-weather',
              NotificationKind.weather,
              eventId: 'jazz-no-parque',
              eventName: 'Jazz no Parque',
              title: 'Aviso laranja: chuva forte no Porto amanhã',
              ago: const Duration(days: 2),
              read: true,
            ),
            _notification(
              'n-left',
              NotificationKind.officialChange,
              eventId: 'mercado-outono',
              eventName: 'Mercado de Outono',
              title: 'Abertura adiada para as 11:00',
              ago: const Duration(days: 6),
              read: true,
            ),
          ];

  final List<AppNotification> stored;
  final read = <String>[];
  int lists = 0;
  int connections = 0;
  String? failListWith;

  /// While set, [list] answers only once it completes.
  Completer<void>? listGate;
  StreamController<LiveEvent>? _live;

  /// Copies, as each response from the server is a new object.
  @override
  Future<NotificationPage> list() async {
    lists++;
    if (failListWith != null) throw NotificationsException(failListWith!);
    final stored = [...this.stored];
    await listGate?.future;
    final copies = [
      for (final n in stored)
        _notification(
          n.id,
          n.kind,
          eventId: n.eventId,
          eventName: n.eventName,
          title: n.title,
          body: n.body,
          changeId: n.changeId,
          ago: _now.difference(n.receivedAt),
          read: n.read || read.contains(n.id),
        ),
    ];
    return NotificationPage(
      notifications: copies,
      unread: copies.where((n) => !n.read).length,
    );
  }

  @override
  Future<void> markRead(String notificationId) async {
    read.add(notificationId);
  }

  @override
  Stream<LiveEvent> live() {
    connections++;
    _live = StreamController<LiveEvent>(
      onListen: () => _live!.add(const LiveOpened()),
    );
    return _live!.stream;
  }

  /// Stores [notification] and sends it on the open stream.
  void send(AppNotification notification, {bool store = true}) {
    if (store) stored.add(notification);
    _live!.add(LiveNotificationEvent(notification));
  }

  void drop() {
    _live!.addError(const NotificationsException('NETWORK'));
    _live!.close();
  }

  void refuse() {
    _live!.addError(const NotificationsException('UNAUTHORIZED'));
    _live!.close();
  }
}

/// Core's event pages, without a network: one event the participant is
/// connected to, whose answers a test can change between loads.
class _FakeEventsApi implements EventsApi {
  static const id = '30000000-0000-4000-8000-000000000001';

  String status = 'POSTPONED';
  String start = '2026-11-07T21:30:00Z';

  /// Core's `crowd`: null until the organization publishes a level.
  Map<String, Object?>? crowd;

  /// Core's `queues`: the points with a published waiting time.
  List<Map<String, Object?>> queues = [];
  List<Map<String, Object?>> history = [
    _coreChange('c-doors', 'TIME_CHANGE', {
      'doorsOpenAt': '2026-11-06T19:30:00Z',
    }, at: _now.subtract(_doorsChangedAgo).toUtc().toIso8601String()),
    _coreChange('c-date', 'POSTPONEMENT', {
      'startDateTime': '2026-11-07T21:30:00Z',
    }, at: '2026-10-02T09:05:00Z'),
  ];

  /// While set, every request fails with this code.
  String? failWith;
  int pages = 0;

  /// While set, [page] answers only once it completes.
  Completer<void>? gate;

  /// The stream [live] opened last, which a test drives with [publish] and
  /// [drop].
  StreamController<EventPage>? _live;
  int connections = 0;

  /// Whether the page's stream is open and listened to.
  bool get streaming => _live?.hasListener ?? false;

  EventPage _current() => eventPageFromJson({
    'id': id,
    'name': 'Festival Margens do Douro',
    'status': status,
    'startDateTime': start,
    'venue': {'name': 'Cais da Ribeira', 'district': 'Porto'},
    'organization': {'id': 'o-1', 'name': 'Ribeira Live'},
    'latestChange': history.firstOrNull,
    'crowd': crowd,
    'queues': queues,
  })!;

  @override
  Future<EventPage> page(String eventId) async {
    pages++;
    await gate?.future;
    if (failWith != null) throw EventsException(failWith!);
    return _current();
  }

  /// Like Core, sends the page as soon as the stream opens.
  @override
  Stream<EventPage> live(String eventId) {
    connections++;
    final live = StreamController<EventPage>();
    live.onListen = () {
      if (failWith == null) {
        live.add(_current());
      } else {
        live
          ..addError(EventsException(failWith!))
          ..close();
      }
    };
    return (_live = live).stream;
  }

  /// Sends the page as it is now, as Core does after a change.
  void publish() => _live!.add(_current());

  void drop() {
    _live!
      ..addError(const EventsException('NETWORK'))
      ..close();
  }

  @override
  Future<List<CoreChange>> changes(String eventId) async {
    if (failWith != null) throw EventsException(failWith!);
    return [for (final c in history) coreChangeFromJson(c)!];
  }
}

Map<String, Object?> _coreChange(
  String id,
  String type,
  Map<String, Object?> newValue, {
  required String at,
}) => {
  'id': id,
  'type': type,
  'previousValue': const {'startDateTime': '2026-10-31T21:30:00Z'},
  'newValue': newValue,
  'ticketStillValid': true,
  'provenance': {
    'sourceType': 'ORGANIZATION',
    'sourceName': 'Ribeira Live',
    'lastUpdatedAt': at,
    'confirmed': true,
  },
};

/// When the latest change in [_FakeEventsApi] was published: today.
const _doorsChangedAgo = Duration(minutes: 88);

const _mobile = Size(390, 844);
const _desktop = Size(1440, 1000);

Future<AppStore> _pump(
  WidgetTester tester, {
  String location = '/events',
  Size size = _mobile,
  bool signedIn = true,
  AuthApi? auth,
  NotificationsApi? notifications,
  EventsApi? events,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final store = AppStore(
    clock: () => _now,
    auth: auth ?? _FakeAuthApi(),
    notifications: notifications ?? _FakeNotificationsApi(),
    events: events ?? _FakeEventsApi(),
    session: signedIn ? _session(_email) : null,
  );
  await tester.pumpWidget(CrowdedApp(store: store, initialLocation: location));
  await tester.pumpAndSettle();
  return store;
}

void main() {
  // No camera in widget tests: a button stands in for a scanned QR code.
  setUpAll(() {
    // No file picker either: choosing the PDF returns an empty file.
    ticketPdfPicker = () async => (bytes: Uint8List(0), name: 'bilhete.pdf');
    qrScannerBuilder = (context, onCode) => Center(
      child: TextButton(
        onPressed: () => onCode('SAOJOAO24'),
        child: const Text('Ler código QR'),
      ),
    );
  });

  group('P1 os meus eventos', () {
    testWidgets('lists tickets, changes and public events', (tester) async {
      // Tall enough to build every card of the list.
      await _pump(tester, size: const Size(390, 4000));

      expect(find.text('Os meus eventos'), findsWidgets);
      expect(find.text('HOJE'), findsOneWidget);
      expect(find.text('ADIADO · 8 NOV'), findsOneWidget);
      expect(find.text('Adiado'), findsOneWidget);
      expect(find.text('Corrida de Natal'), findsOneWidget);
      expect(find.text('Feira Medieval de Gaia'), findsOneWidget);
      expect(find.text('Público'), findsNWidgets(2));
    });

    testWidgets('opens the event page from the front card', (tester) async {
      await _pump(tester);

      await tester.tap(find.text('Festival Margens').first);
      await tester.pumpAndSettle();

      expect(find.text('Mostrar código de entrada'), findsOneWidget);
      expect(find.text('Porta B fechada. Use a Porta C.'), findsOneWidget);
    });

    testWidgets('shows the empty state and a disabled Reportar', (
      tester,
    ) async {
      final store = await _pump(tester);
      for (final event in store.connectedEvents) {
        store.leaveEvent(event.id);
      }
      await tester.pumpAndSettle();

      expect(find.text('Ainda não tens eventos'), findsOneWidget);
      expect(find.text('Inserir código de acesso'), findsOneWidget);
      final report = tester.widget<AppReportButton>(
        find.byType(AppReportButton),
      );
      expect(report.onPressed, isNull);
    });

    testWidgets('hides the tab bar on scroll and floats Reportar', (
      tester,
    ) async {
      await _pump(tester);
      expect(find.byKey(const Key('floating-report')), findsNothing);

      await tester.drag(find.byType(ListView).first, const Offset(0, -200));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('floating-report')), findsOneWidget);
      expect(find.byType(AppTabBar), findsNothing);

      await tester.drag(find.byType(ListView).first, const Offset(0, 100));
      await tester.pumpAndSettle();
      expect(find.byType(AppTabBar), findsOneWidget);
    });
  });

  group('P2 import', () {
    testWidgets('adds a PDF ticket whose event code matches (P2a)', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/import');

      await tester.tap(find.text('PDF do bilhete'));
      await tester.pumpAndSettle();
      expect(find.text('Adicionaste o evento Noite de Fado'), findsOneWidget);
      expect(find.text('Casa do Infante, Porto'), findsOneWidget);
      expect(store.tickets, hasLength(5));

      await tester.ensureVisible(find.text('Não é o teu evento? Remover'));

      await tester.pumpAndSettle();
      await tester.tap(find.text('Não é o teu evento? Remover'));
      await tester.pumpAndSettle();
      expect(store.tickets, hasLength(4));
    });

    testWidgets('asks for the event code, pre-filled, for an image (P2b)', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/import');

      await tester.tap(find.text('Captura de ecrã ou foto'));
      await tester.pumpAndSettle();
      expect(
        find.text('O código lido não corresponde a nenhum evento'),
        findsOneWidget,
      );
      expect(find.text('FADO-2O26'), findsOneWidget);

      await tester.enterText(find.byType(TextField), 'fado-2026');
      await tester.tap(find.text('Associar bilhete'));
      await tester.pumpAndSettle();
      expect(find.text('Adicionaste o evento Noite de Fado'), findsOneWidget);
      expect(store.ticketFor('noite-de-fado'), isNotNull);
    });

    testWidgets('does not store a ticket for an event not on Crowded', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/import');

      await tester.tap(find.text('Captura de ecrã ou foto'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Associar bilhete'));
      await tester.pumpAndSettle();
      expect(find.text('Este evento ainda não usa o Crowded'), findsOneWidget);
      expect(store.tickets, hasLength(4));
      expect(store.pendingImport, isNull);
    });

    testWidgets('explains a ticket without a QR code', (tester) async {
      await _pump(tester, location: '/import/error/no-qr');
      expect(
        find.text('Não encontrámos o código QR do bilhete'),
        findsOneWidget,
      );
    });

    testWidgets('explains a ticket linked to another account', (tester) async {
      await _pump(tester, location: '/import/error/other-account');
      expect(
        find.text('Este bilhete já está ligado a outra conta'),
        findsOneWidget,
      );
    });
  });

  group('P21 access code', () {
    testWidgets('joins the event from the QR code on the poster', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/access-code');

      await tester.tap(find.text('Ler código QR'));
      await tester.pumpAndSettle();
      expect(store.isConnected('sao-joao'), isTrue);
      expect(find.text('São João na Ribeira'), findsWidgets);
    });

    testWidgets('says when the code is not found', (tester) async {
      await _pump(tester, location: '/access-code');

      await tester.enterText(find.byType(TextField), 'SAOJOA024');
      await tester.tap(find.text('Abrir evento'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Código não encontrado.'), findsOneWidget);
    });

    testWidgets('joins the public event and opens its page', (tester) async {
      final store = await _pump(tester, location: '/access-code');

      await tester.enterText(find.byType(TextField), 'saojoao24');
      await tester.tap(find.text('Abrir evento'));
      await tester.pumpAndSettle();
      expect(store.isConnected('sao-joao'), isTrue);
      expect(find.text('São João na Ribeira'), findsWidgets);
    });
  });

  testWidgets('P8 shows a left event instead of its page', (tester) async {
    await _pump(tester, location: '/event/mercado-outono');

    expect(find.text('Já não estás ligado a este evento'), findsOneWidget);
    expect(find.textContaining('Mercado de Outono · saíste'), findsOneWidget);
    expect(find.text('Inserir código de acesso'), findsOneWidget);
  });

  group('P5 event page', () {
    testWidgets('shows live information on the event day', (tester) async {
      await _pump(tester, location: '/event/festival-margens');

      expect(find.text('Agora no recinto'), findsOneWidget);
      expect(find.text('AVISO AMARELO · IPMA'), findsOneWidget);
      expect(
        find.textContaining('Previsão IPMA · distrito do Porto'),
        findsOneWidget,
      );
      expect(find.text('Lotação Moderada', findRichText: true), findsOneWidget);
      expect(find.text('~25 min'), findsOneWidget);
      expect(find.text('a aumentar'), findsOneWidget);
    });

    testWidgets('shows a countdown on other days', (tester) async {
      await _pump(tester, location: '/event/orquestra-sinfonica');

      expect(find.text('Faltam 28 dias'), findsOneWidget);
      expect(find.text('Agora no recinto'), findsNothing);
    });

    testWidgets('shows saved data and disables reports offline', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/event/festival-margens');
      store.setOffline(true);
      await tester.pumpAndSettle();

      expect(find.text('Sem rede'), findsOneWidget);
      expect(find.text('Lotação e filas'), findsOneWidget);
      expect(find.text('Agora no recinto'), findsNothing);
    });

    testWidgets('links to the participant estimates', (tester) async {
      await _pump(tester, location: '/event/festival-margens');

      await tester.ensureVisible(find.text('Estimativas de participantes · 2'));

      await tester.pumpAndSettle();
      await tester.tap(find.text('Estimativas de participantes · 2'));
      await tester.pumpAndSettle();
      expect(find.text('9 confirmaram · 1 negou'), findsOneWidget);
    });

    testWidgets('removes the ticket from the event page', (tester) async {
      final store = await _pump(tester, location: '/event/a-tempestade');

      await tester.tap(find.bySemanticsLabel('Mais opções'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(AppButton, 'Remover bilhete'));
      await tester.pumpAndSettle();
      expect(store.isConnected('a-tempestade'), isFalse);
      expect(find.text('ADIADO · 8 NOV'), findsNothing);
    });

    testWidgets('P10 asks for location before reporting', (tester) async {
      await _pump(tester, location: '/event/festival-margens');

      await tester.tap(find.byType(AppReportButton));
      await tester.pumpAndSettle();
      expect(find.text('Confirmar que estás no recinto'), findsOneWidget);

      await tester.tap(find.text('Agora não'));
      await tester.pumpAndSettle();
      expect(find.text('Confirmar que estás no recinto'), findsNothing);
    });
  });

  testWidgets('P6 → P7 shows the official change', (tester) async {
    await _pump(tester, location: '/event/a-tempestade/history');

    expect(find.text('ESTADO ATUAL'), findsOneWidget);
    expect(find.text('Data alterada'), findsOneWidget);
    expect(find.text('Evento publicado'), findsOneWidget);

    await tester.tap(find.text('Data alterada'));
    await tester.pumpAndSettle();
    expect(find.text('A Tempestade foi adiada'), findsOneWidget);
    expect(find.text('O teu bilhete continua válido'), findsOneWidget);
    expect(find.text('Reembolso, se não puderes ir'), findsOneWidget);
    expect(find.text('Sem alteração'), findsNWidgets(2));
  });

  group('P5–P6 events from Core', () {
    const page = '/event/${_FakeEventsApi.id}';

    testWidgets('shows the current status and the changes, newest first', (
      tester,
    ) async {
      await _pump(tester, location: '$page/history');

      expect(find.text('Festival Margens do Douro'), findsOneWidget);
      expect(find.text('Adiado'), findsOneWidget);
      expect(find.text('ESTADO ATUAL'), findsOneWidget);
      final doors = find.text(
        'Portas às ${formatTime(DateTime.utc(2026, 11, 6, 19, 30).toLocal())}',
      );
      final date = find.text('Data alterada');
      expect(doors, findsOneWidget);
      expect(date, findsOneWidget);
      expect(
        tester.getTopLeft(doors).dy,
        lessThan(tester.getTopLeft(date).dy),
        reason: 'newest first',
      );
      expect(find.text('Organização'), findsNWidgets(2));
      expect(find.text('Ribeira Live'), findsNWidgets(2));
      expect(
        find.text('HOJE · ${formatTime(_now.subtract(_doorsChangedAgo))}'),
        findsOneWidget,
      );
    });

    testWidgets('says when no change has been recorded', (tester) async {
      final events = _FakeEventsApi()
        ..status = 'SCHEDULED'
        ..history = [];
      await _pump(tester, location: '$page/history', events: events);

      expect(find.text('Confirmado'), findsOneWidget);
      expect(find.text('Ainda não há alterações'), findsOneWidget);
    });

    testWidgets('shows when it last loaded once Core cannot be reached', (
      tester,
    ) async {
      final events = _FakeEventsApi();
      await _pump(tester, location: page, events: events);
      expect(find.text('Sem rede'), findsNothing);
      expect(find.textContaining('Fonte: organização'), findsWidgets);

      events.failWith = 'NETWORK';
      await tester.ensureVisible(find.text('Histórico de alterações'));
      await tester.tap(find.text('Histórico de alterações'));
      await tester.pumpAndSettle();
      expect(find.text('Data alterada'), findsOneWidget);
      await tester.tap(find.text('Evento').first);
      await tester.pumpAndSettle();

      expect(events.pages, 2);
      expect(find.text('Sem rede'), findsOneWidget);
      expect(
        find.text('A mostrar o que guardámos às ${formatTime(_now)}.'),
        findsOneWidget,
      );
      expect(find.text('Adiado'), findsWidgets, reason: 'the saved status');
    });

    test('keeps showing data as saved while a retry is on its way', () async {
      final events = _FakeEventsApi();
      final store = AppStore(
        clock: () => _now,
        auth: _FakeAuthApi(),
        notifications: _FakeNotificationsApi(),
        events: events,
        session: _session(_email),
      );
      const id = _FakeEventsApi.id;
      await store.refreshEvent(id);
      events.failWith = 'NETWORK';
      await store.refreshEvent(id);
      expect(store.showsSavedData(id), isTrue);

      events.gate = Completer<void>();
      final retry = store.refreshEvent(id);
      expect(store.syncOf(id)!.loading, isTrue);
      expect(store.showsSavedData(id), isTrue);
      expect(store.savedAtFor(id), _now);

      events
        ..failWith = null
        ..gate!.complete();
      await retry;
      expect(store.showsSavedData(id), isFalse);
      store.signOut();
    });

    testWidgets('shows the crowd level the organization publishes today', (
      tester,
    ) async {
      Map<String, Object?> crowd(String level, DateTime at) => {
        'level': level,
        'provenance': {
          'sourceType': 'ORGANIZATION',
          'sourceName': 'Ribeira Live',
          'lastUpdatedAt': at.toUtc().toIso8601String(),
          'confirmed': true,
        },
      };
      final events = _FakeEventsApi()
        ..status = 'SCHEDULED'
        ..history = []
        ..start = _now.add(const Duration(hours: 4)).toUtc().toIso8601String()
        ..crowd = crowd('MODERATE', _now.subtract(const Duration(minutes: 5)));
      await _pump(tester, location: page, events: events);

      expect(find.text('Agora no recinto'), findsOneWidget);
      expect(find.text('Lotação Moderada', findRichText: true), findsOneWidget);
      expect(
        find.text('Fonte: organização · atualizado há 5 min'),
        findsOneWidget,
      );
      expect(find.text('Filas'), findsNothing);

      events
        ..crowd = crowd('FULL', _now)
        ..publish();
      await tester.pumpAndSettle();

      expect(find.text('Lotação Lotado', findRichText: true), findsOneWidget);
      expect(
        find.text('Fonte: organização · atualizado agora'),
        findsOneWidget,
      );
      expect(events.pages, 1, reason: 'no reload: the stream sent it');
    });

    testWidgets(
      'shows the queue times published today, before any crowd level',
      (tester) async {
        Map<String, Object?> queue(String point, int minutes, DateTime at) => {
          'pointName': point,
          'waitMinutes': minutes,
          'provenance': {
            'sourceType': 'ORGANIZATION',
            'sourceName': 'Ribeira Live',
            'lastUpdatedAt': at.toUtc().toIso8601String(),
            'confirmed': true,
          },
        };
        final events = _FakeEventsApi()
          ..status = 'SCHEDULED'
          ..history = []
          ..start = _now.add(const Duration(hours: 4)).toUtc().toIso8601String()
          ..queues = [
            queue('Porta A', 10, _now.subtract(const Duration(minutes: 8))),
            queue('Bar central', 25, _now.subtract(const Duration(minutes: 3))),
          ];
        await _pump(tester, location: page, events: events);

        expect(find.text('Agora no recinto'), findsOneWidget);
        expect(find.text('Filas'), findsOneWidget);
        expect(find.text('Porta A'), findsOneWidget);
        expect(find.text('~10 min'), findsOneWidget);
        expect(find.text('~25 min'), findsOneWidget);
        expect(find.textContaining('Lotação'), findsNothing);
        expect(
          find.text('Fonte: organização · atualizado há 8 min'),
          findsOneWidget,
          reason: 'the oldest time, never fresher than it is',
        );

        events
          ..queues = [queue('Porta A', 5, _now), queue('Bar central', 15, _now)]
          ..publish();
        await tester.pumpAndSettle();

        expect(find.text('~5 min'), findsOneWidget);
        expect(
          find.text('Fonte: organização · atualizado agora'),
          findsOneWidget,
        );
        expect(events.pages, 1, reason: 'no reload: the stream sent it');
      },
    );

    testWidgets('follows what is published while the page is open', (
      tester,
    ) async {
      final events = _FakeEventsApi();
      await _pump(tester, location: '$page/history', events: events);
      expect(find.text('Evento cancelado'), findsNothing);

      events
        ..status = 'CANCELLED'
        ..history = [
          _coreChange('c-cancel', 'CANCELLATION', {
            'status': 'CANCELLED',
          }, at: _now.toUtc().toIso8601String()),
          ...events.history,
        ]
        ..publish();
      await tester.pumpAndSettle();

      expect(find.text('Evento cancelado'), findsOneWidget);
      expect(find.text('Cancelado'), findsWidgets);
      expect(events.pages, 1, reason: 'no reload: the stream sent it');
    });

    testWidgets('shows saved data while the stream is down, then recovers', (
      tester,
    ) async {
      final events = _FakeEventsApi();
      await _pump(tester, location: page, events: events);
      expect(events.connections, 1);

      events.drop();
      await tester.pumpAndSettle();
      expect(find.text('Sem rede'), findsOneWidget);
      expect(
        find.text('A mostrar o que guardámos às ${formatTime(_now)}.'),
        findsOneWidget,
      );

      await tester.pump(AppStore.reconnectDelay(1));
      await tester.pumpAndSettle();
      expect(events.connections, 2);
      expect(find.text('Sem rede'), findsNothing);
    });

    testWidgets('closes the stream when the participant signs out', (
      tester,
    ) async {
      final events = _FakeEventsApi();
      final store = await _pump(tester, location: page, events: events);
      expect(events.streaming, isTrue);

      store.signOut();
      await tester.pumpAndSettle();
      expect(events.streaming, isFalse);
    });

    testWidgets('keeps one stream for the pages of an event', (tester) async {
      final events = _FakeEventsApi();
      await _pump(tester, location: page, events: events);
      await tester.ensureVisible(find.text('Histórico de alterações'));
      await tester.tap(find.text('Histórico de alterações'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Evento').first);
      await tester.pumpAndSettle();

      expect(events.connections, 1);
      expect(events.streaming, isTrue, reason: 'P5 is still open');
    });

    testWidgets('loads again after a first load fails', (tester) async {
      final events = _FakeEventsApi()..failWith = 'HTTP_503';
      await _pump(tester, location: page, events: events);
      expect(find.text('Não foi possível carregar o evento'), findsOneWidget);

      events.failWith = null;
      await tester.tap(find.text('Tentar de novo'));
      await tester.pumpAndSettle();
      expect(find.text('Festival Margens do Douro'), findsWidgets);
    });

    testWidgets('says when the participant is no longer connected', (
      tester,
    ) async {
      final events = _FakeEventsApi()..failWith = 'NOT_CONNECTED';
      await _pump(tester, location: page, events: events);

      expect(find.text('Já não estás ligado a este evento'), findsOneWidget);
    });
  });

  testWidgets('P8 lists the notifications, filters and opens them', (
    tester,
  ) async {
    final api = _FakeNotificationsApi();
    final store = await _pump(
      tester,
      location: '/notifications',
      notifications: api,
    );
    expect(store.unreadCount, 2);
    expect(store.notifications.map((n) => n.id), [
      'n-alert',
      'n-change',
      'n-weather',
      'n-left',
    ]);
    expect(find.text('HOJE'), findsOneWidget);
    expect(find.text('A Tempestade · Organização'), findsOneWidget);
    expect(find.text('Era SÁB 25 OUT.\nTraz o bilhete.'), findsOneWidget);

    await tester.ensureVisible(find.text('Tempo'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Tempo'));
    await tester.pumpAndSettle();
    expect(
      find.text('Aviso laranja: chuva forte no Porto amanhã'),
      findsOneWidget,
    );
    expect(find.text('Porta B fechada. Use a Porta C.'), findsNothing);

    await tester.ensureVisible(find.text('Todas'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Todas'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Adiado para SÁB 8 NOV · 21:30'));
    await tester.pumpAndSettle();
    expect(api.read, ['n-change']);
    expect(store.unreadCount, 1);
    expect(find.text('A Tempestade foi adiada'), findsOneWidget);
    await tester.tap(find.text('Fechar').first);
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.text('Abertura adiada para as 11:00'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    expect(find.text('Já não estás ligado a este evento.'), findsOneWidget);
    // Tap the start of the line: the floating Reportar sits on the right.
    await tester.tapAt(
      tester.getTopLeft(find.text('Abertura adiada para as 11:00')) +
          const Offset(4, 4),
    );
    await tester.pumpAndSettle();
    expect(find.text('Já não estás ligado a este evento'), findsOneWidget);
  });

  testWidgets('P8 shows a failed load and loads again', (tester) async {
    final api = _FakeNotificationsApi()..failListWith = 'NETWORK';
    final store = await _pump(
      tester,
      location: '/notifications',
      notifications: api,
    );
    expect(
      find.text('Não foi possível carregar as notificações'),
      findsOneWidget,
    );

    api.failListWith = null;
    await tester.tap(find.text('Tentar novamente'));
    await tester.pumpAndSettle();
    expect(store.notificationsFailed, isFalse);
    expect(find.text('Porta B fechada. Use a Porta C.'), findsOneWidget);
  });

  group('P8 live banner', () {
    AppNotification fresh() => _notification(
      'n-new',
      NotificationKind.officialChange,
      eventId: 'a-tempestade',
      eventName: 'A Tempestade',
      title: 'Cancelado',
      changeId: 'tempestade-date',
      ago: Duration.zero,
    );

    testWidgets('shows a new notification and updates the badge once', (
      tester,
    ) async {
      final api = _FakeNotificationsApi();
      final store = await _pump(tester, notifications: api);
      expect(store.unreadCount, 2);

      api.send(fresh());
      await tester.pumpAndSettle();
      expect(store.unreadCount, 3);
      expect(find.text('Cancelado'), findsOneWidget);
      expect(find.text('A Tempestade · Organização'), findsOneWidget);
      expect(find.bySemanticsLabel('Notificações, 3 por ler'), findsWidgets);

      // Sent twice, as after a redelivery: shown once.
      api.send(fresh(), store: false);
      await tester.pumpAndSettle();
      expect(store.unreadCount, 3);
      expect(store.notifications.where((n) => n.id == 'n-new'), hasLength(1));

      await tester.tap(find.text('Cancelado'));
      await tester.pumpAndSettle();
      expect(api.read, ['n-new']);
      expect(store.banner, isNull);
      expect(find.text('A Tempestade foi adiada'), findsOneWidget);
    });

    testWidgets('closes, and hides by itself after 8 seconds', (tester) async {
      final api = _FakeNotificationsApi();
      final store = await _pump(tester, notifications: api);

      api.send(fresh());
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('live-banner-close')));
      await tester.pumpAndSettle();
      expect(find.text('Cancelado'), findsNothing);
      expect(store.unreadCount, 3, reason: 'closing does not read it');

      api.send(
        _notification(
          'n-other',
          NotificationKind.alert,
          title: 'Porta A aberta',
          ago: Duration.zero,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Porta A aberta'), findsOneWidget);
      await tester.pump(const Duration(seconds: 8));
      await tester.pumpAndSettle();
      expect(find.text('Porta A aberta'), findsNothing);
      expect(store.banner, isNull);
    });

    testWidgets('reconnects after a drop and lists what was missed once', (
      tester,
    ) async {
      final api = _FakeNotificationsApi();
      final store = await _pump(tester, notifications: api);
      final listsBefore = api.lists;

      api.drop();
      await tester.pump();
      // Created while the stream was down.
      api.stored.add(fresh());
      await tester.pump(AppStore.reconnectDelay(1));
      await tester.pumpAndSettle();

      expect(api.connections, 2);
      expect(api.lists, greaterThan(listsBefore));
      expect(store.notifications.where((n) => n.id == 'n-new'), hasLength(1));
      expect(store.unreadCount, 3);
      expect(store.banner, isNull, reason: 'missed ones wait in the center');
    });

    testWidgets('stops when the token is refused, and on sign-out', (
      tester,
    ) async {
      final api = _FakeNotificationsApi();
      final store = await _pump(tester, notifications: api);

      api.refuse();
      await tester.pump(const Duration(minutes: 1));
      expect(api.connections, 1);

      store.signOut();
      await tester.pumpAndSettle();
      expect(store.notifications, isEmpty);
      expect(store.unreadCount, 0);
    });

    testWidgets('keeps a notification received while the list loads', (
      tester,
    ) async {
      final api = _FakeNotificationsApi();
      final store = await _pump(tester, notifications: api);

      api.listGate = Completer();
      final refreshed = store.refreshNotifications();
      await tester.pump();
      api.send(fresh());
      await tester.pump();
      api.listGate!.complete();
      await tester.runAsync(() => refreshed);
      await tester.pumpAndSettle();

      expect(store.notifications.where((n) => n.id == 'n-new'), hasLength(1));
    });

    testWidgets('reads from the banner after the list was reloaded', (
      tester,
    ) async {
      final api = _FakeNotificationsApi();
      final store = await _pump(tester, notifications: api);
      api.send(fresh());
      await tester.pumpAndSettle();

      await store.refreshNotifications();
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancelado'));
      await tester.pumpAndSettle();

      expect(api.read, ['n-new']);
      expect(store.unreadCount, 2);
    });

    test('waits longer after each drop, up to 30 seconds', () {
      expect(AppStore.reconnectDelay(1), const Duration(seconds: 5));
      expect(AppStore.reconnectDelay(3), const Duration(seconds: 15));
      expect(AppStore.reconnectDelay(9), const Duration(seconds: 30));
    });
  });

  group('C1 sign in', () {
    testWidgets('signed-out users are sent to C1', (tester) async {
      await _pump(tester, location: '/events', signedIn: false);

      expect(find.text('Sabe o que se passa nos teus eventos'), findsOneWidget);
      expect(find.text('Esqueci-me da palavra-passe'), findsOneWidget);
    });

    testWidgets('validates and signs in', (tester) async {
      final store = await _pump(tester, location: '/sign-in', signedIn: false);

      await tester.enterText(find.byType(TextField).at(0), 'ines@email');
      await tester.enterText(find.byType(TextField).at(1), 'curta');
      await tester.tap(find.widgetWithText(AppButton, 'Entrar'));
      await tester.pumpAndSettle();
      expect(find.text('Introduz um email válido.'), findsOneWidget);

      await tester.enterText(find.byType(TextField).at(0), _email);
      await tester.enterText(find.byType(TextField).at(1), 'errada-errada');
      await tester.tap(find.widgetWithText(AppButton, 'Entrar'));
      await tester.pumpAndSettle();
      expect(
        find.text('O email ou a palavra-passe estão errados.'),
        findsOneWidget,
      );
      expect(store.signedIn, isFalse);

      await tester.enterText(find.byType(TextField).at(1), 'palavra-secreta');
      await tester.tap(find.widgetWithText(AppButton, 'Entrar'));
      await tester.pumpAndSettle();
      expect(store.signedIn, isTrue);
      expect(store.session?.accessToken, 'token');
      await tester.scrollUntilVisible(find.text('ADIADO · 8 NOV'), 200);
      expect(find.text('ADIADO · 8 NOV'), findsOneWidget);
    });

    testWidgets('sends an organization member to the backoffice', (
      tester,
    ) async {
      final auth = _FakeAuthApi();
      final store = await _pump(
        tester,
        location: '/sign-in',
        signedIn: false,
        auth: auth,
      );

      await tester.enterText(
        find.byType(TextField).at(0),
        'equipa@ribeira-live.test',
      );
      await tester.enterText(find.byType(TextField).at(1), 'x');
      await tester.tap(find.widgetWithText(AppButton, 'Entrar'));
      await tester.pumpAndSettle();

      expect(
        find.text('Esta conta é de uma organização. Entra no backoffice.'),
        findsOneWidget,
      );
      expect(store.signedIn, isFalse);
      // Core started a session for the organization's account: it is ended.
      expect(auth.signOuts, 1);
    });

    testWidgets('says when Core cannot be reached or limits the attempts', (
      tester,
    ) async {
      final auth = _FakeAuthApi()..failWith = 'NETWORK';
      await _pump(tester, location: '/sign-in', signedIn: false, auth: auth);

      await tester.enterText(find.byType(TextField).at(0), _email);
      await tester.enterText(find.byType(TextField).at(1), 'palavra-secreta');
      await tester.tap(find.widgetWithText(AppButton, 'Entrar'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'Não foi possível ligar ao Crowded. Verifica a ligação e tenta novamente.',
        ),
        findsOneWidget,
      );

      auth.failWith = 'RATE_LIMITED';
      await tester.tap(find.widgetWithText(AppButton, 'Entrar'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'Demasiadas tentativas. Tenta novamente daqui a alguns minutos.',
        ),
        findsOneWidget,
      );
    });

    testWidgets('creates an account without a name and signs in', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/sign-in', signedIn: false);

      await tester.tap(find.text('Criar conta').first);
      await tester.pumpAndSettle();
      expect(find.text('Nome'), findsNothing);
      expect(find.text('Confirmar palavra-passe'), findsOneWidget);
      await tester.enterText(find.byType(TextField).at(0), 'ana@email.pt');
      await tester.enterText(find.byType(TextField).at(1), 'curta');
      await tester.ensureVisible(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.tap(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.pumpAndSettle();
      expect(
        find.text('Usa pelo menos 8 caracteres na palavra-passe.'),
        findsOneWidget,
      );

      expect(find.text('Força: Muito fraca'), findsOneWidget);
      expect(find.text('A palavra-passe deve conter:'), findsOneWidget);
      expect(find.byIcon(Icons.check_circle), findsOneWidget);
      expect(find.byIcon(Icons.radio_button_unchecked), findsNWidgets(4));
      await tester.enterText(find.byType(TextField).at(1), 'palavra-secreta');
      await tester.pump();
      expect(find.text('Força: Fraca'), findsOneWidget);
      await tester.ensureVisible(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.tap(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'A palavra-passe não cumpre os requisitos de segurança: deve incluir '
          'uma letra maiúscula, uma letra minúscula, um algarismo e um símbolo.',
        ),
        findsOneWidget,
      );

      await tester.enterText(find.byType(TextField).at(1), 'Segr3do!');
      await tester.pump();
      expect(find.text('Força: Moderada'), findsOneWidget);
      expect(find.byIcon(Icons.radio_button_unchecked), findsNothing);
      await tester.enterText(find.byType(TextField).at(1), 'Segr3do!-Forte');
      await tester.pump();
      expect(find.text('Força: Forte'), findsOneWidget);
      await tester.enterText(find.byType(TextField).at(1), 'Palavra-secr3ta!');
      await tester.pump();
      expect(find.text('Força: Muito forte'), findsOneWidget);
      expect(
        find.bySemanticsLabel(
          'Força da palavra-passe: Muito forte. Cumpre todos os requisitos.',
        ),
        findsOneWidget,
      );
      await tester.enterText(find.byType(TextField).at(2), 'Palavra-secrta!');
      await tester.ensureVisible(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.tap(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.pumpAndSettle();
      expect(find.text('As palavras-passe não coincidem.'), findsOneWidget);
      expect(store.signedIn, isFalse);

      await tester.enterText(find.byType(TextField).at(2), 'Palavra-secr3ta!');
      await tester.ensureVisible(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.tap(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.pumpAndSettle();
      expect(store.account?.email, 'ana@email.pt');
      await tester.scrollUntilVisible(find.text('ADIADO · 8 NOV'), 200);
      expect(find.text('ADIADO · 8 NOV'), findsOneWidget);
    });

    testWidgets('refuses an email that already has an account', (tester) async {
      await _pump(tester, location: '/sign-in', signedIn: false);

      await tester.tap(find.text('Criar conta').first);
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(0), _email);
      await tester.enterText(find.byType(TextField).at(1), 'Outra-palavr4');
      await tester.enterText(find.byType(TextField).at(2), 'Outra-palavr4');

      await tester.ensureVisible(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.tap(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.pumpAndSettle();
      expect(
        find.text('Já existe uma conta com este email. Entra com ela.'),
        findsOneWidget,
      );
    });
  });

  testWidgets('C0 lists the account menu and signs out to C1', (tester) async {
    final auth = _FakeAuthApi();
    final store = await _pump(tester, location: '/account', auth: auth);

    expect(find.text(_email), findsOneWidget);
    expect(find.text('Contribuições'), findsOneWidget);
    expect(find.text('Os teus reportes e confirmações'), findsOneWidget);
    await tester.ensureVisible(find.text('Terminar sessão'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Terminar sessão'));
    await tester.pumpAndSettle();
    expect(store.signedIn, isFalse);
    expect(auth.signOuts, 1);
    expect(find.text('Sabe o que se passa nos teus eventos'), findsOneWidget);
  });

  group('the session of an earlier visit (ADR 0021)', () {
    Future<AppStore> start(WidgetTester tester, _FakeAuthApi auth) async {
      final store = AppStore(
        clock: () => _now,
        auth: auth,
        notifications: _FakeNotificationsApi(),
        events: _FakeEventsApi(),
      );
      // As the app does: the session is picked up before the first page.
      final restoring = store.restoreSession();
      await tester.pumpWidget(
        CrowdedApp(store: store, initialLocation: '/account'),
      );
      await tester.pump();
      expect(store.restoring, isTrue);
      expect(find.bySemanticsLabel('A carregar'), findsOneWidget);
      auth.restoreAnswered.complete();
      await restoring;
      await tester.pumpAndSettle();
      return store;
    }

    testWidgets('keeps the participant on the page they reopened', (
      tester,
    ) async {
      final auth = _FakeAuthApi()..restored = _session(_email);
      final store = await start(tester, auth);

      expect(store.signedIn, isTrue);
      expect(find.text(_email), findsOneWidget);
    });

    testWidgets('asks to sign in when there is none', (tester) async {
      final store = await start(tester, _FakeAuthApi());

      expect(store.signedIn, isFalse);
      expect(find.text('Sabe o que se passa nos teus eventos'), findsOneWidget);
    });

    testWidgets('says an organization account belongs in the backoffice', (
      tester,
    ) async {
      final auth = _FakeAuthApi()
        ..restored = _session(
          'equipa@ribeira-live.test',
          role: 'ORGANIZATION_MEMBER',
        );
      final store = await start(tester, auth);

      expect(store.signedIn, isFalse);
      expect(
        find.text('Esta conta é de uma organização. Entra no backoffice.'),
        findsOneWidget,
      );
      await tester.tap(find.text('Terminar sessão'));
      await tester.pumpAndSettle();
      expect(auth.signOuts, 1);
      expect(store.organizationNotice, isNull);
    });

    testWidgets('signs out when Core ends the session', (tester) async {
      final auth = _FakeAuthApi()..restored = _session(_email);
      final store = await start(tester, auth);

      auth.ended!();
      await tester.pumpAndSettle();

      expect(store.signedIn, isFalse);
      expect(find.text('Sabe o que se passa nos teus eventos'), findsOneWidget);
    });
  });

  testWidgets('C2 updates notification preferences', (tester) async {
    final store = await _pump(tester, location: '/account/notifications');

    expect(find.text('Só alterações oficiais'), findsOneWidget);
    await tester.tap(find.text('Avisos meteorológicos'));
    await tester.pumpAndSettle();
    expect(store.preferences.weatherAlerts, isFalse);

    await tester.tap(find.text('Todos'));
    await tester.pumpAndSettle();
    expect(store.preferences.requests, RequestNotificationLevel.all);

    await tester.ensureVisible(find.text('A Tempestade'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('A Tempestade'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Nada'));
    await tester.pumpAndSettle();
    expect(store.levelFor('a-tempestade'), EventNotificationLevel.none);
  });

  testWidgets('C3 deletes the account only after confirmation', (tester) async {
    final store = await _pump(tester, location: '/account/delete');

    expect(
      find.text('4 bilhetes e os respetivos códigos de entrada'),
      findsOneWidget,
    );
    expect(find.text('2 eventos públicos a que estás ligado'), findsOneWidget);
    final button = find.widgetWithText(
      AppButton,
      'Apagar conta definitivamente',
    );
    expect(tester.widget<AppButton>(button).onPressed, isNull);

    await tester.enterText(find.byType(TextField).at(0), 'APAGAR');
    await tester.enterText(find.byType(TextField).at(1), 'palavra-secreta');
    await tester.pumpAndSettle();
    expect(tester.widget<AppButton>(button).onPressed, isNotNull);

    await tester.ensureVisible(button);

    await tester.pumpAndSettle();
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(store.signedIn, isFalse);
    expect(store.tickets, isEmpty);
    expect(find.text('A tua conta foi apagada.'), findsOneWidget);
  });

  group('P11–P12 report', () {
    Future<void> openReport(WidgetTester tester) async {
      final floating = find.byType(AppReportButton);
      await tester.tap(
        floating.evaluate().isEmpty ? find.text('Reportar') : floating,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Permitir localização'));
      await tester.pumpAndSettle();
    }

    testWidgets('sends a queue report and shows what happens next', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/event/festival-margens');
      final before = store.contributions.length;
      await openReport(tester);

      expect(find.text('O que se passa?'), findsOneWidget);
      expect(find.text('No recinto'), findsOneWidget);
      final send = find.widgetWithText(AppButton, 'Enviar reporte');
      expect(tester.widget<AppButton>(send).onPressed, isNull);

      await tester.tap(find.text('Fila'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Escolher'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Porta C').last);
      await tester.pumpAndSettle();
      await tester.tap(find.text('20–40'));
      await tester.pumpAndSettle();
      expect(tester.widget<AppButton>(send).onPressed, isNotNull);

      await tester.ensureVisible(send);

      await tester.pumpAndSettle();
      await tester.tap(send);
      await tester.pumpAndSettle();
      expect(find.text('Reporte enviado'), findsOneWidget);
      expect(
        find.text(
          'A organização do Festival Margens já recebeu o teu reporte sobre '
          'a fila na Porta C.',
        ),
        findsOneWidget,
      );
      expect(store.contributions, hasLength(before + 1));
      expect(store.contributions.first.title, 'Fila na Porta C');

      await tester.tap(find.text('Voltar ao evento'));
      await tester.pumpAndSettle();
      expect(find.text('Agora no recinto'), findsOneWidget);
    });

    testWidgets('away from the venue only allows lost items', (tester) async {
      final store = await _pump(tester, location: '/event/festival-margens');
      store.setAwayFromVenue(true);
      await openReport(tester);

      expect(find.text('Não estás perto do recinto'), findsOneWidget);
      await tester.tap(find.text('Perdeste alguma coisa?'));
      await tester.pumpAndSettle();
      expect(find.text('Longe do recinto'), findsOneWidget);
      expect(find.text('Perdidos e achados'), findsOneWidget);
      expect(find.text('Fila'), findsNothing);
      expect(find.text('O que perdeste?'), findsOneWidget);
    });

    testWidgets('opens the form in a dialog on desktop', (tester) async {
      await _pump(tester, location: '/event/festival-margens', size: _desktop);
      await openReport(tester);

      expect(find.byType(Dialog), findsOneWidget);
      expect(find.text('Estás no recinto'), findsOneWidget);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(find.byType(Dialog), findsNothing);
    });
  });

  testWidgets('P13 asks to confirm a report from a notification', (
    tester,
  ) async {
    final store = await _pump(
      tester,
      location: '/event/festival-margens?confirm=c-porta-c',
    );
    await tester.tap(find.text('Permitir localização'));
    await tester.pumpAndSettle();

    expect(find.text('Confirmas isto?'), findsOneWidget);
    expect(find.text('Espera de 20 a 40 minutos'), findsOneWidget);
    await tester.tap(find.text('Sim'));
    await tester.pumpAndSettle();

    expect(find.text('Confirmas isto?'), findsNothing);
    expect(
      store.confirmationRequest('c-porta-c')!.answer,
      ConfirmationAnswer.yes,
    );
    expect(store.contributions.first.kind, ContributionKind.confirmation);
  });

  testWidgets('P14 lists confirmed estimates', (tester) async {
    await _pump(tester, location: '/event/festival-margens/estimates');

    expect(find.text('Fila nos WC junto ao palco · 15–20 min'), findsOneWidget);
    expect(find.text('Rampa da entrada Norte bloqueada'), findsOneWidget);
    expect(find.text('9 confirmaram · 1 negou'), findsOneWidget);
  });

  testWidgets('P15 shows the impact of a helpful report', (tester) async {
    await _pump(tester, location: '/recognition/r-agua');

    expect(find.text('O teu reporte ajudou!'), findsOneWidget);
    expect(find.text('Problema resolvido em 18 min'), findsOneWidget);
    expect(find.text('Alerta enviado a 1 240 pessoas'), findsOneWidget);
  });

  testWidgets('P17 answers a request and shares an idea', (tester) async {
    final store = await _pump(
      tester,
      location: '/event/festival-margens/requests',
    );
    expect(find.text('2 pedidos da organização · 1 urgente'), findsOneWidget);

    await tester.tap(find.text('Sim'));
    await tester.pumpAndSettle();
    expect(find.text('Respondeste: Sim'), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'Mais casas de banho');
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Partilhar ideia'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Partilhar ideia'));
    await tester.pumpAndSettle();
    expect(store.ideasFor('festival-margens').single.ideasShared, 24);
    expect(find.text('Partilhaste a tua ideia. Obrigado!'), findsOneWidget);
  });

  testWidgets('P18 shows transport options with disruptions', (tester) async {
    await _pump(tester, location: '/event/festival-margens/directions');

    expect(find.text('Parque da Cidade · Porta A'), findsOneWidget);
    expect(find.text('Autocarro 502'), findsOneWidget);
    expect(find.text('Perturbação'), findsOneWidget);
    expect(find.textContaining('horários GTFS'), findsOneWidget);
  });

  group('P19 entry code', () {
    testWidgets('shows the code in the app', (tester) async {
      await _pump(tester, location: '/event/festival-margens/code');

      expect(find.byType(EntryCodeImage), findsOneWidget);
      expect(find.text('Ref. •••• 4821'), findsOneWidget);
      expect(find.text(_email), findsOneWidget);
    });

    testWidgets('asks for the password on the web and hides after 2 min', (
      tester,
    ) async {
      await _pump(
        tester,
        location: '/event/festival-margens/code',
        size: _desktop,
      );
      expect(find.byType(EntryCodeImage), findsNothing);

      await tester.tap(find.text('Mostrar código'));
      await tester.pump();
      expect(find.text('Introduz a tua palavra-passe.'), findsOneWidget);

      await tester.enterText(find.byType(TextField), 'teste');
      await tester.tap(find.text('Mostrar código'));
      await tester.pump();
      expect(find.byType(EntryCodeImage), findsOneWidget);
      expect(find.text('Confirmado · esconde-se em 2:00'), findsOneWidget);

      await tester.pump(const Duration(seconds: 13));
      expect(find.text('Confirmado · esconde-se em 1:47'), findsOneWidget);

      await tester.pump(const Duration(minutes: 2));
      expect(find.byType(EntryCodeImage), findsNothing);
    });
  });

  testWidgets('P20 filters contributions', (tester) async {
    await _pump(tester, location: '/account/contributions');

    expect(find.text('FESTIVAL MARGENS · HOJE'), findsOneWidget);
    expect(find.text('Fila longa na Porta C'), findsOneWidget);
    expect(find.text('Rampa da entrada Norte bloqueada'), findsOneWidget);

    await tester.tap(find.text('Confirmações'));
    await tester.pumpAndSettle();
    expect(find.text('Rampa da entrada Norte bloqueada'), findsOneWidget);
    expect(find.text('Fila longa na Porta C'), findsNothing);
  });

  for (final (name, size) in [('mobile', _mobile), ('desktop', _desktop)]) {
    testWidgets('every screen renders on $name', (tester) async {
      for (final location in [
        '/events',
        '/account',
        '/notifications',
        '/import',
        '/import/code',
        '/import/added',
        '/import/error/no-qr',
        '/import/error/not-on-crowded',
        '/import/error/other-account',
        '/access-code',
        '/disconnected/mercado-outono',
        '/report',
        '/event/festival-margens',
        '/event/jazz-no-parque',
        '/event/a-tempestade',
        '/event/a-tempestade/history',
        '/event/a-tempestade/change/tempestade-date',
        '/event/feira-medieval',
        '/event/nao-existe',
        '/account/notifications',
        '/account/delete',
        '/event/festival-margens/report',
        '/event/festival-margens/report-result?status=sent',
        '/event/festival-margens/report-result?status=away',
        '/event/festival-margens/estimates',
        '/recognition/r-agua',
        '/event/festival-margens/requests',
        '/event/festival-margens/directions',
        '/event/feira-medieval/directions',
        '/event/festival-margens/code',
        '/event/jazz-no-parque/code',
        '/account/contributions',
      ]) {
        await _pump(tester, location: location, size: size);
        expect(tester.takeException(), isNull, reason: location);
      }
    });
  }
}
