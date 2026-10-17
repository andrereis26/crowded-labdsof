import 'package:flutter/painting.dart';

/// Colour tokens from the "Crowded — Mockups" Figma file.
///
/// Text colours meet WCAG 2.1 AA on [background] and [surface]. Never use a
/// lighter grey than [inkSecondary] for text.
abstract final class AppColors {
  // Neutrals
  static const ink = Color(0xFF121214);
  static const inkSecondary = Color(0xFF6B6B73);
  static const background = Color(0xFFF2F2F7);
  static const surface = Color(0xFFFFFFFF);
  static const border = Color(0xFFE3E3E8);
  static const borderStrong = Color(0xFFC2C2C9);
  static const segmentTrack = Color(0xFFE5E5EB);
  static const skeleton = Color(0xFFDBDBE0);
  static const inactive = Color(0xFFCCCCD1);
  static const scrim = Color(0x73000000);

  // Brand
  static const primary = Color(0xFF0066D9);
  static const primarySoft = Color(0xFFE8F2FF);
  static const avatar = Color(0xFFDBE5FA);

  // Status
  static const successSoft = Color(0xFFDEF5E3);
  static const success = Color(0xFF146B33);
  static const warningSoft = Color(0xFFFFF2D6);
  static const warningBorder = Color(0xFFEDC266);
  static const warning = Color(0xFF8C5400);
  static const dangerSoft = Color(0xFFFFEBEB);
  static const dangerBorder = Color(0xFFED9E9E);
  static const danger = Color(0xFFBF1A1A);
  static const counter = Color(0xFFD93026);

  // Crowd level meter
  static const crowdLow = Color(0xFF29994D);
  static const crowdModerate = Color(0xFFD98C0D);
  static const crowdHigh = Color(0xFFE05C0F);
  static const crowdFull = Color(0xFFC71F1F);

  // Wallet card
  static const walletStatusSoft = Color(0xFFDBF7E3);
  static const walletStatus = Color(0xFF14542B);
  static const walletFallbackPhoto = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF596B9E), Color(0xFF1A1F38)],
    stops: [0.14, 0.86],
  );
  static const walletShade = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [
      Color(0xA6000000),
      Color(0x26000000),
      Color(0x4D000000),
      Color(0xB8000000),
      Color(0xE6000000),
    ],
    stops: [0, 0.3, 0.5, 0.72, 1],
  );

  // Navigation
  static const tabBar = Color(0xF0FAFAFC);
  static const tabBarBorder = Color(0xFFDBDBE3);
}
