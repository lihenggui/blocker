# Blocker agent guide

## Overview

Blocker is a Kotlin Android app for managing activities, services, broadcast
receivers, and content providers in installed apps. It can use root-backed
PackageManager, Intent Firewall (IFW), combined IFW and PackageManager, or Shizuku/Sui.
The UI uses Jetpack Compose, Material 3, Navigation 3, and Hilt.
The module boundaries follow [Android architecture guidance](https://developer.android.com/topic/architecture/recommendations)
and [Now in Android](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md).
Edit this file to update either agent entry point. `CLAUDE.md` links here.

## Module map

- [`app-compose`](app-compose) assembles features, owns the app shell and
  navigation, and supplies app-level Hilt bindings.
- [`feature/`](feature) contains app list, app detail, general rules, rule detail,
  search, settings, debloater, and per-app and global IFW editors. `api` modules
  define navigation contracts and `impl` modules own screens and ViewModels.
- [`core/domain`](core/domain) contains optional use cases. [`core/data`](core/data)
  owns repository interfaces and implementations. [`core/model`](core/model)
  contains shared models.
- [`core/component-controller`](core/component-controller) implements privileged
  operations. [`core/ifw-api`](core/ifw-api) owns IFW rules and XML.
- [`core/database`](core/database), [`core/datastore`](core/datastore), and
  [`core/datastore-proto`](core/datastore-proto) own Room and Proto DataStore.
  [`core/network`](core/network) provides Retrofit clients.
- [`core/analytics`](core/analytics), [`core/git`](core/git),
  [`core/provider`](core/provider), and [`core/rule`](core/rule) handle analytics,
  Git-backed rule sync, content providers, and rule matching.
- [`core/common`](core/common) holds shared infrastructure and extensions.
  [`core/designsystem`](core/designsystem) holds Material 3 wrappers and styling.
  [`core/ui`](core/ui) and [`core/navigation`](core/navigation) hold shared UI and
  navigation helpers.
- [`sync/work`](sync/work) runs WorkManager jobs. [`build-logic`](build-logic)
  owns Gradle conventions. [`lint`](lint) owns custom lint checks.
  [`benchmarks`](benchmarks) owns Macrobenchmark and baseline profile tests.
- [`core/screenshot-testing`](core/screenshot-testing) supplies screenshot
  helpers. [`ui-test-hilt-manifest`](ui-test-hilt-manifest) supplies the test manifest.

## Everyday commands

Use the Gradle wrapper and prefer `fossDebug` for local development.

| Task | Command |
| --- | --- |
| Build a debug APK | `./gradlew :app-compose:assembleFossDebug` |
| Run FOSS debug unit tests and detector tests | `./gradlew testFossDebugUnitTest :lint:test` |
| Run one module's unit tests | `./gradlew :feature:applist:impl:testFossDebugUnitTest` |
| Verify screenshots on Linux | `./gradlew verifyRoborazziFossDebug` |
| Record intentional screenshot changes on Linux | `./gradlew recordRoborazziFossDebug` |
| Check formatting | `./gradlew spotlessCheck --no-configuration-cache` |
| Apply formatting | `./gradlew spotlessApply` |
| Run lint | `./gradlew :app-compose:lintMarketRelease :lint:lint` |
| Check dependency baselines | `./gradlew dependencyGuard` |
| Update dependency baselines after intentional changes | `./gradlew dependencyGuardBaseline` |

See the [build guide](docs/build.md) for the toolchain, variants, build logic,
device tests, coverage, graphs, release builds, and baseline profiles.

## Code structure

- Keep production files under roughly 800 lines, or 500 for Compose, and
  functions under roughly 80. Split by responsibility and name each piece for its work.
- Keep one implementation and one name per concept. Check `core:common` for
  shared helpers, `core:ui` for reusable UI, and `core:designsystem` for widgets.
- Delete unused code and compatibility shims when replacing internal APIs.
  Migrate callers together and preserve supported external contracts.
- Handle or return each failure, log it with context, or document why it is
  safe to ignore. Let coroutine cancellation propagate.
- Preserve behavior and screenshots in refactors. Include relevant tests and
  screenshot verification when moving behavior or Compose code.

## Architecture rules

- Keep the module graph acyclic. Feature implementations may depend on feature
  `api` modules for navigation. Share implementation code through `core`.
- Route UI through `core:data` repository interfaces, with `core:domain` use cases
  where needed. Bind repositories with Hilt in `DataModule`.
- For persisted app data, observe Room or DataStore through the repository.
  `OfflineFirstGeneralRuleRepository` owns the rule cache and sync updates.
- Use `<Verb><Noun>UseCase` and `operator fun invoke` in `core:domain`.
  `GetControllerUseCase` shows the naming and Hilt constructor injection.
- Collect flows with `collectAsStateWithLifecycle` at the screen boundary.
  Pass state and callbacks to child composables and keep ViewModels at screen scope.
- Use design system equivalents where provided. Keep adaptive navigation in
  `BlockerApp`, which uses `NavDisplay` and a list-detail scene strategy.
- Check compact and expanded layouts when changing navigation. The
  `BlockerNavigationSuiteScaffold` selects the adaptive bar or rail.
- Put user-facing text in the owning module's `src/main/res/values/strings.xml`.
  Follow its existing locale qualifiers, such as `values-zh-rCN`.
- For Room schema changes, bump the version and commit the exports in
  [`core/database/schemas`](core/database/schemas). Test the upgrade and migrate any data that must survive.

### Guidance for new code

- Keep DAOs, data sources, `IIntentFirewall`, and `RootCommandExecutor` behind
  data or domain APIs. Existing exceptions include `SettingsViewModel` using DataStore directly.
- Add a use case only for logic shared by several ViewModels. Keep single-screen
  orchestration in its ViewModel and data access in the repository.
- Expose one read-only `StateFlow<ScreenUiState>` called `uiState`. For upstream flows,
  use `stateIn` with `viewModelScope` and `SharingStarted.WhileSubscribed(5_000)`.
- Start observation through the state flow and launch action work from explicit
  events. Keep coroutine launches and calls that start them out of `init`.
- Keep `Context` and `Application` out of ViewModels. Put platform I/O behind
  injected interfaces and keep UI-only platform actions in the UI layer.
- Map expected failures to typed results or errors in `core:data`. Carry them
  into UI state and use existing types such as `Result` and `UiMessage` where suitable.

### Component control

- Use the Hilt-bound controllers. `GetControllerUseCase` selects `IController`
  from the DataStore-backed `UserDataRepository`.
- Keep PackageManager operations on the typed librootkotlinx path used by
  `RootApiController`.
- PackageManager and privileged Shizuku persist enabled state in each user's
  `package_restrictions.xml` under `/data/system/users/`. Apps can detect that state.
- IFW uses root to write XML in `/data/system/ifw/`. It filters activity,
  service, and broadcast intents while leaving PackageManager enabled state alone.
- Preserve the Shizuku permission distinction. Shell-based component switching is
  limited to eligible test-only targets. Regular targets require root-backed Shizuku.
- Verify enabled state and matching IFW rules separately when changing combined mode.
- See the [project README](README.md) for the user-facing controller modes.

## Tests

- Name new tests `givenX_whenY_thenZ`, or `whenY_thenZ` if setup is implicit.
  Keep Android instrumented test names free of backticks.
- `TestMethodNameDetector` warns about the `test` prefix on JUnit 4 tests and
  checks the two- or three-part format in `androidTest` sources.
- Use JUnit 4 and Truth for JVM tests. Use `runTest` for coroutine tests and
  Turbine for flow emissions. Use `MainDispatcherRule` when substituting Main.
- Prefer fakes over mocks. Repository fakes live in [`core/data-test`](core/data-test),
  DataStore doubles in [`core/datastore-test`](core/datastore-test), and shared
  helpers and controller fakes in [`core/testing`](core/testing).
- Test repositories with fake DAOs or in-memory Room when appropriate.
  Exercise navigation through the affected route and back stack.
- Use Roborazzi for visual regressions. Record screenshots on Linux using the CI
  environment as the reference. Run screenshot comparisons on Linux too.
- Use the existing Compose UI or Espresso test setup for instrumented coverage.
  The Hilt test runner is `BlockerTestRunner` where configured.

## Commits and pull requests

- Use a short imperative subject without a trailing period and explain why in
  the body. Ordinary changes use plain subjects. Dependency automation uses prefixes.
- Follow the [PR template](.github/pull_request_template.md). Discuss bug reports
  and features in an issue first, link it in the PR, and update affected docs.
- Run relevant tests, lint, and formatting checks. Review the diff and wait for
  the required CI checks to pass before merging.
