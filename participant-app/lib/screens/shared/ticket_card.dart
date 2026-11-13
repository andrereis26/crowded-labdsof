import 'package:flutter/material.dart';

import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';

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

/// Wallet card for a ticket, with the date and status wording of the
/// mockups ("HOJE", "ADIADO · 8 NOV", "Adiado · era 25 OUT").
///
/// Wrapped in a [Hero] so it expands into the event page (P1 → P5).
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

  static Object heroTag(String eventId) => 'ticket-$eventId';

  @override
  Widget build(BuildContext context) {
    final event = ticket.event;
    final (dateLabel, statusLabel, tone) = eventCardStatus(
      event,
      now,
      scheduledLabel: 'Bilhete',
    );

    final updatedAt = event.updatedAt;
    final source = updatedAt == null
        ? 'Fonte: organização'
        : savedAt != null
        ? 'Fonte: organização · guardado às ${formatTime(savedAt!)}'
        : formatSource('organização', updatedAt, now: now);

    return Hero(
      tag: heroTag(event.id),
      child: WalletCard(
        eventName: event.name,
        dateLabel: dateLabel,
        venue: event.place,
        time: formatTime(event.start),
        gate: ticket.gate,
        seat: ticket.seat,
        statusLabel: statusLabel,
        statusTone: tone,
        source: source,
        gradient: event.art.gradient,
        onTap: onTap,
      ),
    );
  }
}
