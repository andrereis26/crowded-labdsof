# Crowded participant app

Flutter app for participants, on mobile and the web ([ADR 0006](../docs/deliverables/adr/0006-technology-stack.md)).

## Running

```bash
flutter pub get
flutter run -d chrome
flutter test
```

For now the app opens the component gallery (`lib/dev/component_gallery.dart`). Replace `home` in `lib/main.dart` when the first screens exist.

## Container image

The image serves the web build with NGINX as a non-root user on port 8080, with a health check at `/healthz`:

```bash
flutter build web --release
docker build -t crowded/participant-app .
docker run -p 8080:8080 crowded/participant-app
```

## CI

[`.github/workflows/participant-app.yml`](../.github/workflows/participant-app.yml) runs on every pull request that touches this folder and on every push to `main`:

| Job | What it checks |
|---|---|
| Analyze and test | `dart format`, `flutter analyze --fatal-infos`, `flutter test --coverage`; fails below 80% line coverage |
| Build and scan image | Web build, Docker image, Trivy scan (fails on fixable HIGH/CRITICAL), and a request to the running container |

[`.github/workflows/security.yml`](../.github/workflows/security.yml) runs Gitleaks (committed secrets) and Trivy (dependency vulnerabilities and Dockerfile misconfigurations) on every pull request, on `main` and weekly. Dependabot opens weekly update PRs for pub packages, the base image and the GitHub Actions. The Flutter version is pinned in the workflow (`FLUTTER_VERSION`); update it there when upgrading.

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
