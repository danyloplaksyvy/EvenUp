# EVU-UI-021 - Standardize Adjustments, Fees, Discounts, and Included Tax Terminology

## Goal

Standardize terminology and behavior across EvenUp so `Adjustments` and `Fees` are not used inconsistently.

The product should use clear user-facing terms:

- `Fees` for positive additive charges that increase the payable total.
- `Discounts` for negative adjustments that reduce the payable total.
- `Included tax` for tax/VAT amounts that are already included in the receipt total.
- `Adjustments` only as an internal umbrella term when the domain needs a broad category.

The UI should avoid showing `Adjustments` to users unless it intentionally represents a mixed technical bucket. In normal consumer-facing screens, use the specific term that matches the actual data.

Codex should decide the exact implementation approach and internal structure. This task defines expected behavior, UX outcomes, and validation requirements without prescribing code structure.

---

## Context

The app currently uses `Adjustments` and `Fees` inconsistently. This creates confusion because the same concept can appear under different names in different steps.

The terminology problem became more visible during Receipt Review and Allocate Fees work:

- Some positive charges are shown as `Adjustments`.
- The Allocate Fees screen uses `Fees`.
- Included VAT such as `DI CUI IVA` can be confused with additive tax/fees.
- Summary rows can show `Adjustments`, even when the user would better understand `Fees` or `Discounts`.
- Users may not know whether `Adjustments` are extra charges, corrections, discounts, taxes, or metadata.

The product needs one naming convention across UI, domain mapping, summaries, and downstream allocation logic.

---

## Recommended terminology model

Use this model across the product:

- Internal umbrella: `Adjustments`.
- User-facing positive charges: `Fees`.
- User-facing negative amounts: `Discounts`.
- User-facing included tax metadata: `Included tax`.

Meaning:

- `Adjustments` is a broad internal category that can contain fees, discounts, included tax metadata, corrections, rounding, and other non-item receipt data.
- `Fees` are positive additive charges that increase the payable total.
- `Discounts` are negative amounts that reduce the payable total.
- `Included tax` is informational tax/VAT already included in the receipt total and must not increase the payable total.

---

## Relevant files

- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/receiptreview/**`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/allocatefees/**`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/reviewexpense/**`
- `domain/receipt/api/**`
- `domain/receipt/impl/**`
- `domain/expense/api/**`
- `domain/expense/impl/**`
- `core/designsystem/api/**`
- shared formatting and receipt parsing models, if present

Adjust paths if the current project structure differs.

---

## Scope

You may edit:

- Receipt Review terminology and sections.
- Receipt Review summary labels.
- Add/edit adjustment or fee sheets.
- Allocate Fees terminology.
- Review Expense calculation detail labels if they expose fee totals.
- Domain presentation mapping for adjustment types.
- Receipt issue and total calculation mapping.
- Parser classification output if needed to distinguish additive fees from included tax.
- Tests covering terminology, calculation, and screen state.

Do not edit:

- Unrelated OCR behavior.
- Assign Items logic unless it consumes shared receipt total/fee models.
- Settlement business logic unless a terminology/categorization bug affects final math.
- Backend/API contracts unless a model mismatch is discovered and documented.
- Visual design tokens unrelated to these labels.

---

## Architecture constraints

- Keep API/implementation separation.
- Feature modules must not depend on data implementation modules.
- Domain modules must not depend on Compose, Android UI, DTOs, or database entities.
- Do not use floating point types for money.
- Use existing safe money abstractions and deterministic rounding.
- Classification of additive fees, discounts, and included tax should not live only in Composables.
- Compose UI should consume presentation-ready labels and groups.
- Use design-system typography, spacing, colors, and shapes.
- Do not hardcode currency symbols or raw colors.

---

## Sequential tasks

1. Audit all user-facing occurrences of `Adjustment`, `Adjustments`, `Fee`, `Fees`, `Tax`, `Tip`, `Discount`, and related labels across the expense flow.

2. Audit all internal/domain occurrences where `adjustment` is used to determine whether it represents an internal umbrella concept or a user-facing section.

3. Define a single mapping from receipt non-item data to user-facing groups: fees, discounts, included tax, and other metadata.

4. Keep `Adjustments` as the internal umbrella term only where a broad technical category is useful.

5. Remove or replace user-facing `Adjustments` labels when the screen can show a more specific label.

6. In Receipt Review, show positive additive charges under `Fees`.

7. In Receipt Review, show negative amounts under `Discounts`.

8. In Receipt Review, show included tax separately as `Included tax` only if it is useful for user trust or review.

9. Do not show included tax under `Fees`.

10. Do not show included tax under `Adjustments`.

11. Do not pass included tax into Allocate Fees as an amount that needs allocation.

12. Ensure included tax does not increase the calculated payable total.

13. Ensure additive fees do increase the calculated payable total.

14. Ensure discounts reduce the calculated payable total.

15. Update Receipt Review summary labels so they use specific names instead of generic `Adjustments`.

16. If only positive additive fees exist, the summary should show `Fees`.

17. If only discounts exist, the summary should show `Discounts`.

18. If both fees and discounts exist, the summary should show separate `Fees` and `Discounts` rows.

19. If included tax exists, the summary should either omit it from the total summary or show it as a separate informational row labeled `Included tax`.

20. Ensure the total formula remains clear: total equals items subtotal plus additive fees minus discounts, while included tax is informational only.

21. Update the Receipt Review section currently labeled `Adjustments` to a more specific section label based on its contents.

22. If the section contains only positive additive charges, label it `Fees`.

23. If the section contains only discounts, label it `Discounts`.

24. If the section contains a mix of positive fees and discounts, split it into separate sections instead of using `Adjustments`.

25. If the section is empty, do not show an empty technical `Adjustments` section.

26. Update add/edit actions so they use the right user-facing terminology.

27. Replace `Add adjustment` with `Add fee` when adding a positive charge.

28. If discount support exists, add or preserve a separate `Add discount` action.

29. If the app does not yet support discounts in the UI, avoid introducing confusing discount actions until the flow is supported.

30. Update edit sheets so they describe the actual type being edited, such as `Edit fee`, `Edit discount`, or `Edit included tax`.

31. Ensure delete actions use the same type-specific naming, such as `Delete fee` or `Delete discount`.

32. Update Allocate Fees so it only receives and displays positive additive fees.

33. Keep the Allocate Fees title and copy aligned with the fact that it distributes positive fees, not included tax or discounts.

34. If discounts will be allocated in the future, do not silently include them in Allocate Fees under `Fees`.

35. Ensure Review Expense calculation details use `Fees` for allocated positive charges.

36. Ensure Review Expense calculation details do not include included tax in each participant’s fee share.

37. Ensure final participant shares include items plus additive fees minus discounts according to the supported business rules.

38. Update receipt parsing/classification rules so phrases indicating included VAT/tax are categorized as included tax metadata.

39. Included VAT phrases should include examples such as `DI CUI IVA`, `IVA inclusa`, `VAT included`, `incl. VAT`, `includes tax`, `tax included`, `MwSt enthalten`, `TVA incluse`, and `IVA incluido`.

40. Update parser/classification rules so clear positive extra charges remain additive fees.

41. Additive fee examples include service charge, tip, gratuity, delivery fee, surcharge, cover charge when not already included in item totals, and tax added on top.

42. Ensure ambiguous tax cases are flagged for review instead of silently choosing the wrong category.

43. Review issue copy for included tax so users understand it does not increase the total.

44. If included tax is shown, label it with copy such as `Tax included` or `Included VAT`.

45. If included tax confidence is low, make it reviewable without adding it to the payable total by default.

46. Update all issue messages and warnings to avoid generic `adjustment` language.

47. Update accessibility labels so screen readers announce `fees`, `discounts`, or `included tax` instead of generic `adjustments`.

48. Ensure all money formatting continues to use the selected receipt currency.

49. Validate that changing currency updates all fees, discounts, included tax, and summary labels consistently.

50. Validate that adding, editing, and deleting a fee recalculates subtotal, fees, discounts, and total immediately.

51. Validate that adding, editing, and deleting a discount recalculates totals correctly if discount editing is supported.

52. Validate that editing included tax metadata does not change the payable total unless the user explicitly converts it into an additive fee.

53. If conversion between included tax and additive fee is supported, make that action explicit and reviewable.

54. Update any sample data, preview states, fake presenters, or screenshot fixtures that still use inconsistent labels.

55. Update or add tests for terminology mapping and total calculation.

56. Run project checks and fix regressions.

---

## UX requirements

The user-facing app should use the most specific label available.

Use `Fees` when the amount increases the total.

Use `Discounts` when the amount reduces the total.

Use `Included tax` when the amount is already inside the receipt total.

Avoid showing `Adjustments` to users unless the screen intentionally exposes a mixed technical bucket and no better label is possible.

The UI should avoid:

- using `Adjustments` and `Fees` as synonyms;
- showing included tax as a fee;
- allocating included tax in Allocate Fees;
- adding included VAT on top of the receipt total;
- hiding discounts inside fees;
- showing a single summary row that mixes fees and discounts;
- using generic issue copy such as `Check adjustment`.

---

## Expected Receipt Review outcome

When a receipt has positive additive charges, the user sees a `Fees` section.

Example:

Fees

Service charge

Tip

When a receipt has discounts, the user sees a `Discounts` section.

Example:

Discounts

Promo

Voucher

When a receipt has included VAT, the user does not see it as an additive fee. If shown, it appears separately.

Example:

Included tax

VAT included

The summary uses specific labels.

Example with fees only:

Subtotal

Fees

Total

Example with fees and discounts:

Subtotal

Fees

Discounts

Total

Example with included tax:

Subtotal

Fees

Total

Included tax

The exact layout is left to Codex, but the meaning must be consistent.

---

## Expected Allocate Fees outcome

Allocate Fees should distribute only positive additive fees.

It should not distribute:

- included VAT;
- tax included in total;
- informational tax metadata;
- discounts;
- unrelated receipt metadata.

If there are no positive additive fees, Allocate Fees should not show only because included tax exists.

---

## Expected Review Expense outcome

Review Expense should show participant fee shares only for positive additive fees that were actually allocated.

Included tax should not appear inside participant fee shares.

If included tax is displayed in calculation details, it should be informational and clearly labeled as included tax.

---

## Validation

Run project checks:

- Gradle unit tests.
- Static analysis.
- Debug build.

Add or update tests for:

- User-facing Receipt Review uses `Fees` for positive additive charges.
- User-facing Receipt Review uses `Discounts` for negative amounts.
- User-facing Receipt Review does not use `Adjustments` when a specific label is available.
- Summary uses `Fees` when fees exist.
- Summary uses `Discounts` when discounts exist.
- Summary separates fees and discounts when both exist.
- Included VAT phrases are classified as included tax metadata.
- `DI CUI IVA` does not create an additive fee.
- Included tax does not change the payable total.
- Included tax is not sent to Allocate Fees.
- Additive service charges and tips still increase total.
- Discounts reduce total if supported.
- Review Expense fee shares exclude included tax.
- Accessibility labels use fees, discounts, and included tax correctly.
- Currency formatting applies consistently to fees, discounts, and included tax.

---

## Manual QA scenarios

### Scenario 1 - only additive fees

Open a receipt with item subtotal and service charge.

Expected:

- Section label is `Fees`.
- Summary row is `Fees`.
- Total includes the service charge.
- Allocate Fees receives the service charge.

### Scenario 2 - included VAT

Open a receipt with `DI CUI IVA`.

Expected:

- No additive fee is created for the included VAT.
- Payable total is not increased by VAT.
- Allocate Fees does not open only because of included VAT.
- If VAT is visible, it is labeled as included tax.

### Scenario 3 - fees and discounts

Open or create a receipt with both a service charge and a discount.

Expected:

- Fees and Discounts are separate.
- Summary has separate rows.
- Total calculation is correct.
- Labels do not use generic `Adjustments`.

### Scenario 4 - uncertain tax classification

Open a receipt where tax could be additive or included.

Expected:

- The app flags the tax for review or uses conservative classification.
- The UI explains what needs review.
- It does not silently add included tax on top of total.

### Scenario 5 - review and final settlement

Complete the flow through Allocate Fees and Review Expense.

Expected:

- Only additive fees are allocated.
- Included tax does not affect participant fee shares.
- Final totals balance correctly.

---

## Done when

- `Adjustments` and `Fees` are no longer used as synonyms in user-facing UI.
- `Adjustments` remains only as an internal umbrella concept where needed.
- Receipt Review shows `Fees`, `Discounts`, and `Included tax` according to the actual data.
- Summary rows use specific labels.
- Included VAT is not added to the payable total.
- Included VAT is not allocated as a fee.
- Allocate Fees only distributes positive additive fees.
- Review Expense fee shares exclude included tax.
- Accessibility labels and issue copy use consistent terminology.
- Tests and manual QA scenarios pass.
