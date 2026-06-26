# EvenUp MVP Tasks

## Working rules

- Execute one task at a time.
- Do not continue to the next task unless explicitly asked.
- Prefer small, reviewable changes.
- Run the most specific relevant validation command before claiming a task is done.
- Calculation-related changes require domain unit tests.
- Guest-view work must follow `API_CONTRACT.md`, `DESIGN_SYSTEM.md`, and `docs/design/STITCH_REFERENCE.md`.

## Foundation

### T000 - Restore instruction and planning files

Goal: Keep repo planning docs present and internally consistent.

Done when:

- `AGENTS.md`, `MVP_SCOPE.md`, `ARCHITECTURE.md`, `CALCULATIONS.md`, `API_CONTRACT.md`, `DESIGN_SYSTEM.md`, `TASKS.md`, and `docs/design/STITCH_REFERENCE.md` agree on MVP behavior.

### T001 - Verify Android project baseline

Goal: Confirm the Android project builds with the expected Gradle structure.

Validation:

```bash
./gradlew assembleDebug
```

### T002 - Verify module boundaries

Goal: Confirm API/implementation module boundaries match `ARCHITECTURE.md`.

Done when:

- Feature modules do not depend on data implementation modules.
- Domain modules remain free of Android UI, Compose, DTO, and persistence dependencies.

## Domain and Calculation

### T010 - Implement money, quantity, and percentage value objects

Goal: Keep money in integer minor units and percentages in basis points.

Validation:

```bash
./gradlew :domain:receipt:api:test :domain:expense:api:test
```

### T011 - Implement item assignment validation

Goal: Validate full, unit, shared equal, custom amount, and percentage item assignments.

Validation:

```bash
./gradlew :domain:expense:impl:test
```

### T012 - Implement fee allocation

Goal: Allocate fees equally, proportionally, or by custom amount with deterministic rounding.

Validation:

```bash
./gradlew :domain:expense:impl:test
```

### T013 - Implement final settlement calculation

Goal: Produce participant summaries and settlement rows from receipt, participants, payer, assignments, and fee allocations.

Validation:

```bash
./gradlew :domain:expense:impl:test
```

### T014 - Implement guest passcode domain rules

Goal: Generate and validate four-letter guest passcodes in `:domain:sharing`.

Requirements:

- Generate exactly four letters.
- Normalize user-visible values by trimming and uppercasing.
- Do not put backend hashing or storage rules in domain.

Validation:

```bash
./gradlew :domain:sharing:impl:test
```

## Backend

### T050 - Implement Worker health endpoint

Goal: `GET /health` returns `{ "ok": true }`.

Validation:

```bash
cd backend
npm test
```

### T051 - Implement D1 expense storage

Goal: Create or migrate the D1 schema for immutable finalized expenses.

Requirements:

- Store finalized payload JSON.
- Store generated share ID separately.
- Add nullable guest passcode hash and salt columns for new protected shares.
- Preserve legacy rows without passcode metadata.

Validation:

```bash
cd backend
npm test
```

### T052 - Implement receipt parse endpoint

Goal: `POST /v1/receipts/parse` calls OpenAI from the Worker and returns structured receipt data.

Validation:

```bash
cd backend
npm test
```

### T053 - Implement finalized expense save endpoint

Goal: `POST /v1/expenses` stores immutable expense payloads and returns share link metadata.

Requirements:

- Validate finalized payload shape.
- Validate `guestAccess.passcode` for new clients.
- Store only a salted passcode hash.
- Do not return plaintext passcode from the Worker.

Validation:

```bash
cd backend
npm test
```

### T054 - Implement finalized expense fetch endpoint

Goal: `GET /v1/expenses/:shareId` returns saved payloads by share ID.

Requirements:

- Protected rows require a remembered guest-access cookie or `X-EvenUp-Guest-Passcode`.
- Legacy rows without passcode metadata remain public.

Validation:

```bash
cd backend
npm test
```

### T061 - Implement base guest web page

Goal: `GET /e/:shareId` renders a mobile-first, read-only page using the guest Stitch reference.

Requirements:

- No payment UI.
- White/black premium style.
- Show expense title, merchant/date/total, payer, settlement summary, and participant overview.

Validation:

```bash
cd backend
npm test
```

### T062 - Implement guest web error states

Goal: Render safe missing-link, invalid-passcode, and locked-out states.

Requirements:

- Do not expose stack traces.
- Do not reveal backend internals.
- Keep the page visually consistent with the guest view.

Validation:

```bash
cd backend
npm test
```

### T063 - Add guest passcode save contract

Goal: Wire the save contract for Android-generated four-letter passcodes.

