import 'package:flutter/material.dart';

import '../ui/ui.dart';

/// Catalogue of the shared components with sample data, mirroring the
/// "0 · Componentes base" page in Figma. Used while the screens are built.
class ComponentGallery extends StatefulWidget {
  const ComponentGallery({super.key});

  @override
  State<ComponentGallery> createState() => _ComponentGalleryState();
}

class _ComponentGalleryState extends State<ComponentGallery> {
  final _now = DateTime.now();
  int _tab = 1;
  String _category = 'queue';
  String? _wait = '20-40';
  String? _place = 'gate-c';
  String _match = 'margens';
  int _pollOption = 0;

  DateTime _ago(int minutes) => _now.subtract(Duration(minutes: minutes));

  @override
  Widget build(BuildContext context) {
    final desktop = isDesktopLayout(context);
    return Scaffold(
      appBar: desktop
          ? AppWebHeader(
              currentIndex: _tab,
              onNavigate: (i) => setState(() => _tab = i),
              userInitials: 'IM',
              unreadNotifications: 2,
              onReport: () {},
              onNotifications: () {},
            )
          : AppTopBar(
              title: 'Componentes',
              leadingLabel: 'Fechar',
              onLeading: () {},
            ),
      bottomNavigationBar: desktop
          ? null
          : AppTabBar(
              currentIndex: _tab,
              onTap: (i) => setState(() => _tab = i),
            ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.screen),
            children: [
              _section('Cartão de bilhete', [
                WalletCard(
                  eventName: 'Festival Margens',
                  dateLabel: 'HOJE',
                  venue: 'Parque da Cidade, Porto',
                  time: '22:00',
                  gate: 'A',
                  seat: 'Geral',
                  statusLabel: 'Confirmado',
                  source: formatSource('organização', _ago(5)),
                  onTap: () {},
                ),
                WalletCard(
                  eventName: 'A Tempestade',
                  dateLabel: 'SÁB 8 NOV',
                  venue: 'Teatro Nacional São João',
                  time: '21:30',
                  seat: 'F-12',
                  statusLabel: 'Adiado · 8 Nov',
                  statusTone: WalletCardStatusTone.changed,
                  source: formatSource('organização', _ago(120)),
                ),
              ]),
              _section('Banner de alerta', [
                AlertBanner(
                  kind: AlertBannerKind.organizationAlert,
                  message: 'Porta B fechada. Use a Porta C.',
                  origin: formatSource(
                    'Organização Margens',
                    _ago(3),
                    withPrefix: false,
                  ),
                ),
                AlertBanner(
                  kind: AlertBannerKind.officialChange,
                  message: 'A Tempestade foi adiada para SÁB 8 NOV · 21:30',
                  origin: formatSource('organização', _ago(120)),
                ),
                AlertBanner(
                  kind: AlertBannerKind.urgent,
                  message: 'Evacuação da zona do palco. Siga as indicações da equipa.',
                  origin: formatSource(
                    'Organização Margens',
                    _now,
                    withPrefix: false,
                  ),
                ),
              ]),
              _section('Lotação, confiança e estimativas', [
                for (final level in CrowdLevel.values)
                  CrowdLevelIndicator(level: level),
                const Wrap(
                  spacing: AppSpacing.sm,
                  runSpacing: AppSpacing.sm,
                  children: [
                    ConfidenceIndicator(level: ConfidenceLevel.high),
                    ConfidenceIndicator(level: ConfidenceLevel.medium),
                    ConfidenceIndicator(level: ConfidenceLevel.low),
                  ],
                ),
                const EstimateNotice(),
                const ConfirmationBar(confirmed: 9, denied: 1),
              ]),
              _section('Etiqueta de origem e etiquetas', [
                SourceLabel(source: 'organização', updatedAt: _ago(2)),
                SourceLabel(source: 'organização', updatedAt: _ago(42)),
                const Wrap(
                  spacing: AppSpacing.sm,
                  runSpacing: AppSpacing.sm,
                  children: [
                    AppTag(
                      label: 'No recinto',
                      tone: AppTagTone.success,
                      icon: Icons.check,
                    ),
                    AppTag(
                      label: 'Correspondência alta',
                      tone: AppTagTone.success,
                    ),
                    AppTag(label: 'Correspondência baixa'),
                    AppTag(
                      label: 'PEDIDO DE CONFIRMAÇÃO',
                      tone: AppTagTone.info,
                      size: AppTagSize.medium,
                    ),
                    AppTag(
                      label: 'Código de entrada encontrado',
                      tone: AppTagTone.success,
                      size: AppTagSize.large,
                      icon: Icons.check,
                    ),
                    AppTag(
                      label: 'Aviso laranja',
                      tone: AppTagTone.orange,
                      icon: Icons.warning_amber_rounded,
                    ),
                  ],
                ),
                const Wrap(
                  spacing: AppSpacing.sm,
                  children: [
                    AppIconBadge(
                      icon: Icons.schedule,
                      tone: AppTagTone.warning,
                    ),
                    AppIconBadge(
                      icon: Icons.accessibility_new,
                      tone: AppTagTone.info,
                    ),
                    AppIconBadge(icon: Icons.directions_walk),
                    AppIconBadge(icon: Icons.location_on_outlined, dark: true),
                  ],
                ),
                AppBreadcrumbs(
                  items: [
                    AppBreadcrumb('Os meus eventos', onTap: () {}),
                    AppBreadcrumb('Festival Margens', onTap: () {}),
                    const AppBreadcrumb('Como chegar'),
                  ],
                ),
              ]),
              _section('Formulário', [
                const AppStepHeading(
                  step: 2,
                  totalSteps: 3,
                  title: 'Confirma os dados do bilhete',
                  subtitle: 'Preenchemos a partir do teu PDF.',
                ),
                AppPanel(
                  children: [
                    const AppTextField(
                      label: 'Evento',
                      initialValue: 'Festival Margens 2026',
                    ),
                    const Row(
                      spacing: AppSpacing.md,
                      children: [
                        Expanded(
                          child: AppTextField(
                            label: 'Abertura de portas',
                            initialValue: '20:00',
                            prefixIcon: Icons.schedule,
                          ),
                        ),
                        Expanded(
                          child: AppTextField(
                            label: 'Início',
                            initialValue: '22:00',
                            prefixIcon: Icons.schedule,
                          ),
                        ),
                      ],
                    ),
                    const AppTextField(
                      label: 'Local',
                      initialValue: 'Parque da Cidade, Porto',
                      prefixIcon: Icons.place_outlined,
                    ),
                    AppSelectField<String>(
                      label: 'Onde?',
                      value: _place,
                      options: const [
                        AppSelectOption(value: 'gate-a', label: 'Porta A'),
                        AppSelectOption(value: 'gate-b', label: 'Porta B'),
                        AppSelectOption(value: 'gate-c', label: 'Porta C'),
                      ],
                      onChanged: (v) => setState(() => _place = v),
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      spacing: AppSpacing.xs,
                      children: [
                        const AppFieldLabel('Tempo de espera'),
                        AppSegmentedControl<String>(
                          selected: _wait,
                          onChanged: (v) => setState(() => _wait = v),
                          segments: const [
                            AppSegment(value: '<10', label: '< 10 min'),
                            AppSegment(value: '10-20', label: '10–20'),
                            AppSegment(value: '20-40', label: '20–40'),
                            AppSegment(value: '>40', label: '> 40'),
                          ],
                        ),
                      ],
                    ),
                    const AppTextField.multiline(
                      label: 'Descrição',
                      optional: true,
                      hint: 'O que estás a ver?',
                    ),
                    const AppTextField(
                      label: 'Email',
                      hint: 'nome@exemplo.pt',
                      errorText: 'Introduz um email válido.',
                    ),
                  ],
                ),
              ]),
              _section('Botões', [
                AppButton(label: 'Confirmar e continuar', onPressed: () {}),
                AppButton(
                  label: 'Enviar reporte',
                  icon: Icons.send_outlined,
                  onPressed: () {},
                ),
                AppButton.secondary(
                  label: 'Introduzir dados manualmente',
                  icon: Icons.edit_outlined,
                  onPressed: () {},
                ),
                Row(
                  spacing: AppSpacing.md,
                  children: [
                    Expanded(
                      child: AppButton.secondary(
                        label: 'Não',
                        icon: Icons.thumb_down_outlined,
                        size: AppButtonSize.large,
                        onPressed: () {},
                      ),
                    ),
                    Expanded(
                      child: AppButton(
                        label: 'Sim',
                        icon: Icons.thumb_up_outlined,
                        size: AppButtonSize.large,
                        onPressed: () {},
                      ),
                    ),
                  ],
                ),
                AppButton.link(
                  label: 'Não consigo ver daqui',
                  onPressed: () {},
                ),
                const AppButton(label: 'Desativado', onPressed: null),
                Center(
                  child: AppInlineLink(
                    label: 'Como tratamos os teus dados',
                    icon: Icons.shield_outlined,
                    onPressed: () {},
                  ),
                ),
                AppButton.secondary(
                  label: 'Abrir no mapa',
                  trailingIcon: Icons.open_in_new,
                  onPressed: () {},
                ),
                AppButton.secondary(
                  label: 'Abrir folha (bottom sheet)',
                  onPressed: () => _showConfirmationSheet(context),
                ),
                AppButton.secondary(
                  label: 'Abrir diálogo (desktop)',
                  onPressed: () => showAppDialog<void>(
                    context: context,
                    builder: (context) => AppDialog(
                      title: 'Reportar algo',
                      onClose: () => Navigator.pop(context),
                      child: const AppResultHeader(
                        icon: Icons.check,
                        title: 'Reporte enviado',
                        message: 'A organização já recebeu o teu reporte.',
                      ),
                    ),
                  ),
                ),
              ]),
              _section('Escolhas', [
                // Two columns; tiles keep their natural height.
                LayoutBuilder(
                  builder: (context, constraints) => Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      for (final (id, icon, label) in const [
                        ('crowd', Icons.people_outline, 'Lotação'),
                        ('queue', Icons.schedule, 'Fila'),
                        ('access', Icons.accessibility_new, 'Acessibilidade'),
                        ('venue', Icons.build_outlined, 'Problema no recinto'),
                      ])
                        SizedBox(
                          width: (constraints.maxWidth - 10) / 2,
                          child: AppCategoryTile(
                            icon: icon,
                            label: label,
                            selected: _category == id,
                            onTap: () => setState(() => _category = id),
                          ),
                        ),
                    ],
                  ),
                ),
                AppRadioCard(
                  title: 'Festival Margens 2026',
                  subtitle: 'SÁB 17 OUT · 22:00 · Parque da Cidade, Porto',
                  selected: _match == 'margens',
                  onTap: () => setState(() => _match = 'margens'),
                  leading: _thumbnail(),
                  footer: const AppTag(
                    label: 'Correspondência alta',
                    tone: AppTagTone.success,
                  ),
                ),
                AppRadioCard(
                  title: 'Nenhum destes',
                  subtitle:
                      'Não recebes alterações oficiais deste evento. '
                      'Podes associá-lo mais tarde.',
                  selected: _match == 'none',
                  onTap: () => setState(() => _match = 'none'),
                ),
                for (final (i, option) in const [
                  'Sim, vou',
                  'Talvez',
                  'Não vou',
                ].indexed)
                  AppOptionTile(
                    label: option,
                    selected: _pollOption == i,
                    onTap: () => setState(() => _pollOption = i),
                  ),
                AppNavigationCard(
                  icon: Icons.description_outlined,
                  title: 'PDF do bilhete',
                  subtitle:
                      'O ficheiro que recebeste quando compraste o bilhete.',
                  onTap: () {},
                ),
                AppNavigationCard(
                  icon: Icons.mail_outline,
                  title: 'Email de confirmação (.eml)',
                  subtitle:
                      'Guarda o email como ficheiro .eml e carrega-o aqui.',
                  onTap: () {},
                  footer: AppInlineLink(
                    label: 'Como guardar um email em .eml?',
                    icon: Icons.info_outline,
                    onPressed: () {},
                  ),
                ),
              ]),
              _section('Estados', [
                const AppResultHeader(
                  icon: Icons.gps_fixed,
                  tone: AppTagTone.warning,
                  title: 'Não estás perto do recinto',
                  message: 'Tens de estar a menos de 500 m do recinto.',
                ),
                AppEmptyState(
                  icon: Icons.credit_card,
                  message: 'Ainda sem bilhetes',
                  actionLabel: 'Importar',
                  onAction: () {},
                ),
                const AppLoadingState(),
                AppErrorState(onRetry: () {}),
                const AppStatusStrip.offline(
                  message: 'Sem rede · a mostrar dados de há 12 min',
                ),
                const AppStatusStrip.degraded(
                  message: 'Bilheteira indisponível · última sincronização há 18 min',
                ),
              ]),
            ],
          ),
        ),
      ),
    );
  }

  Widget _thumbnail() => Container(
    width: 52,
    height: 52,
    decoration: const BoxDecoration(
      borderRadius: BorderRadius.all(AppRadii.field),
      gradient: AppColors.walletFallbackPhoto,
    ),
  );

  Widget _section(String title, List<Widget> children) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: AppSpacing.md,
        children: [
          Text(title, style: AppTextStyles.headline),
          ...children,
        ],
      ),
    );
  }

  void _showConfirmationSheet(BuildContext context) {
    showAppBottomSheet<void>(
      context: context,
      builder: (context) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        spacing: AppSpacing.lg,
        children: [
          const Center(
            child: AppTag(
              label: 'PEDIDO DE CONFIRMAÇÃO',
              tone: AppTagTone.info,
              size: AppTagSize.medium,
            ),
          ),
          Text(
            'Confirmas isto?',
            textAlign: TextAlign.center,
            style: AppTextStyles.sheetTitle,
          ),
          Row(
            spacing: AppSpacing.md,
            children: [
              Expanded(
                child: AppButton.secondary(
                  label: 'Não',
                  icon: Icons.thumb_down_outlined,
                  size: AppButtonSize.large,
                  onPressed: () => Navigator.pop(context),
                ),
              ),
              Expanded(
                child: AppButton(
                  label: 'Sim',
                  icon: Icons.thumb_up_outlined,
                  size: AppButtonSize.large,
                  onPressed: () => Navigator.pop(context),
                ),
              ),
            ],
          ),
          AppButton.link(
            label: 'Não consigo ver daqui',
            onPressed: () => Navigator.pop(context),
          ),
        ],
      ),
    );
  }
}
