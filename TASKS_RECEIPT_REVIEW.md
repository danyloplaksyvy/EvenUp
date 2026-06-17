# TASKS_RECEIPT_REVIEW.md

## Task: EVU-UI-001 — Convert receipt review from always-editable form to review-first layout

### Goal:
Create a receipt review screen where scanned receipt data is easy to verify at a glance, with editing available only after tapping a row or field.

### Context:
The current receipt review screen displays every value as a large editable input field. This makes the screen feel like a manual data-entry form instead of an AI-assisted receipt verification flow.

### Scope:
You may edit:
- Receipt review UI composables
- Receipt review presentation state
- Receipt review event handling
- Design system list/card components if needed

Do not edit:
- Receipt OCR/scanning implementation
- Receipt parser implementation
- Data persistence implementation
- Networking modules
- Database schema unless strictly required by existing state shape

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Replace default editable fields with read-only review rows.
- Merchant, date, and currency should appear as compact receipt detail rows.
- Item rows should be compact and tappable.
- Fee/tax/tip rows should be compact and tappable.
- Editing should happen through an existing edit state, dialog, bottom sheet, or expandable row pattern.
- Preserve all existing business logic and data validation.
- Preserve accessibility semantics for editable rows.
- Display money through the existing money formatting abstraction or introduce one in the proper module if missing.
- Use integer minor units, BigDecimal, or existing Money value object for monetary values.
- Do not introduce Float or Double for prices, totals, tax, tip, fees, or calculations.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- Receipt details are displayed as compact review rows by default.
- Item list no longer renders every item as a large always-visible form.
- User can still edit merchant, date, currency, item name, quantity, item amount, and fees.
- Existing receipt review tests pass.
- No feature module depends directly on a data implementation module.

---

## Task: EVU-UI-002 — Redesign receipt items into compact scan-friendly rows

### Goal:
Make the items section dense, readable, and optimized for reviewing 10+ receipt items without excessive scrolling.

### Context:
The current item layout consumes too much vertical space. Each item is rendered as multiple large fields, so only one or two items are visible at a time.

### Relevant files:
- `feature/receipt-review/**`
- `core/designsystem/**`
- `domain/receipt/**`

### Scope:
You may edit:
- Receipt item list composables
- Receipt item row composables
- Receipt item edit interaction
- UI state mapping for item display

Do not edit:
- Receipt OCR implementation
- Receipt parser implementation
- Data repository implementations
- Domain calculation rules unless a bug is found and documented

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Render each item as a compact row.
- Each row must show:
  - Item name
  - Quantity
  - Unit price or quantity multiplier when available
  - Line total
- Use tap-to-edit behavior instead of always-visible text fields.
- Provide a clear `+ Add item` action after the item list.
- Keep delete behavior available from the edit state, swipe action, overflow menu, or expanded item state.
- Use consistent spacing and typography from the design system.
- Long item names must ellipsize or wrap gracefully without breaking row alignment.
- Quantity must support non-money numeric representation without using Float or Double for money.
- Line totals must use safe money representation.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- At least 5 receipt items are visible or partially visible on a typical phone viewport before scrolling.
- Item rows are readable and tappable.
- User can still add, edit, and delete items.
- Money formatting is consistent across item rows.
- No monetary calculations use Float or Double.

---

## Task: EVU-UI-003 — Replace vague scan status chip with actionable AI review status

### Goal:
Replace the unclear “LOOKS GOOD” chip with a status message that explains what the AI verified and what the user needs to check.

### Context:
The current green “LOOKS GOOD” label is ambiguous. It does not explain whether the total matched, OCR confidence is high, or the user has approved the receipt.

### Relevant files:
- `feature/receipt-review/**`
- `feature/receipt-review-api/**`
- `domain/receipt/**`
- `core/designsystem/**`

### Scope:
You may edit:
- Receipt review header composables
- Receipt review UI state
- Receipt confidence/status mapping
- Design system chip/badge component if needed

Do not edit:
- OCR provider implementation
- Receipt parser model contracts unless already exposed confidence fields require mapping
- Data impl modules

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Remove or replace the “LOOKS GOOD” label.
- Add an actionable receipt status message near the total.
- Supported states should include at minimum:
  - Total matched
  - Review needed
  - Missing or uncertain fields
  - Total mismatch, if this state exists in current domain/presentation model
- Example copy:
  - “AI found 10 items · Total matches”
  - “Check 2 uncertain items”
  - “Total does not match receipt”
- Status styling must be visual but not rely on color alone.
- Do not invent fake confidence data. Use existing confidence/status fields where available.
- If confidence data is unavailable, derive only from deterministic validation such as item subtotal + fees = total.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- The receipt header explains the verification state clearly.
- The old “LOOKS GOOD” chip is no longer shown.
- Status copy is based on real state, validation, or existing confidence fields.
- Accessibility label communicates the same status as the visual UI.

---

## Task: EVU-UI-004 — Fix sticky Continue button overlap and safe-area behavior

### Goal:
Ensure the sticky bottom Continue button never covers receipt content and behaves correctly with system navigation bars.

### Context:
The current sticky CTA overlaps the bottom content. Users can scroll fields behind the button, which makes the form feel broken and reduces trust.

### Relevant files:
- `feature/receipt-review/**`
- `core/designsystem/**`
- `app/**`

