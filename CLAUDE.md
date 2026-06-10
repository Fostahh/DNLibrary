# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

DNLibrary is a Kotlin Multiplatform (KMP) library targeting Android and iOS. It is currently an
early-stage skeleton: only the `sharedLogic` module exists (the `androidApp`/`iosApp`/`sharedUI`
modules referenced in README.md have not been created yet, so commands targeting them will fail).

The shared logic is being built around a small remote data layer for a video games API (RAWG-style:
base URL + `?key=<apiKey>` query auth), structured as the start of a Clean Architecture setup
(commit "Simulating Clean Architecture").

## Build & development commands

All commands run from the project root using the Gradle wrapper.

- `./gradlew build` — build all modules
- `./gradlew :sharedLogic:build` — build just the shared library
- `./gradlew :sharedLogic:assemble` — assemble outputs (Android library + iOS XCFramework)
- `./gradlew :sharedLogic:check` — run all checks/tests for sharedLogic
- `./gradlew :sharedLogic:testDebugUnitTest` — run Android unit tests (host tests)
- `./gradlew :sharedLogic:iosSimulatorArm64Test` — run iOS simulator tests

There is no `commonTest` source set populated yet; `kotlin-test` is wired up in
`sharedLogic/build.gradle.kts` and ready to use.

The iOS app (when present) must be opened/run from `/iosApp` in Xcode — it cannot be built via
Gradle alone.

## Architecture

### Module layout
- `sharedLogic/src/commonMain` — shared Kotlin code for all targets
- `sharedLogic/src/androidMain` — Android-specific `actual` implementations
- `sharedLogic/src/iosMain` — iOS-specific `actual` implementations

### Dependency versions & catalog
Dependencies/plugins are managed via the version catalog at `gradle/libs.versions.toml` with
TYPESAFE_PROJECT_ACCESSORS enabled (referenced in build files as `libs.xxx`). Notable versions:
Kotlin 2.3.21, AGP 9.0.1, Compose Multiplatform 1.11.1, Ktor 3.5.0, kotlinx-coroutines 1.11.0,
SKIE 0.10.12 (improves Swift API generated from Kotlin for the iOS framework).

### Platform abstraction (expect/actual)
`Platform.kt` declares `expect fun getPlatform(): Platform`, implemented per-target in
`Platform.android.kt` (`AndroidPlatform`, uses `Build.VERSION.SDK_INT`) and `Platform.ios.kt`
(`IOSPlatform`, uses `UIDevice`). Follow this pattern for any other platform-specific behavior.

### Networking layer
Located under `id.dn.fostah.dnlibrary.datasource.remote`:

- `network/NetworkManager.kt` — `DNNetworkManager` is a singleton (`initialize(config)` /
  `getInstance()`) wrapping a Ktor `HttpClient` configured with `ContentNegotiation` + kotlinx
  JSON (lenient, ignores unknown keys). `DNNetworkManagerConfig` holds `baseUrl` and `apiKey`.
  **Consumers must call `DNNetworkManager.initialize(config)` once before any data source uses
  `getInstance()`**, otherwise it throws `error("NetworkManager not initialized.")`.
- `RemoteDataSource.kt` — `IRemoteDataSource` / `RemoteDataSource` take a `DNNetworkManager` and
  expose suspend functions (e.g. `getDaFreakingVideoGames()`) that call the API and map errors by
  rethrowing a wrapped `Exception` with context.
- `network/responses/VideoGameResponse.kt` — `@Serializable` DTOs (`ListVideoGameResponse`,
  `VideoGameResponse`) matching the remote JSON shape, all fields nullable with `@SerialName`.

Per-platform Ktor engines are wired in `sharedLogic/build.gradle.kts`: `ktor-client-android` for
Android, `ktor-client-darwin` for iOS.

### iOS framework packaging
`sharedLogic/build.gradle.kts` builds a static XCFramework named `DNLibrary` (bundle id
`id.dn.fostah.DNLibrary`) for `iosArm64` and `iosSimulatorArm64`.
