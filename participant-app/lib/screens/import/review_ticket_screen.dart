import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/shell.dart';

/// P3 · Rever dados extraídos / entrada manual.
class ReviewTicketScreen extends StatefulWidget {
  const ReviewTicketScreen({super.key});

  @override
  State<ReviewTicketScreen> createState() => _ReviewTicketScreenState();
}

class _ReviewTicketScreenState extends State<ReviewTicketScreen> {
  TicketDraft? _draft;
  final _event = TextEditingController();
  final _date = TextEditingController();
  final _gate = TextEditingController();
  final _doors = TextEditingController();
  final _start = TextEditingController();
  final _place = TextEditingController();
  final _seat = TextEditingController();
  String? _eventError;
  String? _dateError;
  String? _startError;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_draft != null) return;
    final store = AppStore.of(context);
    // Opening the page directly (e.g. a web refresh) starts a manual entry.
    final draft = store.importDraft ?? store.startImport(TicketOrigin.manual);
    _draft = draft;
    _event.text = draft.eventName;
    _date.text = draft.date == null ? '' : formatNumericDate(draft.date!);
    _gate.text = draft.gate;
    _doors.text = draft.doorsOpen;
    _start.text = draft.start;
    _place.text = draft.place;
    _seat.text = draft.seat;
  }

  @override
  void dispose() {
    for (final c in [_event, _date, _gate, _doors, _start, _place, _seat]) {
      c.dispose();
    }
    super.dispose();
  }

  static DateTime? _parseDate(String value) {
    final match = RegExp(r'^(\d{1,2})/(\d{1,2})/(\d{4})$')
        .firstMatch(value.trim());
    if (match == null) return null;
    final (d, m, y) = (
      int.parse(match[1]!),
      int.parse(match[2]!),
      int.parse(match[3]!),
    );
    final date = DateTime(y, m, d);
    return date.day == d && date.month == m ? date : null;
  }

  static bool _isTime(String value) =>
      value.isEmpty || RegExp(r'^([01]?\d|2[0-3]):[0-5]\d$').hasMatch(value);

  void _continue() {
    final date = _parseDate(_date.text);
    setState(() {
      _eventError = _event.text.trim().isEmpty
          ? 'Indica o nome do evento.'
          : null;
      _dateError = date == null ? 'Usa o formato dd/mm/aaaa.' : null;
      _startError = _isTime(_start.text.trim()) && _isTime(_doors.text.trim())
          ? null
          : 'Usa o formato hh:mm.';
    });
    if (_eventError != null || _dateError != null || _startError != null) {
      return;
    }
    _draft!
      ..eventName = _event.text.trim()
      ..date = date
      ..gate = _gate.text.trim()
      ..doorsOpen = _doors.text.trim()
      ..start = _start.text.trim()
      ..place = _place.text.trim()
      ..seat = _seat.text.trim();
    context.push('/importar/associar');
  }

  @override
  Widget build(BuildContext context) {
    final draft = _draft!;
    final manual = draft.origin == TicketOrigin.manual;
    final timeFormatter = FilteringTextInputFormatter.allow(RegExp(r'[0-9:]'));

    return AppPage(
      title: 'Rever bilhete',
      maxWidth: 560,
      children: [
        AppStepHeading(
          step: 2,
          totalSteps: 3,
          title: manual
              ? 'Introduz os dados do bilhete'
              : 'Confirma os dados do bilhete',
          subtitle: switch (draft.origin) {
            TicketOrigin.pdf => 'Preenchemos a partir do teu PDF.',
            TicketOrigin.email => 'Preenchemos a partir do teu email.',
            TicketOrigin.manual => null,
          },
        ),
        if (draft.hasEntryCode)
          const Align(
            alignment: Alignment.centerLeft,
            child: AppTag(
              label: 'Código de entrada encontrado',
              tone: AppTagTone.success,
              size: AppTagSize.large,
              icon: Icons.check,
            ),
          ),
        AppPanel(
          children: [
            AppTextField(
              label: 'Evento',
              controller: _event,
              errorText: _eventError,
              textInputAction: TextInputAction.next,
            ),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: AppSpacing.md,
              children: [
                Expanded(
                  child: AppTextField(
                    label: 'Data',
                    controller: _date,
                    hint: 'dd/mm/aaaa',
                    errorText: _dateError,
                    keyboardType: TextInputType.datetime,
                    inputFormatters: [
                      FilteringTextInputFormatter.allow(RegExp(r'[0-9/]')),
                    ],
                  ),
                ),
                Expanded(
                  child: AppTextField(
                    label: 'Porta / setor',
                    controller: _gate,
                    optional: manual,
                  ),
                ),
              ],
            ),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: AppSpacing.md,
              children: [
                Expanded(
                  child: AppTextField(
                    label: 'Abertura de portas',
                    controller: _doors,
                    hint: 'hh:mm',
                    prefixIcon: Icons.schedule,
                    keyboardType: TextInputType.datetime,
                    inputFormatters: [timeFormatter],
                  ),
                ),
                Expanded(
                  child: AppTextField(
                    label: 'Início',
                    controller: _start,
                    hint: 'hh:mm',
                    prefixIcon: Icons.schedule,
                    errorText: _startError,
                    keyboardType: TextInputType.datetime,
                    inputFormatters: [timeFormatter],
                  ),
                ),
              ],
            ),
            AppTextField(
              label: 'Local',
              controller: _place,
              prefixIcon: Icons.place_outlined,
            ),
            AppTextField(label: 'Lugar', controller: _seat, optional: manual),
          ],
        ),
        AppButton(label: 'Confirmar e continuar', onPressed: _continue),
      ],
    );
  }
}
