import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../data/app_store.dart';
import '../../data/models.dart';
import '../../ui/ui.dart';
import '../../utils/pt_format.dart';
import '../shared/event_context_row.dart';
import '../shared/shell.dart';

/// Places offered when the event has no list of its own.
const _fallbackPlaces = [
  VenuePlace('Entrada', 'na'),
  VenuePlace('Bar', 'no'),
  VenuePlace('WC', 'nos'),
  VenuePlace('Palco', 'no'),
];

/// P11 · Reportar — evento, categoria e detalhes (mobile page). On desktop
/// the same form opens in a dialog (see `startReport`). The event in
/// progress is chosen by default; other events of the day are in the
/// "Evento" selector.
class ReportScreen extends StatelessWidget {
  const ReportScreen({super.key, required this.eventId, this.category});

  final String eventId;
  final ReportCategory? category;

  @override
  Widget build(BuildContext context) {
    final store = AppStore.of(context);
    final event = store.eventById(eventId);
    if (event == null) {
      return AppPage(
        children: [
          AppErrorState(
            message: 'Não encontrámos este evento',
            retryLabel: 'Voltar aos meus eventos',
            onRetry: () => context.go('/eventos'),
          ),
        ],
      );
    }
    return AppPage(
      title: 'Reportar algo',
      leadingLabel: 'Cancelar',
      showBackChevron: false,
      children: [
        ReportForm(
          event: event,
          nearVenue: store.isNearVenue(event),
          initialCategory: category,
          onSent: (report) => context.pushReplacement(
            '/evento/${report.eventId}/reporte?estado=enviado',
          ),
          onCancel: () => AppPage.goBack(context),
        ),
      ],
    );
  }
}

/// Category, place, details and description of a report. Sends it with
/// [AppStore.sendReport] and hands the result to [onSent].
class ReportForm extends StatefulWidget {
  const ReportForm({
    super.key,
    required this.event,
    required this.nearVenue,
    required this.onSent,
    required this.onCancel,
    this.initialCategory,
    this.desktop = false,
  });

  final Event event;

  /// Away from the venue, only lost items can be reported.
  final bool nearVenue;
  final ReportCategory? initialCategory;
  final ValueChanged<ParticipantReport> onSent;
  final VoidCallback onCancel;

  /// Five categories in a row and fields in two columns (dialog).
  final bool desktop;

  @override
  State<ReportForm> createState() => _ReportFormState();
}

class _ReportFormState extends State<ReportForm> {
  late ReportCategory? _category =
      widget.nearVenue || widget.initialCategory == ReportCategory.lostAndFound
      ? widget.initialCategory
      : ReportCategory.lostAndFound;
  late Event _event = widget.event;
  VenuePlace? _place;
  WaitTime? _waitTime;
  CrowdLevel? _crowdLevel;
  final _description = TextEditingController();

  List<ReportCategory> get _categories => widget.nearVenue
      ? ReportCategory.values
      : const [ReportCategory.lostAndFound];

  List<VenuePlace> get _places =>
      _event.places.isEmpty ? _fallbackPlaces : _event.places;

  bool get _isValid {
    final category = _category;
    if (category == null || _place == null) return false;
    return switch (category) {
      ReportCategory.queue => _waitTime != null,
      ReportCategory.crowd => _crowdLevel != null,
      ReportCategory.lostAndFound => _description.text.trim().isNotEmpty,
      _ => true,
    };
  }

  @override
  void dispose() {
    _description.dispose();
    super.dispose();
  }

  void _send() {
    final report = AppStore.read(context).sendReport(
      event: _event,
      category: _category!,
      place: _place!,
      waitTime: _category == ReportCategory.queue ? _waitTime : null,
      crowdLevel: _category == ReportCategory.crowd ? _crowdLevel : null,
      description: _description.text,
    );
    widget.onSent(report);
  }

