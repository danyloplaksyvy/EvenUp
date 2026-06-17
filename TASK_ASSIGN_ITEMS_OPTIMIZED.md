# TASK_ASSIGN_ITEMS_OPTIMIZED.md

Task:
EVU-ASSIGN-001 — Optimize assign-items fast path and quick actions

Goal:
Make assigning receipt items clear and fast by prioritizing the primary interaction: selected person + item tap = exclusive assignment.

Context:
The current Assign Items screen is functional but still exposes too much split complexity in the main flow. The `Split all equally` action appears after the receipt item card, which makes it hard to discover on long receipts. Item cards are too large, the item state is not explicit enough, and advanced split behavior competes with the fast assignment path. The desired UX is: select a person, tap an item, and assign that item exclusively to the selected person. Shared/custom splits should be secondary through `Customize split`.

Relevant files:
- feature/assign-items/**
- feature/assign-items-api/**
- feature/assign-items-impl/**
- core/designsystem/**
- domain/receipt/**
- domain/split/**

Scope:
You may edit:
- Assign items screen composables
- Assign items UI state and presentation mapping
- Assign items event handling
- Participant/person selector composables
- Item assignment row/card composables
- Split bottom sheet composables
- Split validation presentation state
- Tests for assignment and split behavior

Do not edit:
- Receipt scanner implementation
- Receipt parser implementation
- Data repository implementations
- Database entities unless an existing mapping bug blocks the task
- Network DTOs
- Payment/settlement modules outside the assign-items flow

Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.
- Keep assignment and split calculation rules outside Compose UI.
- Compose UI must consume presentation-ready state and dispatch user events.

Implementation requirements:
- Select the first participant by default when the Assign Items screen opens and participants are available.
- If the selected participant is removed or unavailable, select the next available participant.
- If no participants are available, show an empty participant state and disable item assignment.
- Render the participant row using a lazy horizontally scrolling list.
- Make the selected participant visually clear without making the chip oversized.
- Replace generic copy with stateful action-oriented copy:
  - When a participant is selected: `[Name] selected · Tap items to assign`
  - When no participant is selected: `Select a person to assign items`
  - When all items are assigned: `All items assigned`
- Move `Split all equally` above the item list, directly below the participant row.
- Treat `Split all equally` as a global quick action, not a per-item or list-footer action.
- Use compact secondary styling for `Split all equally`; it must not visually compete with the primary Continue CTA.
- If `Split all equally` would overwrite existing manual assignments, require confirmation or use the app's existing destructive/overwrite confirmation pattern.
- Replace large item cards with compact tappable item rows or compact cards.
- Remove duplicated centered item titles from item cards.
- Remove per-card subtotal rows; totals belong in the footer/summary, not in every item card.
- Make item assignment state explicit:
  - Unassigned item with selected participant: `Tap to assign to [Name]`
  - Single-owner item: `Assigned to [Name]`
  - Equal split item: `Split equally · [N] people · [amount] each`
  - Custom amount item: `Custom split · [N] people`
  - Percent split item: `Percent split · [N] people`
  - Unit split item: `Unit split · [assigned units] of [total units] units`
- Tapping the main item body must assign the currently selected participant as the sole owner of that item.
- If the item was previously assigned to another person, tapping with a different selected participant must reassign the item to the newly selected participant and remove the previous owner.
- If the item was previously shared, customized, percentage-based, or unit-split, tapping the item body with a selected participant must replace that split with a single-owner assignment.
- If no participant is selected, tapping an item must not assign it and must guide the user to select a participant first.
- `Customize split` must be the secondary action for advanced item sharing.
- Tapping `Customize split` opens the split bottom sheet without triggering single-owner reassignment.
- Keep `Customize split` visually secondary compared with the item body assignment action.
- Progress copy above the Continue CTA must be action-oriented:
  - Incomplete: `Assign [N] items to continue`
  - Complete: `All items assigned`
- Continue must remain disabled until all required item assignment rules are valid.
- Preserve accessibility semantics for participant chips, item rows, quick actions, and split controls.

Validation:
Run:
- ./gradlew test
- ./gradlew detekt
- ./gradlew :app:assembleDebug

Done when:
- The first participant is automatically selected when the screen opens.
- Participant selector uses a lazy horizontal list.
- `Split all equally` is visible above the item list and no longer appears after item cards.
- Tapping an item body assigns it exclusively to the selected participant.
- Reassigning an item from John to Amy removes John from that item and assigns Amy.
- Shared/custom/unit/percent splits are replaced by single-owner assignment when the item body is tapped with a selected participant.
- Advanced sharing remains available through `Customize split`.
- Item rows are compact and show explicit assignment state.
- Continue progress copy guides the next action.
- Tests pass and architecture constraints are preserved.

---

Task:
EVU-ASSIGN-002 — Improve split bottom sheet mode behavior and validation

Goal:
Make the split bottom sheet open in the correct mode, use full available height, and avoid confusing invalid initial states.

Context:
The split bottom sheet currently supports By units, Equal, Amount, and Percent, but mode selection and validation can be confusing. The sheet may open in a mode that does not match the current split, the mode selector can clip horizontally, and the By units state can show impossible guidance such as negative remaining units.

Relevant files:
- feature/assign-items/**
- feature/assign-items-api/**
- feature/assign-items-impl/**
- core/designsystem/**
- domain/split/**
- domain/receipt/**

Scope:
You may edit:
- Split bottom sheet composables
- Split mode selector composables
- Split validation presentation mapping
- Split edit UI state
- Split edit event handling
- Split calculation tests

Do not edit:
- Receipt scanner implementation
- Receipt parser implementation
- Data repository implementations
- Database entities unless required to fix a proven mapping issue
- Network DTOs

Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.
- Keep split validation and money calculations outside Compose UI.

Implementation requirements:
- Show the split bottom sheet fully expanded when opened.
- Open the bottom sheet in the mode that matches the current item assignment:
  - Equal split item opens in `Equal` mode.
  - Custom amount split item opens in `Amount` mode.
  - Percent split item opens in `Percent` mode.
  - Unit split item opens in `By units` mode.
  - Single-owner item with quantity greater than 1 may open in `By units` with the owner assigned all units.
  - Single-owner item with quantity equal to 1 may open in `Equal` or `By units`, but must not initialize an invalid over-assigned state.
  - Unassigned item opens in the last-used mode if available, otherwise a sensible default such as `Equal`.
- Never initialize By units with every participant assigned 1 unit when the item has only 1 total unit.
- Never show negative guidance such as `Assign -3 more units to save`.
- If units are over-assigned, show `Remove [N] units to save`.
- If units are under-assigned, show `Assign [N] more units to save`.
- Use proper bounded stepper controls for unit assignment.
- Disable decrement when a participant has 0 units.
- Disable increment when total assigned units already equals the item quantity, unless incrementing is part of an existing rebalancing behavior.
- Make the mode selector fit reliably:
  - Preferred: use a two-row 2x2 mode grid for `By units`, `Equal`, `Amount`, and `Percent`.
  - Alternative: keep a horizontal lazy selector but ensure the selected mode scrolls fully into view and does not appear clipped.
- Use clear mode labels:
  - `By units`
  - `Equal`
  - `Amount`
  - `Percent`
- Equal mode must use compact selectable rows with clear selected/unselected state and checkmarks.
- Equal mode should show calculated per-person amount when valid, for example `[N] people selected · [amount] each`.
- Amount mode must show neutral fields until the user edits fields or attempts to save.
- Amount mode must show remaining amount, for example `[amount] remaining`.
- Amount mode must use money-safe values only.
- Percent mode must use numeric percent placeholders such as `0%` or `%`, not vague text-only placeholders.
- Percent mode must show remaining percentage, for example `100% remaining`.
- If the current assignment is equal split, switching to Amount may prefill equal amounts.
- If the current assignment is equal split, switching to Percent may prefill equal percentages where possible.
- Do not show red error states before user interaction or save attempt.
- Save split must be enabled only when the current mode has a valid split.
- The close action must be in a stable and accessible location.

Validation:
Run:
- ./gradlew test
- ./gradlew detekt
- ./gradlew :app:assembleDebug

Done when:
- Bottom sheet opens fully expanded.
- Bottom sheet opens on the mode matching the current split.
- Mode selector does not clip selected or important options.
- By units never initializes in an impossible over-assigned state.
- Validation copy is actionable and never uses negative `assign more` wording.
- Equal mode has compact selectable rows with visible checkmarks.
- Amount and Percent modes do not show premature error styling.
- Save split enables only for valid split states.
- No monetary calculations use Float or Double.

---

Task:
EVU-ASSIGN-003 — Add assignment-flow tests for default selection, reassignment, and equal split shortcut

Goal:
Protect the optimized assign-items behavior with tests for the fast path, default participant selection, and global equal split action.

Context:
The assign-items flow now depends on specific interaction rules: first participant selected by default, item body tap assigns the selected participant as sole owner, and `Split all equally` is a global action above the item list. These rules must be covered by tests to prevent regressions.

Relevant files:
- feature/assign-items/**
- feature/assign-items-api/**
- feature/assign-items-impl/**
- domain/split/**
- domain/receipt/**

Scope:
You may edit:
- Unit tests for assign-items presentation/view model logic
- Unit tests for split assignment use cases
- Compose UI tests if the project already uses them for this feature
- Test fixtures/builders for participants, items, and split assignments

Do not edit:
- Production data implementations unless required by existing tests
- OCR/scanner/parser modules
- Network DTOs
- Database entities

Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use Float or Double for money.

Implementation requirements:
- Add or update tests for default participant selection:
  - Given participants exist, first participant is selected when Assign Items screen state is initialized.
  - Given selected participant is removed, next available participant is selected.
  - Given no participants exist, selected participant is null and item assignment is disabled.
- Add or update tests for item tap assignment:
  - Given John is selected and Pizza is unassigned, tapping Pizza assigns Pizza to John.
  - Given Pizza is assigned to John and Amy is selected, tapping Pizza reassigns Pizza to Amy and removes John.
  - Given Pizza has equal/custom/percent/unit split and Amy is selected, tapping Pizza replaces the split with Amy as sole owner.
  - Given no participant is selected, tapping Pizza does not assign Pizza.
- Add or update tests for `Customize split`:
  - Tapping `Customize split` opens split editing without triggering single-owner reassignment.
  - Existing split mode is preserved when opening the sheet.
- Add or update tests for `Split all equally`:
  - Action applies equal split to all items among all participants.
  - If manual assignments exist, overwrite behavior follows the chosen confirmation/policy.
- Add or update tests for progress state:
  - Incomplete assignments show `Assign [N] items to continue`.
  - Complete assignments show `All items assigned` and enable Continue.

Validation:
Run:
- ./gradlew test
- ./gradlew detekt
- ./gradlew :app:assembleDebug

Done when:
- Default participant selection is tested.
- Exclusive item assignment and reassignment are tested.
- Advanced split replacement through item body tap is tested.
- `Customize split` does not accidentally trigger reassignment.
- `Split all equally` behavior is tested.
- Progress and Continue enabled states are tested.
- Tests pass without violating module boundaries.
