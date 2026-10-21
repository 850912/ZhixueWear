# Changelog

## 0.2.0-m3e
- Upgraded Wear OS UI and motion to Google's **Material 3 Expressive (M3E)** (`androidx.wear.compose.material3` 1.6.2):
  - Added `BodyTemperatureWearTheme` with `dynamicColorScheme(LocalContext.current)` watch-face dynamic color extraction, a 29-token biothermal `ColorScheme` fallback, and `MotionScheme.expressive()` spring physics.
  - Added `ScreenScaffold` bottom-hugging `EdgeButton` (`EdgeButtonSize.Medium`) and perimeter bezel `CircularProgressIndicator` with spring-interpolated sample progress.
  - Upgraded `TransformingLazyColumn` with `rememberTransformationSpec()`, `Modifier.transformedHeight(this, transformationSpec)`, `SurfaceTransformation(transformationSpec)`, and rotary crown snap fling (`RotaryScrollableDefaults.snapBehavior`).
  - Added `ExpressiveThermalHeroCard` with corner-shape morphing (`animateDpAsState`), breathing radial thermal aura (`rememberInfiniteTransition`), spring-animated numeral readout (`MaterialTheme.typography.numeralExtraLarge`), and morphing status pill (`AnimatedContent`).
  - Replaced static mode and trend range rows with M3E `ButtonGroup` using `Modifier.animateWidth(interactionSource)` and spring-driven corner/weight morphing.
  - Added `TitleCard` personal trend summary with a custom Bezier sparkline (`ExpressiveTrendSparkline`) and per-reading delta badges.

## 0.1.0
- Replaced the retired score viewer with a Wear OS wrist skin-temperature research prototype.
- Added sample quality checks and an opt-in local paired-observation log.
- No core body-temperature estimate is produced.
