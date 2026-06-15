# Android AGENTS.md

## Scope

These instructions apply to the Android app.

## Architecture

Use real Gradle modules and API/implementation separation.

Domain modules should be pure Kotlin where possible.
UI modules may use Android and Compose.
Data modules may use networking/local persistence libraries.

## Module boundaries

Feature implementation modules may depend on:

- their feature API module
- domain API modules
- core API modules
- design system API
- navigation API

Feature implementation modules must not depend directly on data implementation modules.

Domain modules must not depend on:

- Compose
- Android framework classes
- Retrofit/Ktor DTOs
- Room/DataStore implementation details
- feature modules

## Compose rules

Use stateless composables where practical.
Each screen should have:

- Route composable if needed
- Screen composable
- ViewModel
- immutable UiState
- UiEvent sealed interface
- one-shot navigation/event handling when required

ViewModels must not contain split calculation logic.
They should call domain use cases.

## Naming conventions

Use these suffixes consistently:

- Screen
- Route
- ViewModel
- UiState
- UiEvent
- UseCase
- Repository
- Dto
- Mapper

## Validation commands

Prefer the smallest validation command that covers the change.
Examples:

```bash
./gradlew :domain:expense:impl:test
./gradlew :feature:expense-flow:impl:compileDebugKotlin
./gradlew assembleDebug
```
