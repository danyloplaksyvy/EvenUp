# AI Expense Input — UI/UX Polish Requirements

## Purpose

Polish the recently added natural-language expense flow so it feels consistent with the rest of EvenUp, reduces unnecessary visual noise, and gives the user a clear path from description to review.

This document contains client-side product and UX requirements only. Codex should choose the implementation approach.

---

## Evaluation summary

The current flow is functional, but it feels like an internal editing tool rather than a finished consumer experience.

The main problems are:

- The **Add expense** screen keeps stale text and result state after an expense has been saved.
- The natural-language composer uses nested gray surfaces and lacks a strong visual focus.
- Processing is presented as another content card instead of a temporary modal state.
- Parsed information is duplicated on the Add expense screen in an **Extracted details** card.
- The full **Review details** screen is a dense raw form with comma-separated and pipe-separated data.
- The review path does not follow the button-first and bottom-sheet patterns already used across EvenUp.
- Defaulted values are incorrectly presented as warnings.
- Internal field names such as `transactionDate` are visible to the user.

The target experience should feel lightweight:

1. Describe the expense.
2. Wait briefly while EvenUp organizes it.
3. Review or correct it in a focused bottom sheet.
4. Continue into the established expense review flow.
5. Save and return to a clean Add expense state.

---

# 1. Priority order

## P0 — Required before considering the feature polished

1. Clear all AI input state after a successful expense save.
2. Replace the processing card with a blocking overlay loading state.
3. Remove the **Extracted details** card from the Add expense screen.
4. Replace the raw full-screen **Review details** form with a structured bottom-sheet review experience.
5. Stop showing defaults as warnings.
6. Remove internal field names from all user-facing copy.

## P1 — Strong visual and interaction improvements

1. Redesign the natural-language composer.
2. Use a button-first **Review expense** action consistent with existing EvenUp action cards.
3. Improve hierarchy, spacing, button states, and copy.
4. Distinguish actionable issues from informational defaults.

## P2 — Final quality improvements

1. Refine accessibility and keyboard behavior.
2. Improve cancellation and error recovery.
3. Normalize capitalization and display formatting.
4. Polish transitions between input, processing, review, and saved states.

---

# 2. Add expense state lifecycle

## 2.1 Clean initial state

The Add expense screen must open in a clean state when there is no active unsaved expense.

The clean state contains:

- Empty natural-language input.
- Idle microphone.
- Disabled send action.
- No processing indicator.
- No extracted summary.
- No clarification message.
- No previous validation or network error.
- No open review bottom sheet.

## 2.2 State after successful save

After an expense is saved successfully, returning to **Add expense** must show the clean initial state.

The following must not remain from the saved expense:

- Description text or voice transcript.
- Parsed title, total, people, payer, items, fees, or split.
- Clarification answers.
- Review warnings.
- Processing or success state.
- Any review-sheet edits.

This applies when the user:

- Taps **Add another expense**.
- Returns to Add expense after the saved confirmation screen.
- Starts a new expense within the same app session.

A completed expense must never appear as an editable draft for the next expense.

## 2.3 Leaving an unsaved expense

If the user has unsaved text or parsed edits and tries to close the flow, show a discard confirmation with:

- **Keep editing**
- **Discard**

Discarding returns to the clean state.

---

# 3. Natural-language composer redesign

## 3.1 Visual direction

The composer should be the visual focus of the Add expense screen without looking like a large gray form nested inside another gray card.

Use a premium, minimal composition consistent with EvenUp:

- White or elevated surface for the editable area.
- Subtle neutral border in the idle state.
- Clear primary-color focus treatment when active.
- One container hierarchy rather than an outer gray card plus an inner gray field.
- Comfortable internal spacing.
- Rounded corners consistent with other inputs and cards.
- Near-black entered text and muted placeholder text.

Avoid:

- Gray-on-gray nested surfaces.
- Excessively tall empty input areas.
- Low-contrast send and microphone actions.
- Decorative elements that compete with the input.

## 3.2 Recommended composition

The composer should contain:

- Section label: **Describe the expense**.
- Multiline editable input.
- Microphone action.
- Send action.
- A compact secondary line for language and character count only when useful.

The input should expand naturally for multiple sentences within a reasonable height. Long content may scroll inside the field rather than continually pushing all fallback actions off-screen.

## 3.3 Placeholder copy

Use a natural example rather than instructional syntax.

Recommended placeholder:

> Dinner with John and Maya. I paid €64 and we split everything equally.

The placeholder must disappear when the user enters text and must not be mistaken for actual content.

## 3.4 Microphone action

