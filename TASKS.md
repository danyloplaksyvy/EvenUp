# EvenUp MVP Codex Task Board

## How to use this file

Codex should execute one task at a time.

Before every task, read:

- `AGENTS.md`
- this file
- any task-specific referenced files

Rules:

- Do not start the next task unless explicitly asked.
- Keep changes small and reviewable.
- Run the validation command listed in the task when possible.
- If the repository does not yet contain the required command, explain what is missing and add the minimal command only if appropriate.
- Do not implement out-of-scope features.
- Do not collapse the architecture to speed up implementation.

## Task statuses

Use these statuses manually:

```text
TODO
IN_PROGRESS
BLOCKED
DONE
```

## Current implementation strategy

Build the MVP in this order:

1. Persistent Codex/project docs.
2. Android module skeleton.
3. Domain and calculation engine.
4. Backend save/fetch/share.
5. Android data layer.
6. Manual full flow.
7. Scanner integration.
8. UI polish using Stitch references.
9. Demo hardening.

Manual full flow should work before receipt scan integration.

---

# Milestone 0 - Project instructions and design references

## T000 - Add Codex project files

Status: DONE
Priority: P0
Scope:

- `AGENTS.md`
- `TASKS.md`
- `ARCHITECTURE.md`
- `MVP_SCOPE.md`
- `CALCULATIONS.md`
- `API_CONTRACT.md`
- `DESIGN_SYSTEM.md`
- `android/AGENTS.md`
- `backend/AGENTS.md`
- `prompts/CODEX_PROMPTS.md`
- `docs/design/STITCH_REFERENCE.md`
- `docs/design/stitch/`
- `docs/design/original_refs/`

Goal:

Add all instructions and design references into the repo.

Done when:

- Files exist in the repository.
- Codex confirms the architecture rules, MVP scope, design references, and task execution rules.

Validation:

- No build command required.

---

# Milestone 1 - Android foundation

## T001 - Create or verify Android project baseline

Status: DONE
Priority: P0
Scope:

- `settings.gradle.kts`
- root build files
- `app` module
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

Status: DONE
Priority: P0
Scope:

Create modules listed in `ARCHITECTURE.md`.

Goal:

Set up real multi-module Clean Architecture with API/implementation separation.
To exclude boilerplate repeatable code for gradle files, use convention plugin approach.

Do not implement product logic yet.

Done when:

- All modules compile.
- No circular dependencies.
- `./gradlew assembleDebug` passes.

## T003 - Wire baseline dependency graph

Status: DONE
Priority: P0
Scope:

- module `build.gradle.kts` files

Goal:

Apply dependency rules from `ARCHITECTURE.md`.

Done when:

- Feature impl modules do not depend on data impl modules.
- Domain modules do not depend on Compose or Android UI.
- `./gradlew assembleDebug` passes.

## T004 - Add Hilt composition pattern

Status: DONE
Priority: P0
Scope:

- `app` module
- implementation modules that need DI

Goal:

Create DI bindings for placeholder interfaces/implementations where needed.

Done when:

- App can inject at least one sample use case into a ViewModel.
- `./gradlew assembleDebug` passes.

---

# Milestone 2 - Design system foundation

## T005 - Implement design tokens and app theme

Status: DONE
Priority: P0
Scope:

- `:core:designsystem:api`
- `:core:designsystem:impl`
- `DESIGN_SYSTEM.md`
- `docs/design/STITCH_REFERENCE.md`

Goal:

Implement EvenUp theme tokens for the white/black premium fintech UI.

Requirements:

- Colors exposed through semantic tokens.
- Typography roles.
- Shapes and spacing.
- Compose theme wrapper.
- No product screens yet.

Done when:

- App can use `EvenUpTheme`.
- No feature screen hardcodes design tokens.
- `./gradlew :core:designsystem:impl:compileDebugKotlin` or `./gradlew assembleDebug` passes.

## T006 - Implement base design system components

Status: DONE
Priority: P0
Scope:

- `:core:designsystem:api`
- `:core:designsystem:impl`

Goal:

Implement reusable components required by feature screens.

Required components:

