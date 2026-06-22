# EVU-UI-019 - Refine Review Expense Final Confirmation UX

## Goal

Refine the updated Review Expense screen so it feels like a polished final confirmation step, not a dense accounting view.

The screen should make the answer clear immediately:

- who paid;
- who needs to pay whom;
- how much each person pays;
- why the payer receives money;
- whether the totals are valid.

Codex should decide the exact implementation approach and internal structure. This task defines expected behavior, UX outcomes, and validation requirements without prescribing specific implementation details.

---

## Context

The current updated Review Expense screen is a clear improvement. It now uses the correct currency, shows action-oriented settlement rows, separates payer summary from settlement rows, and moves calculation details out of the default main view.

Current strengths:

- Receipt currency is now shown correctly.
- Settlement rows use `Pays`, which is better than `owes`.
- Payer summary is no longer mixed into the settlement list.
- `Totals balanced` increases trust before saving.
- Calculation details are no longer always expanded inline by default.

Current weaknesses:

- The `Paid by Kehny - 7 people` row is too small and visually weak.
- The paid-by avatar size is inconsistent with other participant avatars.
- The payer summary sentence is readable but too general and not scannable enough.
- The `To settle up` list is clear but can become visually heavy with many participants.
- The calculation details bottom sheet or details surface is not reliably scrollable.
- The calculation details presentation is still quite vertical.
- The `Calculation details` card feels like a clickable row plus a separate status row rather than one cohesive trust/action block.
- The floating or sticky back behavior is inconsistent; the previous sticky button caused overlap, while disappearing can feel unexpected.
- Long names, many participants, and content under the sticky Save button still need robust handling.

---

## Relevant files

- `feature/expense-flow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/reviewexpense/**`
- `domain/expense/api/**`
- `domain/expense/impl/**`
- `domain/receipt/api/**`
- `core/designsystem/api/**`
- shared money/currency formatter, if present

Adjust paths if the current project structure differs.

---

## Scope

You may edit:

- Review Expense screen UI.
- Review Expense UI state.
- Review Expense events.
- Review Expense presenter/reducer logic.
- Settlement row presentation mapping.
- Payer summary presentation mapping.
- Calculation details presentation.
- Navigation/back behavior for this screen.
- Shared UI components if useful.
- Tests for presentation state, settlement details, and final validation.

Do not edit:

- OCR/parser behavior.
- Receipt Review flow, except shared component/formatter changes.
- Choose People flow.
- Assign Items flow.
- Allocate Fees flow, except harmless shared model/presentation mapping changes if required.
- Backend/API contracts.
- Settlement calculation business logic unless a correctness bug is discovered and documented.

---

## Architecture constraints

- Keep API/implementation separation.
- Feature modules must not depend on data implementation modules.
- Domain modules must not depend on Compose, Android UI, DTOs, or database entities.
- Do not use floating point types for money.
- Use existing safe money abstractions and deterministic rounding.
- Keep settlement calculation outside Composables.
- Compose UI should consume presentation-ready state.
- Use design-system spacing, typography, color, and shape tokens.
- Do not hardcode currency symbols or raw colors.

---

## Sequential tasks

1. Audit the current Review Expense implementation and identify where the screen builds the top total, paid-by metadata, settlement rows, payer summary, calculation details, totals-balanced state, navigation, and Save CTA.

2. Verify that all amounts on the main screen and in calculation details use the receipt currency through the shared money formatter.

3. Refine the top area so the merchant, total, and paid-by metadata have a clearer hierarchy and consume less unnecessary vertical space.

4. Redesign the paid-by metadata row/card so it no longer looks like a tiny chip. It should have consistent participant avatar sizing, readable text, and enough visual weight to act as expense metadata.

5. Make the paid-by row/card communicate both the payer and participant count clearly without becoming as large as the settlement card.

6. Review the back navigation behavior on this screen and choose a predictable approach that does not overlap content. Prefer consistency with the rest of the app and standard Android navigation expectations over a custom floating control if the floating control creates layout issues.

