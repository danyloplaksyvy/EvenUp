# EVU-UI-020 - Improve Receipt Review Issue Resolution Flow

## Goal

Refactor Receipt Review so users understand exactly what needs review, how to jump to the issue, how to confirm or fix it, and when the receipt is safe to continue.

The screen should not only detect receipt problems. It should guide the user through resolving them.

The intended flow is:

- show the exact issue;
- navigate the user to the relevant field or item;
- let the user confirm or edit the value;
- clear the issue after confirmation;
- allow Continue only when blocking issues are resolved.

Codex should decide the exact implementation approach and internal structure. This task defines expected behavior, UX outcomes, and validation requirements without prescribing code structure.

---

## Context

The current Receipt Review screen is visually close, but the review workflow is confusing.

Observed issues:

- The top banner says generic text like "Review needed", but the user does not know what specifically needs review.
- After the user opens a flagged item, the warning can remain even if the user believes they reviewed it.
- The item edit sheet uses a generic "Done" CTA, so the user does not understand that tapping it confirms or resolves the issue.
- The top warning does not clearly navigate to the item or field that needs review.
- Some item warnings use technical copy like "Multiple amount candidates found".
- The screen can show duplicated mismatch messaging in the Summary card.
- The parser can treat included VAT, such as "DI CUI IVA", as an additive fee, causing an incorrect calculated total.
- Date values can appear as raw timestamps instead of readable dates.
- Currency can default to USD even when the receipt image contains another currency symbol.
- Long merchant names can wrap awkwardly.
- Continue behavior is not clearly tied to blocking versus non-blocking issues.

---

## Relevant files

- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/receiptreview/**`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/receiptreview/ReceiptReviewScreen.kt`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/receiptreview/ReceiptReviewUiState.kt`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/receiptreview/ReceiptReviewUiEvent.kt`
- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/receiptreview/ReceiptReviewPresenter.kt`
- `domain/receipt/api/**`
- `domain/receipt/impl/**`
- `domain/expense/api/**`
- `domain/expense/impl/**`
- `core/designsystem/api/**`
- shared money, date, and currency formatting utilities if present

Adjust paths if the current project structure differs.

---

## Scope

You may edit:

- Receipt Review UI.
- Receipt Review UI state.
- Receipt Review events.
- Receipt Review presenter/reducer logic.
- Receipt issue model and mapping.
- Item review confirmation state.
- Receipt total reconciliation state.
- Included tax versus additive adjustment classification.
- Date display formatting.
- Currency inference and confidence handling.
- Summary mismatch presentation.
- Continue button enabled/disabled behavior.
- Tests for issue resolution, total reconciliation, date formatting, and included VAT handling.

Do not edit:

- Assign Items flow, except navigation assumptions if Continue behavior changes.
- Allocate Fees flow, except if included tax metadata must be hidden from additive fees.
- Review Expense flow.
- Backend/API contracts.
- Unrelated OCR prompt behavior unless required to classify included tax correctly.
- Settlement calculation logic unless a correctness bug is found and documented.

---

## Architecture constraints

- Keep API/implementation separation.
- Feature modules must not depend on data implementation modules.
- Domain modules must not depend on Compose, Android UI, DTOs, or database entities.
- Do not use floating point types for money.
- Use existing safe money abstractions and deterministic rounding.
- Review issue classification and validation should not live inside Composables.
- Compose UI should consume presentation-ready state.
- Use design-system spacing, typography, color, and shape tokens.
- Do not hardcode currency symbols or raw colors.
- Do not expose raw ISO timestamp strings to the UI.

---

## Sequential tasks

1. Audit the current Receipt Review implementation and identify where receipt-level issues, item-level issues, summary state, edit sheets, warnings, date formatting, currency formatting, total calculation, and Continue state are built.

2. Replace the generic top-level "Review needed" state with specific issue summaries that tell the user what needs attention.

3. Make the top issue banner actionable. Tapping it should navigate to the first unresolved issue, scroll to the relevant row, or open an issue list that lets the user choose what to review.

4. Support multiple concurrent issues in the banner. If more than one issue exists, the banner should communicate the issue count or the most important issue plus the remaining count.

5. Define receipt-level issue types such as total mismatch, missing date, future date, missing merchant, uncertain currency, and invalid total.

6. Define item-level issue types such as amount may be wrong, multiple possible prices found, quantity unclear, item name unclear, and invalid item amount.

7. Add issue resolution state so each issue can move from unresolved to confirmed or edited.

8. Ensure that opening a flagged item does not automatically clear the issue. The issue should clear only when the user confirms the field or saves a corrected value.

9. Replace generic item sheet CTA text with state-specific actions. If the user has not edited a flagged value, the CTA should communicate confirmation. If the user edited a value, the CTA should communicate saving changes.

10. Update the flagged item bottom sheet copy so it explains what the issue is and what the user should do next.

11. For item amount ambiguity, replace technical copy like "Multiple amount candidates found" with user-facing copy such as "Amount may be wrong" or "Check price from receipt".

12. In item edit sheets, explain ambiguous values in plain language. The user should understand that the app found multiple possible prices and needs confirmation or correction.

13. Ensure confirmed item issues visually clear from the item list after confirmation.

14. If an issue remains unresolved after closing the sheet, keep the item highlighted and preserve the issue in the banner or issue list.

15. Add an issue navigator experience if multiple issues are present. It should list each issue, explain where it is, and provide an action to review it.

16. Refine total mismatch detection so it distinguishes between an actual total mismatch and a tax-included receipt where tax should not be added on top.

17. Treat included tax phrases as non-additive tax metadata. Examples include "DI CUI IVA", "IVA inclusa", "VAT included", "incl. VAT", "includes tax", "tax included", "MwSt enthalten", "TVA incluse", and "IVA incluido".

18. Do not add included tax metadata to the additive Adjustments or Fees section as an amount that increases the calculated total.

19. Represent included tax separately from additive adjustments if it needs to be shown. It may be displayed as "Tax included" or hidden from the main flow, but it must not affect the payable total.

20. Keep additive adjustments for true extra charges such as service charge, tip, gratuity, delivery fee, surcharge, and tax that is clearly added on top of subtotal.

21. For receipts where included VAT caused a false mismatch, recompute the total so the payable total matches the receipt total instead of adding included VAT on top.

22. Add a total mismatch resolution flow for cases where calculated total and receipt total genuinely differ.

23. The total mismatch flow should show the calculated total, the scanned receipt total, and the difference in a way that is easy to compare.

24. The total mismatch flow should offer fast resolution actions such as using the receipt total, keeping the calculated total, and editing manually.

25. If the user chooses to use the receipt total, reconcile the receipt data so the summary becomes valid and the issue clears.

26. If the user chooses to keep the calculated total, mark the mismatch as user-confirmed and make the UI clearly show that the total was confirmed by the user.

27. If the user chooses manual edit, preserve the existing total edit behavior but make validation and formatting clear.

28. Avoid showing duplicated total mismatch warnings in the Summary section. Use one clear warning or status message for the same mismatch.

29. If the Summary total is invalid or differs from the receipt total, use clear language such as "Total differs by $1.09" instead of duplicating a long receipt-says message in multiple places.

30. Make Summary status labels specific. Replace generic "Needs review" with more precise statuses such as "Total differs", "Items need review", "Ready", or "Confirmed".

31. Format parsed dates into user-facing date strings. Do not show raw timestamp values like "2018-11-03T16:39:00".

32. Use a readable date format for Receipt Review. The exact display format can follow the app convention, but it should be date-first and not expose raw ISO datetime strings.

33. If time is parsed and useful, display it in a friendly format. Otherwise show only the date.

34. Preserve validation that future dates are not acceptable. Future dates should remain blocking or clearly flagged.

35. Improve currency inference and review behavior. If a receipt image contains an obvious currency symbol, the inferred currency should match it.

36. If currency confidence is low or conflicting symbols are found, flag the currency row as needing review instead of silently defaulting to USD.

37. Make the currency row actionable and visually reviewable when currency confidence is low.

38. Ensure all money values on Receipt Review use the selected receipt currency through the shared formatter.

39. Improve long merchant name handling in both the top header and receipt details card.

40. Long merchant names should truncate or wrap gracefully without making the details card visually unbalanced.

41. Keep full merchant value accessible in the edit sheet even if it is truncated in the card.

42. Review the item row highlighted state and ensure it is noticeable without becoming too visually heavy.

43. Ensure highlighted item rows are tappable and have accessible descriptions that explain the review issue.

44. Add clear behavior for the top issue banner when the flagged item is below the fold. The user should be able to reach the exact issue without manually searching the list.

45. Review the Continue button behavior and define blocking versus non-blocking issues.

46. Block Continue for invalid total, unresolved total mismatch, future date, invalid amount, negative quantity, missing required currency, or other states that would create a broken expense.

47. Allow Continue for soft warnings only after the user has confirmed them, or change the CTA copy to make continuing with warnings explicit.

48. If Continue is disabled, show a visible reason that tells the user what must be fixed.

49. If Continue is enabled with confirmed soft warnings, make it clear that the user has reviewed the issues.

50. Ensure adding, editing, or deleting items recalculates subtotal, adjustments, total, mismatch state, and review issues immediately.

51. Ensure editing quantity and amount fields updates item total and unit price consistently.

52. Ensure amount values are formatted on save and not shown as partially formatted text.

53. Ensure all receipt-level and item-level review states survive recomposition, scrolling, and sheet open/close cycles.

54. Add accessibility labels for the top issue banner, flagged item rows, receipt detail rows, summary status, edit sheet warnings, confirmation CTAs, and total mismatch actions.

55. Ensure accessibility labels are specific and action-oriented. They should explain what needs review and what tapping will do.

56. Validate behavior with a receipt where all items have been reviewed. The top banner should clear or change to a ready state once all issues are resolved.

57. Validate behavior with a receipt containing "DI CUI IVA" or equivalent included tax. Included tax should not create an additive adjustment or false total mismatch.

58. Validate behavior with a receipt that has a real additive service charge or tip. Additive charges should still appear as adjustments and participate in total calculation.

59. Validate behavior with a missing date, parsed raw timestamp, future date, and valid past date.

60. Validate behavior with long merchant names, long item names, many items, and flagged items below the fold.

61. Update or add tests for issue classification, issue confirmation, total mismatch resolution, included tax handling, currency confidence, date formatting, and Continue behavior.

62. Run project checks and fix regressions.

---

## UX requirements

The Receipt Review screen should prioritize:

1. Receipt total and merchant.
2. Exact review status.
3. Receipt details that can be edited.
4. Items with clear issue indicators.
5. Adjustments that only include additive charges.
6. Summary that clearly explains whether totals match.
7. Continue action with clear enabled or disabled reasoning.

The screen should avoid:

- generic "Review needed" without details;
- warnings that do not tell the user where to go;
- technical copy such as "amount candidates";
- generic "Done" CTAs for flagged fields;
- false total mismatches from included VAT;
- duplicated mismatch warnings;
- raw timestamp strings;
- silent currency defaults when confidence is low;
- unresolved warnings after the user explicitly confirms values.

---

## Expected main-screen outcome

The exact layout can be decided by Codex, but the screen should communicate review state in a structure similar to this:

Receipt review

$106.59

Jose Pizarro

Total differs by $1.09 - Tap to review

Receipt details

Merchant: Jose Pizarro

Date: Nov 3, 2018

Currency: GBP

Items

Padron Peppers - $12.00

Check price from receipt

Summary

Subtotal

Adjustments

Total

Receipt total

The important outcome is that the user knows what needs attention and can reach it directly.

---

## Expected item-review outcome

When a flagged item is opened, the sheet should explain the issue and provide a resolving action.

Expected behavior:

- If amount is flagged and unchanged, the CTA confirms the amount.
- If the user edits the amount, the CTA saves changes.
- After confirmation or save, the item-level issue clears.
- If no action is taken, the issue remains.

---

## Expected total-mismatch outcome

When the calculated total and receipt total differ, the user should not be forced to manually guess what to edit.

The resolution flow should show:

- calculated total from items and additive adjustments;
- scanned receipt total;
- difference;
- possible reason when detected, such as included VAT;
- action to use receipt total;
- action to keep calculated total;
- action to edit manually.

If the mismatch comes from included VAT, the preferred behavior is to classify VAT as included tax metadata and avoid the mismatch before asking the user to resolve it.

---

## Expected included-tax outcome

For receipts with included tax wording such as "DI CUI IVA":

- Do not add the included tax amount as an additive adjustment.
- Do not increase the calculated total by the included tax amount.
- Optionally store or show it as included tax metadata.
- If shown, label it clearly as included tax.
- Do not send it into Allocate Fees as an extra fee that needs allocation.

---

## Expected date outcome

Dates should be displayed as readable user-facing dates.

Raw parsed values, timestamps, and ISO datetime strings should not appear in Receipt Review.

If the date includes time and the app chooses to show it, it should be formatted in a readable localized style.

---

## Validation

Run project checks:

- Gradle unit tests.
- Static analysis.
- Debug build.

Add or update tests for:

- Generic review state is replaced with specific issue summaries.
- Top issue banner navigates to the first unresolved issue.
- Item issue remains unresolved until the user confirms or edits it.
- Confirming a flagged amount clears the issue.
- Editing a flagged amount clears or updates the issue correctly.
- Multiple issues are summarized correctly.
- Included VAT phrases do not create additive adjustments.
- "DI CUI IVA" is treated as included tax metadata.
- Included tax does not change payable total.
- Real additive service charges and tips still affect calculated total.
- Total mismatch resolution can use receipt total.
- Total mismatch resolution can keep calculated total as user-confirmed.
- Manual total edit validates and formats the amount.
- Duplicate mismatch warnings are not shown.
- Date display does not expose raw ISO datetime values.
- Future dates are blocked or clearly flagged.
- Currency inference uses obvious receipt symbols.
- Uncertain currency creates a review issue.
- Continue is disabled for blocking issues.
- Continue is enabled only when blocking issues are resolved.
- Long merchant and item names render safely.
- Accessibility labels describe review issues and actions.

---

## Manual QA scenarios

### Scenario 1 - flagged item already reviewed

Open a receipt with one flagged item.

Expected:

- Banner says the specific item issue.
- Tapping the banner navigates to the item.
- Opening the item shows the issue explanation.
- CTA clearly confirms the amount or saves changes.
- After confirmation, the item warning clears.
- The top banner updates or clears.

### Scenario 2 - included VAT receipt

Open a receipt with "DI CUI IVA" and a total that already includes tax.

Expected:

- Included VAT is not added as an adjustment.
- Calculated total matches receipt total.
- No false difference appears.
- If included VAT is shown, it is labeled as included tax.
- Allocate Fees should not receive the included VAT as an extra fee.

### Scenario 3 - real additive service charge

Open a receipt with a true service charge added on top of subtotal.

Expected:

- Service charge appears as an additive adjustment.
- Calculated total includes the service charge.
- Summary reconciles correctly.

### Scenario 4 - genuine total mismatch

Open a receipt where item subtotal and additive adjustments genuinely differ from scanned total.

Expected:

- Banner says total differs.
- Summary shows one clear mismatch message.
- Tapping total or banner opens a resolution flow.
- User can use receipt total, keep calculated total, or edit manually.
- The issue clears only after a valid resolution.

### Scenario 5 - date parsing

Open receipts with raw timestamp, missing date, future date, and valid date.

Expected:

- Raw timestamp is displayed as a readable date.
- Missing date is shown as a review issue if required.
- Future date is blocked or clearly flagged.
- Valid date is displayed cleanly.

### Scenario 6 - uncertain currency

Open a receipt where the currency is not confidently detected.

Expected:

- Currency row is highlighted or marked for review.
- User can change the currency.
- Amount formatting updates consistently after currency selection.

### Scenario 7 - long receipt

Open a receipt with many items and a flagged item below the fold.

Expected:

- Top banner can take the user to the flagged item.
- User does not need to manually search.
- Sticky Continue does not hide the Summary or final rows.

---

## Done when

- Review warnings are specific and actionable.
- Users can tap the banner or issue list to reach the exact issue.
- Item warnings clear only after explicit confirmation or edit.
- Flagged item sheets use clear CTAs instead of generic "Done".
- Included VAT such as "DI CUI IVA" is not treated as an additive fee.
- Total mismatch flow gives fast resolution options instead of forcing manual editing.
- Date values are displayed in readable format.
- Currency inference is safer and uncertain currency is reviewable.
- Duplicate mismatch warnings are removed.
- Continue behavior reflects blocking versus confirmed warnings.
- Long names and flagged items below the fold are handled cleanly.
- Accessibility labels describe issues and resolution actions.
- Tests and manual QA scenarios pass.
