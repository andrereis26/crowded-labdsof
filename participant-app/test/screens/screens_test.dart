import 'package:crowded/app.dart';
import 'package:crowded/data/app_store.dart';
import 'package:crowded/data/models.dart';
import 'package:crowded/screens/event/entry_code_screen.dart';
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
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final store = AppStore(clock: () => _now);
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

  group('P11–P12 report', () {
    Future<void> openReport(WidgetTester tester) async {
      await tester.ensureVisible(find.text('Reportar algo no recinto'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reportar algo no recinto'));
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

  testWidgets('P16 sends a poll answer once every question has one', (
    tester,
  ) async {
    final store = await _pump(tester, location: '/sondagem/p-inverno');
    final send = find.widgetWithText(AppButton, 'Enviar resposta');
    expect(tester.widget<AppButton>(send).onPressed, isNull);

    await tester.tap(find.text('Sim, vou'));
    await tester.ensureVisible(find.text('Entre as 20h e as 22h'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Entre as 20h e as 22h'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(send);
    await tester.tap(send);
    await tester.pumpAndSettle();

    expect(store.poll('p-inverno')!.answers, [0, 1]);
    expect(store.isFollowing('margens-inverno'), isFalse);
  });

  testWidgets('P17 answers a request and shares an idea', (tester) async {
    final store = await _pump(
      tester,
      location: '/evento/festival-margens/pedidos',
    );
    expect(find.text('3 pedidos da organização · 1 urgente'), findsOneWidget);

    await tester.tap(find.text('Sim'));
    await tester.pumpAndSettle();
    expect(find.text('Respondeste: Sim'), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'Mais casas de banho');
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Partilhar ideia'));
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
  });

  group('P19 entry code', () {
    testWidgets('shows the code in the app', (tester) async {
      await _pump(tester, location: '/evento/festival-margens/codigo');

      expect(find.byType(EntryCodeImage), findsOneWidget);
      expect(find.text('Ref. •••• 4821'), findsOneWidget);
      expect(find.text('Inês Martins'), findsOneWidget);
    });

    testWidgets('explains when the ticket has no code', (tester) async {
      await _pump(tester, location: '/evento/jazz-no-parque/codigo');

      expect(find.text('Código de entrada não disponível'), findsOneWidget);
      expect(find.byType(EntryCodeImage), findsNothing);
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
        '/evento/festival-margens/reportar',
        '/evento/festival-margens/reporte?estado=enviado',
        '/evento/festival-margens/reporte?estado=longe',
        '/evento/festival-margens/estimativas',
        '/reconhecimento/r-agua',
        '/sondagem/p-inverno',
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