- Idle microphone is visually available but secondary to Send.
- Active recording state must be unmistakable.
- Stopping recording leaves the transcript editable.
- Voice input must never submit automatically.
- A speech failure must preserve any usable transcript.

## 3.5 Send action

- Disabled when the input is blank.
- High-contrast and clearly tappable when enabled.
- Disabled while processing to prevent duplicate requests.
- Must have a clear accessibility label such as **Organize expense**.

## 3.6 Defaults helper

Do not display a detached line such as:

> Defaults: Danya · USD

Instead, either omit it entirely or show a subtle contextual helper beneath the composer:

> Uses Danya and USD when they are not mentioned.

This helper is informational, not a warning, and should remain visually low emphasis.

---

# 4. Processing state

## 4.1 Replace the loading card

Do not insert an **Organizing expense…** card into the normal page content.

Processing is a temporary modal state and should be presented as an overlay above the current screen.

## 4.2 Overlay behavior

While processing:

- Keep the current Add expense screen visible beneath a scrim.
- Prevent interaction with the composer and fallback actions.
- Show a compact centered progress presentation.
- Keep the original entered text visible in the background.
- Prevent repeated submission.

Recommended copy:

**Organizing expense**

> Finding people, amounts, items, and split details…

Use an explicit **Cancel** action rather than an unexplained close icon.

## 4.3 Cancellation

When the user cancels processing:

- Close the overlay.
- Restore the editable composer.
- Preserve the exact entered text.
- Do not clear a voice transcript.
- Do not display a false error.

## 4.4 Long processing

If processing takes noticeably longer than expected, update the supporting message so the interface does not appear frozen.

The user should continue to have a clear Cancel action.

## 4.5 Failure

On a recoverable failure:

- Remove the loading overlay.
- Keep the description editable.
- Show a concise inline message near the composer.
- Offer **Try again**.
- Keep **Scan receipt** and **Enter manually** available.

Do not use technical error details.

---

# 5. Remove Extracted details from the main screen

## 5.1 No duplicated parsed card

Do not show a large **Extracted details** card below the composer.

This card creates several problems:

- It duplicates content that must still be reviewed elsewhere.
- It makes the Add expense screen longer and visually unstable.
- It exposes implementation-style labels and warnings.
- It introduces a second review surface before the real preview.
- It weakens the hierarchy between description, review, and fallback actions.

## 5.2 Successful processing behavior

When processing succeeds:

1. Close the loading overlay.
2. Open the review bottom sheet automatically.
3. Present the organized expense in the bottom sheet.
4. Keep the original description available behind the sheet.

## 5.3 Reopening review after dismissal

If the user dismisses the review sheet without continuing, show one button-first action on the Add expense screen:

**Review expense**

> Check people, items, amounts, and split

The action must use the same visual language as existing EvenUp navigation/action cards:

- Leading icon.
- Clear title.
- Supporting description.
- Trailing chevron.
- Full-row tap target.

Do not recreate the extracted detail list on the main screen.

---

# 6. Review bottom sheet

## 6.1 Replace the raw Review details screen

Remove the full-screen raw form shown in the current implementation.

Do not require users to edit:

- Comma-separated participants.
- Pipe-separated item syntax.
- Free-text fee syntax.
- A payer as an unrestricted text field.
- Currency as an unrestricted text field.

These controls look unfinished and increase the risk of invalid edits.

## 6.2 Bottom-sheet purpose

The bottom sheet is a focused checkpoint between natural-language interpretation and the established expense creation/review flow.

It should let the user:

- Confirm the organized result quickly.
- Identify missing or conflicting information.
- Edit a specific section without parsing a dense form.
- Continue once the expense is sufficiently complete.

## 6.3 Sheet structure

Recommended hierarchy:

### Header

- Title: **Review expense**
- Short status message:
  - **Ready to continue**, or
  - **1 detail needs attention**
- Close affordance.

### Expense summary

Show a compact summary of:

- Title.
- Date.
- Currency and total.
- Payer.
- Participant count.

### Sections

Use separate scan-friendly sections:

1. **Expense details**
2. **People and payer**
3. **Items and prices**
4. **Split**
5. **Fees and discounts**, only when present or explicitly added

Each section should show a concise summary and a direct **Edit** action.

### Bottom action

Use a persistent primary action for forward progress:

- **Continue** when the next step is the established expense flow.
- Disable it only when an actionable required detail is missing or invalid.

A secondary action may allow editing the original description.

## 6.4 Editing behavior

Editing should be progressive rather than presenting every field at once.

When the user taps Edit:

- Open the relevant focused editor.
- Show only controls required for that section.
- Preserve edits in every other section.
- Return to the review summary after applying the edit.

## 6.5 Expense details

Show user-friendly controls for:

- Title.
- Date.
- Currency.
- Total.

