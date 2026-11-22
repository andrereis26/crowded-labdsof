import 'package:flutter/material.dart';

import '../../data/models.dart';
import '../../ui/ui.dart';

/// The words that name where information comes from: "organização", "IPMA",
/// "estimativa de participantes".
String sourceName(Provenance provenance) => switch (provenance.type) {
  SourceType.organization => 'organização',
  SourceType.official || SourceType.external => provenance.name,
  SourceType.estimate => 'estimativa de participantes',
  SourceType.ai => 'IA',
};

/// "Fonte: organização · há 5 min", for places that show only text.
String formatProvenance(Provenance provenance, {DateTime? now}) =>
    formatSource(sourceName(provenance), provenance.updatedAt, now: now);

/// A [SourceLabel] for [provenance]: its real source and time, tagged when
/// Core says it is out of date and when it is AI-generated and unconfirmed.
class ProvenanceLabel extends StatelessWidget {
  const ProvenanceLabel({
    super.key,
    required this.provenance,
    this.now,
    this.updated = false,
    this.color,
  });

  final Provenance provenance;
  final DateTime? now;

  /// Words the time as "atualizado há 5 min".
  final bool updated;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final reference = now ?? DateTime.now();
    return SourceLabel(
      source: sourceName(provenance),
      updatedAt: provenance.updatedAt,
      now: reference,
      stale: provenance.isStaleAt(reference),
      generatedByAi: provenance.isUnconfirmedAi,
      updated: updated,
      color: color,
    );
  }
}
