# EVU-UI-018 - Optimize Review Expense Final Confirmation Screen

## Goal

Refactor the Review Expense screen into a clear final confirmation step focused on the user's main question:

```text
Who pays whom, and how much?
```

The screen should make the final settlement understandable in under five seconds, keep accounting details available for trust/debugging, and avoid exposing raw internal balance math by default.

The final screen should feel like:

```text
Here is the total, who paid, and how everyone settles up.
```

not like a spreadsheet or calculation-debug view.

---

## Context

Current screen structure:

```text
Review expense
Merchant
Total
Paid by
Settlement Summary
Calculation details
Save expense
```

The current layout is understandable, but several issues reduce trust and clarity:

- Currency appears hardcoded as EUR even when the receipt is USD.
- The top area wastes vertical space.
- The `Paid by` card is too large for one piece of metadata.
- `Settlement Summary` mixes settlement actions with the payer's own share.
- Settlement rows use `owes`, but action-oriented copy like `pays` is clearer.
- Expanded calculation details are very tall and feel like a raw accounting/debug screen.
- `Net balance` uses negative numbers, which are technically correct but user-hostile.
- Sticky/floating back button overlaps expanded details while scrolling.
- Sticky `Save expense` footer can hide expanded content.
- There is no compact payer summary explaining why the payer receives money.
- `Save expense` may be ambiguous depending on whether saving also shares/creates the expense.

---

## Relevant files

- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/reviewexpense/**`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/reviewexpense/ReviewExpenseScreen.kt`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/reviewexpense/ReviewExpenseUiState.kt`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/reviewexpense/ReviewExpenseUiEvent.kt`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/reviewexpense/ReviewExpensePresenter.kt`
- `domain/expense/api/**`
- `domain/expense/impl/**`
- `domain/receipt/api/**`
- `core/designsystem/api/**`
- Shared money/currency formatter, if present.

Adjust paths if current module/file names differ.

---

## Scope

You may edit:

- Review Expense UI
- Review Expense UI state
- Review Expense events
- Review Expense presenter/reducer logic
- Settlement summary mapping
- Calculation details mapping
- Shared money formatting usage
- Save CTA label if needed
- Design-system components if reusable summary/detail rows are needed
- Tests for settlement summary and calculation detail presentation

Do not edit:

- Receipt scanner/parser
- Receipt Review flow, except shared formatter/component fixes
- Choose People flow
- Assign Items flow
- Allocate Fees flow, except if shared settlement models require harmless mapping updates
- Backend/API contracts
- Settlement calculation domain logic unless a correctness bug is found and documented

---

## Architecture constraints

- Keep API/implementation separation.
- Feature modules must not depend on data implementation modules.
- Domain modules must not depend on Compose, Android UI, DTOs, or database entities.
- Do not use `Float` or `Double` for money.
- Use `MoneyMinor`, integer minor units, `BigDecimal`, or existing safe money abstractions.
- Settlement calculation should remain in domain/presenter logic, not inside Composables.
- Compose UI should consume presentation-ready state.
- Use design-system colors, typography, spacing, and shapes.
- Do not hardcode currency symbols or raw colors in feature Composables.

---

# P0 - Required fixes

## P0.1 - Use receipt currency everywhere

### Problem

Review Expense currently shows Euro amounts even when the scanned receipt is USD. This is a trust-breaking issue because this screen is the final confirmation before saving.

### Requirements

Use the receipt currency for every amount:

- top total;
- settlement rows;
- payer summary;
- calculation details;
- items;
- fees;
- total share;
- amount paid;
- balance/result;
- save confirmation if shown.

Examples:

```text
USD -> $82.25
EUR -> €82.25 or localized EUR format from shared formatter
GBP -> £82.25
```

Do not hardcode `€`, `$`, or `£`.

Prefer shared formatting:

```kotlin
//formatMoney(amount: MoneyMinor, currencyCode: CurrencyCode): String
```

### Done when

- Review Expense uses the same currency as Receipt Review.
- No hardcoded Euro remains in Review Expense.
- Tests or UI-state assertions cover currency propagation.

---

## P0.2 - Replace raw negative `Net balance` with user-facing `Pays` / `Receives`

### Problem

Expanded calculation details show values like:

```text
Net balance    €-15.61
```

This is technically correct but user-hostile. Users should not need to interpret signs.

### Requirements

Replace `Net balance` with `Result`.

For a participant who owes money:

```text
Result    Pays $15.61
```

For a participant who should receive money:

```text
Result    Receives $71.73
```

For a participant settled exactly:

```text
Result    Settled
```

Do not show negative money values to users unless there is a strong debug-only reason.

### Done when

- Calculation details no longer show negative balances.
- Result labels clearly say `Pays`, `Receives`, or `Settled`.
- Tests cover positive, negative, and zero balance presentation.

---

## P0.3 - Separate payer share from settlement actions

### Problem

Current `Settlement Summary` contains both:

```text
Storak owes Kehn
Billy owes Kehn
Danya owes Kehn
Kehn's share
```

The first rows are settlement actions. `Kehn's share` is a cost breakdown, not a settlement action. Mixing them creates conceptual noise.

### Requirements

Remove payer share from the settlement action card.

Use a separate payer summary:

```text
Kehn paid $82.25
Kehn's share is $10.52
Kehn receives $71.73
```

or compact:

```text
Kehn paid $82.25 · share $10.52 · receives $71.73
```

Settlement card should only show who pays whom:

```text
To settle up

Storak pays Kehn        $15.61
Billy pays Kehn          $4.83
Danya pays Kehn         $51.29
```

### Done when

- Settlement card contains only payment actions.
- Payer share is shown separately.
- User can understand why payer receives money.

---

## P0.4 - Rename `Settlement Summary` to action-oriented copy

### Problem

`Settlement Summary` is accurate but formal. The screen should communicate the action people need to take.

### Requirements

Rename:

```text
Settlement Summary
```

to:

```text
To settle up
```

or:

```text
Who pays whom
```

Recommended:

```text
To settle up
```

### Done when

- Settlement card title is action-oriented.
- Copy matches the final confirmation purpose.

---

## P0.5 - Change `owes` to `pays`

### Problem

Current copy:

```text
Storak owes Kehn
```

is understandable, but `pays` is more action-oriented and better suited to a final settlement screen.

### Requirements

Use:

```text
Storak pays Kehn
```

instead of:

```text
Storak owes Kehn
```

Recommended row:

```text
Storak                 $15.61
Pays Kehn
```

or:

```text
Storak pays Kehn       $15.61
```

Choose whichever fits the existing row layout better.

### Done when

- Settlement rows use `pays`, not `owes`.
- Accessibility labels also use action language.

---

## P0.6 - Fix sticky back button overlap

### Problem

The floating/sticky back button overlaps content when the calculation details are expanded and scrolled.

### Requirements

Fix globally or on this screen:

- ensure scroll content does not pass under the back button in a way that hides text;
- add top/start content exclusion if using floating back;
- add a surface/scrim/elevation behind the button if needed;
- verify expanded details at multiple scroll positions.

### Done when

- Back button does not obscure calculation details.
- Manual QA covers expanded details and long participant lists.

---

## P0.7 - Add enough bottom padding for sticky Save CTA

### Problem

Expanded details can be hidden behind the sticky `Save expense` footer.

### Requirements

Scroll content must include bottom padding for:

- sticky footer height;
- navigation bar height;
- additional comfortable spacing.

Recommended behavior:

```text
Last detail row can scroll fully above the Save button.
```

### Done when

- Expanded content is not obscured by the sticky CTA.
- Manual QA covers 4+ participants and expanded calculation details.

---

# P1 - Important UX improvements

## P1.1 - Compress the top paid-by section

### Problem

Current `Paid by` card is large for one metadata value.

### Requirements

Replace the large card with a compact metadata row/card.

Options:

```text
Paid by Kehn · 4 people
```

or:

```text
Paid by        Kehn
Participants   4
```

or:

```text
Paid by Kehn
```

with avatar inline.

Recommended main top area:

```text
Taberna do Mercado
$82.25

Paid by Kehn · 4 people
```

### Done when

- Top section uses less vertical space.
- Paid-by information remains clear.
- Settlement summary appears higher on screen.

---

## P1.2 - Add compact payer summary

### Requirements

After settlement actions, show a compact payer summary.

Example:

```text
Kehn paid $82.25
Kehn's share is $10.52
Kehn receives $71.73
```

or:

```text
Kehn receives $71.73 after covering their $10.52 share.
```

This makes the settlement math more understandable without forcing the user into calculation details.

### Done when

- Payer summary exists outside the settlement action list.
- User can understand payer-specific result without expanded details.

---

## P1.3 - Move calculation details into a bottom sheet or separate screen

### Problem

Expanded inline calculation details turn the final confirmation screen into a long accounting/debug page.

### Requirements

Preferred: tapping `Calculation details` opens a bottom sheet.

Alternative: open a separate details screen if easier for long content.

Bottom sheet content:

```text
Calculation details

Kehn
Items          $7.41
Fees           $3.11
Total share   $10.52
Paid          $82.25
Result        Receives $71.73

Storak
Items         $11.00
Fees           $4.61
Total share   $15.61
Paid           $0.00
Result        Pays $15.61
```

If keeping inline expansion for MVP, make it much more compact and ensure no overlap with back/footer.

### Done when

- Main screen remains focused on settlement.
- Calculation details are available but secondary.
- Details view avoids raw negative balances.

---

## P1.4 - Make calculation detail labels friendlier

### Requirements

Use:

```text
Items
Fees
Total share
Paid
Result
```

Avoid:

```text
Amount paid
Net balance
```

`Amount paid` is acceptable if needed, but `Paid` is shorter and better for dense details.

Use clear result labels:

```text
Pays $15.61
Receives $71.73
Settled
```

### Done when

- Details read as explanation, not debug output.
- No user-facing negative values are needed.

---

## P1.5 - Add final total consistency validation

### Requirements

Before enabling save, validate:

```text
sum(participant total shares) == expense total
sum(amounts paid) == expense total
sum(settlement transfers) == total owed to payers
```

Use integer minor units only.

If inconsistent, show a warning banner and disable Save:

```text
Expense totals need review.
```

or more specific:

```text
Shares do not add up to the receipt total.
```

This should be rare and should catch state bugs from previous steps.

### Done when

- Save is blocked for inconsistent totals.
- User sees a clear reason.
- Tests cover valid and invalid final totals.

---

## P1.6 - Review `Save expense` CTA label

### Requirements

Choose CTA copy based on product behavior:

If it only saves locally:

```text
Save expense
```

If it creates the expense in backend/storage:

```text
Create expense
```

If it then opens share flow:

```text
Save and share
```

If it advances to another final screen:

```text
Save and continue
```

For current MVP, keep `Save expense` only if it accurately describes the action.

### Done when

- CTA label matches the actual next behavior.
- No ambiguity around whether the expense is shared/created/saved.

---

# P2 - Polish and quality improvements

## P2.1 - Add subtle balanced/success state

If final totals are valid, optionally show a compact success indicator:

```text
Ready to save
```

or a small check near calculation details:

```text
Totals balanced
```

Do not add noisy green banners unless there was a previous error.

---

## P2.2 - Add accessibility descriptions

### Requirements

Top total:

```text
Expense total, $82.25
```

Paid by:

```text
Paid by Kehn
```

Settlement rows:

```text
Storak pays Kehn fifteen dollars and sixty-one cents
```

Payer summary:

```text
Kehn paid eighty-two dollars and twenty-five cents, their share is ten dollars and fifty-two cents, and they receive seventy-one dollars and seventy-three cents
```

Calculation details:

```text
Kehn, items seven dollars and forty-one cents, fees three dollars and eleven cents, total share ten dollars and fifty-two cents, paid eighty-two dollars and twenty-five cents, receives seventy-one dollars and seventy-three cents
```

### Done when

- Screen reader output communicates settlement actions clearly.
- Negative values are not announced as raw negatives.

---

## P2.3 - Add haptic feedback on successful save

Use light success haptic when the expense is saved successfully.

Do not trigger haptic before save succeeds.

---

## P2.4 - Add save loading and error states

### Requirements

When saving:

```text
Save expense -> Saving...
```

Disable repeated taps.

On success:

- navigate to next destination;
- or show success state if staying on page.

On failure:

```text
Could not save expense. Try again.
```

Do not lose user state.

---

## P2.5 - Improve long-name and many-participant handling

### Requirements

Handle:

- long merchant names;
- long participant names;
- 2 participants;
- 10+ participants;
- multiple payers if supported later.

Rules:

- Names should ellipsize gracefully.
- Amounts remain aligned.
- Settlement card remains readable.

---

# Target UX

## Main screen target

```text
Review expense

Taberna do Mercado
$82.25

Paid by Kehn · 4 people

To settle up

Storak pays Kehn        $15.61
Billy pays Kehn          $4.83
Danya pays Kehn         $51.29

Kehn receives $71.73 after covering their $10.52 share.

Calculation details       >

[Save expense]
```

---

## Calculation details target

Preferred as bottom sheet or separate screen:

```text
Calculation details

Kehn
Items          $7.41
Fees           $3.11
Total share   $10.52
Paid          $82.25
Result        Receives $71.73

Storak
Items         $11.00
Fees           $4.61
Total share   $15.61
Paid           $0.00
Result        Pays $15.61

Billy
Items          $3.41
Fees           $1.42
Total share    $4.83
Paid           $0.00
Result        Pays $4.83

Danya
Items         $36.18
Fees          $15.11
Total share   $51.29
Paid           $0.00
Result        Pays $51.29
```

---

# UI state requirements

Review Expense UI state should expose presentation-ready values.

Suggested structure:

```kotlin
data class ReviewExpenseUiState(
    val merchantName: String,
    val totalAmountLabel: String,
    val paidByLabel: String,
    val participantCountLabel: String,
    val settlementRows: List<SettlementRowUiState>,
    val payerSummary: PayerSummaryUiState?,
    val calculationDetails: List<ParticipantCalculationUiState>,
    val canSave: Boolean,
    val saveButtonLabel: String,
    val validationMessage: String?,
)
```

Settlement row:

```kotlin
data class SettlementRowUiState(
    val fromParticipantId: ParticipantId,
    val fromName: String,
    val fromInitials: String,
    val toName: String,
    val amountLabel: String,
    val actionLabel: String, // "Pays Kehn"
    val contentDescription: String,
)
```

Calculation detail:

```kotlin
data class ParticipantCalculationUiState(
    val participantId: ParticipantId,
    val name: String,
    val initials: String,
    val itemsLabel: String,
    val feesLabel: String,
    val totalShareLabel: String,
    val paidLabel: String,
    val resultLabel: String, // "Pays $15.61", "Receives $71.73", "Settled"
)
```

Avoid exposing raw signed balance strings directly to Composables.

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
Review Expense uses receipt currency
Settlement rows use pays copy
Payer share is not included as a settlement row
Payer summary shows paid/share/receives values
Calculation details show Pays for negative balances
Calculation details show Receives for positive balances
Calculation details show Settled for zero balances
No user-facing negative net balance labels are produced
Save is disabled when final totals are inconsistent
Bottom/expanded details state does not hide content behind footer
CTA label matches expected save behavior
```

---

# Manual QA scenarios

## Scenario 1 - normal single payer

Given:

```text
Total = $82.25
Paid by Kehn
Storak owes $15.61
Billy owes $4.83
Danya owes $51.29
Kehn share = $10.52
```

Expected main screen:

```text
To settle up
Storak pays Kehn $15.61
Billy pays Kehn $4.83
Danya pays Kehn $51.29

Kehn receives $71.73 after covering their $10.52 share.
```

No Euro if receipt currency is USD.

---

## Scenario 2 - calculation details

Action:

```text
Open Calculation details
```

Expected:

- Details open in bottom sheet or compact expanded UI.
- No raw negative net balances are shown.
- Rows use `Pays`, `Receives`, or `Settled`.
- Back button does not cover text.
- Save footer does not cover bottom rows.

---

## Scenario 3 - equal participant settled state

Given one participant's paid amount equals their total share.

Expected detail result:

```text
Settled
```

not:

```text
Net balance €0.00
```

---

## Scenario 4 - long participant names

Expected:

- Settlement rows remain aligned.
- Names ellipsize if needed.
- Amounts remain readable.

---

## Scenario 5 - invalid final totals

Given calculation mismatch from state bug or invalid previous step.

Expected:

- Save is disabled.
- Warning explains the problem.
- User does not save inconsistent expense.

---

# Done when

## P0 done when

- Review Expense uses receipt currency everywhere.
- User-facing negative balances are replaced with `Pays` / `Receives`.
- Payer share is separated from settlement actions.
- Settlement card is renamed to action-oriented copy.
- Rows use `pays`, not `owes`.
- Back button no longer overlaps expanded content.
- Sticky Save footer no longer hides content.

## P1 done when

- Top paid-by section is compact.
- Payer summary explains payer result.
- Calculation details are moved to a bottom sheet/separate screen or significantly compacted.
- Calculation labels are friendlier.
- Final total consistency validation exists.
- Save CTA label matches behavior.

## P2 done when

- Optional balanced/success state is added.
- Accessibility labels are clear and action-oriented.
- Save loading/error states are handled.
- Long names and many participants are handled gracefully.