- `EvenUpPrimaryButton`
- `EvenUpSecondaryButton`
- `EvenUpTextButton`
- `EvenUpTextField`
- `EvenUpMoneyField`
- `EvenUpCard`
- `EvenUpBottomActionBar`
- `EvenUpBottomSheet`
- `EvenUpParticipantAvatar`
- `EvenUpParticipantChip`
- `EvenUpValidationMessage`
- `EvenUpLoadingState`
- `EvenUpErrorState`
- `EvenUpSuccessState`

Done when:

- Components compile.
- Basic previews exist if the project supports previews.
- `./gradlew :core:designsystem:impl:compileDebugKotlin` or `./gradlew assembleDebug` passes.

## T007 - Implement receipt and settlement UI components

Status: DONE
Priority: P0
Scope:

- `:core:designsystem:api`
- `:core:designsystem:impl`
- `docs/design/stitch/assign_items/`
- `docs/design/stitch/review_expense/`

Goal:

Add reusable components for receipt rows, assignment states, and settlement summaries.

Required components:

- `EvenUpReceiptItemRow`
- `EvenUpSettlementRow`
- `EvenUpSummaryCard`
- `EvenUpExpandableDetailsCard`

Done when:

- Components compile.
- Components support the states required by the Stitch references.
- `./gradlew :core:designsystem:impl:compileDebugKotlin` or `./gradlew assembleDebug` passes.

---

# Milestone 3 - Domain and calculation engine

## T010 - Define domain value objects

Status: DONE
Priority: P0
Scope:

- `:domain:expense:api`
- `:domain:receipt:api`
- `:domain:participant:api`

Goal:

Define common domain value objects.

Required:

- `MoneyMinor`
- `CurrencyCode`
- `Quantity`
- `PercentageBasisPoints`
- strongly typed IDs for expense, draft, receipt item, fee, participant

Done when:

- Domain api modules compile.
- No Android dependencies.
- Unit tests cover basic value validation if implemented.

Validation:

- `./gradlew :domain:expense:api:test` if tests exist, otherwise relevant compile command.

## T011 - Define receipt and participant models

Status: DONE
Priority: P0
Scope:

- `:domain:receipt:api`
- `:domain:participant:api`

Goal:

Define `Receipt`, `ReceiptItem`, `ReceiptFee`, `FeeType`, `Participant`.

Done when:

- Models compile.
- No DTOs or UI types are introduced.

## T012 - Define assignment and fee allocation models

Status: DONE
Priority: P0
Scope:

- `:domain:expense:api`

Goal:

Define `ItemAssignment`, `ItemParticipantShare`, `FeeAllocation`, `FeeParticipantShare`, assignment modes, and allocation modes.

Done when:

- Models support full, by-units, shared equal, custom amount, and percentage item splits.
- Models support equal, proportional, and custom fee allocation.

## T013 - Implement deterministic split rounding utility

Status: DONE
Priority: P0
Scope:

- `:domain:expense:impl`

Goal:

Implement deterministic splitting of integer minor units.

Tests required:

- `1000 / 3 -> 334, 333, 333`
- `1001 / 3 -> 334, 334, 333`
- stable participant order is respected

Done when:

- Unit tests pass.
- No Float or Double is used.

Validation:

- `./gradlew :domain:expense:impl:test`

## T014 - Implement receipt and participant validation

Status: DONE
Priority: P0
Scope:

- `:domain:receipt:api`
- `:domain:receipt:impl`
- `:domain:participant:api`
- `:domain:participant:impl`

Goal:

Add use cases and results for validating receipts and participant setup.

Done when:

- Invalid receipt fields return specific errors.
- At least two participants required.
- Payer must exist in participants.
- Tests pass.

## T015 - Implement item assignment validation

Status: DONE
Priority: P0
Scope:

- `:domain:expense:api`
- `:domain:expense:impl`

Goal:

Validate item assignments.

Rules:

- Every item assigned.
- Amount shares sum to item total.
- Unit shares sum to item quantity.
- Percentage shares sum to 10000 basis points.
- No negative values.
- No unknown participants.

Done when:

- Unit tests cover valid and invalid assignment cases.
- `./gradlew :domain:expense:impl:test` passes.

