import 'package:crowded/app.dart';
import 'package:crowded/data/app_store.dart';
import 'package:crowded/data/auth_api.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

/// Core's sign-in, refusing every attempt, so C1 shows its error. With
/// [restored], the browser holds an organization account's session.
class _RefusingAuthApi implements AuthApi {
  _RefusingAuthApi({this.restored});

  final AuthSession? restored;

  @override
  Future<void> register({
    required String email,
    required String password,
  }) async => throw const AuthException('EMAIL_ALREADY_REGISTERED');

  @override
  Future<AuthSession> signIn({
    required String email,
    required String password,
  }) async => throw const AuthException('INVALID_CREDENTIALS');

  @override
  Future<AuthSession?> restore() async => restored;

  @override
  Future<void> signOut() async {}

  @override
  http.Client client() => http.Client();

  @override
  set onEnded(void Function()? callback) {}
}

/// The WCAG 2.1 AA checks Flutter can run: every tap target has a label, and
/// text has enough contrast. Android's and iOS's minimum tap sizes go beyond
/// 2.1 AA, so they are not checked here.
Future<void> _expectAccessible(WidgetTester tester) async {
  await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
  await expectLater(tester, meetsGuideline(textContrastGuideline));
}

void main() {
  for (final size in [const Size(390, 844), const Size(1440, 900)]) {
    testWidgets('C1 meets the accessibility guidelines at $size', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final store = AppStore(auth: _RefusingAuthApi());
      await tester.pumpWidget(
        CrowdedApp(store: store, initialLocation: '/sign-in'),
      );
      await tester.pumpAndSettle();

      await _expectAccessible(tester);

      // Signing in and creating an account, with their error messages.
      await tester.enterText(find.byType(TextField).at(0), 'ana@email.pt');
      await tester.enterText(find.byType(TextField).at(1), 'errada');
      await tester.tap(find.text('Entrar').last);
      await tester.pumpAndSettle();
      expect(
        find.text('O email ou a palavra-passe estão errados.'),
        findsOneWidget,
      );
      await _expectAccessible(tester);

      await tester.tap(find.text('Criar conta').first);
      await tester.pumpAndSettle();
      await _expectAccessible(tester);

      // The strength meter, in its five colours.
      for (final password in [
        'curta',
        'palavra-secreta',
        'Segr3do!',
        'Segr3do!-Forte',
        'Palavra-secr3ta!',
      ]) {
        await tester.enterText(find.byType(TextField).at(1), password);
        await tester.pumpAndSettle();
        await _expectAccessible(tester);
      }

      semantics.dispose();
    });
  }

  testWidgets('C1 announces the error to screen readers', (tester) async {
    final semantics = tester.ensureSemantics();
    final store = AppStore(auth: _RefusingAuthApi());
    await tester.pumpWidget(
      CrowdedApp(store: store, initialLocation: '/sign-in'),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).at(0), 'ana@email.pt');
    await tester.enterText(find.byType(TextField).at(1), 'errada');
    await tester.tap(find.text('Entrar').last);
    await tester.pumpAndSettle();

    final error = tester.getSemantics(
      find.text('O email ou a palavra-passe estão errados.'),
    );
    expect(error.flagsCollection.isLiveRegion, isTrue);
    expect(find.bySemanticsLabel(RegExp('^Email')), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('C1 can be filled in and sent with the keyboard alone', (
    tester,
  ) async {
    final store = AppStore(auth: _RefusingAuthApi());
    await tester.pumpWidget(
      CrowdedApp(store: store, initialLocation: '/sign-in'),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byType(TextField).at(0));
    await tester.enterText(find.byType(TextField).at(0), 'ana@email.pt');
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    final password = tester.widget<EditableText>(
      find.descendant(
        of: find.byType(TextField).at(1),
        matching: find.byType(EditableText),
      ),
    );
    expect(password.focusNode.hasFocus, isTrue);

    await tester.enterText(find.byType(TextField).at(1), 'errada');
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    expect(
      find.text('O email ou a palavra-passe estão errados.'),
      findsOneWidget,
    );
  });

  testWidgets("C1 meets the guidelines with an organization's session", (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final store = AppStore(
      auth: _RefusingAuthApi(
        restored: const AuthSession(
          userId: 'u-1',
          role: 'ORGANIZATION_MEMBER',
          email: 'equipa@ribeira-live.test',
        ),
      ),
    );
    await store.restoreSession();
    await tester.pumpWidget(
      CrowdedApp(store: store, initialLocation: '/sign-in'),
    );
    await tester.pumpAndSettle();
    expect(find.text('Terminar sessão'), findsOneWidget);

    await _expectAccessible(tester);
    semantics.dispose();
  });
}