7. Ensure the selected back behavior works correctly at the top of the screen, while scrolling, and when details content is open.

8. Tighten the `To settle up` card layout so it remains readable for 2 participants and still efficient for 7+ participants.

9. Keep settlement rows action-oriented. Rows should clearly communicate that one participant pays the payer, not expose internal debt terminology.

10. Make settlement row avatar sizing, name typography, amount typography, and vertical spacing consistent with the rest of the app.

11. Ensure long participant names do not break settlement rows. Names should truncate or wrap gracefully while preserving amount alignment.

12. Replace the payer summary sentence with a more scannable summary. The payer result should clearly show what the payer paid, what their own share is, and what they receive.

13. Avoid generic or overly narrative payer-summary copy. The user should be able to scan the payer result as financial rows or another compact structured summary.

14. Ensure payer summary copy handles participant names naturally and avoids awkward pronouns when a direct name is clearer.

15. Rework the `Calculation details` affordance so it feels like a cohesive trust/action block. The row/action and the `Totals balanced` status should visually belong together.

16. Decide whether calculation details should be integrated into `To settle up` as expandable participant rows, remain as a separate detail surface, or combine both approaches. Choose the approach that is most maintainable and gives the clearest UX.

17. If using expandable settlement rows, each row should remain compact when collapsed and reveal only useful participant-specific calculation details when expanded.

18. If using a separate calculation details surface, make sure it is fully scrollable, supports all participants, and does not freeze at a static position.

19. Calculation details must remain available for trust and debugging, but the main screen should still prioritize the final settlement actions.

20. Make calculation details more compact and easier to scan. The details should explain each participant's items, fees, share, paid amount, and final result without feeling like a raw debug dump.

21. Use user-facing result language in calculation details. Show `Pays`, `Receives`, or `Settled` instead of raw signed net balances.

22. Avoid showing negative money values to users unless they are strictly necessary for a debug-only state.

23. Rename or simplify overly technical labels in calculation details where possible. The labels should feel explanatory rather than internal.

24. Ensure the calculation details content has enough bottom padding so the last participant and last row are reachable above system navigation and any sticky footer.

25. Ensure the main screen content has enough bottom padding so the payer summary and details card are not hidden behind the Save CTA.

26. Keep the `Totals balanced` state, but make it subtle and contextual. It should build trust without competing with the settlement summary.

27. Add or preserve final consistency validation before enabling Save. The final screen should not allow saving if shares, paid amounts, or settlement transfers do not balance.

28. If final totals are invalid, show a clear visible reason and disable Save until the state is corrected.

29. Review the `Save expense` CTA label and confirm it accurately matches the next behavior. Keep it if it only saves the expense; adjust only if the flow actually creates, shares, or continues elsewhere.

30. Add loading and failure states for saving if they are not already handled. Repeated taps should be prevented while saving, and save failure should not lose user state.

31. Add accessibility descriptions for the total, paid-by metadata, each settlement row, payer summary, totals-balanced status, calculation details entry point, calculation detail rows, and Save CTA.

32. Ensure accessibility descriptions use action-oriented language and do not announce raw negative balances.

33. Validate behavior with small and large participant counts, including 2 participants, 7 participants, and 10+ participants.

34. Validate behavior with long merchant names and long participant names.

35. Validate behavior when no settlement transfers are needed, if this state is possible. Show a clear settled state instead of an empty settlement card.

36. Validate behavior for one payer and prepare the presentation layer to remain understandable if multiple payers are supported later.

37. Update or add tests for presentation-state mapping and final-screen validation.

38. Run project checks and fix regressions.

---

## UX requirements

The main screen should prioritize:

1. Merchant and total.
2. Who paid and how many people are involved.
3. Who pays whom.
4. The payer's final result.
5. Optional calculation details.
6. Final save action.

The main screen should avoid:

- raw negative balances;
- debug-like terminology;
- large metadata cards for small pieces of information;
- calculation details dominating the main view;
- sticky controls overlapping content;
- repeated or inconsistent avatar sizing;
- sentence-only summaries where structured financial rows are easier to scan.

