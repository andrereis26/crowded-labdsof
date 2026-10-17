import 'package:flutter/material.dart';

import '../../ui/ui.dart';

/// P10 · Pedido de localização: asked once, when the participant reports or
/// confirms something at the venue. Returns true when allowed.
Future<bool> showLocationRequest(BuildContext context, String eventName) async {
  final allowed = await showAppBottomSheet<bool>(
    context: context,
    builder: (context) => Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.lg,
      children: [
        Center(
          child: Container(
            padding: const EdgeInsets.all(AppSpacing.xl),
            decoration: const BoxDecoration(
              color: AppColors.primarySoft,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.my_location,
              size: 32,
              color: AppColors.primary,
            ),
          ),
        ),
        Text(
          'Confirmar que estás no recinto',
          textAlign: TextAlign.center,
          style: AppTextStyles.title,
        ),
        Text(
          'Pedimos a tua localização só para confirmar que estás no '
          '$eventName. Assim, os reportes são mais fiáveis.',
          textAlign: TextAlign.center,
          style: AppTextStyles.bodySecondary.copyWith(fontSize: 15),
        ),
        AppButton(
          label: 'Permitir localização',
          onPressed: () => Navigator.of(context).pop(true),
        ),
        AppButton.link(
          label: 'Agora não',
          onPressed: () => Navigator.of(context).pop(false),
        ),
      ],
    ),
  );
  return allowed ?? false;
}
