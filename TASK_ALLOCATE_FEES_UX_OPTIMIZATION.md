# EVU-UI-017 - Optimize Allocate Fees Flow

## Goal

Refactor the Allocate Fees screen into a confirmation-first step. The app should automatically allocate positive extra fees fairly, let the user quickly review the result, and reserve Custom mode for exceptions.

Default flow:

```text
Fees exist -> allocate proportionally -> user reviews -> Continue
No fees -> skip Allocate Fees
```

The screen should feel like:

```text
We found $27.25 in extra fees and split them fairly based on what each person had.
```

not like a manual accounting form.

---

## Context

Current weaknesses:

- Allocate Fees can appear even when fees are $0.00.
- Currency appears hardcoded as Euro.
- Header copy says "Split tax & tip", but fees can include service charge, tax, tip, or other adjustments.
- Fee summary uses inconsistent layouts: service charge/tip cards plus a full-width tax row.
- Equal is shown as the first/default mode, but Proportional is usually fairer.
- Total fees is shown before participant shares instead of after them.
- Custom mode shows too many repeated fields and becomes extremely vertical.
- Custom inputs can be over-allocated or under-allocated.
- Sticky Continue can hide the last custom fields.
- There is no quick action for a common real-life case: one person covers all fees.

---

## Relevant files

- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/allocatefees/**`
- `domain/expense/api/**`
- `domain/expense/impl/**`
- `domain/receipt/api/**`
- `core/designsystem/api/**`
- shared money/currency formatter, if present

Adjust paths if current names differ.

---

## Scope

You may edit:

- Allocate Fees UI
- Allocate Fees UI state
- Allocate Fees events
- Allocate Fees presenter/reducer logic
- Fee allocation calculation/mapping
- Custom allocation validation
- Routing around Allocate Fees
- Shared money/currency formatting usage
- Tests for allocation calculation, validation, and navigation behavior

Do not edit:

- OCR/parser implementation
- Receipt Review behavior, except shared formatter/component fixes
- Assign Items behavior, except navigation into Allocate Fees
- Choose People flow
- Backend/API contracts
- Settlement calculation logic unless a bug is found and documented

---

## Architecture constraints

- Keep API/implementation separation.
- Feature modules must not depend on data implementation modules.
- Domain modules must not depend on Compose, Android UI, DTOs, or database entities.
- Do not use `Float` or `Double` for money.
- Use `MoneyMinor`, integer minor units, `BigDecimal`, basis points, or existing safe money abstractions.
- Fee allocation calculation and validation must not live inside Composables.
- Compose UI should consume presentation-ready state.
- Use design-system colors, typography, spacing, and shapes.
- Do not hardcode raw colors or currency symbols in feature Composables.

---

# P0 - Required fixes

## P0.1 - Skip Allocate Fees when total fees are zero

### Requirements

If the sum of all positive fee/adjustment amounts is zero, skip this screen.

```text
Assign Items -> Review Expense
```

instead of:

```text
Assign Items -> Allocate Fees -> Review Expense
```

Rules:

- If all fees are absent, skip.
- If all fee amounts are `0.00`, skip.
- If at least one fee is positive, show Allocate Fees.
- Discounts/negative adjustments are out of scope unless already supported by the domain.

### Done when

- Allocate Fees is not shown for zero fees.
- Navigation proceeds to the next relevant step.
- Tests cover zero-fee routing.

---

## P0.2 - Use receipt currency everywhere

### Requirements

Use the receipt currency for:

- fee summary rows;
- allocation preview;
- custom input fields;
- custom validation messages;
- total fees row;
- quick-assign sheets;
- review/continue summaries.

Examples:

```text
USD -> $27.25
EUR -> EUR/€ formatted according to existing formatter
GBP -> £27.25
```

Use a shared money formatter. Do not hardcode `€`, `$`, or `£`.

### Done when

- Allocate Fees always displays the same currency as the receipt.
- No hardcoded Euro remains in Allocate Fees.
- Tests or assertions cover currency propagation.

---

## P0.3 - Make Proportional the default mode

### Requirements

Default mode should be:

```text
Proportional
```

Definition:

```text
Fees follow each person's assigned item subtotal.
```

Example:

```text
Kehn item subtotal = 40% of assigned items
Kehn fee share = 40% of total fees
```

Use deterministic rounding so participant fee shares sum exactly to total fees.

Fallback:

- If item subtotals are unavailable or all zero, fall back to Equal.
- Optionally show: `Using equal split because item subtotals are unavailable.`

### Done when

- Proportional is selected by default.
- Equal and Custom remain available.
- Proportional shares sum exactly to total fees.
- Tests cover proportional rounding.

---

## P0.4 - Refactor top copy

### Problem

`Split tax & tip` is incomplete because fees can include service charge, tip, tax, and other charges.

### Requirements

Use clearer product copy.

Recommended:

```text
Extra fees
Choose how $27.25 in fees should be shared.
```

or:

```text
Allocate fees
Service charge, tip, and tax are split automatically.
```

Use the actual total fee amount and receipt currency.

### Done when

- Copy no longer implies only tax/tip.
- Copy includes the total fees amount.
- Copy makes the screen feel like a review/confirmation step.

---

## P0.5 - Replace mixed fee cards with one compact fee list

### Requirements

Replace separate large cards with one compact list:

```text
Fees

Service charge       $7.25
Tip                 $10.00
Tax                 $10.00
```

Rules:

- All fee types use the same row pattern.
- Zero-value fees are hidden.
- Labels use consistent casing.
- The list is compact and does not consume excessive vertical space.

### Done when

- Fee summary uses one consistent component.
- Service charge, tip, tax, and other fees do not use mixed layouts.
- More screen space is available for allocation preview.

---

## P0.6 - Move Total fees to the end

### Requirements

Participant rows should come first; `Total fees` should appear last:

```text
Kehn                 $9.19
Storak               $5.91
Billy                $4.17
Danya                $7.98
-------------------------
Total fees          $27.25
```

Rules:

- Total fees is visually distinct.
- Sum of participant allocations equals total fees in valid modes.
- In invalid Custom mode, show remaining/over status near the total.

### Done when

- `Total fees` appears after participant allocations.
- The allocation summary reads like a normal financial summary.
- Tests cover valid allocation sums.

---

## P0.7 - Validate Custom mode per fee

### Requirements

Each fee must be independently valid:

```text
feeTotal = fee amount
allocated = sum(participant allocations for this fee)
remaining = feeTotal - allocated
```

Valid only when:

```text
remaining == 0
```

Validation messages:

```text
$2.00 remaining
Fully allocated
Over by $0.50
```

Rules:

- Continue disabled if any fee is under-allocated.
- Continue disabled if any fee is over-allocated.
- Show the exact fee that needs fixing.
- Negative participant allocations are not allowed.
- Use safe money types only.

### Done when

- Custom mode cannot continue with invalid allocation.
- Per-fee validation identifies the problem.
- Tests cover under-allocation, full allocation, and over-allocation.

---

## P0.8 - Lock or clamp Custom input when max value is allocated

### Requirements

When a fee is fully allocated:

```text
remaining == 0
```

the UI should prevent accidental extra allocation.

Acceptable MVP behavior:

- Disable empty fields or treat them as `0.00`.
- Clamp typed values to the maximum allowed amount when possible.
- If clamping would be surprising, show an immediate `Over by $X` error and disable Continue.
- Do not allow hidden over-allocation.

Recommended max for a participant field:

```text
maxAllowed = currentParticipantAmount + remainingForFee
```

### Done when

- Normal interaction cannot accidentally over-allocate a fee.
- Invalid pasted/manual values are surfaced immediately.
- Continue remains disabled until fixed.

---

## P0.9 - Fix bottom padding under sticky Continue

### Requirements

Scroll content must include enough bottom padding for:

- sticky footer height;
- navigation bar height;
- extra comfortable spacing.

No custom field or card should be hidden behind Continue.

### Done when

- Last custom editor row scrolls fully above the footer.
- Manual QA covers multiple fees and multiple participants.

---

# P1 - Important UX improvements

## P1.1 - Make Custom mode a compact overview

### Problem

Current Custom mode shows every fee/person input at once. This does not scale.

### Requirements

Replace all-at-once inputs with one compact overview:

```text
Custom allocation

Kehn                 $9.19
Service $2.45 · Tip $3.37 · Tax $3.37

Storak               $5.91
Service $1.57 · Tip $2.17 · Tax $2.17

Billy                $4.17
Service $1.11 · Tip $1.53 · Tax $1.53

Danya                $7.98
Service $2.12 · Tip $2.93 · Tax $2.93
-------------------------
Total fees          $27.25
```

Rules:

- Overview shows each participant's total fee share.
- Overview optionally shows compact per-fee breakdown.
- Exact editing happens in a focused editor, not all fields at once.

### Done when

- Custom mode is readable without dozens of visible fields.
- Users can still edit exact custom values.

---

## P1.2 - Use focused Custom editing

### Recommended option: edit one fee at a time

```text
Custom allocation

[Service charge] [Tip] [Tax]

Service charge     $7.25

Kehn               $2.45
Storak             $1.57
Billy              $1.11
Danya              $2.12

Fully allocated
```

This is preferred because validation is per-fee.

Alternative: edit one participant at a time.

```text
Edit Kehn's fees

Service charge     $2.45
Tip                $3.37
Tax                $3.37
-------------------------
Kehn total         $9.19
```

### Done when

- Custom editing is focused.
- User is not shown every fee/person field at once.
- Per-fee validity is visible.

---

## P1.3 - Add `Assign all fees to one person`

### Requirements

In Custom mode, add a quick action:

```text
Assign all fees to one person
```

Tap opens a participant picker:

```text
Who covers all fees?

Kehn
Storak
Billy
Danya
```

Selecting a participant sets:

```text
Selected participant = totalFees
All other participants = $0.00
```

Example result:

```text
Kehn covers all fees

Kehn                $27.25
Storak              $0.00
Billy               $0.00
Danya               $0.00
-------------------------
Total fees          $27.25
```

Rules:

- All fees are allocated to the selected participant.
- Every other participant gets zero for all fees.
- Custom mode becomes valid immediately.
- Continue is enabled if no other blockers exist.
- User can still manually edit afterward.
- Use receipt currency.

### Done when

- User can assign all fees to one participant in a few taps.
- Custom allocation remains valid after applying.
- Tests cover the allocation matrix.

---

## P1.4 - Add `Assign this fee to one person`

### Requirements

In focused fee editor, add:

```text
Assign this fee to one person
```

Example:

```text
Edit Tip
Tip total $10.00
[Assign this fee to one person]
```

Picker:

```text
Assign tip to:

Kehn
Storak
Billy
Danya
```

Result:

```text
Tip

Kehn        $10.00
Storak       $0.00
Billy        $0.00
Danya        $0.00

Fully allocated
```

Rules:

- Only the selected fee is changed.
- Other fee allocations remain unchanged.
- Participant totals recalculate.
- Continue enabled only if all fees are valid.

### Done when

- User can assign only service charge, only tip, or only tax to one participant.
- Per-fee quick assignment does not overwrite unrelated fee allocations.
- Tests cover one-fee assignment.

---

## P1.5 - Auto-adjust covering participant after manual edits where possible

### Requirements

If a participant was selected as the covering/balancing participant, editing another participant's amount should auto-adjust the covering participant for that fee.

Example:

```text
Tip total = $10.00
Kehn covers tip = $10.00

User enters Storak = $3.00

Kehn auto-updates to $7.00
Storak = $3.00
Tip remains fully allocated
```

Rules:

- Covering participant amount cannot go below `0.00`.
- If the edit exceeds available amount, clamp or show error.
- If no covering participant exists, use normal remaining/over validation.
- Optional helper: `Kehn covers the remaining amount.`

### Done when

- Common edits after quick assignment stay valid.
- No negative balancing amount can occur.
- Tests cover balancing behavior.

---

## P1.6 - Add `Reset to proportional`

### Requirements

Custom mode should include:

```text
Reset to proportional
```

If custom edits exist, confirm:

```text
Reset custom allocation?
Your custom fee changes will be replaced by proportional allocation.

Cancel
Reset
```

Recommended behavior:

- Switch mode back to Proportional.
- Recompute allocations from assigned item subtotals.

### Done when

- User can recover from custom edits.
- Custom values are not lost silently.

---

## P1.7 - Improve allocation mode descriptions

Use concise mode descriptions:

### Proportional

```text
Fees follow each person's assigned item subtotal.
```

Optional tooltip:

```text
People who had more items cover more of the fees.
```

### Equal

```text
Every participant receives the same share.
```

### Custom

```text
Set exact fee amounts manually.
```

### Done when

- Mode descriptions are short and understandable.
- Proportional feels like the smart recommended default.

---

## P1.8 - Reduce custom field size and repeated labels

### Requirements

Avoid repeated floating labels like `Amount` for every participant row.

Prefer compact rows:

```text
Kehn                  [ $ 2.45 ]
Storak                [ $ 1.57 ]
Billy                 [ $ 1.11 ]
Danya                 [ $ 2.12 ]
```

Rules:

- Use one section/column meaning instead of repeated labels.
- Keep row height close to standard list rows.
- Preserve minimum touch target.

### Done when

- Custom editing is much more compact.
- Repeated `Amount` labels are removed or minimized.

---

## P1.9 - Continue behavior and invalid messaging

### Requirements

Continue enabled only when all fee allocations are valid.

Invalid messages should be specific:

```text
Tip has $2.00 remaining.
Service charge is over by $0.50.
Tax is not allocated.
```

If Continue is disabled, show a visible reason:

```text
Allocate all fees to continue.
```

### Done when

- Continue does not proceed with invalid Custom allocation.
- User can tell exactly what needs fixing.

---

# P2 - Polish and quality improvements

## P2.1 - Add subtle animation when switching modes

Animate content changes when switching:

```text
Proportional <-> Equal <-> Custom
```

Avoid disorienting jumps.

## P2.2 - Add light haptic feedback for valid custom completion

Use haptic feedback when:

- one fee becomes fully allocated;
- all custom allocations become valid.

## P2.3 - Add accessibility descriptions

Examples:

```text
Proportional selected. Fees follow each person's assigned item subtotal.
Kehn, fee share $9.19.
Kehn service charge amount, $2.45.
Assign all fees to Kehn.
Tip has $2.00 remaining.
```

## P2.4 - Fix sticky back overlap

The floating back button should not obscure content while scrolling.

Fix globally or on this screen with content exclusion/padding/scrim.

## P2.5 - Add undo for large automatic changes where feasible

After actions like:

```text
Assigned all fees to Kehn
Reset to proportional
```

show:

```text
Undo
```

Undo restores previous custom allocations.

---

# Target UX

## No fees

```text
Assign Items -> Review Expense
```

Allocate Fees is skipped.

---

## Default state with fees

```text
Allocate fees

Extra fees
Choose how $27.25 in fees should be shared.
TABERNA DO MERCADO

Fees
Service charge       $7.25
Tip                 $10.00
Tax                 $10.00

How should fees be shared?
[Proportional] [Equal] [Custom]

Fees follow each person's assigned item subtotal.

Kehn                 $9.19
Storak               $5.91
Billy                $4.17
Danya                $7.98
-------------------------
Total fees          $27.25

Continue
```

---

## Equal mode

```text
[Proportional] [Equal] [Custom]

Every participant receives the same share.

Kehn                 $6.82
Storak               $6.81
Billy                $6.81
Danya                $6.81
-------------------------
Total fees          $27.25
```

---

## Custom overview

```text
Custom allocation
[Assign all fees to one person]
[Reset to proportional]

Kehn                 $9.19
Service $2.45 · Tip $3.37 · Tax $3.37

Storak               $5.91
Service $1.57 · Tip $2.17 · Tax $2.17

Billy                $4.17
Service $1.11 · Tip $1.53 · Tax $1.53

Danya                $7.98
Service $2.12 · Tip $2.93 · Tax $2.93
-------------------------
Total fees          $27.25
```

---

## Assign all fees to one person

```text
Assign all fees to one person
```

Picker:

```text
Who covers all fees?

Kehn
Storak
Billy
Danya
```

Result:

```text
Kehn covers all fees

Kehn                $27.25
Storak              $0.00
Billy               $0.00
Danya               $0.00
-------------------------
Total fees          $27.25
```

---

## Focused fee editor

```text
Edit service charge

Service charge total        $7.25
[Assign this fee to one person]

Kehn                        $2.45
Storak                      $1.57
Billy                       $1.11
Danya                       $2.12
-------------------------
Fully allocated

Done
```

Invalid state:

```text
Edit tip

Tip total                  $10.00

Kehn                        $3.00
Storak                      $2.00
Billy                       $1.00
Danya                       $0.00
-------------------------
$4.00 remaining

Done disabled
```

---

# Validation

Run:

```bash
./gradlew test
./gradlew detekt
./gradlew :app:assembleDebug
```

Add or update tests for:

```text
Allocate Fees screen is skipped when total fees are zero
receipt currency is used in all fee allocation labels
Proportional is default allocation mode
Proportional allocation follows item subtotals
Proportional allocation sums exactly to total fees
Equal allocation sums exactly to total fees
Fee summary hides zero-value fees
Total fees appears after participant allocations in UI state
Custom allocation validates each fee independently
Custom under-allocation disables Continue
Custom over-allocation disables Continue or is clamped
Custom fully allocated state enables Continue
Assign all fees to one person sets selected participant to total fees
Assign all fees to one person sets all others to zero
Assign this fee to one person changes only selected fee
Manual edit after quick assignment adjusts covering participant where possible
Reset to proportional replaces custom allocation after confirmation
Bottom content padding allows last custom editor row to scroll above footer
```

---

# Manual QA scenarios

## Scenario 1 - no fees

Given all fees are zero or absent.

Expected:

```text
Assign Items -> Review Expense
```

Allocate Fees is skipped.

---

## Scenario 2 - default proportional allocation

Given total fees are positive.

Expected:

- Proportional is selected by default.
- Copy says `Extra fees` or equivalent.
- Fee list is compact.
- Participant shares follow assigned item subtotals.
- Total fees appears at the bottom.
- Continue is enabled.

---

## Scenario 3 - equal allocation

Action:

```text
Tap Equal
```

Expected:

- Fees are split equally.
- Participant shares sum to total fees.
- Rounding is deterministic.
- Total fees remains at the bottom.

---

## Scenario 4 - custom allocation overview

Action:

```text
Tap Custom
```

Expected:

- Compact custom overview appears.
- It does not show every per-fee/per-person input at once.
- Quick actions are visible:
  ```text
  Assign all fees to one person
  Reset to proportional
  ```

---

## Scenario 5 - assign all fees to one person

Action:

```text
Tap Assign all fees to one person
Select Kehn
```

Expected:

```text
Kehn covers all fees
Kehn = totalFees
All others = $0.00
Continue enabled
```

---

## Scenario 6 - assign only tip to one person

Action:

```text
Open Tip editor
Tap Assign this fee to one person
Select Storak
```

Expected:

```text
Storak = full tip amount
Everyone else = $0.00 for tip
Service charge and tax allocations remain unchanged
```

---

## Scenario 7 - custom under-allocation

Given Tip is `$10.00`.

Action:

```text
Allocate only $8.00 of Tip
```

Expected:

```text
Tip has $2.00 remaining
Continue disabled
```

---

## Scenario 8 - custom over-allocation

Given Service charge is `$7.25`.

Action:

```text
Try to allocate $8.00
```

Expected:

- Input is clamped or immediate error appears.
- Continue disabled if over state exists.
- No silent invalid state.

---

## Scenario 9 - edit after one person covers all fees

Given:

```text
Kehn covers all fees
Tip total = $10.00
Kehn tip = $10.00
```

Action:

```text
Set Storak tip = $3.00
```

Expected:

```text
Kehn tip auto-updates to $7.00
Storak tip = $3.00
Tip remains fully allocated
```

---

# Done when

## P0 done when

- Allocate Fees is skipped for zero fees.
- Currency is correct everywhere.
- Proportional is the default mode.
- Top copy clearly describes extra fees.
- Fee summary is one compact list.
- Total fees appears at the end of allocation summary.
- Custom allocation cannot continue when invalid.
- Custom input prevents or clearly surfaces over-allocation.
- Sticky footer no longer hides content.

## P1 done when

- Custom mode uses one compact overview instead of all fields at once.
- Focused custom editing exists for one fee or one participant at a time.
- `Assign all fees to one person` quick action exists.
- `Assign this fee to one person` quick action exists.
- Covering participant can auto-adjust after manual edits where possible.
- `Reset to proportional` exists.
- Mode descriptions are clearer and shorter.
- Custom fields are compact and avoid repeated labels.
- Continue disabled states explain what needs fixing.

## P2 done when

- Mode transitions are polished.
- Accessibility descriptions are complete.
- Sticky back overlap is fixed.
- Undo exists for large automatic allocation changes if implemented.
- Fee allocation feels like a quick confirmation step, not a manual accounting form.
