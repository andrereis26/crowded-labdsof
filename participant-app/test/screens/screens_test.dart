import 'package:crowded/app.dart';
import 'package:crowded/data/app_store.dart';
import 'package:crowded/data/auth_api.dart';

import 'dart:async';

import 'package:crowded/data/models.dart';
import 'package:crowded/data/notifications_api.dart';
import 'package:crowded/screens/event/entry_code_screen.dart';
import 'package:crowded/screens/import/access_code_screen.dart';
import 'package:crowded/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// Saturday 17 October 2026, 15:00.
final _now = DateTime(2026, 10, 17, 15);

const _email = 'ines.martins@email.pt';

AuthSession _session(String email, {String role = 'PARTICIPANT'}) =>
    AuthSession(
      accessToken: 'token',
      expiresAt: _now.add(const Duration(hours: 1)),
      userId: 'u-1',
      role: role,
      email: email,
    );

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
  final tokens = <String>[];
  int lists = 0;
  int connections = 0;
  String? failListWith;

  /// While set, [list] answers only once it completes.
  Completer<void>? listGate;
  StreamController<LiveEvent>? _live;

  /// Copies, as each response from the server is a new object.
  @override
  Future<NotificationPage> list(String token) async {
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
  Future<void> markRead(String token, String notificationId) async {
    read.add(notificationId);
  }

  @override
  Stream<LiveEvent> live(String token) {
    connections++;
    tokens.add(token);
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

const _mobile = Size(390, 844);
const _desktop = Size(1440, 1000);

Future<AppStore> _pump(
  WidgetTester tester, {
  String location = '/eventos',
  Size size = _mobile,
  bool signedIn = true,
  AuthApi? auth,
  NotificationsApi? notifications,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final store = AppStore(
    clock: () => _now,
    auth: auth ?? _FakeAuthApi(),
    notifications: notifications ?? _FakeNotificationsApi(),
    session: signedIn ? _session(_email) : null,
  );
  await tester.pumpWidget(CrowdedApp(store: store, initialLocation: location));
  await tester.pumpAndSettle();
  return store;
}

void main() {
  // No camera in widget tests: a button stands in for a scanned QR code.
  setUpAll(() {
    qrScannerBuilder = (context, onCode) => Center(
      child: TextButton(
        onPressed: () => onCode('SAOJOAO24'),
        child: const Text('Ler código QR'),
      ),
    );
  });

  group('P1 os meus eventos', () {
    testWidgets('lists tickets, changes and public events', (tester) async {
      await _pump(tester);

      expect(find.text('Os meus eventos'), findsWidgets);
      expect(find.text('HOJE'), findsOneWidget);
      expect(find.text('ADIADO · 8 NOV'), findsOneWidget);
      expect(find.text('Adiado'), findsOneWidget);

      await tester.scrollUntilVisible(find.text('Corrida de Natal'), 200);
      expect(find.text('Eventos públicos'), findsOneWidget);
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
      final store = await _pump(tester, location: '/importar');

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
      final store = await _pump(tester, location: '/importar');

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
      final store = await _pump(tester, location: '/importar');

      await tester.tap(find.text('Captura de ecrã ou foto'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Associar bilhete'));
      await tester.pumpAndSettle();
      expect(find.text('Este evento ainda não usa o Crowded'), findsOneWidget);
      expect(store.tickets, hasLength(4));
      expect(store.pendingImport, isNull);
    });

    testWidgets('explains a ticket without a QR code', (tester) async {
      await _pump(tester, location: '/importar/erro/sem-qr');
      expect(
        find.text('Não encontrámos o código QR do bilhete'),
        findsOneWidget,
      );
    });

    testWidgets('explains a ticket linked to another account', (tester) async {
      await _pump(tester, location: '/importar/erro/outra-conta');
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
      final store = await _pump(tester, location: '/codigo-de-acesso');

      await tester.tap(find.text('Ler código QR'));
      await tester.pumpAndSettle();
      expect(store.isConnected('sao-joao'), isTrue);
      expect(find.text('São João na Ribeira'), findsWidgets);
    });

    testWidgets('says when the code is not found', (tester) async {
      await _pump(tester, location: '/codigo-de-acesso');

      await tester.enterText(find.byType(TextField), 'SAOJOA024');
      await tester.tap(find.text('Abrir evento'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Código não encontrado.'), findsOneWidget);
    });

    testWidgets('joins the public event and opens its page', (tester) async {
      final store = await _pump(tester, location: '/codigo-de-acesso');

      await tester.enterText(find.byType(TextField), 'saojoao24');
      await tester.tap(find.text('Abrir evento'));
      await tester.pumpAndSettle();
      expect(store.isConnected('sao-joao'), isTrue);
      expect(find.text('São João na Ribeira'), findsWidgets);
    });
  });

  testWidgets('P8 shows a left event instead of its page', (tester) async {
    await _pump(tester, location: '/evento/mercado-outono');

    expect(find.text('Já não estás ligado a este evento'), findsOneWidget);
    expect(find.textContaining('Mercado de Outono · saíste'), findsOneWidget);
    expect(find.text('Inserir código de acesso'), findsOneWidget);
  });

  group('P5 event page', () {
    testWidgets('shows live information on the event day', (tester) async {
      await _pump(tester, location: '/evento/festival-margens');

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
      await _pump(tester, location: '/evento/orquestra-sinfonica');

      expect(find.text('Faltam 28 dias'), findsOneWidget);
      expect(find.text('Agora no recinto'), findsNothing);
    });

    testWidgets('shows saved data and disables reports offline', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/evento/festival-margens');
      store.setOffline(true);
      await tester.pumpAndSettle();

      expect(find.text('Sem rede'), findsOneWidget);
      expect(find.text('Lotação e filas'), findsOneWidget);
      expect(find.text('Agora no recinto'), findsNothing);
    });

    testWidgets('links to the participant estimates', (tester) async {
      await _pump(tester, location: '/evento/festival-margens');

      await tester.ensureVisible(find.text('Estimativas de participantes · 2'));

      await tester.pumpAndSettle();
      await tester.tap(find.text('Estimativas de participantes · 2'));
      await tester.pumpAndSettle();
      expect(find.text('9 confirmaram · 1 negou'), findsOneWidget);
    });

    testWidgets('removes the ticket from the event page', (tester) async {
      final store = await _pump(tester, location: '/evento/a-tempestade');

      await tester.tap(find.bySemanticsLabel('Mais opções'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(AppButton, 'Remover bilhete'));
      await tester.pumpAndSettle();
      expect(store.isConnected('a-tempestade'), isFalse);
      expect(find.text('ADIADO · 8 NOV'), findsNothing);
    });

    testWidgets('P10 asks for location before reporting', (tester) async {
      await _pump(tester, location: '/evento/festival-margens');

      await tester.tap(find.byType(AppReportButton));
      await tester.pumpAndSettle();
      expect(find.text('Confirmar que estás no recinto'), findsOneWidget);

      await tester.tap(find.text('Agora não'));
      await tester.pumpAndSettle();
      expect(find.text('Confirmar que estás no recinto'), findsNothing);
    });
  });

  testWidgets('P6 → P7 shows the official change', (tester) async {
    await _pump(tester, location: '/evento/a-tempestade/historico');

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

  testWidgets('P8 lists the notifications, filters and opens them', (
    tester,
  ) async {
    final api = _FakeNotificationsApi();
    final store = await _pump(
      tester,
      location: '/notificacoes',
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
      location: '/notificacoes',
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
      expect(api.tokens, ['token']);
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
      await _pump(tester, location: '/eventos', signedIn: false);

      expect(find.text('Sabe o que se passa nos teus eventos'), findsOneWidget);
      expect(find.text('Esqueci-me da palavra-passe'), findsOneWidget);
    });

    testWidgets('validates and signs in', (tester) async {
      final store = await _pump(tester, location: '/entrar', signedIn: false);

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
      expect(find.text('ADIADO · 8 NOV'), findsOneWidget);
    });

    testWidgets('sends an organization member to the backoffice', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/entrar', signedIn: false);

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
    });

    testWidgets('says when Core cannot be reached or limits the attempts', (
      tester,
    ) async {
      final auth = _FakeAuthApi()..failWith = 'NETWORK';
      await _pump(tester, location: '/entrar', signedIn: false, auth: auth);

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
      final store = await _pump(tester, location: '/entrar', signedIn: false);

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
      expect(find.text('ADIADO · 8 NOV'), findsOneWidget);
    });

    testWidgets('refuses an email that already has an account', (tester) async {
      await _pump(tester, location: '/entrar', signedIn: false);

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
    final store = await _pump(tester, location: '/conta');

    expect(find.text(_email), findsOneWidget);
    expect(find.text('Contribuições'), findsOneWidget);
    expect(find.text('Os teus reportes e confirmações'), findsOneWidget);
    await tester.ensureVisible(find.text('Terminar sessão'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Terminar sessão'));
    await tester.pumpAndSettle();
    expect(store.signedIn, isFalse);
    expect(find.text('Sabe o que se passa nos teus eventos'), findsOneWidget);
  });

  testWidgets('C2 updates notification preferences', (tester) async {
    final store = await _pump(tester, location: '/conta/notificacoes');

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
    final store = await _pump(tester, location: '/conta/apagar');

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
      final store = await _pump(tester, location: '/evento/festival-margens');
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
      final store = await _pump(tester, location: '/evento/festival-margens');
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
      await _pump(tester, location: '/evento/festival-margens', size: _desktop);
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
      location: '/evento/festival-margens?confirmar=c-porta-c',
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
    await _pump(tester, location: '/evento/festival-margens/estimativas');

    expect(find.text('Fila nos WC junto ao palco · 15–20 min'), findsOneWidget);
    expect(find.text('Rampa da entrada Norte bloqueada'), findsOneWidget);
    expect(find.text('9 confirmaram · 1 negou'), findsOneWidget);
  });

  testWidgets('P15 shows the impact of a helpful report', (tester) async {
    await _pump(tester, location: '/reconhecimento/r-agua');

    expect(find.text('O teu reporte ajudou!'), findsOneWidget);
    expect(find.text('Problema resolvido em 18 min'), findsOneWidget);
    expect(find.text('Alerta enviado a 1 240 pessoas'), findsOneWidget);
  });

  testWidgets('P17 answers a request and shares an idea', (tester) async {
    final store = await _pump(
      tester,
      location: '/evento/festival-margens/pedidos',
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
    await _pump(tester, location: '/evento/festival-margens/como-chegar');

    expect(find.text('Parque da Cidade · Porta A'), findsOneWidget);
    expect(find.text('Autocarro 502'), findsOneWidget);
    expect(find.text('Perturbação'), findsOneWidget);
    expect(find.textContaining('horários GTFS'), findsOneWidget);
  });

  group('P19 entry code', () {
    testWidgets('shows the code in the app', (tester) async {
      await _pump(tester, location: '/evento/festival-margens/codigo');

      expect(find.byType(EntryCodeImage), findsOneWidget);
      expect(find.text('Ref. •••• 4821'), findsOneWidget);
      expect(find.text(_email), findsOneWidget);
    });

    testWidgets('asks for the password on the web and hides after 2 min', (
      tester,
    ) async {
      await _pump(
        tester,
        location: '/evento/festival-margens/codigo',
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
    await _pump(tester, location: '/conta/contribuicoes');

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
        '/eventos',
        '/conta',
        '/notificacoes',
        '/importar',
        '/importar/codigo',
        '/importar/adicionado',
        '/importar/erro/sem-qr',
        '/importar/erro/sem-crowded',
        '/importar/erro/outra-conta',
        '/codigo-de-acesso',
        '/sem-ligacao/mercado-outono',
        '/reportar',
        '/evento/festival-margens',
        '/evento/jazz-no-parque',
        '/evento/a-tempestade',
        '/evento/a-tempestade/historico',
        '/evento/a-tempestade/alteracao/tempestade-date',
        '/evento/feira-medieval',
        '/evento/nao-existe',
        '/conta/notificacoes',
        '/conta/apagar',
        '/evento/festival-margens/reportar',
        '/evento/festival-margens/reporte?estado=enviado',
        '/evento/festival-margens/reporte?estado=longe',
        '/evento/festival-margens/estimativas',
        '/reconhecimento/r-agua',
        '/evento/festival-margens/pedidos',
        '/evento/festival-margens/como-chegar',
        '/evento/feira-medieval/como-chegar',
        '/evento/festival-margens/codigo',
        '/evento/jazz-no-parque/codigo',
        '/conta/contribuicoes',
      ]) {
        await _pump(tester, location: location, size: size);
        expect(tester.takeException(), isNull, reason: location);
      }
    });
  }
}
