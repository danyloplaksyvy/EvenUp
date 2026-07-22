# EvenUp Stitch Design Reference

## Purpose

This directory contains the generated Stitch with Google design output for EvenUp.

Use these files as persistent visual references for Codex and implementation work. They are not production source code.

## Source of truth order

For product behavior and scope:

1. `MVP_SCOPE.md`
2. `TASKS.md`
3. `ARCHITECTURE.md`
4. `CALCULATIONS.md`
5. `API_CONTRACT.md`

For UI implementation:

1. `DESIGN_SYSTEM.md`
2. This file
3. Relevant `docs/design/stitch/<screen>/screen.png`
4. Relevant `docs/design/stitch/<screen>/code.html`
5. Original references under `docs/design/original_refs/`

If Stitch output conflicts with MVP scope, follow `MVP_SCOPE.md`.
If Stitch output conflicts with architecture, follow `ARCHITECTURE.md`.
If Stitch HTML conflicts with native Android best practices, implement native Compose and ignore the web-specific structure.

## Important implementation rule

Do not copy Stitch HTML or Tailwind-style structure directly into Android production code.

Translate the design into native Jetpack Compose using:

- `:core:designsystem:api`
- `:core:designsystem:impl`
- `:feature:expense-flow:impl`

Feature screens should depend on design system APIs/components, not on hardcoded screen-local styling.

## Original design references

These files are included for context:

```text
docs/design/original_refs/functionality_flow_reference.png
docs/design/original_refs/premium_ui_reference.png
```

Use them as secondary references only. The generated Stitch screens are the primary screen-by-screen visual reference.

## Stitch export map

### New Expense

Files:

```text
docs/design/stitch/new_expense/screen.png
docs/design/stitch/new_expense/code.html
```

Implements:

- App entry point
- Multiline AI expense composer as the primary action
- White outlined composer with footer utilities and a solid-black circular send action
- Text/voice, processing, clarification, offline, retry, and defaults states
- Root-level blocking processing overlay; no inline loading or extracted-details card
- Receipt scan and manual-entry fallback actions
- No example chips or privacy card

Related tasks:

- `T070 - Implement New Expense screen`
- `T069 - Implement AI session, connectivity, and speech data`

The Stitch image remains a hierarchy/spacing reference. Where it shows a static receipt-first card, follow the approved AI composer behavior in `DESIGN_SYSTEM.md` and `TASK_AI_EXPENSE_INPUT_CLIENT.md`.

### Receipt Scan

Files:

```text
docs/design/stitch/receipt_scan/screen.png
docs/design/stitch/receipt_scan/code.html
```

Implements:

- Camera/gallery scan entry
- Loading state
- Retry/manual fallback behavior

Known issue:

- The exported `receipt_scan/screen.png` may be invalid and may contain `FIFE Image failed to fetch`. If the PNG is unusable, use `receipt_scan/code.html`, `DESIGN_SYSTEM.md`, and original references to implement the screen.

Related tasks:

- `T071 - Implement Receipt Scan screen`
- `T110 - Add CameraX capture`
- `T111 - Add Android Photo Picker gallery import`

### Manual Entry

Files:

```text
docs/design/stitch/manual_entry/screen.png
docs/design/stitch/manual_entry/code.html
```

Implements:

- Manual fallback receipt creation
- Merchant/date/currency fields
- Item rows
- Tax/tip/total fields

Related tasks:

- `T072 - Implement Manual Receipt Entry screen`

### Receipt Review

Files:

```text
docs/design/stitch/receipt_review/screen.png
docs/design/stitch/receipt_review/code.html
```

Implements:

- Parsed receipt review
- Editable merchant/date/currency
- Editable items and fees
- Continue into people flow

Related tasks:

- `T073 - Implement Receipt Review screen`

### Choose People

Files:

```text
docs/design/stitch/choose_people/screen.png
docs/design/stitch/choose_people/code.html
```

Implements:

- Manual participant creation
- Color avatars
- Saved participant suggestions
- Saved participant deletion
- Payer selection

Related tasks:

- `T080 - Implement Choose People screen`
- `T081 - Implement saved participant names`

### Assign Items

Files:

```text
docs/design/stitch/assign_items/screen.png
docs/design/stitch/assign_items/code.html
```

