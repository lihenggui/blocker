# Building and testing Blocker

See [the agent guide](../AGENTS.md) for everyday commands and test rules.

## Toolchain and variants

- Use JDK 21 and Android SDK 37. The app targets API 36 and supports API 24 and up.
- The [Gradle wrapper](../gradle/wrapper/gradle-wrapper.properties) pins Gradle 9.9.0.
  The [version catalog](../gradle/libs.versions.toml) pins Kotlin 2.4.21 and ktlint
  1.8.0. Spotless uses the templates in [`spotless/`](../spotless).
- `foss` omits Firebase and Google Mobile Services and is distributed through
  F-Droid and GitHub Releases. `market` includes Firebase Analytics and Crashlytics
  and is the Google Play flavor.
- Use `implementation` for shared dependencies and `fossImplementation` or
  `marketImplementation` for flavor-specific ones. App build types include
  `debug`, `release`, `benchmarkRelease`, and `nonMinifiedRelease`. The
  Baseline Profile plugin creates the latter two build types.
- [`app-compose/build.gradle.kts`](../app-compose/build.gradle.kts) derives
  `versionCode` from `git rev-list --count HEAD` and uses `2.0.<commit count>`
  for `versionName`. Use full Git history when reproducing release versioning.

## Build logic

Convention plugins are registered in
[`build-logic/convention/build.gradle.kts`](../build-logic/convention/build.gradle.kts)
and applied through `libs.plugins.blocker.*` from the version catalog.

| Convention | Purpose |
| --- | --- |
| `blocker.android.application`, `blocker.android.library` | Android app and library defaults |
| `blocker.android.feature.api`, `blocker.android.feature.impl` | Feature API and implementation dependencies, including Hilt for implementations |
| `blocker.android.application.compose`, `blocker.android.library.compose` | Compose configuration |
| `blocker.android.hilt`, `blocker.android.room` | Hilt and Room setup |
| `blocker.android.application.flavors`, `blocker.android.application.firebase` | Product flavors and market-only Firebase integration |
| `blocker.android.lint` | Lint configuration and custom checks |

## Additional Gradle tasks

Run these from the repository root.

| Task | Command |
| --- | --- |
| Build all app variants | `./gradlew :app-compose:assemble` |
| Build a Market release APK | `./gradlew :app-compose:assembleMarketRelease` |
| Build a Play Store bundle | `./gradlew :app-compose:bundleMarketRelease` |
| Run all unit test variants | `./gradlew test` |
| Run all unit test variants for the data module | `./gradlew :core:data:test` |
| Run project checks | `./gradlew check` |
| Clear local Gradle build outputs | `./gradlew clean` |
| Compare screenshots against baselines on Linux | `./gradlew compareRoborazziFossDebug` |
| Report FOSS debug coverage | `./gradlew createFossDebugCombinedCoverageReport` |
| Update Mermaid graphs in module READMEs | `./gradlew graphUpdate` |

Roborazzi runs on Robolectric. Module screenshot tests use `src/testFoss` or
`src/test`. [`gradle.properties`](../gradle.properties) enables verification by
default with `roborazzi.test.verify=true`. Use Linux for baseline recording.

JaCoCo reports combine existing JVM and instrumented coverage per Android module
and variant. Generate the execution data before running the report task.
Dependency Guard checks the app's Market release runtime classpath against
[`marketReleaseRuntimeClasspath.txt`](../app-compose/dependencies/marketReleaseRuntimeClasspath.txt).
Graph generation is a separate check.

Release builds use R8 by default. Validate affected release variants when
changing [app rules](../app-compose/proguard-rules.pro) or
[component-controller consumer rules](../core/component-controller/consumer-proguard-rules.pro).

## Device tasks and baseline profiles

Run device tasks only when the work calls for a connected device or emulator.

| Task | Command |
| --- | --- |
| Install the FOSS debug APK | `./gradlew :app-compose:installFossDebug` |
| Run FOSS debug instrumentation | `./gradlew connectedFossDebugAndroidTest` |
| Run one module's instrumentation | `./gradlew :core:component-controller:connectedFossDebugAndroidTest` |
| Generate a Market release baseline profile using a managed device | `./gradlew :app-compose:generateMarketReleaseBaselineProfile` |

Instrumented tests live in `src/androidTest` in modules with those tests.
The [benchmark module](../benchmarks) uses a managed Pixel 6 at API 33 for
profile generation. The app sets `automaticGenerationDuringBuild = false` and
provides explicit profile generation tasks.

## CI and releases

- [Build](../.github/workflows/Build.yaml) runs on Linux for applicable pushes
  to main and pull requests. It covers formatting, dependency baselines, graphs,
  screenshots, JVM tests, APKs, lint, and release badging. Its device test matrix
  runs Market debug instrumentation on API 26 and 35.
- [Release](../.github/workflows/Release.yml) builds from release tags and publishes
  APKs to GitHub Releases and the Market bundle to the Play Store beta track.
- [NightlyBaselineProfiles](../.github/workflows/NightlyBaselineProfiles.yaml)
  defines scheduled profile work. Consult the checked-in workflows for the
  current CI commands and device configuration.
