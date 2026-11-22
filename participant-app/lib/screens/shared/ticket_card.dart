import 'package:flutter/material.dart';

import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import 'provenance_label.dart';

/// The date line, status pill and its tone of an event's card ("HOJE",
/// "ADIADO · 8 NOV"); [scheduledLabel] names an event that has not changed.
(String, String, WalletCardStatusTone) eventCardStatus(
  Event event,
  DateTime now, {
  required String scheduledLabel,
}) => switch (event.status) {
  EventStatus.postponed => (
    'ADIADO · ${formatDayMonth(event.start)}',
    'Adiado',
    WalletCardStatusTone.changed,
  ),
  EventStatus.relocated => (
    'NOVO LOCAL · ${formatDayMonth(event.start)}',
    'Novo local',
    WalletCardStatusTone.changed,
  ),
  EventStatus.cancelled => (
    'CANCELADO',
    'Cancelado',
    WalletCardStatusTone.cancelled,
  ),
  EventStatus.scheduled => (
    formatRelativeDay(event.start, now),
    scheduledLabel,
    WalletCardStatusTone.confirmed,
  ),
};

/// The latest change and the active alerts of an event as card notes, each
/// with the severity in words and an icon ("Importante · Evento cancelado").
List<WalletCardNote> eventNotes(Event event) {
  final change = event.changes.firstOrNull;
  final alerts = event.alerts;
  final alertSeverity = alerts.isEmpty
      ? null
      : alerts
            .map((a) => a.severity)
            .reduce((a, b) => a.index <= b.index ? a : b);
  return [
    if (change != null)
      WalletCardNote(
        icon: change.severity.icon,
        text: '${change.severity.label} · ${change.title}',
      ),
    if (alertSeverity != null)
      WalletCardNote(
        icon: alertSeverity.icon,
        text:
            '${alertSeverity.label} · '
            '${alerts.length == 1 ? '1 alerta ativo' : '${alerts.length} alertas ativos'}',
      ),
  ];
}

/// Wallet card for one of the participant's events, with the date and status
/// wording of the mockups ("HOJE", "ADIADO · 8 NOV", "Adiado · era 25 OUT"),
/// the doors opening time, the latest change and the active alerts. A
/// [ticket] adds its gate and seat.
///
/// Wrapped in a [Hero] so it expands into the event page (P1 → P5).
class ConnectionCard extends StatelessWidget {
  const ConnectionCard({
    super.key,
    required this.event,
    required this.now,
    this.ticket,
    this.onTap,
    this.savedAt,
  });

  final Event event;
  final Ticket? ticket;
  final DateTime now;
  final VoidCallback? onTap;

  /// When offline: the time the data was saved, shown instead of "há …".
  final DateTime? savedAt;

  static Object heroTag(String eventId) => 'ticket-$eventId';

  @override
  Widget build(BuildContext context) {
    final (dateLabel, statusLabel, tone) = eventCardStatus(
      event,
      now,
      scheduledLabel: ticket == null ? 'Público' : 'Bilhete',
    );

    final origin = event.source;
    final name = origin == null ? 'organização' : sourceName(origin);
    final source = origin == null
        ? 'Fonte: $name'
        : savedAt != null
        ? 'Fonte: $name · guardado às ${formatTime(savedAt!)}'
        : formatProvenance(origin, now: now);

    return Hero(
      tag: heroTag(event.id),
      child: WalletCard(
        eventName: event.name,
        dateLabel: dateLabel,
        venue: event.place,
        time: formatTime(event.start),
        doors: event.doorsOpen == null ? null : formatTime(event.doorsOpen!),
        gate: ticket?.gate,
        seat: ticket?.seat,
        notes: eventNotes(event),
        statusLabel: statusLabel,
        statusTone: tone,
        source: source,
        gradient: event.art.gradient,
        onTap: onTap,
      ),
    );
  }
}

/// Wallet card for a ticket: a [ConnectionCard] with its ticket.
class TicketCard extends StatelessWidget {
  const TicketCard({
    super.key,
    required this.ticket,
    required this.now,
    this.onTap,
    this.savedAt,
  });

  final Ticket ticket;
  final DateTime now;
  final VoidCallback? onTap;

  /// When offline: the time the data was saved, shown instead of "há …".
  final DateTime? savedAt;

  static Object heroTag(String eventId) => ConnectionCard.heroTag(eventId);

  @override
  Widget build(BuildContext context) => ConnectionCard(
    event: ticket.event,
    ticket: ticket,
    now: now,
    onTap: onTap,
    savedAt: savedAt,
  );
}
