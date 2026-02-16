# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Blocker is an Android component controller that enables users to manage (enable/disable) Activities, Services, Broadcast Receivers, and Content Providers within installed applications to reduce resource usage and disable unwanted functionality.

**Three Control Mechanisms:**

1. **PackageManager (Root)**: Uses `pm` commands via libsu to directly modify component states. Changes are persisted in `/data/system/users/0/package_restrictions.xml`. Components are actually disabled, but apps can detect this and potentially re-enable them.

2. **Intent Firewall (IFW)**: Generates XML rules in `/data/system/ifw/` to filter intents at the framework level (Android 4.4.2+). Components appear enabled to the app but cannot start. Requires root access but provides stealthier blocking.

3. **Shizuku/Sui**: Uses Shizuku API for elevated permissions via ADB or root. Non-root mode requires test-only APKs (android:testOnly="true"). See [Shizuku documentation](https://github.com/RikkaApps/Shizuku).

The architecture follows [Now in Android](https://github.com/android/nowinandroid) patterns with strict modularization and official Android architecture guidelines.

## Build Configuration

- **Compile SDK**: 36
- **Target SDK**: 36
- **Min SDK**: 23
- **JDK**: 21
- **Gradle**: 9.1.0
- **Kotlin**: 2.2.0

## Build Commands

### Build Variants

Two product flavors with multiple build types:
- **foss**: Open-source build without Firebase/GMS (F-Droid version)
- **market**: Google Play build with Firebase Analytics and Crashlytics

Build types: `debug`, `release`, `benchmarkRelease`, `nonMinifiedRelease`

### Common Tasks

```bash
# Build debug APK (foss flavor)
./gradlew assembleFossDebug

# Build release APK (market flavor)
./gradlew assembleMarketRelease

# Build release bundle for Play Store
./gradlew bundleMarketRelease

# Install debug build on connected device
./gradlew installFossDebug

# Clean build
./gradlew clean

# Build all variants
./gradlew assemble
```

### Testing

```bash
# Run all unit tests
./gradlew test

# Run tests for specific module
./gradlew :core:data:test
./gradlew :feature:applist:testFossDebugUnitTest

# Run screenshot tests (verify against baselines)
./gradlew verifyRoborazziFossDebug

# Record/update screenshot baselines (MUST run on Linux)
./gradlew recordRoborazziFossDebug

# Run instrumented tests on connected device
./gradlew connectedFossDebugAndroidTest

# Run specific instrumented test
./gradlew :app-compose:connectedFossDebugAndroidTest
```

### Code Quality

```bash
# Run lint checks
./gradlew lint

# Run all checks (tests + lint)
./gradlew check

# Format code with Spotless
./gradlew spotlessApply

# Check code formatting
./gradlew spotlessCheck

# Generate JaCoCo coverage report
./gradlew createCombinedCoverageReport

# Update dependency baselines
./gradlew dependencyGuard
```

### Other Useful Commands

```bash
# Generate module dependency graphs (Mermaid in READMEs)
./gradlew graphUpdate

# List all available tasks
./gradlew tasks

# Build with baseline profile generation (release only)
./gradlew :app-compose:assembleMarketRelease
```

## Architecture

### Module Structure

**Core Modules (`core/`)** - Shared infrastructure and domain logic:
- `analytics`: Analytics event tracking abstraction
- `common`: Shared utilities, extensions, and base classes
- `component-controller`: Component control implementations (Root/IFW/Shizuku)
- `data`: Repository implementations orchestrating local/remote data sources
- `database`: Room database schemas and DAOs
- `datastore`: Proto DataStore for user preferences
- `datastore-proto`: Protocol buffer definitions
- `designsystem`: Material 3 design system and reusable UI components
- `domain`: Business logic use cases
- `git`: Git operations for syncing rule repositories
- `ifw-api`: Intent Firewall XML generation and parsing
- `model`: Data models and domain entities
- `network`: Retrofit-based API clients for remote rule fetching
- `provider`: Content provider utilities
- `rule`: Rule matching engine for component patterns
- `ui`: Shared UI components used across features
- `testing`: Test utilities, fakes, and test doubles

**Feature Modules (`feature/`)** - Screen-level features:
- `applist`: Application list with filtering and sorting
- `appdetail`: Application detail showing components (activities, services, receivers, providers)
- `generalrule`: General rule management and synchronization
- `ruledetail`: Detailed rule viewing and editing
- `search`: Global search with regex support
- `settings`: User preferences and controller configuration

**App Module (`app-compose/`)**: Main application module with navigation setup, dependency injection configuration, and app-level components.

**Sync Module (`sync/work`)**: Background synchronization using WorkManager.

**Supporting Modules**:
- `build-logic`: Custom Gradle convention plugins
- `benchmarks`: Baseline profile generation
- `lint`: Custom lint rules
- `ui-test-hilt-manifest`: Test-specific Hilt configuration

### Layered Architecture

**Presentation Layer (feature modules)**:
- Jetpack Compose UI built with Material 3
- ViewModels managing UI state via StateFlow
- Unidirectional Data Flow (UDF) pattern
- Hilt-based dependency injection

**Domain Layer (core/domain)**:
- Use cases encapsulating business logic
- Repository interfaces defining data contracts
- Domain models independent of data sources

**Data Layer (core/data, core/database, core/network)**:
- Repository implementations
- Room database as single source of truth (offline-first)
- Remote data sources for rule synchronization
- DataStore for preferences

### Component Controller Architecture

Located in `core/component-controller`, implements the `IController` interface:

```kotlin
interface IController {
    suspend fun enable(component: ComponentInfo): Boolean
    suspend fun disable(component: ComponentInfo): Boolean
    suspend fun switchComponent(component: ComponentInfo, state: Int): Boolean
    suspend fun batchEnable(componentList: List<ComponentInfo>, action: suspend (ComponentInfo) -> Unit): Int
    suspend fun batchDisable(componentList: List<ComponentInfo>, action: suspend (ComponentInfo) -> Unit): Int
    suspend fun checkComponentEnableState(packageName: String, componentName: String): Boolean
}
```

**Implementations**:
- `root/RootController.kt`: Uses libsu for root shell execution
- `ifw/IfwController.kt`: Generates Intent Firewall XML rules
- `shizuku/ShizukuController.kt`: Integrates Shizuku API

The active controller is selected via user preferences in DataStore and injected through Hilt.

### Navigation

Uses Jetpack Navigation Compose with adaptive layouts:
- **Compact screens**: Bottom navigation bar
- **Medium/Expanded screens**: Navigation rail
- **Large screens**: Two-pane layouts using `NavigableListDetailPaneScaffold`

Main destinations: App List → App Detail, General Rules, Search, Settings

### Dependency Injection

Hilt provides dependencies across all modules. Convention plugin `blocker.android.hilt` automatically configures Hilt for modules that require it.

Key Hilt modules:
- `app-compose/src/main/kotlin/com/merxury/blocker/di/`: App-level bindings
- Each core module with `di/` package: Module-specific bindings

## Testing

### Test Types

**Unit Tests** (`src/test/`):
- JUnit 4 with Truth assertions
- Mockito for mocking
- Turbine for Flow testing
- `kotlinx-coroutines-test` for coroutine testing
- Test doubles from `core:testing` module

**Screenshot Tests** (`testFoss/` source sets):
- Roborazzi (Robolectric-based) for screenshot verification
- **Critical**: Must be recorded on Linux as rendering differs across platforms
- CI runs on Linux (API 26, 35) for consistent results
- Property `roborazzi.test.verify=true` enables verification in local tests

**Instrumented Tests** (`src/androidTest/`):
- Espresso for view interactions
- Compose UI Testing for Compose components
- Custom test runner: `BlockerTestRunner` (Hilt-enabled)
- Run on physical devices or emulators

### Testing Best Practices

1. **ViewModels**: Test with fake repositories from `core:data-test`
2. **Repositories**: Test with fake DAOs and in-memory databases
3. **UI Components**: Use screenshot tests for visual regression
4. **Navigation**: Test with `TestNavHostController`
5. **Coverage**: Minimum 40% overall (enforced by JaCoCo)

### Running Screenshot Tests

```bash
# Verify screenshots match baselines
./gradlew verifyRoborazziFossDebug

# Record new baselines (run on Linux only!)
./gradlew recordRoborazziFossDebug

# Compare screenshots
./gradlew compareRoborazziFossDebug
```

⚠️ **Screenshot tests will fail on macOS/Windows** due to rendering differences. Always record baselines on Linux or in CI.

## Build Logic

Custom Gradle convention plugins in `build-logic/convention/` enforce consistent configuration:

- `blocker.android.application`: Base application module setup
- `blocker.android.library`: Base library module setup
- `blocker.android.feature`: Feature module convention (includes Hilt, Compose, navigation)
- `blocker.android.hilt`: Hilt configuration
- `blocker.android.room`: Room database setup
- `blocker.android.application.compose`: Compose configuration for app
- `blocker.android.library.compose`: Compose configuration for libraries
- `blocker.android.application.firebase`: Firebase integration (market flavor only)
- `blocker.android.application.flavors`: Product flavor configuration
- `blocker.android.lint`: Lint rule configuration

These plugins are defined in `build-logic/convention/build.gradle.kts` and applied via the version catalog (`libs.plugins.blocker.*`).

## Product Flavors

Configure dependencies and features per flavor:

**foss flavor**:
- No Firebase (Analytics, Crashlytics)
- No Google Mobile Services (GMS)
- Available on F-Droid and GitHub Releases

**market flavor**:
- Firebase Analytics for usage tracking
- Firebase Crashlytics for crash reporting
- Available on Google Play Store

Flavor-specific dependencies use source sets:
```
implementation(libs.some.library)  // Common
fossImplementation(libs.foss.variant)
marketImplementation(libs.market.variant)
```

## Code Quality Tools

### Spotless
Kotlin code formatting with ktlint 1.7.1. Configuration in `spotless/` directory.
```bash
./gradlew spotlessApply  # Format code
./gradlew spotlessCheck  # Verify formatting
```

### Dependency Guard
Tracks runtime dependencies to prevent unexpected additions. Baselines stored in module-specific files.
```bash
./gradlew dependencyGuard  # Update baselines
```

### Lint
Custom lint rules in `lint/` module detect design system violations and enforce testing conventions.

### JaCoCo
Code coverage with 40% minimum threshold. Combined report includes all modules:
```bash
./gradlew createCombinedCoverageReport
```

## Important Considerations

### Module Dependencies

**Golden Rule**: Feature modules must NOT depend on other feature modules. Share code through core modules instead.

```
✅ feature:applist → core:ui
✅ feature:appdetail → core:data
❌ feature:applist → feature:appdetail
```

Dependency Graph is validated via Dependency Guard.

### Controller Mode Differences

Understanding the behavioral differences is crucial:

| Aspect | PackageManager | Intent Firewall | Shizuku |
|--------|---------------|-----------------|---------|
| Root Required | Yes | Yes | No (with test-only APK) or Yes |
| Component State | Actually disabled | Appears enabled | Actually disabled |
| App Detection | Can detect and re-enable | Cannot detect blocking | Can detect and re-enable |
| Persistence | `/data/system/users/*/package_restrictions.xml` | `/data/system/ifw/*.xml` | Same as PackageManager |
| Reboot Survival | Yes | Yes | Yes |

**Recommendation**: Intent Firewall is stealthier as apps cannot detect blocking.

### Database Migrations

Room schema files are version-controlled in `app-compose/schemas/`. When modifying database entities:

1. Increment database version in `@Database` annotation
2. Add migration in database class or use destructive migration for dev
3. Export schema: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`
4. Test migration with `MigrationTestHelper`

### Baseline Profiles

Release builds automatically generate baseline profiles via `baselineProfile.automaticGenerationDuringBuild = true`. The `benchmarks` module contains Macrobenchmark tests for profile generation.

Manual generation:
```bash
./gradlew :app-compose:generateMarketReleaseBaselineProfile
```

### Version Management

Version code is auto-generated from git commit count:
```kotlin
versionCode = git rev-list --count HEAD
versionName = "2.0.$versionCode"
```

When tagging releases, version code increments automatically.

### String Resources

Strings are located in:
- `app-compose/src/main/res/values/strings.xml`
- `core/[module]/src/main/res/values/strings.xml`
- `feature/[module]/src/main/res/values/strings.xml`

Translated strings follow the pattern: `values-[lang]/strings.xml` (e.g., `values-zh/strings.xml` for Chinese).

### Proguard/R8

Release builds use R8 with optimization. Rules are defined in:
- `app-compose/proguard-rules.pro`: App-specific rules
- `core/component-controller/consumer-proguard-rules.pro`: Module consumer rules

Test with release builds before shipping to catch shrinking issues early.

### CI/CD

GitHub Actions workflows:
- `.github/workflows/Build.yaml`: PR and push builds, runs tests on API 26 and 35
- `.github/workflows/Release.yml`: Tagged release builds, publishes to GitHub Releases

Ensure all checks pass before merging PRs.
