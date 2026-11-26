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
- [`core/domain`](core/domain) owns use cases and component batch orchestration.
  [`core/data`](core/data) owns repository interfaces and implementations, including
  the device repositories and their monitors. [`core/model`](core/model)
  contains shared models.
- [`core/component-controller`](core/component-controller) runs typed privileged
  calls over the root and Shizuku transports. [`core/ifw-api`](core/ifw-api) owns
  `IfwStore` and the rule XML model.
  [`core/system`](core/system) reads users, installed apps, and declared components.
- [`core/database`](core/database), [`core/datastore`](core/datastore), and
  [`core/datastore-proto`](core/datastore-proto) own Room and Proto DataStore.
  [`core/network`](core/network) provides Retrofit clients.
- [`core/analytics`](core/analytics) handles analytics and
  [`core/provider`](core/provider) handles content providers.
  General rule matching lives in [`core/domain`](core/domain).
- [`core/rule`](core/rule) owns Blocker backups, Intent Firewall XML import and export, My Android
  Tools import, PackageManager reset, and the workers for rule operations.
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
- Observe installed apps, users, and component status through the device
  repositories. `PackageChangeMonitor` handles package broadcasts and reconciles
  cached apps on foreground entry and when privileged access becomes ready.
  `DeviceUserMonitor` handles profile lifecycle broadcasts and refreshes the
  cached users while the privileged channel is ready.
- Observe persisted data through the repository. `CatalogGeneralRuleRepository` reads the
  installed catalog. The bundled catalog seed lives in `core/data/src/main/assets/catalog`.
  `CatalogRepository` installs the seed and checks for verified catalog releases.
- Use `<Verb><Noun>UseCase` and `operator fun invoke` in `core:domain`.
  `PerformAppActionUseCase` shows the naming and Hilt constructor injection.
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

- Keep DAOs, data sources, and privileged calls behind data or domain APIs.
  `SettingsViewModel` accesses `BlockerPreferencesDataSource` directly.
  The IFW editors work directly with `IfwStore`.
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

- Use `ComponentStatusRepository.control` for component changes. It stores the
  system read-back. Use `ComponentBatchRunner` for queued changes and progress.
- Use the selected `PrivilegedChannel` for typed system calls. Request access
  in the foreground with `connect()` and check existing grants with
  `connectIfGranted()` when a caller cannot prompt.
- The per-app and global IFW rule editors always use root, regardless of the
  selected controller type.
- PackageManager and privileged Shizuku persist enabled state in each user's
  `package_restrictions.xml` under `/data/system/users/`. Apps can detect that state.
- `IfwStore` reads and replaces XML in `/data/system/ifw/` and reports changes
  to the component cache. Intent Firewall mode uses firewall rules for activities
  and services. It disables broadcast receivers through PackageManager because
  the firewall cannot stop implicit broadcasts to a single receiver. Unblocking a
  receiver also clears earlier firewall blocks.
- Preserve the Shizuku permission distinction. Shell-based component switching is
  limited to eligible test-only targets. Regular targets require root-backed Shizuku.
- Verify enabled state and matching IFW rules separately when changing combined mode.
- See the [project README](README.md) for the user-facing controller modes.

## Wake protection

- When running in the owner profile, Blocker collects Android wake evidence
  across available profiles, stores local history, and identifies how apps
  restart or keep one another running.
  Protection applies approved service rules through Intent Firewall when the
  selected privileged channel can write them.
- Collection and privileged calls live in
  [`core/component-controller`](core/component-controller) under `privileged.wake`
  and `privileged.calls`. The wake repository in [`core/data`](core/data) stores
  evidence in the wake history database. [`core/domain`](core/domain) owns wake
  attribution, detection, and protection reconciliation.
- `WakeWork` in [`sync/work`](sync/work) schedules `WakeHistoryWorker`.
  [`core/ifw-api`](core/ifw-api) renders and manages the wake feature's separate
  Intent Firewall files.

## Tests

- Name new tests `givenX_whenY_thenZ`, or `whenY_thenZ` if setup is implicit.
  Keep Android instrumented test names free of backticks.
- `TestMethodNameDetector` warns about the `test` prefix on JUnit 4 tests and
  checks the two- or three-part format in `androidTest` sources.
- Use JUnit 4 and Truth for JVM tests. Use `runTest` for coroutine tests and
  Turbine for flow emissions. Use `MainDispatcherRule` when substituting Main.
- Prefer fakes over mocks. [`core/data-test`](core/data-test) provides repository
  fakes and Hilt test bindings. [`core/testing`](core/testing) provides device
  repository and privileged-channel fakes, additional repository test doubles,
  and shared helpers. [`core/datastore-test`](core/datastore-test) provides an
  in-memory DataStore and its Hilt test bindings.
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
