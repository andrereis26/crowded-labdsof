import 'package:flutter/material.dart';

import '../../data/models.dart';
import '../../ui/ui.dart';
import 'shell.dart';

/// Small event picture, name and a line of context at the top of a task
/// ("Festival Margens · Parque da Cidade · agora" in P11, P14, P17).
class EventContextRow extends StatelessWidget {
  const EventContextRow({
    super.key,
    required this.event,
    required this.subtitle,
    this.trailing,
    this.background,
  });

  final Event event;
  final String subtitle;

  /// Usually an [AppTag] ("No recinto").
  final Widget? trailing;

  /// Draws the row as a rounded box (white on pages, grey in dialogs).
  final Color? background;

  @override
  Widget build(BuildContext context) {
    final row = Row(
      spacing: 10,
      children: [
        EventThumbnail(gradient: event.art.gradient, size: 36, radius: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            spacing: 1,
            children: [
              Text(
                event.name,
                style: AppTextStyles.subhead,
                overflow: TextOverflow.ellipsis,
              ),
              Text(
                subtitle,
                style: AppTextStyles.caption.copyWith(height: 1.2),
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
        ?trailing,
      ],
    );
    if (background == null) return row;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: background,
        borderRadius: const BorderRadius.all(AppRadii.button),
      ),
      child: row,
    );
  }
}