## T016 - Implement fee allocation use cases

Status: DONE
Priority: P0
Scope:

- `:domain:expense:api`
- `:domain:expense:impl`

Goal:

Implement equal, proportional, and custom fee allocation.

Done when:

- Equal allocation distributes remainder cents deterministically.
- Proportional allocation uses item subtotal by participant.
- Custom allocation validates exact totals.
- Tests pass.

## T017 - Implement settlement calculation use case

Status: DONE
Priority: P0
Scope:

- `:domain:expense:api`
- `:domain:expense:impl`

Goal:

Calculate participant summaries and one-payer settlement rows.

Rules:

```text
person_share = assigned_item_total + allocated_tax + allocated_tip + other_allocated_fees
net_balance = amount_paid_by_person - person_share
```

Done when:

- Sum of participant shares equals receipt total.
- Sum of net balances equals zero.
- Settlement rows are correct for one-payer MVP.
- Tests pass.

## T018 - Implement final expense validation and save orchestration contracts

Status: DONE
Priority: P0
Scope:

- `:domain:expense:api`
- `:domain:expense:impl`
- `:domain:sharing:api`

Goal:

Define and implement final validation before save.

Done when:

- Invalid draft cannot become finalized expense.
- Valid draft can produce finalized expense payload.
- Tests pass.

---

# Milestone 4 - Backend Worker and D1

## T050 - Create Cloudflare Worker baseline

Status: DONE
Priority: P0
Scope:

- `backend/`
- `backend/AGENTS.md`
- `API_CONTRACT.md`

Goal:

Create or verify Worker project with health route and environment bindings.

Done when:

- `GET /health` returns `{ "ok": true }`.
- Worker runs locally.

## T051 - Add D1 migration

Status: DONE
Priority: P0
Scope:

- backend migrations

Goal:

Create MVP `expenses` table.

Schema is defined in `API_CONTRACT.md` and `backend/AGENTS.md`.

Done when:

- Migration runs.
- D1 binding is configured.

## T052 - Implement save finalized expense endpoint

Status: DONE
Priority: P0
Scope:

- backend only
- `API_CONTRACT.md`

Endpoint:

```http
POST /v1/expenses
```

Done when:

- Valid payload is stored in D1.
- Server generates expense ID and unique share ID.
- Response includes `expenseId`, `shareId`, and `shareUrl`.

## T053 - Implement fetch public expense endpoint

Status: DONE
Priority: P0
Scope:

- backend only

Endpoint:

```http
GET /v1/expenses/:shareId
```

Done when:

- Existing share ID returns saved payload.
- Missing share ID returns safe 404.
- No auth required.

## T054 - Implement receipt parse endpoint

Status: DONE
Priority: P0
Scope:

- backend only

Endpoint:

```http
POST /v1/receipts/parse
```

Requirements:

- Accept base64 image payload.
- Call OpenAI from Worker only.
- Return strict receipt JSON matching `API_CONTRACT.md`.
- Return safe errors.
- Do not log image payloads.

Done when:

- A real receipt image can be parsed.
- Invalid parse returns retry/manual fallback-friendly error.

## T061 - Implement guest web page

Status: DONE
Priority: P0
Scope:

- backend only
- `docs/design/stitch/guest_view_web/`
- `docs/design/STITCH_REFERENCE.md`

Endpoint:

```http
GET /e/:shareId
```

Goal:

Render public read-only expense page.

Done when:

- Mobile web page follows guest view reference.
- Shows settlement summary and breakdown.
- No login, editing, or payment UI.

## T062 - Implement guest web error states

Status: DONE
Priority: P1
Scope:

- backend only
- `docs/design/stitch/validation_states/`

Goal:

Render safe guest web error pages.

Done when:

- Invalid/missing link does not expose stack traces.
- Page remains visually consistent.

---

# Milestone 5 - Android data layer

## T060 - Implement network core

Status: DONE
Priority: P0
Scope:

- `:core:network:api`
- `:core:network:impl`

Goal:

Provide Worker API client foundation.

Done when:

- Base URL is configurable.
- JSON serialization works.
- Network errors are mapped safely.

## T061A - Implement receipt repository and DTO mapper