Use appropriate selection or formatted input behavior rather than exposing raw internal formats as the primary interaction.

The date may be displayed in a readable localized form. An implementation-oriented label such as `Date (YYYY-MM-DD)` should not dominate the interface.

## 6.6 People and payer

- Display participants as individual rows, chips, or cards.
- Normalize accidental capitalization differences where appropriate, for example `john` should be presented as `John` unless the user deliberately uses another spelling.
- Let the user add, remove, and rename one participant at a time.
- Select the payer from the participant list.
- Do not use a comma-separated participant text field.

## 6.7 Items and prices

Display items as structured rows containing:

- Item name.
- Quantity.
- Price when known.
- Assignment or split summary when relevant.

Allow the user to add, edit, and remove items individually.

Do not use lines such as:

> pizza | 1

The interface should present the structure; the user should not need to know a text format.

## 6.8 Overall-total-only state

When only the overall total is known:

- Show a clearly labelled option such as **Split using the overall total**.
- Explain that item names are descriptive and do not have individual prices.
- Keep the total visible.
- Allow the user to add item prices later.
- Do not show missing item prices as errors for an equal split.

When an item-specific split requires prices, explain which items need a price and why.

## 6.9 Fees and discounts

- Show each fee or discount as a structured row.
- Let the user add or edit the label and amount through focused controls.
- Do not use a free-text `label | amount` format.
- Hide an empty fees section from the summary unless the user chooses to add one.

---

# 7. Continue into the established expense flow

## 7.1 Consistency requirement

After the bottom-sheet checkpoint, continue into the existing EvenUp expense creation and review experience rather than introducing a parallel AI-only workflow.

The user should encounter familiar:

- Button hierarchy.
- Participant presentation.
- Item rows.
- Split controls.
- Bottom action bar.
- Final Review expense screen.
- Save expense action.

## 7.2 Button-first review entry

The main Add expense screen should use a single **Review expense** action card when a parsed draft exists and the bottom sheet is closed.

This action should be visually consistent with **Scan receipt** and **Enter manually**, rather than placing a button inside a separate extracted-details card.

## 7.3 Final save

The final review screen remains the place where the user explicitly saves the expense.

Natural-language processing must never save automatically.

---

# 8. Defaults, inference, and warnings

## 8.1 Use defaults silently

When the user does not explicitly provide the following, use the available defaults without presenting them as warnings:

- Personal name.
- Default currency.
- Current date.

Examples:

- “Me and John had lunch” may use the stored personal name.
- “Lunch was 78” may use the default currency.
- An expense without a date may use today’s date.

These values remain editable in review.

## 8.2 Title behavior

When the user does not provide a formal title, derive a simple human-readable title from the description where possible, such as **Lunch** or **Dinner**.

Use a neutral fallback such as **Expense** when no useful title can be derived.

Do not show a warning merely because the title was generated.

## 8.3 What should be a warning

Warnings are reserved for cases requiring user attention, such as:

- Payer cannot be determined.
- Two saved participants could match the same spoken name.
- Total conflicts with item prices.
- A required item price is missing for an item-specific split.
- Split instruction is unclear or incomplete.
- A value is invalid and blocks continuing.

## 8.4 What should not be a warning

Do not warn for:

- Current date applied automatically.
- Default currency applied automatically.
- Stored personal name used for “I” or “me.”
- A reasonable generated title.
- Formatting normalization.

## 8.5 Warning copy

Never expose internal keys such as:

- `transactionDate`
- `payerId`
- `currencyCode`

Use human language:

- **Choose who paid**
- **Check which John you meant**
- **Add a price for Pizza to split by items**
- **The item prices do not match the total**

## 8.6 Visual treatment

- Defaulted or inferred values use normal styling.
- Optional informational text uses neutral secondary styling.
- Amber is reserved for actionable uncertainty.
- Red is reserved for invalid or failed states.
- Green is reserved for success or confirmed readiness.
- Do not communicate state through color alone.

---

# 9. Main-screen hierarchy

The Add expense screen should remain focused and stable.

Recommended order:

1. Top bar.
2. **Add expense** title and helper copy.
3. Natural-language composer.
4. **Review expense** action card, only when a parsed unsaved result exists and the sheet is closed.
5. **Scan receipt** fallback.
6. **Enter manually** fallback.
7. Existing low-emphasis footer, when retained.

Do not insert result summaries or loading cards between these sections.

Reduce excessive vertical spacing so the primary and fallback actions remain discoverable on common phone sizes.

---

# 10. Interaction details

## 10.1 Keyboard

- Keep the composer editable after speech transcription.
- The keyboard action may organize the expense when the text is valid.
- Avoid covering the send action or essential clarification controls.
- Restore focus predictably after dismissing the review sheet.

