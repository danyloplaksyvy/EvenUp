# EvenUp MVP Codex Task Board

## Working rules

- Complete tasks in order unless explicitly instructed otherwise.
- Work on exactly one task at a time.
- Do not proceed to the next task automatically.
- Keep changes small and reviewable.
- End every task with validation commands and results.
- Do not implement out-of-scope features.
- Do not collapse the architecture.
- Do not put business logic in ViewModels.
- Do not use Float or Double for money.

## Status legend

```text
TODO
IN_PROGRESS
BLOCKED
DONE
```

---

# Milestone 0 - Repository instruction setup

## T000 - Add Codex instruction files

Status: TODO
Priority: P0
Scope:

- AGENTS.md
- TASKS.md
- ARCHITECTURE.md
- MVP_SCOPE.md
- CALCULATIONS.md
- API_CONTRACT.md
- DESIGN_SYSTEM.md
- android/AGENTS.md
- backend/AGENTS.md

Goal:

Add the instruction files and confirm Codex has read them.

Done when:

- Files exist in the repository.
- Codex confirms the architecture and task execution rules.

---

# Milestone 1 - Android foundation

## T001 - Create Android project baseline

Status: TODO
Priority: P0
Scope:

- settings.gradle.kts
- root build files
- app module
- version catalog if used

Goal:

Create or verify the Android app baseline.

Requirements:

- Kotlin
- Jetpack Compose
- Hilt
- Coroutines
- Kotlin Serialization
- DataStore
- CameraX dependencies available later

Do not implement product screens yet.

Done when:

- App builds.
- Empty Compose app launches.
- `./gradlew assembleDebug` passes.

## T002 - Create Gradle module skeleton

Status: TODO
Priority: P0
Scope:

Create modules listed in ARCHITECTURE.md.

Goal:

Set up real multi-module Clean Architecture with API/implementation separation.

Do not implement product logic yet.

Done when:

- All modules compile.
- No circular dependencies.
- `./gradlew assembleDebug` passes.

## T003 - Wire baseline dependency graph

Status: TODO
Priority: P0
Scope:

- module build.gradle.kts files

Goal:

Apply dependency rules from ARCHITECTURE.md.

Done when:

- Feature impl modules do not depend on data impl modules.
- Domain modules do not depend on Compose or Android UI.
- `./gradlew assembleDebug` passes.

## T004 - Add Hilt composition pattern

Status: TODO
Priority: P0
Scope:

- app module
- implementation modules that need DI

Goal:

Create DI bindings for placeholder interfaces/implementations where needed.

Done when:

- App can inject at least one sample use case into a ViewModel.
- `./gradlew assembleDebug` passes.

---

# Milestone 2 - Domain and calculation engine

## T010 - Define domain value objects

Status: TODO
Priority: P0
Scope:

- :domain:expense:api
- :domain:receipt:api
- :domain:participant:api

Goal:

Define common domain value objects.

Required:

- MoneyMinor
- CurrencyCode
- Quantity
- PercentageBasisPoints
- strongly typed IDs for expense, draft, receipt item, fee, participant

Done when:

- Domain api modules compile.
- No Android dependencies.
- Unit tests cover basic value validation if implemented.

## T011 - Define receipt and participant models

Status: TODO
Priority: P0
Scope:

- :domain:receipt:api
- :domain:participant:api

Goal:

Define Receipt, ReceiptItem, ReceiptFee, FeeType, Participant.

Done when:

- Models compile.
- No DTOs or UI types are introduced.

## T012 - Define assignment and fee allocation models

Status: TODO
Priority: P0
Scope:

- :domain:expense:api

Goal:

Define ItemAssignment, ItemParticipantShare, FeeAllocation, FeeParticipantShare, assignment modes, and allocation modes.

Done when:

- Models support full, by-units, shared equal, custom amount, and percentage item splits.
- Models support equal, proportional, and custom fee allocation.

## T013 - Implement deterministic split rounding utility

Status: TODO
Priority: P0
Scope:

- :domain:expense:impl

Goal:

Implement minor-unit splitting and remainder distribution.

Tests required:

- 1000 / 3 -> 334, 333, 333
- 1001 / 3 -> 334, 334, 333
- 1002 / 3 -> 334, 334, 334
- Stable ordering is respected.

Done when:

- `./gradlew :domain:expense:impl:test` passes.

## T014 - Implement receipt validation

Status: TODO
Priority: P0
Scope:

- :domain:receipt:api
- :domain:receipt:impl

Goal:

Validate receipt completeness and consistency.

Done when:

- Invalid receipt produces specific validation errors.
- Total mismatch can be represented as warning.
- Tests pass.