Requirements:

- Accept `guestAccess.passcode` in `POST /v1/expenses`.
- Hash and salt the passcode before storage.
- Keep no plaintext passcode in `payload_json`.
- Existing no-passcode rows remain readable as legacy public shares.

Validation:

```bash
cd backend
npm test
```

### T064 - Add guest passcode verification, remembered access, and rate limiting

Goal: Gate protected guest pages with `POST /e/:shareId/access`.

Requirements:

- Render a passcode form before showing protected expense details.
- Set a secure remembered-access cookie after successful verification.
- Rate-limit repeated failures, recommended threshold 5 failed attempts per 10 minutes.
- Track attempts by share ID and privacy-preserving client key.

Validation:

```bash
cd backend
npm test
```

### T065 - Add person-level guest breakdown

Goal: Make the guest page explain exactly what each participant pays for.

Requirements:

- Person list is the primary interaction.
- Expanding a participant shows item shares, split method labels, fee allocations, discounts, paid amount, total share, net balance, and settlement result.
- Use saved `itemAssignments`, `feeAllocations`, and `summary`; do not invent missing rows.
- Full names only.

Validation:

```bash
cd backend
npm test
```

## Android Data and Share Flow

### T067 - Send guest passcode during save

Goal: Generate a four-letter passcode before saving and include it in the save request.

Requirements:

- Keep generation in domain/sharing.
- Keep the generated passcode in saved/share UI state.
- Do not depend on backend echoing plaintext passcode.

Validation:

```bash
./gradlew :data:expense:impl:test :feature:expense-flow:impl:test
```

### T068 - Update Saved / Share screen for passcode

Goal: Show the link and generated passcode clearly after save.

Requirements:

- Link and passcode are visually separate.
- Share-sheet text includes both values.
- Keep the screen simple and read-only.

Validation:

```bash
./gradlew :feature:expense-flow:impl:compileDebugKotlin
```

## Android Screens

### T070 - Implement New Expense screen

Use:

- `docs/design/stitch/new_expense/screen.png`
- `docs/design/stitch/new_expense/code.html`

### T071 - Implement Receipt Scan screen

Use:

- `docs/design/stitch/receipt_scan/code.html`
- `DESIGN_SYSTEM.md`

### T072 - Implement Manual Receipt Entry screen

Use:

- `docs/design/stitch/manual_entry/screen.png`
- `docs/design/stitch/manual_entry/code.html`

### T073 - Implement Receipt Review screen

Use:

- `docs/design/stitch/receipt_review/screen.png`
- `docs/design/stitch/receipt_review/code.html`

### T080 - Implement Choose People screen

Use:

- `docs/design/stitch/choose_people/screen.png`
- `docs/design/stitch/choose_people/code.html`

### T081 - Implement saved participant names

Goal: Store reusable local participant names with DataStore.

### T090 - Implement Assign Items screen skeleton

Use:

- `docs/design/stitch/assign_items/screen.png`
- `docs/design/stitch/assign_items/code.html`

### T091 - Implement full item assignment UI

Goal: Support tap-person-then-tap-items assignment.

### T092 - Implement quantity assignment UI

Goal: Support quantity/unit-based item assignment.

### T093 - Implement Item Split bottom sheet

Use:

- `docs/design/stitch/item_split/screen.png`
- `docs/design/stitch/item_split/code.html`

### T094 - Implement shared/custom/percentage split UI

Goal: Support all MVP split modes with inline validation.

### T100 - Implement Fees Allocation screen

Use:

- `docs/design/stitch/fees_allocation/screen.png`
- `docs/design/stitch/fees_allocation/code.html`

### T101 - Implement Review Expense screen

Use:

- `docs/design/stitch/review_expense/screen.png`
- `docs/design/stitch/review_expense/code.html`

### T102 - Implement calculation details UI

Goal: Show expandable calculation details before save.

### T103 - Implement Saved and Share screen

Use:

- `docs/design/stitch/expense_saved/screen.png`
- `docs/design/stitch/expense_saved/code.html`

Also include the generated four-letter guest passcode.

### T104 - Implement Android share sheet

Goal: Share a message containing both the guest link and passcode as separate values.

### T110 - Add CameraX capture

Goal: Capture receipt images from camera.

### T111 - Add Android Photo Picker gallery import

Goal: Import receipt images from gallery.

### T120 - Demo hardening and error states

Use:

- `docs/design/stitch/validation_states/screen.png`
- `docs/design/stitch/validation_states/code.html`

Goal: Polish empty states, validation, network errors, save failures, and loading states for the primary demo flow.
