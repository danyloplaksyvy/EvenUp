# TASKS_RECEIPT_REVIEW.md

## Receipt Review UI/UX Implementation Tasks

These tasks convert the receipt review flow into a review-first, AI-assisted experience with compact rows, clear reconciliation feedback, proper Material 3 sizing, safer money handling, and polished scroll behavior.

---

## Task: EVU-UI-001 — Convert receipt review from always-editable form to review-first layout

### Goal:
Create a receipt review screen where scanned receipt data is easy to verify at a glance, with editing available only after tapping a row or field.

### Context:
The current receipt review screen has improved from a form-heavy layout, but it still needs final refinement. The screen should communicate: “AI parsed this receipt; quickly confirm what matters.” The default state must remain optimized for review, not manual data entry.

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
- Keep the screen review-first by default.
- Merchant, date, and currency should appear as compact receipt detail rows or as a compact summary block.
- Item rows should be compact and tappable.
- Fee/tax/tip rows should be compact and tappable.
- Editing should happen through a bottom sheet, dialog, or expandable row pattern.
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
- Receipt details are displayed as compact review content by default.
- Item list no longer renders every item as a large always-visible form.
- User can still edit merchant, date, currency, item name, quantity, item amount, and adjustments.
- Existing receipt review tests pass.
- No feature module depends directly on a data implementation module.

---

## Task: EVU-UI-002 — Redesign receipt items into compact scan-friendly rows

### Goal:
Make the items section dense, readable, and optimized for reviewing 10+ receipt items without excessive scrolling.

### Context:
The new row-based item layout is directionally correct. It should be refined to make item rows obviously tappable, reduce excess vertical space, and make add/edit actions secondary rather than visually dominant.

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
- Add a subtle edit affordance for tappable rows, such as a chevron, trailing edit indicator, or consistent clickable row styling.
- Use tap-to-edit behavior instead of always-visible text fields.
- Change centered `+ Add item` into a secondary left-aligned action row unless the design system already defines a better secondary list action pattern.
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
- At least 5 receipt items are visible or partially visible on a typical phone viewport before scrolling, excluding the sticky footer area.
- Item rows are readable and obviously tappable.
- User can still add, edit, and delete items.
- `+ Add item` is visually secondary and does not compete with the primary Continue CTA.
- Money formatting is consistent across item rows.
- No monetary calculations use Float or Double.

---

## Task: EVU-UI-003 — Replace vague scan status chip with actionable AI review status

### Goal:
Show an actionable status message that explains what the AI verified and what the user needs to check.

### Context:
The new copy `AI found 10 items · Total matches` is much better than `LOOKS GOOD`. Keep this direction and make sure the message is state-driven, accessible, and not based on fake confidence data.

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
- Design system chip/badge/banner component if needed

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
- Keep the old `LOOKS GOOD` chip removed.
- Add an actionable receipt status message near the total.
- Supported states should include at minimum:
  - Total matched
  - Review needed
  - Missing or uncertain fields
  - Total mismatch, if this state exists in the current domain/presentation model
- Example copy:
  - `AI found 10 items · Total matches`
  - `Check 2 uncertain items`
  - `Total does not match receipt`
- Status styling must be visual but not rely on color alone.
- Do not invent fake confidence data.
- Use existing confidence/status fields where available.
- If confidence data is unavailable, derive only from deterministic validation such as item subtotal + adjustments = total.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- The receipt header explains the verification state clearly.
- Status copy is based on real state, validation, or existing confidence fields.
- Accessibility label communicates the same status as the visual UI.
- Mismatch, uncertain, and matched states have distinct copy.

---

## Task: EVU-UI-004 — Standardize top and bottom bars using Material 3 sizing

### Goal:
Make the receipt review top app bar and bottom action area use standard Material 3 sizing and behavior instead of custom oversized bars.

### Context:
The current screen has a heavy sticky bottom CTA and a large top header area. The app should use Material 3 top bar conventions and a cleaner bottom action area while preserving the custom brand feel through design system tokens.

### Relevant files:
- `feature/receipt-review/**`
- `core/designsystem/**`
- `app/theme/**`

### Scope:
You may edit:
- Receipt review screen scaffold
- Top app bar composable usage
- Bottom bar / bottom CTA container
- Design system wrappers around Material 3 bars
- Insets handling

Do not edit:
- Receipt review domain logic
- Receipt parser/scanner logic
- Data impl modules
- Navigation graph unless required for scaffold integration

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Use Material 3 top app bar height conventions through `TopAppBar`, `CenterAlignedTopAppBar`, or an existing design system wrapper.
- Use Material 3/scaffold-compatible bottom bar sizing instead of a custom oversized footer.
- The primary Continue button must remain reachable and visually primary.
- The bottom bar must respect navigation bar and gesture insets.
- Avoid hardcoded device-specific dimensions.
- Keep CTA content padding sufficient so receipt content is not covered.
- Use design system tokens for spacing, typography, shape, and color.
- Do not mix iOS-specific visual patterns into Android UI.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- Top app bar uses a Material 3-compatible height and layout.
- Bottom action area uses standard app/design-system sizing and respects system insets.
- Continue button remains prominent without visually dominating the screen.
- No receipt content is hidden under the bottom bar.
- Layout works with gesture navigation and 3-button navigation.