  Future<void> _chooseEvent(List<Event> events) async {
    final now = AppStore.read(context).now;
    final chosen = await showAppBottomSheet<Event>(
      context: context,
      builder: (context) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: AppSpacing.md,
        children: [
          Text('Eventos de hoje', style: AppTextStyles.sheetTitle),
          AppListGroup(
            children: [
              for (final event in events)
                AppListTile(
                  leading: EventThumbnail(
                    gradient: event.art.gradient,
                    size: 36,
                    radius: 8,
                  ),
                  title: event.name,
                  subtitle: _whenLabel(event, now),
                  trailing: event.id == _event.id
                      ? const Icon(Icons.check, color: AppColors.primary)
                      : null,
                  showChevron: false,
                  onTap: () => Navigator.pop(context, event),
                ),
            ],
          ),
        ],
      ),
    );
    if (chosen == null || chosen.id == _event.id || !mounted) return;
    setState(() {
      _event = chosen;
      _place = null;
    });
  }

  /// "A decorrer · Parque da Cidade" or "Hoje às 22:00 · Jardim do Palácio".
  static String _whenLabel(Event event, DateTime now) {
    final started = !event.start.isAfter(now);
    final when = started ? 'A decorrer' : 'Hoje às ${formatTime(event.start)}';
    return '$when · ${event.venue}';
  }

  @override
  Widget build(BuildContext context) {
    final desktop = widget.desktop;
    final store = AppStore.of(context);
    final today = store.eventsToday;
    final canChoose = today.length > 1;
    final tag = widget.nearVenue
        ? AppTag(
            label: desktop ? 'Estás no recinto' : 'No recinto',
            tone: AppTagTone.success,
            icon: Icons.check,
          )
        : const AppTag(
            label: 'Longe do recinto',
            icon: Icons.location_off_outlined,
          );
    final header = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.xs,
      children: [
        const AppFieldLabel('Evento'),
        Semantics(
          button: canChoose,
          label: canChoose ? 'Escolher outro evento de hoje' : null,
          child: InkWell(
            onTap: canChoose ? () => _chooseEvent(today) : null,
            borderRadius: const BorderRadius.all(AppRadii.field),
            child: EventContextRow(
              event: _event,
              subtitle: '${_event.venue} · agora',
              background: desktop ? AppColors.background : AppColors.surface,
              trailing: canChoose
                  ? Row(
                      mainAxisSize: MainAxisSize.min,
                      spacing: AppSpacing.xs,
                      children: [
                        tag,
                        const Icon(
                          Icons.expand_more,
                          color: AppColors.inkSecondary,
                        ),
                      ],
                    )
                  : tag,
            ),
          ),
        ),
      ],
    );
    final question = Text(
      'O que se passa?',
      style: desktop ? AppTextStyles.bodyLarge : AppTextStyles.headline,
    );
    final sendButton = AppButton(
      label: 'Enviar reporte',
      icon: Icons.send_outlined,
      expand: !desktop,
      onPressed: _isValid ? _send : null,
    );

    if (desktop) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: 18,
        children: [
          header,
          question,
          _categoryRow(),
          if (_category != null)
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              spacing: AppSpacing.xl,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    spacing: AppSpacing.lg,
                    children: [_placeField(), ?_detailField()],
                  ),
                ),
                Expanded(child: _descriptionField()),
              ],
            ),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            spacing: AppSpacing.md,
            children: [
              AppButton.secondary(
                label: 'Cancelar',
                expand: false,
                onPressed: widget.onCancel,
              ),
              sendButton,
            ],
          ),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.lg,
      children: [
        header,
        question,
        _categoryGrid(),
        if (_category != null)
          AppPanel(
            title: '${_category!.label} · detalhes',
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.xl,
              vertical: AppSpacing.lg,
            ),
            children: [_placeField(), ?_detailField(), _descriptionField()],
          ),
        sendButton,
      ],
    );
  }

  Widget _tile(ReportCategory category) => AppCategoryTile(
    icon: category.icon,
    label: category.label,
    selected: _category == category,
    onTap: () => setState(() => _category = category),
  );

  /// Two columns; tiles in a row share the height of the taller one.
  Widget _categoryGrid() => Column(
    spacing: 10,
    children: [
      for (var i = 0; i < _categories.length; i += 2)
        IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            spacing: 10,
            children: [
              Expanded(child: _tile(_categories[i])),
              Expanded(
                child: i + 1 < _categories.length
                    ? _tile(_categories[i + 1])
                    : const SizedBox.shrink(),
              ),
            ],
          ),
        ),
    ],
  );

  Widget _categoryRow() => IntrinsicHeight(
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 10,
      children: [
        for (final category in _categories) Expanded(child: _tile(category)),
        // Keeps a single tile at the width of five.
        for (var i = _categories.length; i < 5; i++) const Spacer(),
      ],
    ),
  );

  Widget _placeField() => AppSelectField<VenuePlace>(
    label: 'Onde?',
    value: _place,
    options: [
      for (final place in _places)
        AppSelectOption(value: place, label: place.name),
    ],
    onChanged: (place) => setState(() => _place = place),
  );

  Widget? _detailField() {
    final Widget control;
    final String label;
    switch (_category) {
      case ReportCategory.queue:
        label = 'Tempo de espera';
        control = AppSegmentedControl<WaitTime>(
          selected: _waitTime,
          onChanged: (v) => setState(() => _waitTime = v),
          segments: [
            for (final wait in WaitTime.values)
              AppSegment(value: wait, label: wait.label),
          ],
        );
      case ReportCategory.crowd:
        label = 'Quanta gente?';
        control = AppSegmentedControl<CrowdLevel>(
          selected: _crowdLevel,
          onChanged: (v) => setState(() => _crowdLevel = v),
          segments: [
            for (final level in CrowdLevel.values)
              AppSegment(value: level, label: level.label),
          ],
        );
      default:
        return null;
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.xs,
      children: [AppFieldLabel(label), control],
    );
  }

  Widget _descriptionField() {
    final lost = _category == ReportCategory.lostAndFound;
    return AppTextField.multiline(
      label: lost ? 'O que perdeste?' : 'Descrição',
      optional: !lost,
      minLines: 2,
      controller: _description,
      hint: lost ? 'Ex.: carteira preta perto do bar' : 'O que estás a ver?',
      onChanged: (_) => setState(() {}),
    );
  }
}
