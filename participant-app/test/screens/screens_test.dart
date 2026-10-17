import 'package:crowded/app.dart';
import 'package:crowded/data/app_store.dart';
import 'package:crowded/data/models.dart';
import 'package:crowded/ui/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// Saturday 17 October 2026, 15:00.
final _now = DateTime(2026, 10, 17, 15);

const _mobile = Size(390, 844);
const _desktop = Size(1440, 1000);

Future<AppStore> _pump(
  WidgetTester tester, {
  String location = '/carteira',
  Size size = _mobile,
  bool signedIn = true,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final store = AppStore(clock: () => _now);
  if (!signedIn) store.signOut();
  await tester.pumpWidget(CrowdedApp(store: store, initialLocation: location));
  await tester.pumpAndSettle();
  return store;
}

void main() {
  group('P1 wallet', () {
    testWidgets('lists tickets, changes and followed events', (tester) async {
      await _pump(tester);

      expect(find.text('Carteira'), findsWidgets);
      expect(find.text('4 bilhetes · 1 alteração'), findsOneWidget);
      expect(find.text('HOJE'), findsOneWidget);
      expect(find.text('ADIADO · 8 NOV'), findsOneWidget);
      expect(find.text('Adiado · era 25 OUT'), findsOneWidget);

      await tester.scrollUntilVisible(find.text('Corrida de Natal'), 200);
      expect(find.text('Feira Medieval de Gaia'), findsOneWidget);
    });

    testWidgets('opens the event page from the front card', (tester) async {
      await _pump(tester);

      await tester.tap(find.text('Festival Margens').first);
      await tester.pumpAndSettle();

      expect(find.text('Mostrar código de entrada'), findsOneWidget);
      expect(find.text('Porta B fechada. Use a Porta C.'), findsOneWidget);
    });
  });

  group('P2–P4 import', () {
    testWidgets('imports a PDF and links it to the catalog event', (
      tester,
    ) async {
      final store = await _pump(tester, location: '/importar');

      await tester.tap(find.text('PDF do bilhete'));
      await tester.pumpAndSettle();
      expect(find.text('Confirma os dados do bilhete'), findsOneWidget);
      expect(find.text('Código de entrada encontrado'), findsOneWidget);
      expect(find.text('Festival Margens 2026'), findsOneWidget);

      await tester.tap(find.text('Confirmar e continuar'));
      await tester.pumpAndSettle();
      expect(
        find.text('Qual destes eventos é o do teu bilhete?'),
        findsOneWidget,
      );
      expect(find.text('Correspondência alta'), findsOneWidget);
      expect(find.text('Correspondência baixa'), findsOneWidget);

      await tester.tap(find.text('Associar e guardar'));
      await tester.pumpAndSettle();
      expect(find.text('Bilhete guardado na carteira.'), findsOneWidget);
      expect(store.tickets, hasLength(4));
      expect(store.importDraft, isNull);
    });

    testWidgets('manual entry validates required fields', (tester) async {
      await _pump(tester, location: '/importar');

      await tester.tap(find.text('Introduzir dados manualmente'));
      await tester.pumpAndSettle();
      expect(find.text('Introduz os dados do bilhete'), findsOneWidget);

      await tester.ensureVisible(find.text('Confirmar e continuar'));
      await tester.tap(find.text('Confirmar e continuar'));
      await tester.pumpAndSettle();
      expect(find.text('Indica o nome do evento.'), findsOneWidget);
      expect(find.text('Usa o formato dd/mm/aaaa.'), findsOneWidget);
    });

    testWidgets('saves a ticket without linking it', (tester) async {
      final store = await _pump(tester, location: '/importar');
      store.startImport(TicketOrigin.manual)
        ..eventName = 'Noite de Fado'
        ..date = DateTime(2026, 11, 2)
        ..start = '21:00'
        ..place = 'Casa do Fado, Porto';
      final ticket = store.saveDraft(store.importDraft!);

      expect(ticket.event.name, 'Noite de Fado');
      expect(ticket.event.start, DateTime(2026, 11, 2, 21));
      expect(ticket.event.changes, isEmpty);
      expect(store.tickets, hasLength(5));
    });
  });

  group('P5 event page', () {
    testWidgets('shows live information on the event day', (tester) async {
      await _pump(tester, location: '/evento/festival-margens');

      expect(find.text('Agora no recinto'), findsOneWidget);
      expect(
        find.text('Lotação Moderada · 2 de 4', findRichText: true),
        findsOneWidget,
      );
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

    testWidgets('P10 asks for location before reporting', (tester) async {
      await _pump(tester, location: '/evento/festival-margens');

      await tester.ensureVisible(find.text('Reportar algo no recinto'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reportar algo no recinto'));
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
    expect(find.text('Sala confirmada'), findsOneWidget);

    await tester.tap(find.text('Data alterada'));
    await tester.pumpAndSettle();
    expect(find.text('A Tempestade foi adiada'), findsOneWidget);
    expect(find.text('O teu bilhete continua válido'), findsOneWidget);
    expect(find.text('Sem alteração'), findsNWidgets(2));
  });

  testWidgets('P8 filters notifications and answers requests', (tester) async {
    final store = await _pump(tester, location: '/notificacoes');
    expect(store.unreadCount, 2);

    await tester.ensureVisible(find.text('Pedidos'));
    await tester.tap(find.text('Pedidos'));
    await tester.pumpAndSettle();
    expect(
      find.text('Vais usar o autocarro especial das 23:30?'),
      findsOneWidget,
    );
    expect(find.text('O teu reporte ajudou'), findsNothing);

    await tester.tap(find.text('Sim'));
    await tester.pumpAndSettle();
    expect(find.text('Respondeste: Sim'), findsOneWidget);
    expect(store.unreadCount, 1);
  });

  testWidgets('P9 lists today\'s events and follows one', (tester) async {
    final store = await _pump(tester, location: '/eventos');

    expect(find.text('Tens bilhete'), findsOneWidget);
    expect(find.text('A seguir'), findsOneWidget);
    expect(store.isFollowing('mercado-outono'), isFalse);

    await tester.tap(find.text('Seguir').first);
    await tester.pumpAndSettle();
    expect(store.isFollowing('mercado-outono'), isTrue);
  });

  group('C1 sign in', () {
    testWidgets('signed-out users are sent to C1', (tester) async {
      await _pump(tester, location: '/carteira', signedIn: false);

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

      await tester.enterText(find.byType(TextField).at(0), 'ines@email.pt');
      await tester.enterText(find.byType(TextField).at(1), 'palavra-secreta');
      await tester.tap(find.widgetWithText(AppButton, 'Entrar'));
      await tester.pumpAndSettle();
      expect(store.signedIn, isTrue);
      expect(find.text('4 bilhetes · 1 alteração'), findsOneWidget);
    });

    testWidgets('creates an account', (tester) async {
      final store = await _pump(tester, location: '/entrar', signedIn: false);

      await tester.tap(find.text('Criar conta').first);
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(0), 'Ana Costa');
      await tester.enterText(find.byType(TextField).at(1), 'ana@email.pt');
      await tester.enterText(find.byType(TextField).at(2), 'palavra-secreta');
      await tester.tap(find.widgetWithText(AppButton, 'Criar conta'));
      await tester.pumpAndSettle();
      expect(store.account?.name, 'Ana Costa');
    });
  });

  testWidgets('Conta signs out to C1', (tester) async {
    final store = await _pump(tester, location: '/conta');

    expect(find.text('Inês Martins'), findsOneWidget);
    await tester.ensureVisible(find.text('Terminar sessão'));
    await tester.tap(find.text('Terminar sessão'));
    await tester.pumpAndSettle();
    expect(store.signedIn, isFalse);
    expect(find.text('Sabe o que se passa nos teus eventos'), findsOneWidget);
  });

  testWidgets('C2 updates notification preferences', (tester) async {
    final store = await _pump(tester, location: '/conta/notificacoes');

    expect(find.text('Só alterações oficiais'), findsOneWidget);
    await tester.tap(find.text('Alertas de meteorologia'));
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
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(store.signedIn, isFalse);
    expect(store.tickets, isEmpty);
    expect(find.text('A tua conta foi apagada.'), findsOneWidget);
  });

  for (final (name, size) in [('mobile', _mobile), ('desktop', _desktop)]) {
    testWidgets('every screen renders on $name', (tester) async {
      for (final location in [
        '/carteira',
        '/eventos',
        '/conta',
        '/notificacoes',
        '/importar',
        '/importar/rever',
        '/importar/associar',
        '/evento/festival-margens',
        '/evento/jazz-no-parque',
        '/evento/a-tempestade',
        '/evento/a-tempestade/historico',
        '/evento/a-tempestade/alteracao/tempestade-date',
        '/evento/feira-medieval',
        '/evento/nao-existe',
        '/conta/notificacoes',
        '/conta/apagar',
      ]) {
        await _pump(tester, location: location, size: size);
        expect(tester.takeException(), isNull, reason: location);
      }
    });
  }
}