---

## Expected main-screen outcome

The screen should communicate the final result in a structure similar to this, while allowing Codex to choose the exact UI composition:

```text
Review expense

Jose Pizarro
$106.59

Kehny paid - 7 people

To settle up
Vlad pays Kehny        $4.77
Danbya pays Kehny     $17.95
Rostislav pays Kehny  $14.44
Amy pays Kehny        $14.52
Liza pays Kehny       $23.81
Kate pays Kehny       $23.81

Payer summary
Kehny paid            $106.59
Kehny's share           $7.29
Kehny receives         $99.30

Calculation details
Totals balanced

Save expense
```

The exact structure does not need to match this example. The important outcome is clear hierarchy, scanability, and trust.

---

## Expected calculation-detail outcome

Calculation details should show participant-level breakdowns in a compact, scrollable, user-facing format.

Each participant should make clear:

- item share;
- fee share;
- total share;
- paid amount;
- result.

The result should read as:

- `Pays X`;
- `Receives X`;
- `Settled`.

The exact surface and layout are left to Codex.

---

## Validation

Run:

```text
./gradlew test
./gradlew detekt
./gradlew :app:assembleDebug
```

Add or update tests for:

- Review Expense uses the receipt currency everywhere.
- Paid-by metadata maps payer and participant count correctly.
- Settlement rows use action-oriented `pays` language.
- Payer summary exposes paid amount, payer share, and amount received.
- Calculation details are available and include every participant.
- Calculation details do not expose raw negative balances.
- Participant result labels map correctly to `Pays`, `Receives`, or `Settled`.
- Final totals-balanced state is shown only when final totals are valid.
- Save is disabled when final totals are inconsistent.
- Long names do not break presentation-state assumptions.
- No-settlement-needed state is handled if supported.
- Save loading and error states work if saving is asynchronous.

---

## Manual QA scenarios

### Scenario 1 - normal single payer with many participants

Given one payer and several participants who owe the payer.

Expected:

- Paid-by metadata is readable and visually consistent.
- `To settle up` rows are clear.
- Payer summary is structured and scannable.
- Save CTA is visible and not covering content.

### Scenario 2 - calculation details

Open calculation details.

Expected:

- Details are scrollable.
- Every participant can be reached.
- Rows use `Pays`, `Receives`, or `Settled`.
- No raw negative balances are shown.
- No content is hidden behind navigation or footer areas.

### Scenario 3 - long names

Use a long merchant name and long participant names.

Expected:

- The total stays readable.
- Settlement amounts remain aligned.
- Names truncate or wrap gracefully.
- Rows do not visually collapse.

### Scenario 4 - no settlement needed

If all participants are already settled or no transfers are needed.

Expected:

- The screen does not show an empty settlement list.
- A clear settled message is shown.
- Save behavior remains available if the expense is valid.

### Scenario 5 - invalid totals

Force or simulate an inconsistent final calculation state.

Expected:

- Save is disabled.
- A visible validation message explains what is wrong.
- The user is not allowed to save a broken expense.

### Scenario 6 - save behavior

Tap Save.

Expected:

- Repeated taps are blocked while saving.
- Success proceeds to the expected next destination.
- Failure shows an error and preserves the current review state.

---

## Done when

- The paid-by row/card no longer looks too small and uses consistent avatar sizing.
- The payer result is a scannable structured summary, not only a generic sentence.
- Settlement rows remain clear and compact with many participants.
- Calculation details are reachable, scrollable, and not static.
- Calculation details use friendly result labels and avoid raw negative balances.
- The chosen details interaction is maintainable and does not clutter the main screen.
- Back navigation is predictable and does not overlap content.
- Sticky Save CTA does not hide content.
- `Totals balanced` remains visible in a subtle trust-building way.
- Save is blocked for inconsistent final totals.
- Accessibility labels describe the final settlement clearly.
- Tests and manual QA scenarios pass.
