import 'package:flutter/painting.dart';

/// Spacing and corner radii from the mockups.
abstract final class AppSpacing {
  static const xxs = 4.0;
  static const xs = 6.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 14.0;
  static const xl = 16.0;
  static const xxl = 20.0;

  /// Horizontal padding of mobile screens (358 px content in a 390 px frame).
  static const screen = 16.0;

  /// Content width of a mobile screen; desktop screens centre a column.
  static const mobileContentWidth = 358.0;
}

abstract final class AppRadii {
  static const sm = Radius.circular(8);
  static const field = Radius.circular(10);
  static const button = Radius.circular(12);
  static const panel = Radius.circular(14);
  static const card = Radius.circular(16);
  static const sheet = Radius.circular(24);
  static const pill = Radius.circular(999);
}