## T015 - Implement participant validation

Status: TODO
Priority: P0
Scope:

- :domain:participant:api
- :domain:participant:impl

Goal:

Validate participant setup and payer selection.

Done when:

- At least two participants required.
- Payer must exist in participant list.
- Empty names fail validation.
- Tests pass.

## T016 - Implement item assignment validation

Status: TODO
Priority: P0
Scope:

- :domain:expense:api
- :domain:expense:impl

Goal:

Validate item assignments.

Done when:

- Unassigned item fails validation.
- Custom amount split must equal item total.
- Percentage split must equal 10000 basis points.
- Quantity split must assign all units.
- Tests pass.

## T017 - Implement fee allocation logic

Status: TODO
Priority: P0
Scope:

- :domain:expense:api
- :domain:expense:impl

Goal:

Implement equal, proportional, and custom fee allocation.

Done when:

- Equal fee allocation works.
- Proportional fee allocation works from item subtotals.
- Custom fee allocation validates exact total.
- Tests pass.

## T018 - Implement settlement calculation

Status: TODO
Priority: P0
Scope:

- :domain:expense:api
- :domain:expense:impl

Goal:

Calculate participant summaries and settlement rows.

Done when:

- Sum of participant shares equals receipt total.
- Sum of net balances equals zero.
- Settlement rows are correct for one-payer MVP.
- Tests pass.

## T019 - Implement final expense validation

Status: TODO
Priority: P0
Scope:

- :domain:expense:api
- :domain:expense:impl

Goal:

Validate draft before save.

Done when:

- Receipt valid.
- Participants valid.
- Payer valid.
- All items assigned.
- All fees allocated.
- Summary can be calculated.
- Tests pass.

---

# Milestone 3 - Backend Worker and D1

## T030 - Create Cloudflare Worker baseline

Status: TODO
Priority: P0
Scope:

- backend project

Goal:

Create Worker project with health endpoint.

Done when:

- `GET /health` returns `{ "ok": true }`.
- Worker runs locally.

## T031 - Add D1 migration

Status: TODO
Priority: P0
Scope:

- backend migrations

Goal:

Create expenses table.

Done when:

- Migration runs.
- Worker can access D1 binding.

## T032 - Implement POST /v1/expenses

Status: TODO
Priority: P0
Scope:

- backend routes

Goal:

Save finalized expense payload and generate share link.

Done when:

- Expense stored in D1.
- Response returns expenseId, shareId, shareUrl.
- Duplicate share ID generation retries.

## T033 - Implement GET /v1/expenses/:shareId

Status: TODO
Priority: P0
Scope:

- backend routes

Goal:

Fetch finalized expense by public share ID.

Done when:

- Existing expense returns payload.
- Missing expense returns 404.

## T034 - Implement GET /e/:shareId guest page

Status: TODO
Priority: P0
Scope:

- backend guest HTML rendering

Goal:

Render public read-only guest expense page.

Done when:

- Mobile-friendly HTML page renders.
- Shows total, payer, settlement, participants, item breakdown, fee breakdown.
- No login required.

## T035 - Implement POST /v1/receipts/parse

Status: TODO
Priority: P0
Scope:

- backend parse route
- OpenAI integration

Goal:

Parse receipt image through OpenAI and return structured receipt JSON.

Done when:

- OpenAI API key is read from Worker secret.
- Android-compatible JSON response returned.
- Invalid parse returns safe error.
- Image payloads are not logged.

---

# Milestone 4 - Android data layer

## T040 - Implement core network module

Status: TODO
Priority: P0
Scope:

- :core:network:api
- :core:network:impl

Goal:

Create network client abstraction and implementation.

Done when:

- Base URL configurable.
- JSON configured.
- Errors mapped.
- Compile passes.

## T041 - Implement receipt repository and DTO mapper

Status: TODO
Priority: P0
Scope:

- :data:receipt:api
- :data:receipt:impl

Goal:

Map `/v1/receipts/parse` response into domain Receipt.

Done when:

- ReceiptRepository.parseReceipt works through network client.
- DTOs do not leak to domain.

## T042 - Implement expense save repository

Status: TODO
Priority: P0
Scope:

- :data:expense:api
- :data:expense:impl

Goal:

Save FinalizedExpense to Worker and return ShareLink.

Done when:

- POST /v1/expenses integration compiles.
- Errors are mapped.

## T043 - Implement draft persistence

Status: TODO
Priority: P0
Scope:

- :data:expense:api
- :data:expense:impl
- :core:datastore:api
- :core:datastore:impl

Goal:

Persist ExpenseDraft locally with DataStore JSON.

Done when:

- observeDraft, saveDraft, clearDraft work.
- Draft survives navigation and basic recreation.

## T044 - Implement saved participants persistence

Status: TODO
Priority: P0
Scope:

- :data:participant:api
- :data:participant:impl

Goal:

Persist saved participant names locally.

Done when:

- Save name.
- Observe saved names.
- Delete saved name.
- Avoid duplicates.

---

# Milestone 5 - Manual product flow first

## T050 - Implement design system tokens and base components

Status: TODO
Priority: P0
Scope:

- :core:designsystem:api
- :core:designsystem:impl

Goal:

Implement theme, tokens, buttons, fields, cards, bottom action bar, loading/error/success states.

Done when:

- Feature screens can use design system components.
- No hardcoded feature colors needed.

## T051 - Implement New Expense screen

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:api
- :feature:expense-flow:impl

Goal:

Show Scan receipt and Enter manually entry points.

Done when:

- Primary CTA goes to scan placeholder or screen.
- Secondary CTA goes to manual entry.

## T052 - Implement Manual Receipt Entry screen

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Allow complete manual receipt creation.

Done when:

- User can add/edit/delete items.
- User can enter tax/tip/total/currency.
- Valid receipt can continue.

## T053 - Implement Receipt Review screen

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Review and edit parsed or manually entered receipt.

Done when:

- Same model used for scanner and manual flow.
- Invalid fields show errors.

## T054 - Implement Choose People screen

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Add participants, reuse saved names, delete saved names, choose payer.

Done when:

- At least two participants required.
- Payer required.
- Saved names work.

## T055 - Implement Assign Receipt screen with simple full assignment

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Tap participant then tap item to assign full item.

Done when:

- Full item assignment works for quantity 1 items.
- Continue blocked until all items assigned.

## T056 - Implement quantity assignment

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Support item quantity assignment by units.

Done when:

- A quantity 3 item can be assigned as 2 units to one person and 1 to another.
- Remaining quantity displayed.

## T057 - Implement Item Detail Bottom Sheet

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Support by-units, shared equal, custom amount, and percentage modes.

Done when:

- Valid split can be saved.
- Invalid split shows error.

## T058 - Implement Fees Allocation screen

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Allocate tax/tip/fees equally, proportionally, or custom.

Done when:

- Equal default works.
- Proportional preview works.
- Custom exact-match validation works.

## T059 - Implement Review Expense screen

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Show final settlement summary and expandable calculation details.

Done when:

- Settlement rows display correctly.
- Details explain item and fee totals.
- Save disabled if invalid.

## T060 - Implement Save / Share screen

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Save finalized expense and show share URL.

Done when:

- Expense saved through use case.
- Draft cleared only after successful save.
- Share URL shown.
- Native share sheet opens.

---

# Milestone 6 - Scanner integration

## T070 - Implement camera abstraction

Status: TODO
Priority: P0
Scope:

- :core:camera:api
- :core:camera:impl

Goal:

Provide camera capture result to feature layer.

Done when:

- Camera capture returns image data/URI usable by parse flow.

## T071 - Implement gallery import

Status: TODO
Priority: P0
Scope:

- :core:camera:api
- :core:camera:impl

Goal:

Import receipt image from gallery/photo picker.

Done when:

- Gallery selected image can be passed to parse flow.

## T072 - Implement Receipt Scan screen

Status: TODO
Priority: P0
Scope:

- :feature:expense-flow:impl

Goal:

Scan/import receipt and parse through ParseReceiptUseCase.

Done when:

- Camera path works.
- Gallery path works.
- Loading shown.
- Errors allow retry/manual fallback.

---

# Milestone 7 - Demo hardening

## T080 - Domain regression test pass

Status: TODO
Priority: P0
Goal:

Run and fix domain tests.

Done when:

- All domain tests pass.

## T081 - Manual full-flow smoke test

Status: TODO
Priority: P0
Goal:

Complete full app flow without receipt scanning.

Done when:

- Manual entry to guest link works.

## T082 - Real receipt scan smoke test

Status: TODO
Priority: P0
Goal:

Complete full flow with prepared real receipt image.

Done when:

- Receipt parse succeeds or fallback path works.

## T083 - Guest link smoke test

Status: TODO
Priority: P0
Goal:

Open generated share link in browser.

Done when:

- Guest page renders and balances match Android.

## T084 - UI polish pass

Status: TODO
Priority: P1
Goal:

Make the main pitch flow visually coherent and premium.

Done when:

- Screens use design system components.
- No obvious placeholder styling in main flow.
