import 'dart:math';

import 'package:crowded/dev/component_gallery.dart';
import 'package:crowded/ui/ui.dart';
import 'package:crowded/utils/pt_format.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _wrap(Widget child) => MaterialApp(
  theme: AppTheme.light(),
  home: Scaffold(
    body: Padding(padding: const EdgeInsets.all(16), child: child),
  ),
);

void main() {
  final now = DateTime(2026, 10, 17, 21);

  group('formatTimeAgo', () {
    test('formats minutes, hours and days in European Portuguese', () {
      expect(
        formatTimeAgo(now.subtract(const Duration(seconds: 30)), now: now),
        'agora',
      );
      expect(
        formatTimeAgo(now.subtract(const Duration(minutes: 5)), now: now),
        'há 5 min',
      );
      expect(
        formatTimeAgo(now.subtract(const Duration(hours: 2)), now: now),
        'há 2 h',
      );
      expect(
        formatTimeAgo(now.subtract(const Duration(days: 1)), now: now),
        'há 1 dia',
      );
      expect(
        formatTimeAgo(now.subtract(const Duration(days: 3)), now: now),
        'há 3 dias',
      );
    });

    test('formatSource adds the "Fonte:" prefix unless disabled', () {
      final updated = now.subtract(const Duration(minutes: 3));
      expect(
        formatSource('organização', updated, now: now),
        'Fonte: organização · há 3 min',
      );
      expect(
        formatSource(
          'Organização Margens',
          updated,
          now: now,
          withPrefix: false,
        ),
        'Organização Margens · há 3 min',
      );
    });
  });

  group('SourceLabel', () {
    testWidgets('shows the tags only when told, with words and an icon', (
      tester,
    ) async {
      await tester.pumpWidget(
        _wrap(
          Column(
            children: [
              SourceLabel(
                source: 'IPMA',
                updatedAt: now.subtract(const Duration(minutes: 19)),
                now: now,
              ),
              SourceLabel(
                source: 'organização',
                updatedAt: now.subtract(const Duration(hours: 5)),
                now: now,
                stale: true,
              ),
              SourceLabel(
                source: 'IA',
                updatedAt: now.subtract(const Duration(minutes: 2)),
                now: now,
                generatedByAi: true,
              ),
            ],
          ),
        ),
      );

      expect(find.text('Fonte: IPMA · há 19 min'), findsOneWidget);
      expect(find.text('Fonte: organização · há 5 h'), findsOneWidget);
      expect(find.text('Fonte: IA · há 2 min'), findsOneWidget);
      expect(find.text('Desatualizado'), findsOneWidget);
      expect(find.text('Gerado por IA'), findsOneWidget);
      expect(find.byIcon(Icons.schedule), findsOneWidget);
      expect(find.byIcon(Icons.auto_awesome), findsOneWidget);
    });
  });

  testWidgets(
    'CrowdLevelIndicator shows the level as text, number for screen readers',
    (tester) async {
      await tester.pumpWidget(
        _wrap(const CrowdLevelIndicator(level: CrowdLevel.high)),
      );

      expect(find.text('Lotação Elevada', findRichText: true), findsOneWidget);
      expect(
        find.bySemanticsLabel('Lotação Elevada, nível 3 de 4'),
        findsOneWidget,
      );
    },
  );

  testWidgets('AppSegmentedControl reports the tapped segment', (tester) async {
    String? selected = 'a';
    await tester.pumpWidget(
      _wrap(
        StatefulBuilder(
          builder: (context, setState) => AppSegmentedControl<String>(
            selected: selected,
            onChanged: (v) => setState(() => selected = v),
            segments: const [
              AppSegment(value: 'a', label: '< 10 min'),
              AppSegment(value: 'b', label: '10–20'),
            ],
          ),
        ),
      ),
    );

    await tester.tap(find.text('10–20'));
    await tester.pump();
    expect(selected, 'b');
  });

  testWidgets('AppSelectField picks an option from the bottom sheet', (
    tester,
  ) async {
    String? value;
    await tester.pumpWidget(
      _wrap(
        StatefulBuilder(
          builder: (context, setState) => AppSelectField<String>(
            label: 'Onde?',
            value: value,
            onChanged: (v) => setState(() => value = v),
            options: const [
              AppSelectOption(value: 'a', label: 'Porta A'),
              AppSelectOption(value: 'c', label: 'Porta C'),
            ],
          ),
        ),
      ),
    );

    expect(find.text('Escolher'), findsOneWidget);
    await tester.tap(find.text('Escolher'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Porta C'));
    await tester.pumpAndSettle();

    expect(value, 'c');
    expect(find.text('Porta C'), findsOneWidget);
  });

  testWidgets('AppButton does nothing when disabled', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      _wrap(
        Column(
          children: [
            AppButton(label: 'Ativo', onPressed: () => taps++),
            const AppButton(label: 'Inativo', onPressed: null),
          ],
        ),
      ),
    );

    await tester.tap(find.text('Ativo'));
    await tester.tap(find.text('Inativo'));
    expect(taps, 1);
  });

  testWidgets('AppTextField shows the optional hint and error text', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        const Column(
          children: [
            AppTextField.multiline(label: 'Descrição', optional: true),
            AppTextField(
              label: 'Email',
              errorText: 'Introduz um email válido.',
            ),
          ],
        ),
      ),
    );

    expect(find.text('(opcional)'), findsOneWidget);
    expect(find.text('Introduz um email válido.'), findsOneWidget);
  });

  for (final (name, size) in [
    ('mobile', const Size(390, 844)),
    ('desktop', const Size(1440, 900)),
  ]) {
    testWidgets('component gallery renders without overflow on $name', (
      tester,
    ) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(
        MaterialApp(theme: AppTheme.light(), home: const ComponentGallery()),
      );
      await tester.pumpAndSettle();

      final scrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(
        find.text('Estados'),
        400,
        scrollable: scrollable,
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });
  }

  test('formats counts and durations in European Portuguese', () {
    expect(formatCount(1240), '1 240');
    expect(formatCount(486), '486');
    expect(formatDuration(const Duration(minutes: 18)), '18 min');
    expect(formatDuration(const Duration(minutes: 65)), '1 h 05 min');
    expect(
      formatSentAgo(now.subtract(const Duration(days: 1)), now: now),
      'ontem',
    );
  });

  testWidgets('ConfirmationBar says how many confirmed and denied', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(const ConfirmationBar(confirmed: 1, denied: 2)),
    );
    expect(find.text('1 confirmou · 2 negaram'), findsOneWidget);
    expect(
      tester
          .widget<LinearProgressIndicator>(find.byType(LinearProgressIndicator))
          .value,
      closeTo(1 / 3, 0.001),
    );
  });

  testWidgets('AppOptionTile reports its selection to accessibility', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      _wrap(
        AppOptionTile(label: 'Talvez', selected: true, onTap: () => taps++),
      ),
    );
    await tester.tap(find.text('Talvez'));
    expect(taps, 1);
    expect(
      tester.getSemantics(find.byType(AppOptionTile)),
      isSemantics(
        isButton: true,
        isChecked: true,
        isInMutuallyExclusiveGroup: true,
        label: 'Talvez',
      ),
    );
  });

  testWidgets('switch thumb stays visible when off', (tester) async {
    await tester.pumpWidget(_wrap(Switch(value: false, onChanged: (_) {})));
    final theme = Theme.of(tester.element(find.byType(Switch))).switchTheme;
    final thumb = theme.thumbColor!.resolve({})!;
    final track = theme.trackColor!.resolve({})!;

    double contrast(Color a, Color b) {
      final (l1, l2) = (a.computeLuminance(), b.computeLuminance());
      return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05);
    }

    // WCAG 2.1 AA non-text contrast for the thumb and the track.
    expect(contrast(thumb, track), greaterThanOrEqualTo(3));
    expect(contrast(track, AppColors.surface), greaterThanOrEqualTo(3));
  });
}
