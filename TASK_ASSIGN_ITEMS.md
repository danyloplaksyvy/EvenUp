# TASK_ASSIGN_ITEMS.md

## Task:
EVU-ASSIGN-001 — Simplify Assign Items screen into direct tap-to-assign flow

## Goal:
Create a fast, low-friction Assign Items screen where the primary action is selecting one person and tapping receipt items to assign them, while keeping advanced split customization available only when needed.

## Context:
The current Assign Items screen has the correct functional pieces, but it exposes too much split complexity too early. The UI treats normal incomplete states as warnings, uses oversized controls, and makes the main assignment flow compete with advanced split modes.

The intended primary flow is:
1. User selects a person.
2. User taps an item.
3. The selected person is assigned to that item.
4. If another person is selected and the same item is tapped, the item is reassigned to the new person.
5. Advanced split options are available through item customization, not the default path.

## Scope:
You may edit:
- Assign Items screen composables
- Assign Items UI state
- Assign Items reducer / ViewModel / presenter
- Split item bottom sheet composables
- Participant selector composables
- Item assignment event handling
- Split validation state mapping
- Design system components if needed for reusable chips, steppers, segmented controls, bottom sheets, and list rows

Do not edit:
- Receipt scanning implementation
- Receipt parsing implementation
- Data repository implementations
- Network DTOs
- Database entities unless the existing model cannot represent the required assignment behavior
- Navigation graph unless the existing route does not expose required callbacks correctly

## Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.
- Keep assignment and split validation logic outside Composables.
- Compose UI must consume presentation-ready state and dispatch explicit events.
- Money must use existing Money/value object abstractions, integer minor units, or BigDecimal depending on current project conventions.

---

# Implementation requirements

## 1. Main assignment model

- Make direct item assignment the primary interaction.
- A selected participant must be required before direct assignment by item tap.
- When a participant is selected and the user taps an unassigned item:
  - assign the selected participant to that item.
- When a participant is selected and the user taps an item already assigned to a different single participant:
  - reassign the item to the newly selected participant;
  - remove the previous single participant assignment from that item.
- Example:
  - John is selected.
  - User taps Pizza.
  - Pizza is assigned to John.
  - Amy is selected.
  - User taps Pizza.
  - Pizza is reassigned to Amy.
  - John is no longer assigned to Pizza.
- Do not create a multi-person split from repeated direct taps on the item.
- Multi-person item splits must be created only through the customize/manual split flow.
- If an item already has a multi-person/custom split and the user taps it with a selected participant:
  - do not silently destroy the custom split;
  - either open the customize split sheet or show a confirmation before replacing it with a single-person assignment.
- Preserve deterministic state updates and add tests for assignment and reassignment behavior.

## 2. Participant row must be lazy

- Render the row of people with a lazy horizontal list.
- Use `LazyRow` or the existing design system equivalent.
- The participant row must support more participants than fit on screen.
- Avoid rendering all participants eagerly when the group is large.
- Preserve stable keys for participant items.
- Remove the ambiguous avatar-plus-ellipsis participant chip pattern.
- If overflow behavior is still needed, make it a clear action such as `More` or use horizontal scrolling only.
- Selected participant state must be visually clear but not oversized.
- Participant chips must remain accessible with selected/unselected semantics.

## 3. Instruction and selected-person state

- Replace the static oversized instruction with state-aware helper copy.
- Before a participant is selected, show concise guidance such as:
  - `Select a person, then tap their items.`
- After a participant is selected, show stateful copy such as:
  - `John selected · Tap items he had`
  - or `Assigning to John`
- Do not use large passive instructional text that consumes excessive vertical space.
- The selected participant should be obvious from both chip styling and helper text.

## 4. Item cards and item rows

- Make item cards more compact and optimized for assignment.
- Each item must show:
  - item name;
  - quantity/unit information when available;
  - item total;
  - assignment status.
- The whole item row/card must look tappable.
- Use a subtle chevron, pressed/ripple state, or equivalent affordance.
- Replace excessive warning text for unassigned items with neutral state text.
- Do not show warning boxes for normal initial unassigned state.
- Use warning/error messaging only after the user tries to continue with unassigned required items.
- Show assignment status clearly:
  - `Unassigned`
  - participant name for single-person assignment, for example `John`
  - shared/custom summary for multi-person split, for example `Split between John and Amy` or `Custom split`
- Rename item action from `Edit split` to `Customize split`.
- `Customize split` should be visually secondary to the direct tap-to-assign flow.

## 5. Split all equally action

- Keep `Split all equally`, but reduce its visual dominance.
- It must not compete with the main assignment flow.
- Prefer a secondary/tonal/text action instead of a large full-width outlined primary-looking button.
- Place it where it is discoverable but not the first dominant action after the participant selector.
- If no assignments exist, it may be presented as a helpful shortcut.
- If assignments already exist, triggering it must either:
  - replace all existing assignments only after confirmation; or
  - clearly state that current assignments will be overwritten.

## 6. Bottom sheet should open fully

- The split customization bottom sheet must open in the full expanded state by default.
- Do not open it half-expanded if content requires scrolling or if the primary action is below the fold.
- The sheet must respect system insets and navigation bar padding.
- The sheet content must be scrollable if it exceeds available height.
- The Save button must remain reachable.
- Do not let the Save button or bottom system navigation cover fields, validation messages, or participants.
- Use the app/design system modal bottom sheet conventions where available.

## 7. Bottom sheet header and close behavior

- Move the close action into the bottom sheet header row.
- Header should follow this structure:
  - title: `Split item`;
  - close icon/action on the trailing side.
- Keep drag handle only if it is part of the design system bottom sheet pattern.
- The close button must have an accessibility label such as `Close split editor`.
- Closing the sheet with unsaved changes should preserve the current project behavior:
  - either discard unsaved changes explicitly;
  - or ask for confirmation if the app already has that pattern.

