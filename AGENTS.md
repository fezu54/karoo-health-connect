# Agent Guidelines: Karoo Health Connect

## Project Purpose
Companion Android application that downloads recorded ride `.fit` files from the SRAM/Hammerhead Dashboard and synchronizes them with the on-device Google Health Connect API.

---

## 1. High-Level Principles & Architecture

### Onion Architecture
Enforce concentric rings with strict inward dependency rules (inner rings have zero imports/knowledge of outer rings):
- **Domain (`domain`)**: Pure Kotlin. Zero framework or Android dependencies. Contains entities, value objects, domain services, and repository/gateway interfaces.
- **Application (`application`)**: Use cases and interactors orchestrating domain logic. Exposes simple, intention-revealing interfaces.
- **Adapters (`adapters`)**: Concrete implementations of gateways, repositories, external APIs (SRAM, Garmin FIT parser, Health Connect, SharedPreferences), and UI (Jetpack Compose).
- **Frameworks (`frameworks`)**: Dependency injection wiring (`AppContainer`), Android Application, WorkManager workers, and Android lifecycle integration.

### Core Engineering Rules
- **YAGNI**: Implement only what is strictly necessary. Less code is always better.
- **Object-Oriented**: Write small, single-purpose objects. Avoid procedural code and multi-hundred-line utility classes.
- **Lightweight Dependencies**: Do not introduce heavy third-party frameworks unless strictly needed. Avoid external DI (Hilt/Koin) or database engines (Room) when manual composition roots and simple key-value storage suffice.

---

## 2. Kotlin Style & Conventions

- **Scope Functions**: Prefer `?.let`, `run`, `apply`, `also`, and `with` to reduce nested conditionals and keep scope tightly bounded:
  - `?.let { ... }` for null-safe transformation chains.
  - `run { ... }` for executing a block returning a value requiring localized scope.
  - `apply { ... }` and `also { ... }` for object configuration and side effects.
- **Control Flow**:
  - Use `when` expressions instead of nested `if-else if` chains.
  - Favor expression-bodied functions (`fun foo() = ...`) for short implementations.
- **Parameters**:
  - Use named parameters in public functions and constructors when it improves clarity (e.g. multiple parameters of the same type).
  - Do NOT use named parameters when the argument variable name is identical to the parameter name (e.g., prefer `foo(bar)` over `foo(bar = bar)`).
- **Imports**: Never use inline imports (fully qualified type names in code like `java.util.UUID`). All imports must be declared at the top of the file.
- **Function Size**: Keep functions small and focused (ideally under 40 lines) with a single reason to change.
- **Naming & Comments**:
  - Use clear, self-explanatory names (PascalCase for types, camelCase for functions and variables).
  - Avoid comments that explain what code does; rename or extract to well-named functions instead. Use data classes for DTOs and value objects.

---

## 3. Testing Standards

- **Coverage**: Every production change must include tests in the same pull request / commit.
- **Determinism**: Tests must be 100% deterministic and never hit real network endpoints or hardware. Use fakes or in-memory test doubles.
- **Frameworks**: Use **Kotest** (FreeSpec) and **MockK**. Manage versions in `gradle/libs.versions.toml`.
- **Test Naming & Structure**:
  - Use backticks with full sentences describing behavior: `should do something when action executed`.
  - Use `//given`, `//when`, `//then` comments where they add clarity.

```kotlin
class SyncRidesUseCaseTest : FreeSpec({
    val rideSourceGateway = mockk<RideSourceGateway>()
    val fitParser = mockk<FitParser>()
    val healthConnectGateway = mockk<HealthConnectGateway>(relaxed = true)
    val preferencesRepository = mockk<SyncPreferencesRepository>()

    val useCase = SyncRidesUseCase(
        rideSourceGateway,
        fitParser,
        healthConnectGateway,
        preferencesRepository,
    )

    "should stream and sync ride when new ride is available" {
        //given
        val rideId = RideId("ride-123")
        val stream = ByteArray(0).inputStream()
        val ride = createSampleRide(rideId)

        coEvery { preferencesRepository.getLastSyncTimestamp() } returns Instant.EPOCH
        coEvery { preferencesRepository.getPreferences() } returns SyncPreferences.Default
        coEvery { rideSourceGateway.fetchUnsyncedRides(any()) } returns listOf(RideSummary(rideId, "Morning Ride"))
        coEvery { rideSourceGateway.fetchRawFitStream(rideId) } returns stream
        coEvery { fitParser.parse(stream) } returns ride
        coEvery { preferencesRepository.markSynced(rideId, any()) } just Runs

        //when
        val result = useCase.execute()

        //then
        result shouldBe SyncResult.Success(syncedRides = 1)
        coVerify(exactly = 1) { healthConnectGateway.upsertRide(ride, any()) }
        coVerify(exactly = 1) { preferencesRepository.markSynced(rideId, any()) }
    }
})
```

---

## 4. Git & Commit Conventions

- **Conventional Commits**: Format commit messages as `<type>(<scope>): <intent>`.
  - Allowed types: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`.
- **Why over What/How**: Never describe mechanical code edits in the commit title or message. Always describe **why** the change was made and the problem it solves.
- **Issue Links**: Relate commits to GitHub issues in the commit footer, never in the summary title:
  ```text
  feat(sram): support email and password authentication

  Enables user authentication against SRAM ID to retrieve bearer tokens
  for downloading recorded Karoo activities.

  Closes #15
  ```

---

## 5. Agent Workflow Rules

- When proposing or implementing changes, provide minimal necessary code.
- Always include or update tests alongside production code changes.
- Never add `TODO` comments in lieu of implementation. If follow-up work is required, note it for issue tracking.
- If a change introduces a new dependency or affects architecture, explicitly state which ring it belongs to and why.
