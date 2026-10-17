# Crowded participant app

Flutter app for participants, on mobile and the web ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)).

## Running

```bash
flutter pub get
flutter run -d chrome
flutter test
```

For now the app opens the component gallery (`lib/dev/component_gallery.dart`). Replace `home` in `lib/main.dart` when the first screens exist.

## Design system

The shared components follow the "0 · Componentes base" page and the participant screens of the [Crowded — Mockups](https://www.figma.com/design/EdgpgHGNgfRGewcW5kvbES/Crowded-%E2%80%94-Mockups) Figma file. Screens should import `package:crowded/ui/ui.dart` and use these components and tokens instead of raw colours or sizes.

| Need | Use |
|---|---|
| Colours, type, spacing, radii | `AppColors`, `AppTextStyles`, `AppSpacing`, `AppRadii`, `AppTheme.light()` |
| Buttons | `AppButton` (primary), `AppButton.secondary`, `AppButton.link`, `AppInlineLink` |
| Form fields | `AppTextField`, `AppTextField.multiline`, `AppSelectField`, `AppSegmentedControl`, `AppFieldLabel` |
| Choices | `AppCategoryTile` (P11), `AppRadioCard` (P4, P16), `AppNavigationCard` (P2) |
| Layout | `AppPanel`, `AppStepHeading`, `showAppBottomSheet` |
| Navigation | `AppTopBar`, `AppTabBar` (mobile), `AppWebHeader` (desktop), `isDesktopLayout` |
| Event information | `WalletCard`, `AlertBanner`, `CrowdLevelIndicator`, `ConfidenceIndicator`, `EstimateNotice`, `SourceLabel` / `formatSource` |
| Tags | `AppTag` |
| States | `AppEmptyState`, `AppLoadingState`, `AppErrorState`, `AppStatusStrip.offline` / `.degraded` |

Rules carried over from the mockups:

- Every piece of event information shows its source and update time (`SourceLabel`); after 20 minutes it shows "Desatualizado".
- Crowd level, confidence and status always have text, never colour alone.
- Low-confidence participant estimates are not shown, and any estimate is shown with `EstimateNotice`.
- UI copy is in European Portuguese.

Inter is bundled in `assets/fonts/inter` (SIL Open Font License) so the app works offline. Icons are Material icons close to the ones in the mockups.