## 8. Split mode labels

- Rename split modes to be clearer:
  - `Units` -> `By units`
  - `Shared` -> `Equal split`
  - `Custom` -> `Amount`
  - `%` -> `Percent`
- Avoid cryptic labels in the segmented control.
- Keep all modes accessible by text label, not icon-only or symbol-only.
- Ensure segmented control can handle localized text without clipping.

## 9. By-units mode

- Use proper bounded stepper controls for each participant.
- The stepper should visually group minus, value, and plus together.
- Each control must have adequate touch target size.
- Minus must be disabled when participant units are zero.
- Plus must be disabled when assigning more units would exceed the item quantity.
- Assigned units must never exceed available units.
- Show action-oriented helper text:
  - `Assign 1 unit to save`
  - `Assign 2 more units to save`
  - `All units assigned`
- Do not show rule-like warnings before the user interacts or tries to save.
- For items with quantity 1, consider defaulting to direct single-person assignment on the main screen and reserve By-units mode for customization.

## 10. Equal split mode

- The Equal split mode must clearly show selected/unselected participant states.
- Participant rows/cards must include an explicit checkmark, checkbox, or selected visual state.
- Tapping a participant toggles inclusion in the equal split.
- Require at least two participants for Equal split.
- Show action-oriented helper text:
  - `Select at least 2 people`
  - `2 people selected · €6.00 each`
- Do not show warning boxes before the user interacts or tries to save.
- Save is enabled only when the selected people satisfy the split rule.

## 11. Amount mode

- Do not show red/error fields on initial empty state.
- Amount fields must use neutral styling until the user has interacted or attempted to save.
- Show each participant with an amount input.
- Use safe money input handling; do not use Float or Double.
- Show progress/remain text:
  - `€12.00 remaining`
  - `€0.00 remaining`
  - or `Assigned: €8.00 of €12.00`
- Show validation only after input interaction or attempted save.
- Error copy should be action-oriented, for example:
  - `Assign the remaining €4.00`
  - `Reduce assigned amount by €2.00`
- Save is enabled only when custom amounts exactly equal the item total.

## 12. Percent mode

- Do not show red/error fields on initial empty state.
- Percent fields must use neutral styling until the user has interacted or attempted to save.
- Use clear percent input formatting, for example `0%` or a trailing `%` indicator.
- Show progress/remain text:
  - `100% remaining`
  - `0% remaining`
  - or `Assigned: 75% of 100%`
- Show validation only after input interaction or attempted save.
- Error copy should be action-oriented, for example:
  - `Assign the remaining 25%`
  - `Reduce assigned percentage by 10%`
- Save is enabled only when percentages add exactly to 100%.
- Avoid floating point percentage calculations where they can create rounding errors.

## 13. Validation states

- Treat initial incomplete states as neutral, not warning/error.
- Use validation levels consistently:
  - neutral helper for untouched incomplete state;
  - warning after attempted continue/save;
  - error for invalid user-entered values;
  - success/ready state when valid.
- Main screen Continue helper copy should be action-oriented:
  - `Assign 1 item to continue`
  - `Assign 3 items to continue`
  - `All items assigned`
- Continue button should remain disabled until required items are assigned or intentionally skipped according to existing business rules.
- Do not use red styling for empty amount/percent fields before interaction.

## 14. Bottom CTA behavior

- Keep the bottom Continue CTA sticky.
- Ensure content has enough bottom padding so item cards and summary text are never covered by the CTA.
- Disabled CTA must not visually dominate the screen.
- CTA enabled/disabled state must have accessible semantics.
- Bottom content must work with gesture navigation and 3-button navigation.

## 15. Density and layout polish

- Reduce unnecessary vertical spacing across the screen.
- Make participant chips smaller and more proportional to the task.
- Make item cards less visually heavy.
- Avoid large instructional/warning blocks for normal states.
- Preserve touch target minimums while improving density.
- Use Material 3 sizing conventions where applicable.
- Align typography, shape, color, and spacing with the app design system.

---

# Validation

Run:
- ./gradlew test
- ./gradlew detekt
- ./gradlew :app:assembleDebug

Add or update tests for:
- selecting a participant and tapping an unassigned item assigns that participant;
- tapping the same item with a different selected participant reassigns it to the new participant;
- direct item tap does not create multi-person assignment;
- custom/multi-person split is not silently overwritten by direct tap;
- LazyRow participant rendering uses stable participant keys;
- amount split validates exact total;
- percent split validates exact 100%;
- unit split validates exact assigned units;
- Continue remains disabled while required items are unassigned;
- Save split remains disabled until the selected split mode is valid.

---

# Done when:

- Assign Items screen uses direct `select person -> tap item` assignment as the primary flow.
- Tapping an item with a selected participant assigns that participant to the item.
- Tapping the same item with another selected participant reassigns the item to the new participant and removes the previous single-person assignment.
- Multi-person splits are created only through Customize split.
- Existing custom/multi-person splits are not silently destroyed by direct tap.
- Participant row is implemented as a lazy horizontal list with stable keys.
- The ambiguous overflow participant chip is removed or replaced with a clear pattern.
- Split customization bottom sheet opens fully expanded by default.
- Bottom sheet modes use clear labels: By units, Equal split, Amount, Percent.
- Incomplete untouched states are neutral, not warnings/errors.
- Amount and Percent fields are not red before interaction or attempted save.
- Equal split mode has clear selected/unselected states.
- By-units mode uses bounded stepper controls.
- Main screen item cards are compact, tappable, and clearly show assignment status.
- Continue helper copy tells the user what action remains.
- Bottom CTA does not cover content.
- All validation commands pass.