## 10.2 Back behavior

- Back closes a focused editor before closing the review sheet.
- Back closes the review sheet before leaving Add expense.
- Leaving with unsaved content requires discard confirmation.
- Back during processing should behave consistently with Cancel and preserve the description.

## 10.3 Accessibility

- Announce the start and completion of processing.
- Announce the recording state.
- Give microphone, send, cancel, review, edit, and continue actions explicit labels.
- Ensure action cards and icon buttons have sufficient touch targets.
- Keep logical focus order within the bottom sheet.
- Support large text without clipping section summaries or actions.

---

# 11. Recommended user flow

## Empty state

1. User opens Add expense.
2. Composer is empty and ready.
3. Scan receipt and Enter manually are visible below.

## Text or voice entry

1. User types or records a description.
2. Transcript remains editable.
3. User taps Send.

## Processing

1. Loading overlay appears.
2. Background remains visible but non-interactive.
3. User may cancel without losing text.

## Successful organization

1. Overlay closes.
2. Review bottom sheet opens automatically.
3. Default name, currency, and date appear normally.
4. Only genuine unresolved issues are highlighted.

## Review

1. User scans structured sections.
2. User edits only the section that needs correction.
3. User taps Continue.
4. Existing expense flow handles detailed assignment and final review as needed.

## Save

1. User explicitly saves from the final review screen.
2. Saved confirmation is shown.
3. Starting another expense returns to a completely clean Add expense state.

---

# 12. Acceptance criteria

## Reset behavior

- [ ] Saving an expense clears the natural-language description and transcript.
- [ ] Saving clears parsed data, clarifications, warnings, errors, and review edits.
- [ ] Add another expense always opens the clean state.
- [ ] A completed expense never appears as the next expense draft.
- [ ] Unsaved work still requires discard confirmation.

## Composer

- [ ] The composer no longer uses a gray card nested around a gray text field.
- [ ] The editable surface has clear idle and focused states.
- [ ] Microphone and Send are easy to distinguish and tap.
- [ ] Send is disabled for blank input and while processing.
- [ ] Voice transcripts remain editable and are not auto-submitted.
- [ ] The optional defaults helper is neutral and visually secondary.

## Processing

- [ ] Processing appears as an overlay rather than an in-page card.
- [ ] The underlying screen is visible but cannot be interacted with.
- [ ] The overlay has clear progress copy and a labelled Cancel action.
- [ ] Cancelling preserves the exact description or transcript.
- [ ] Duplicate submissions cannot be triggered while processing.
- [ ] Recoverable failures return to editable input with Retry.

## Main screen

- [ ] The Extracted details card is removed.
- [ ] Parsed details are not duplicated on the Add expense screen.
- [ ] Successful processing automatically opens the review bottom sheet.
- [ ] Dismissing the sheet leaves one button-first Review expense action.
- [ ] Review expense visually matches the existing action-card language.
- [ ] Scan receipt and Enter manually remain visible fallbacks.

## Review experience

- [ ] The raw full-screen Review details form is removed.
- [ ] Review is presented as a structured bottom sheet.
- [ ] Participants are not edited as comma-separated text.
- [ ] Items are not edited using pipe-separated lines.
- [ ] Fees are not edited using `label | amount` text syntax.
- [ ] The payer is selected from participants rather than entered as unrestricted text.
- [ ] Sections can be edited independently.
- [ ] Applying an edit returns to the review summary.
- [ ] A persistent Continue action is used for forward progress.
- [ ] Final saving still happens explicitly in the established Review expense flow.

## Defaults and warnings

- [ ] Stored personal name is used without a warning when appropriate.
- [ ] Default currency is used without a warning when currency is omitted.
- [ ] Today’s date is used without a warning when date is omitted.
- [ ] A generated title is not automatically treated as uncertain.
- [ ] Internal field names never appear in user-facing text.
- [ ] Amber warnings appear only for actionable uncertainty.
- [ ] Missing item prices do not block a total-only equal split.
- [ ] Item prices are required when an item-specific split depends on them.

## Accessibility and polish

- [ ] Processing and recording states are announced.
- [ ] Every icon-only action has an accessible label.
- [ ] Review content remains usable with large text.
- [ ] Touch targets are large enough for reliable use.
- [ ] Back and dismissal behavior never silently loses unsaved work.
- [ ] Names, dates, currency, and amounts use consistent user-facing formatting.

---

## Definition of done

The feature is complete when the user can move from a clean Add expense screen to a structured review and into the established expense flow without seeing stale data, raw delimiter-based forms, duplicated extracted summaries, internal field names, or warnings for normal default values.