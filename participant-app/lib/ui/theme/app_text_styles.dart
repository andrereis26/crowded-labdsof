import 'package:flutter/painting.dart';

import 'app_colors.dart';

/// Inter type scale used across the mockups.
abstract final class AppTextStyles {
  static const fontFamily = 'Inter';

  static const _base = TextStyle(
    fontFamily: fontFamily,
    color: AppColors.ink,
    height: 1.2,
  );

  /// Screen title on root tabs ("Os meus eventos").
  static final largeTitle = _base.copyWith(
    fontSize: 34,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.4,
  );

  /// Bottom sheet title ("Confirmas isto?").
  static final sheetTitle = _base.copyWith(
    fontSize: 26,
    fontWeight: FontWeight.w700,
  );

  /// Step heading ("Confirma os dados do bilhete").
  static final title = _base.copyWith(
    fontSize: 22,
    fontWeight: FontWeight.w700,
  );

  /// Top bar title and section titles ("O que se passa?").
  static final headline = _base.copyWith(
    fontSize: 17,
    fontWeight: FontWeight.w600,
  );

  /// Top bar actions ("Voltar", "Cancelar").
  static final navAction = _base.copyWith(
    fontSize: 17,
    color: AppColors.primary,
  );

  static final buttonLarge = _base.copyWith(
    fontSize: 18,
    fontWeight: FontWeight.w600,
  );
  static final button = _base.copyWith(
    fontSize: 16,
    fontWeight: FontWeight.w600,
  );

  static final bodyLarge = _base.copyWith(
    fontSize: 16,
    fontWeight: FontWeight.w600,
  );
  static final body = _base.copyWith(fontSize: 15);
  static final bodyStrong = _base.copyWith(
    fontSize: 15,
    fontWeight: FontWeight.w600,
  );
  static final bodySecondary = _base.copyWith(
    fontSize: 14,
    color: AppColors.inkSecondary,
    height: 1.4,
  );
  static final subhead = _base.copyWith(
    fontSize: 14,
    fontWeight: FontWeight.w600,
  );

  /// Form field label ("Evento", "Data").
  static final label = _base.copyWith(
    fontSize: 13,
    fontWeight: FontWeight.w500,
  );
  static final footnote = _base.copyWith(
    fontSize: 13,
    color: AppColors.inkSecondary,
    height: 1.35,
  );
  static final caption = _base.copyWith(
    fontSize: 12,
    color: AppColors.inkSecondary,
    height: 1.4,
  );

  /// Small uppercase labels ("PASSO 2 DE 3", "ALERTA DA ORGANIZAÇÃO").
  static final overline = _base.copyWith(
    fontSize: 11,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.66,
  );

  /// Pills and badges.
  static final tag = _base.copyWith(fontSize: 11, fontWeight: FontWeight.w600);

  /// Source and last-update label ("Fonte: organização · há 2 min").
  static final source = _base.copyWith(
    fontSize: 11,
    color: AppColors.inkSecondary,
  );

  static final tabLabel = _base.copyWith(
    fontSize: 10,
    fontWeight: FontWeight.w500,
  );
}
