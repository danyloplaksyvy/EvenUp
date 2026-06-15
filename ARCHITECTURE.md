# EvenUp Architecture

## Required architecture

Use multi-module Clean Architecture with API/implementation separation.

The app module is the composition root.
Feature modules own UI.
Domain modules own business logic.
Data modules own integration and persistence.
Core modules provide reusable infrastructure.

## MVP module structure

```text
:app

:core:common
:core:model
:core:designsystem:api
:core:designsystem:impl
:core:navigation:api
:core:navigation:impl
:core:network:api
:core:network:impl
:core:datastore:api
:core:datastore:impl
:core:camera:api
:core:camera:impl

:domain:receipt:api
:domain:receipt:impl
:domain:expense:api
:domain:expense:impl
:domain:participant:api
:domain:participant:impl
:domain:sharing:api
:domain:sharing:impl

:data:receipt:api
:data:receipt:impl
:data:expense:api
:data:expense:impl
:data:participant:api
:data:participant:impl
:data:sharing:api
:data:sharing:impl

:feature:expense-flow:api
:feature:expense-flow:impl
```

## Dependency rules

### App

`:app` may depend on:

- feature impl modules
- domain impl modules
- data impl modules
- core impl modules

`:app` wires dependencies together.

### Feature implementation modules

Feature impl modules may depend on:

- own feature api
- domain api modules
- core api modules
- design system api
- navigation api

Feature impl modules must not depend on data impl modules.

### Domain modules

Domain api modules contain:

- entities
- value objects
- use case interfaces
- domain errors/results

Domain impl modules contain:

- use case implementations
- validation
- calculation
- allocation
- settlement logic

Domain modules must not depend on Android UI, Compose, DTOs, or database entities.

### Data modules

Data api modules contain repository contracts.
Data impl modules contain:

- DTOs
- API services
- local persistence implementations
- mappers
- repository implementations

Data impl modules must hide DTOs and persistence entities from domain and feature layers.

## Recommended dependency graph

```text
:feature:expense-flow:impl
  -> :feature:expense-flow:api
  -> :domain:receipt:api
  -> :domain:expense:api
  -> :domain:participant:api
  -> :domain:sharing:api
  -> :core:designsystem:api
  -> :core:navigation:api
  -> :core:camera:api

:data:receipt:impl
  -> :data:receipt:api
  -> :domain:receipt:api
  -> :core:network:api

:data:expense:impl
  -> :data:expense:api
  -> :domain:expense:api
  -> :domain:receipt:api
  -> :domain:participant:api
  -> :core:network:api
  -> :core:datastore:api

:domain:expense:impl
  -> :domain:expense:api
  -> :domain:receipt:api
  -> :domain:participant:api
  -> :data:expense:api if repository contracts are needed
```

## Presentation pattern

Use one-way data flow:

```text
Screen -> UiEvent -> ViewModel -> UseCase -> Repository -> DataSource
ViewModel -> UiState -> Screen
```

Each screen should define:

- UiState
- UiEvent
- ViewModel
- Screen composable
- Route composable if navigation needs dependency collection

## Domain use cases

Required P0 use cases:

```text
ParseReceiptUseCase
ValidateReceiptUseCase
CreateExpenseDraftUseCase
UpdateReceiptUseCase
AddParticipantUseCase
DeleteSavedParticipantUseCase
SelectPayerUseCase
AssignItemUnitsUseCase
AssignSharedItemUseCase
AssignCustomItemSplitUseCase
AssignPercentageItemSplitUseCase
ValidateItemAssignmentsUseCase
AllocateFeesUseCase
CalculateExpenseSummaryUseCase
ValidateExpenseBeforeSaveUseCase
SaveFinalizedExpenseUseCase
CreateShareLinkUseCase
```

## Post-pitch feature split

For MVP speed, keep all screens in `:feature:expense-flow:impl`.
After the pitch, split it into:

```text
:feature:new-expense
:feature:receipt-scan
:feature:receipt-review
:feature:people
:feature:assignment
:feature:fees
:feature:review-expense
:feature:share-expense
```

---

## Design reference integration

The design reference files live in:

```text
docs/design/STITCH_REFERENCE.md
docs/design/stitch/
docs/design/original_refs/
```

Architecture rule:

- Design reference files are documentation only.
- Feature modules must not import or depend on files from `docs/`.
- Stitch HTML must not be copied into production Android code.
- Implement native Compose screens using `:core:designsystem` components.

For UI implementation, use this dependency direction:

```text
:feature:expense-flow:impl -> :core:designsystem:api
:feature:expense-flow:impl -> :core:navigation:api
:feature:expense-flow:impl -> domain api modules
```

Do not introduce feature-to-design-file runtime dependencies.