---

## Task: EVU-UI-005 — Hide top bar on scroll and keep a sticky back button

### Goal:
Improve vertical working space by hiding the full top bar while scrolling, while keeping navigation back always available.

### Context:
Receipt review is a long-scroll screen. The top bar consumes valuable vertical space, especially on item-heavy receipts. The full top bar should collapse/hide on scroll, but the user must always have a sticky back affordance.

### Relevant files:
- `feature/receipt-review/**`
- `core/designsystem/**`
- `app/theme/**`

### Scope:
You may edit:
- Receipt review scaffold
- Top app bar scroll behavior
- Sticky back button composable
- Scroll state integration
- Insets handling

Do not edit:
- Receipt parser/scanner logic
- Domain calculation logic
- Data impl modules
- Navigation graph unless strictly needed for back handling

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Hide or collapse the full top bar when the user scrolls down.
- Restore the full top bar when the user scrolls up or returns to the top, if using standard Material 3 scroll behavior.
- Keep a sticky back button visible and usable while the full top bar is hidden.
- The sticky back button must respect status bar and display cutout insets.
- The sticky back button must have at least 48.dp touch target size.
- The sticky back button must expose correct accessibility semantics.
- Avoid overlapping the sticky back button with receipt content.
- Use Material 3 nested scroll behavior where possible.
- Avoid custom scroll hacks if `TopAppBarDefaults` behavior can solve the requirement.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- Full top bar hides/collapses on downward scroll.
- Back button remains visible and tappable while the full top bar is hidden.
- Full top bar returns according to the selected Material 3 scroll behavior.
- Sticky back button works with status bar/cutout insets.
- Accessibility tests/manual checks confirm the back button has a meaningful label.

---

## Task: EVU-UI-006 — Fix sticky Continue button overlap and safe-area behavior

### Goal:
Ensure the sticky bottom Continue button never covers receipt content and behaves correctly with system navigation bars.

### Context:
The current sticky CTA still visually cuts off content in some screenshots. Users must be able to scroll the final row, final summary, and final action fully above the button.

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
- Verify behavior with the top bar hidden and visible.

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
- Behavior remains correct when the top bar collapses/hides.

---

## Task: EVU-UI-007 — Simplify receipt adjustments and summary sections

### Goal:
Make tax, tip, fees, subtotal, and total easier to understand while preventing inconsistent manual money edits.

### Context:
The `Adjustments` and `Summary` direction is correct. Remaining issues are copy and hierarchy. `Receipt total override` is technically accurate but too developer-oriented for normal users.

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
- Keep section title as `Adjustments`.
- Render adjustments as compact rows.
- Use count copy such as `1 adjustment`, `2 adjustments`, or `No adjustments`.
- Change centered `+ Add adjustment` into a secondary left-aligned action row unless the design system defines another clear secondary action pattern.
- Display subtotal, adjustments total, and final total as a compact summary.
- Replace user-facing copy `Receipt total override` with friendlier copy, such as:
  - `Receipt total`
  - `Final receipt total`
  - Helper text: `Tap to edit scanned total`
- Use `override` only in internal state/model naming or in advanced/debug contexts.
- Subtotal should be derived from items when possible.
- Final total should be reconciled from subtotal plus adjustments unless the existing domain model explicitly supports receipt total override.
- If manual total editing remains supported, it must be clearly labeled as editing the scanned receipt total and must not silently corrupt item-level calculations.
- Delete action must be spatially tied to the adjustment being deleted.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- Adjustments section is compact and readable.
- Summary copy is user-facing and does not expose technical wording like `override` by default.
- Subtotal and total are not presented as ordinary always-editable fields.
- User can add, edit, and delete adjustments.
- Receipt total reconciliation remains correct.
- No monetary values use Float or Double.

---

## Task: EVU-UI-008 — Add quantity stepper controls in item edit sheet

### Goal:
Allow users to increase or decrease item quantity with `-` and `+` controls in the edit item bottom sheet.

### Context:
Quantity editing is a frequent receipt correction. A stepper is faster and less error-prone than manually editing a numeric field, especially for common corrections like changing quantity from 1 to 2.

### Relevant files:
- `feature/receipt-review/**`
- `feature/receipt-review-api/**`
- `feature/receipt-review-impl/**`
- `core/designsystem/**`
- `domain/receipt/**`

### Scope:
You may edit:
- Edit item bottom sheet composables
- Quantity input composable
- Receipt review UI events
- Presentation state for quantity editing
- Domain validation for quantity if needed
- Design system stepper component if it should be reused