Implements:

- Tap person, then tap items
- Assignment progress
- Item row states
- Unassigned/partial/assigned/shared states

Related tasks:

- `T090 - Implement Assign Items screen skeleton`
- `T091 - Implement full item assignment UI`
- `T092 - Implement quantity assignment UI`

### Item Split Bottom Sheet

Files:

```text
docs/design/stitch/item_split/screen.png
docs/design/stitch/item_split/code.html
```

Implements:

- Units mode
- Shared mode
- Custom amount mode
- Percentage mode
- Validation and save behavior

Related tasks:

- `T093 - Implement Item Split bottom sheet`
- `T094 - Implement shared/custom/percentage split UI`

### Fees Allocation

Files:

```text
docs/design/stitch/fees_allocation/screen.png
docs/design/stitch/fees_allocation/code.html
```

Implements:

- Tax allocation
- Tip allocation
- Equal/proportional/custom mode selection
- Participant fee preview

Related tasks:

- `T100 - Implement Fees Allocation screen`

### Review Expense

Files:

```text
docs/design/stitch/review_expense/screen.png
docs/design/stitch/review_expense/code.html
```

Implements:

- Settlement summary
- Payer and total
- Expandable calculation details
- AI original description and review flags
- Section-level user-facing review notices only for actionable ambiguity; silent defaults are not warnings
- Editable facts, people, item/base assignments, fees, and discounts
- Full-screen details hub with typed Apply-only bottom sheets and a sticky contextual action
- Total-only overall split state
- Save CTA

Related tasks:

- `T101 - Implement Review Expense screen`
- `T102 - Implement calculation details UI`
- `T074 - Implement AI extracted-details editor and direct Review return`

### Expense Saved / Share

Files:

```text
docs/design/stitch/expense_saved/screen.png
docs/design/stitch/expense_saved/code.html
```

Implements:

- Save success state
- Share link card
- Share link CTA
- Copy link CTA
- Add another expense CTA

Related tasks:

- `T103 - Implement Saved and Share screen`
- `T104 - Implement Android share sheet`

### Public Guest View

Files:

```text
docs/design/stitch/guest_view_web/screen.png
docs/design/stitch/guest_view_web/code.html
```

Implements:

- Passcode-gated read-only expense page for new shares
- Legacy public read-only expense page for no-passcode rows
- Four-letter passcode gate
- Settlement summary
- Person-first participant breakdown
- Expandable per-participant item and fee details
- Total-only overall base shares and unpriced descriptive items
- Split/allocation method labels
- Discount credits and payer's own share
- Powered by EvenUp footer

Related tasks:

- `T061 - Implement guest web page`
- `T062 - Implement guest web error states`
- `T063 - Add guest passcode generation and save contract`
- `T064 - Add guest passcode verification, remembered access, and rate limiting`
- `T065 - Add person-level guest breakdown`

### Validation States

Files:

```text
docs/design/stitch/validation_states/screen.png
docs/design/stitch/validation_states/code.html
```

Implements:

- Empty participant state
- Unassigned item warning
- Invalid custom split warning
- Receipt parse error
- Save error with retry
- No internet error
- Loading while saving

Related tasks:

- Applies to all UI tasks
- `T120 - Demo hardening and error states`

### Monochrome Utility Design Notes

Files:

```text
docs/design/stitch/monochrome_utility/DESIGN.md
```

Use this as additional design context only. If it conflicts with `DESIGN_SYSTEM.md`, follow `DESIGN_SYSTEM.md`.

## Screen implementation prompt pattern

When asking Codex to implement a screen, use this shape:

```md
Read AGENTS.md, android/AGENTS.md, DESIGN_SYSTEM.md, and docs/design/STITCH_REFERENCE.md.

Task:
Implement [screen name].

Use these design references:
- docs/design/stitch/[folder]/screen.png
- docs/design/stitch/[folder]/code.html

Constraints:
- Do not copy HTML directly.
- Implement native Jetpack Compose.
- Use :core:designsystem components and tokens.
- Do not hardcode colors inside feature screens.
- Do not modify domain/data/backend unless explicitly required.

Done when:
- Screen matches the reference visually and behaviorally.
- Relevant compile command passes.
```
