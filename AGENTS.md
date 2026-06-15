# AGENTS.md

## Project

EvenUp is an Android-only shared expense MVP.

The app lets a user scan or manually enter a receipt, review receipt data, add temporary participants, choose a payer, assign receipt items to people, allocate tax/tip/fees, review who owes whom, save the finalized expense, and create a public read-only share link.

## Current MVP goal

Build a fully functional pitch-ready MVP with scalable architecture.

Primary demo flow:

1. Open app.
2. Scan receipt or enter manually.
3. Review receipt.
4. Add participants.
5. Choose payer.
6. Assign items.
7. Allocate fees.
8. Review settlement.
9. Save expense.
10. Open/share public guest link.

## Read these project references before implementation

Before making implementation decisions, read:

- `MVP_SCOPE.md`
- `ARCHITECTURE.md`
- `CALCULATIONS.md`
- `API_CONTRACT.md`
- `DESIGN_SYSTEM.md`
- `TASKS.md`
- `docs/design/STITCH_REFERENCE.md`

For UI work, also inspect the relevant folder under:

- `docs/design/stitch/`

The Stitch HTML and PNG files are design references only. Do not copy the HTML directly into production Android code. Implement native Jetpack Compose screens using project design system components.

## Architecture rules

Use multi-module Clean Architecture with API/implementation separation.

The app module is the composition root.

Feature modules own UI, ViewModels, UI state, and UI events.
Domain modules own business rules, validation, split calculation, fee allocation, rounding, and settlement logic.
Data modules own network, local persistence, DTOs, repositories, and mappers.
Core modules provide shared infrastructure through stable APIs.

Forbidden dependencies:

- feature impl -> data impl
- domain -> Compose
- domain -> Android UI
- domain -> Retrofit/Ktor DTOs
- domain -> database entities
- data impl -> feature impl
- backend secrets -> Android app

Allowed dependency direction:

```text
:app -> feature impl, domain impl, data impl, core impl
feature:*:impl -> feature:*:api, domain:*:api, core:*:api
feature:*:impl -> core:designsystem:api, core:navigation:api
domain:*:impl -> domain:*:api and data:*:api only when use cases need repository contracts
data:*:impl -> data:*:api, domain:*:api, core:*:api
core:*:impl -> core:*:api
```

## Android stack

Use:

- Kotlin
- Jetpack Compose
- Material 3 customized through the design system
- Hilt
- Coroutines
- Flow
- Kotlin Serialization
- Retrofit or Ktor
- DataStore for MVP draft and saved participant persistence unless there is a strong reason for Room
- CameraX
- Android Photo Picker / Activity Result APIs

## Backend stack

Use:

- Cloudflare Worker
- Cloudflare D1
- OpenAI API only from the Worker, never from Android directly

Required backend endpoints:

```http
GET /health
POST /v1/receipts/parse
POST /v1/expenses
GET /v1/expenses/:shareId
GET /e/:shareId
```

## Money rules

Never use Float or Double for money.
Use integer minor units, for example cents.
Use deterministic rounding.
Distribute remainder cents by stable participant order.

Stable participant order:

1. Payer first if included.
2. Then participant creation order.

Use percentage basis points for percentage splits.

```text
10000 basis points = 100 percent
2500 basis points = 25 percent
```

## Product scope rules

Do not implement out-of-scope features:

- Groups
- Friends/social graph
- In-app payments
- Expense history screen
- AI natural language expense creation
- Contacts import
- Notifications
- Payment reminders
- Multi-expense settlement
- Editing finalized expenses after save

Participants are temporary names, not friends.
Saved participants are local reusable names only.

## UI rules

Use `DESIGN_SYSTEM.md` and `docs/design/STITCH_REFERENCE.md` as the design source of truth.
Use the project design system components.
Do not hardcode colors inside feature screens.
Use a white/black premium fintech visual direction.
Use subtle green only for success states.
Use bottom sheets for complex item split configuration.
Keep screens focused on one task.

The generated Stitch export is a reference, not production source. Use it to match layout, hierarchy, spacing, tone, and screen states. Implement the UI in native Compose.

## Domain rules

Business logic must be in domain modules, not ViewModels.
Calculation-related changes require unit tests.
ViewModels orchestrate use cases and produce UI state.
Repositories hide network and local persistence details.
DTOs and database entities must not leak into domain or feature layers.

## Testing rules

Before claiming completion, run the most specific relevant validation command.
Domain logic must have unit tests.
Calculation engine tests are P0.

Minimum domain test coverage:

- Equal split with remainder cents
- Quantity-based item assignment
- Shared equal item split
- Custom amount item split
- Percentage item split
- Equal fee allocation
- Proportional fee allocation
- Custom fee allocation
- Final settlement calculation
- Invalid assignment blocks save
- Invalid fee allocation blocks save

## Implementation behavior

Prefer small, reviewable changes.
Do not refactor unrelated files.
Do not collapse modules to speed up implementation.
Do not add production dependencies without explicit approval.
Do not replace the architecture with a single-module or package-only structure.
When a task is complex, create a plan first and wait for approval.
Do not proceed to the next task unless explicitly asked.