### Scope:
You may edit:
- Receipt review screen scaffold
- Bottom CTA container
- Scroll content padding
- Insets handling

Do not edit:
- Receipt review domain logic
- Receipt parser/scanner logic
- Navigation graph unless required for screen scaffold integration

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Add bottom content padding equal to bottom CTA height plus navigation/system inset plus extra spacing.
- Use Compose window inset APIs or existing design system inset helpers.
- Continue button must remain reachable.
- Last content row must be fully visible above the button when scrolled to the bottom.
- Avoid hardcoding device-specific dimensions.
- Preserve existing CTA behavior.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- No receipt content is hidden under the Continue button.
- The last field/action can scroll fully above the sticky footer.
- Layout works with gesture navigation and 3-button navigation.
- Layout works on small and large phone viewports.

---

## Task: EVU-UI-005 — Simplify receipt adjustments and summary sections

### Goal:
Make tax, tip, fees, subtotal, and total easier to understand while preventing inconsistent manual money edits.

### Context:
The current “Tax, tip, and fees” section is visually heavy and exposes subtotal and total as large editable fields. This can create inconsistent states where item totals, fees, subtotal, and final total do not reconcile.

### Relevant files:
- `feature/receipt-review/**`
- `domain/receipt/**`
- `core/designsystem/**`

### Scope:
You may edit:
- Adjustments UI
- Receipt summary UI
- Presentation validation messages
- Existing edit interaction for fees/tips/taxes

Do not edit:
- Receipt OCR implementation
- Data repository implementation
- Database entities
- Network DTOs

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Rename “Tax, tip, and fees” section to “Adjustments”.
- Render adjustments as compact rows.
- Replace “1 added” with clearer copy such as “1 fee” or “1 adjustment”.
- Replace centered “Add fee” with `+ Add adjustment`.
- Display subtotal, adjustments total, and final total as a compact summary.
- Subtotal should be derived from items when possible.
- Final total should be reconciled from subtotal plus adjustments unless the existing domain model explicitly supports receipt total override.
- If manual total editing remains supported, it must be clearly labeled as a receipt total override and must not silently corrupt item-level calculations.
- Delete action must be spatially tied to the adjustment being deleted.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- Adjustments section is compact and readable.
- Subtotal and total are not presented as ordinary always-editable fields.
- User can add, edit, and delete adjustments.
- Receipt total reconciliation remains correct.
- No monetary values use Float or Double.

---

## Task: EVU-UI-006 — Align receipt review visual language with Android/Material-based design system

### Goal:
Remove mixed platform styling and make the receipt review screen feel consistent with the Android app design system.

### Context:
The current screen visually mixes iOS-like status/header treatment, Android navigation, Material-ish floating labels, and custom pills. This weakens perceived product quality.

### Relevant files:
- `feature/receipt-review/**`
- `core/designsystem/**`
- `app/theme/**`

### Scope:
You may edit:
- Receipt review top app bar
- Section headers
- Cards
- Buttons
- Chips/badges
- Text field usage
- Spacing and typography tokens

Do not edit:
- Business logic
- Receipt parser/scanner logic
- Data impl modules
- Domain model unless required for UI state cleanup and approved by existing architecture

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Use app design system typography, spacing, shape, and color tokens.
- Avoid iOS-specific visual patterns unless they are already part of the app design system.
- Remove heavy floating-label inputs from default review mode.
- Use consistent section header styling.
- Use consistent destructive action styling for item/fee deletion.
- Ensure primary, secondary, and destructive actions are visually distinct.
- Use Material/Compose accessibility semantics where applicable.
- Preserve dark mode compatibility if the design system supports it.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- Receipt review screen uses consistent app design system components.
- Default state no longer looks like a generated form.
- Delete actions are visually and behaviorally consistent.
- Screen remains readable in light and dark mode if supported.

---

## Task: EVU-UI-007 — Add receipt review validation and mismatch feedback

### Goal:
Show clear validation feedback when receipt items, adjustments, subtotal, and total do not reconcile.

### Context:
Receipt review needs to build user trust. If item totals plus fees do not equal the final total, the UI should clearly explain the mismatch instead of allowing silent inconsistencies.

### Relevant files:
- `domain/receipt/**`
- `feature/receipt-review-api/**`
- `feature/receipt-review-impl/**`
- `core/designsystem/**`

### Scope:
You may edit:
- Receipt review domain validation use case, if it exists
- Receipt review presentation mapper
- Receipt review UI validation messages
- Tests for reconciliation logic

Do not edit:
- OCR implementation
- Network DTOs
- Database entities
- Data impl modules unless a mapping bug blocks validation

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Validate:
  - Sum of item line totals
  - Sum of adjustments
  - Expected final total
  - Parsed or user-confirmed receipt total
- Show a visible warning when totals do not match.
- Warning copy must explain the delta.
- Use exact money-safe calculations.
- Do not use Float or Double.
- Keep validation logic outside Compose UI.
- Compose UI should consume a presentation-ready validation state.
- Add or update unit tests for matched and mismatched total scenarios.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- Matching totals show a positive or neutral confirmation state.
- Mismatched totals show a clear warning with the difference amount.
- Validation logic is tested.
- Validation logic is not implemented directly inside Composables.
