import 'package:crowded/data/models.dart';
import 'package:crowded/screens/shared/provenance_label.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _wrap(Widget child) => MaterialApp(home: Scaffold(body: child));

void main() {
  final now = DateTime(2026, 11, 27, 21);
  final updated = now.subtract(const Duration(minutes: 5));

  Provenance provenance(
    SourceType type, {
    String name = 'Ribeira Live',
    bool confirmed = true,
    DateTime? staleAt,
  }) => Provenance(
    type: type,
    name: name,
    updatedAt: updated,
    confirmed: confirmed,
    staleAt: staleAt,
  );

  test('names each kind of source in words', () {
    expect(sourceName(provenance(SourceType.organization)), 'organização');
    expect(
      sourceName(provenance(SourceType.official, name: 'Câmara do Porto')),
      'Câmara do Porto',
    );
    expect(sourceName(provenance(SourceType.external, name: 'IPMA')), 'IPMA');
    expect(
      sourceName(provenance(SourceType.estimate)),
      'estimativa de participantes',
    );
    expect(sourceName(provenance(SourceType.ai)), 'IA');
    expect(
      formatProvenance(provenance(SourceType.external, name: 'IPMA'), now: now),
      'Fonte: IPMA · há 5 min',
    );
  });

  testWidgets('tags information past the deadline Core sent', (tester) async {
    await tester.pumpWidget(
      _wrap(
        Column(
          children: [
            ProvenanceLabel(
              provenance: provenance(
                SourceType.organization,
                staleAt: now.add(const Duration(minutes: 1)),
              ),
              now: now,
            ),
            ProvenanceLabel(
              provenance: provenance(
                SourceType.estimate,
                staleAt: now.subtract(const Duration(seconds: 1)),
              ),
              now: now,
            ),
          ],
        ),
      ),
    );

    expect(find.text('Fonte: organização · há 5 min'), findsOneWidget);
    expect(
      find.text('Fonte: estimativa de participantes · há 5 min'),
      findsOneWidget,
    );
    expect(find.text('Desatualizado'), findsOneWidget);
  });

  testWidgets('labels AI content nobody confirmed, not confirmed AI', (
    tester,
  ) async {
    await tester.pumpWidget(
      _wrap(
        Column(
          children: [
            ProvenanceLabel(
              provenance: provenance(SourceType.ai, confirmed: false),
              now: now,
            ),
            ProvenanceLabel(provenance: provenance(SourceType.ai), now: now),
          ],
        ),
      ),
    );

    expect(find.text('Gerado por IA'), findsOneWidget);
    expect(find.text('Fonte: IA · há 5 min'), findsNWidgets(2));
  });
}