Status: DONE
Priority: P0
Scope:

- `:data:receipt:api`
- `:data:receipt:impl`
- `:domain:receipt:api`

Goal:

Map `/v1/receipts/parse` response into domain `Receipt`.

Done when:

- Backend JSON maps to domain.
- Unknown fee type maps safely to `OTHER`.
- Invalid values produce controlled errors.

## T062A - Implement expense save repository

Status: DONE
Priority: P0
Scope:

- `:data:expense:api`
- `:data:expense:impl`
- `:data:sharing:api`
- `:data:sharing:impl`

Goal:

Save finalized expenses through Worker.

Done when:

- App can call `POST /v1/expenses`.
- Share link response maps to domain.

## T063 - Implement local draft persistence

Status: DONE
Priority: P0
Scope:

- `:core:datastore:*`
- `:data:expense:*`

Goal:

Persist expense draft as serialized JSON for MVP.

Done when:

- Draft survives navigation and reasonable process recreation.
- Draft clears only after successful save or explicit reset.

## T064 - Implement saved participant persistence

Status: DONE
Priority: P0
Scope:

- `:data:participant:*`
- `:domain:participant:*`

Goal:

Persist reusable participant names locally.

Done when:

- Added names appear in future expense.
- Names can be deleted.
- Duplicates are avoided.

---

# Milestone 6 - Manual app flow first

## T070 - Implement New Expense screen

Status: DONE
Priority: P0
Scope:

- `:feature:expense-flow:api`
- `:feature:expense-flow:impl`
- `docs/design/stitch/new_expense/`

Goal:

Implement app entry screen.

Done when:

- Screen matches Stitch reference.
- `Scan receipt` and `Enter manually` actions route correctly.
- No history/groups/AI text UI is added.

## T072 - Implement Manual Receipt Entry screen

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/manual_entry/`

Goal:

Allow complete manual receipt creation.

Done when:

- User can enter merchant, date, currency, items, tax, tip, and total.
- Valid receipt saves into draft.
- Continue routes to Choose People.

## T073 - Implement Receipt Review screen

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/receipt_review/`

Goal:

Allow parsed receipt correction and editing.

Done when:

- User can edit receipt data.
- User can add/delete/edit items and fees.
- Validation errors are inline.

## T080 - Implement Choose People screen

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/choose_people/`

Goal:

Add participants and choose payer.

Done when:

- User can add participants by name.
- Color avatars appear.
- Payer can be selected from participants.
- Continue blocked until valid.

## T081 - Implement saved participant names UI

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `:data:participant:*` only if needed
- `docs/design/stitch/choose_people/`

Goal:

Show saved participant suggestions and deletion.

Done when:

- Saved names appear as suggestions.
- Tapping suggestion adds participant to current expense.
- Delete removes saved name.

## T090 - Implement Assign Items screen skeleton

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/assign_items/`

Goal:

Implement participant selector, receipt item list, and assignment progress.

Done when:

- User can select participant.
- Receipt items are visible with assignment states.
- Continue is disabled until all assigned.

## T091 - Implement full item assignment UI

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- domain use cases only through existing contracts

Goal:

Support simple full item assignment by tapping selected person then item.

Done when:

- Quantity 1 item can be assigned fully to selected participant.
- Assignment progress updates.

## T092 - Implement quantity assignment UI

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/assign_items/`

Goal:

Support unit-based assignment for quantity > 1 items.

Done when:

- User can assign multiple units to one person.
- User can assign units to multiple people.
- Remaining quantity is visible.

## T093 - Implement Item Split bottom sheet

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/item_split/`

Goal:

Implement advanced item split editing bottom sheet.

Done when:

- Units mode works.
- Shared/custom/percentage modes have UI structure.
- Invalid input shows validation.

## T094 - Implement shared/custom/percentage split UI

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/item_split/`

Goal:

Complete advanced item split modes.

Done when:

- Shared equal split works.
- Custom amount split validates exact amount.
- Percentage split validates 10000 basis points.

## T100 - Implement Fees Allocation screen

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/fees_allocation/`

Goal:

Allocate tax/tip/fees.

Done when:

