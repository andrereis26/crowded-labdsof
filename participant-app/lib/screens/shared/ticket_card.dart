import 'package:flutter/material.dart';

import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';

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
    final (dateLabel, statusLabel, tone) = switch (event.status) {
      EventStatus.postponed => (
        'ADIADO · ${formatDayMonth(event.start)}',
        'Adiado',
        WalletCardStatusTone.changed,
      ),
      EventStatus.cancelled => (
        'CANCELADO',
        'Cancelado',
        WalletCardStatusTone.cancelled,
      ),
      EventStatus.scheduled => (
        formatRelativeDay(event.start, now),
        'Confirmado',
        WalletCardStatusTone.confirmed,
      ),
    };

    final updatedAt = event.sourceUpdatedAt;
    final source = updatedAt == null
        ? 'Fonte: o teu bilhete'
        : savedAt != null
        ? 'Fonte: bilheteira oficial · guardado às ${formatTime(savedAt!)}'
        : formatSource('bilheteira oficial', updatedAt, now: now);

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