Do not edit:
- Receipt OCR implementation
- Receipt parser implementation
- Data impl modules
- Database entities unless quantity type mismatch blocks implementation

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Add `-` and `+` controls next to quantity in the edit item bottom sheet.
- Quantity decrement must not allow invalid quantity values.
- Minimum quantity should follow the existing domain rule. If no rule exists, use minimum quantity `1` for MVP.
- Disable the `-` control at the minimum quantity.
- `+` should increment by 1 for integer quantities.
- If the product already supports fractional quantity or weighted items, preserve that capability and do not break it. In that case, use the existing quantity model and define safe step behavior explicitly.
- Quantity changes must immediately update the presentation state.
- Recalculate displayed unit price / line total consistently based on the existing domain model.
- Do not use Float or Double for money calculations.
- Stepper buttons must have at least 48.dp touch targets.
- Stepper buttons must have accessible labels, such as `Decrease quantity` and `Increase quantity`.
- Manual quantity input may remain available if already implemented, but stepper controls must be the primary interaction.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- Edit item sheet shows quantity with `-` and `+` controls.
- Quantity can be increased and decreased safely.
- Quantity cannot go below the domain minimum.
- Item total display remains correct after quantity changes.
- Stepper controls are accessible and have valid touch target size.
- No monetary calculations use Float or Double.

---

## Task: EVU-UI-009 — Polish item edit bottom sheet layout and actions

### Goal:
Make the edit item bottom sheet feel like a focused editing surface instead of a small version of the old form-heavy UI.

### Context:
The bottom sheet pattern is correct, but the current field styling still feels heavy. Delete action placement, labels, and primary action sizing need refinement.

### Relevant files:
- `feature/receipt-review/**`
- `core/designsystem/**`

### Scope:
You may edit:
- Edit item bottom sheet composables
- Bottom sheet action layout
- Text field usage/styling
- Delete action placement
- Design system form components if needed

Do not edit:
- Receipt parser/scanner logic
- Data impl modules
- Domain money calculation logic unless a bug is found and documented

### Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

### Implementation requirements:
- Use clear labels above fields where possible instead of heavy floating-label styling.
- Keep the sheet title concise, e.g. `Edit item`.
- Provide an explicit close/cancel affordance if the current modal behavior does not make dismissal obvious.
- Keep `Done` or `Save changes` as the primary action.
- Ensure primary sheet action uses standard Material 3/design-system button height.
- Make delete action clearly destructive and spatially separated from money input.
- Suggested structure:
  - Item name
  - Quantity stepper
  - Line total
  - Delete item
  - Save/Done
- Respect IME insets when text fields are focused.
- Preserve keyboard navigation and accessibility semantics.

### Validation:
Run:
- `./gradlew test`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`

### Done when:
- Edit item sheet is visually lighter and easier to scan.
- Quantity uses stepper controls.
- Delete action is clearly associated with deleting the item, not the amount.
- Primary action uses standard sizing and respects safe area/IME insets.
- User can dismiss the sheet without saving if that behavior exists in the product flow.

---

## Task: EVU-UI-010 — Align receipt review visual language with Android/Material-based design system

### Goal:
Remove mixed platform styling and make the receipt review screen feel consistent with the Android app design system.

### Context:
The screen should feel Android-native and product-quality. It should use the app design system on top of Material 3 patterns instead of mixing iOS-like, custom, and form-heavy visual conventions.

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
- Chips/banners
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
- Use Material 3 top/bottom bar conventions through design system wrappers where available.
- Avoid iOS-specific visual patterns unless they are already part of the app design system.
- Remove heavy floating-label inputs from default review mode.
- Use consistent section header styling.
- Use consistent destructive action styling for item/adjustment deletion.
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
- Top and bottom bars use standard Material 3-compatible sizing.
- Delete actions are visually and behaviorally consistent.
- Screen remains readable in light and dark mode if supported.

---

## Task: EVU-UI-011 — Add receipt review validation and mismatch feedback

### Goal:
Show clear validation feedback when receipt items, adjustments, subtotal, and total do not reconcile.

### Context:
Receipt review needs to build user trust. If item totals plus adjustments do not equal the final total, the UI should clearly explain the mismatch instead of allowing silent inconsistencies.

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

---

## Suggested implementation order

1. `EVU-UI-004` — Standardize top and bottom bars using Material 3 sizing
2. `EVU-UI-005` — Hide top bar on scroll and keep a sticky back button
3. `EVU-UI-006` — Fix sticky Continue button overlap and safe-area behavior
4. `EVU-UI-002` — Redesign receipt items into compact scan-friendly rows
5. `EVU-UI-008` — Add quantity stepper controls in item edit sheet
6. `EVU-UI-009` — Polish item edit bottom sheet layout and actions
7. `EVU-UI-007` — Simplify receipt adjustments and summary sections
8. `EVU-UI-003` — Replace vague scan status chip with actionable AI review status
9. `EVU-UI-011` — Add receipt review validation and mismatch feedback
10. `EVU-UI-010` — Align receipt review visual language with Android/Material-based design system
11. `EVU-UI-001` — Final review-first layout cleanup and regression check