- Equal allocation is default.
- Proportional and custom modes work.
- Continue blocked when fee allocation invalid.

## T101 - Implement Review Expense screen

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/review_expense/`

Goal:

Show final settlement summary before saving.

Done when:

- User sees who owes whom.
- Save is blocked if validation fails.
- Screen matches Stitch reference.

## T102 - Implement calculation details UI

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/review_expense/`

Goal:

Add expandable transparent calculation details.

Done when:

- Shows item subtotal, fees, total share, amount paid, net balance per participant.

## T103 - Implement Saved and Share screen

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/expense_saved/`

Goal:

Confirm save and show share link.

Done when:

- Success state appears after save.
- Share URL is shown.
- Add another expense clears draft and restarts flow.

## T104 - Implement Android share sheet

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`

Goal:

Share generated URL through native Android share sheet.

Done when:

- User can share generated link.

---

# Milestone 7 - Receipt scanner integration

## T110 - Add CameraX capture

Status: TODO
Priority: P0
Scope:

- `:core:camera:api`
- `:core:camera:impl`

Goal:

Provide camera capture abstraction.

Done when:

- Feature can request a receipt image through camera API.

## T111 - Add Android Photo Picker gallery import

Status: TODO
Priority: P0
Scope:

- `:core:camera:api`
- `:core:camera:impl`

Goal:

Provide gallery import abstraction.

Done when:

- Feature can request a receipt image from gallery.

## T071 - Implement Receipt Scan screen

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `docs/design/stitch/receipt_scan/`

Goal:

Connect scan/gallery image selection to receipt parsing.

Done when:

- User can capture or select image.
- Loading state is shown.
- Success routes to Receipt Review.
- Error offers retry and manual fallback.

## T112 - Integrate OpenAI receipt parsing through Worker

Status: TODO
Priority: P0
Scope:

- `:data:receipt:*`
- `:domain:receipt:*`
- `:feature:expense-flow:impl`
- backend endpoint if needed

Goal:

Connect real parser to scan flow.

Done when:

- Prepared real receipt image parses successfully.
- Parsed result is editable in Receipt Review.
- API key is not in Android.

---

# Milestone 8 - Demo hardening

## T120 - Implement validation and error states

Status: TODO
Priority: P0
Scope:

- UI screens
- `docs/design/stitch/validation_states/`

Goal:

Add polished validation/error/loading states across the MVP.

Done when:

- Receipt parse error works.
- Save retry works.
- Unassigned item warning works.
- Invalid custom split warning works.
- No internet error works where practical.

## T121 - Domain regression test suite

Status: TODO
Priority: P0
Scope:

- `:domain:*:impl`

Goal:

Lock down calculation behavior before pitch.

Done when:

- All P0 calculation tests pass.

## T122 - End-to-end manual flow smoke test

Status: TODO
Priority: P0
Scope:

- Android app
- backend save/fetch/share

Goal:

Manual fallback demo works without receipt scan.

Done when:

- New Expense → Manual Entry → People → Assignment → Fees → Review → Save → Share link works.

## T123 - End-to-end receipt scan smoke test

Status: TODO
Priority: P0
Scope:

- Android app
- backend parse/save/fetch/share

Goal:

Real receipt scan demo works.

Done when:

- Prepared receipt image parses.
- Full flow completes.
- Guest link opens.

## T124 - Final UI polish pass

Status: TODO
Priority: P0
Scope:

- `:feature:expense-flow:impl`
- `:core:designsystem:*`
- all Stitch folders

Goal:

Align implemented screens with Stitch references.

Done when:

- Screens are visually cohesive.
- CTAs, cards, avatars, rows, and bottom sheets match the intended premium style.
- No out-of-scope UI appears.

---

# Review task

## T900 - Architecture and scope review

Status: TODO
Priority: P0
Scope:

- whole diff

Goal:

Review compliance before merging or moving to the next milestone.

Checklist:

- No feature impl depends on data impl.
- No domain module imports Android, Compose, DTOs, or database entities.
- No money logic uses Float or Double.
- No out-of-scope features were added.
- UI follows design system and Stitch references.
- Relevant tests/compile commands were run.

Done when:

- Review findings are resolved or explicitly accepted.
