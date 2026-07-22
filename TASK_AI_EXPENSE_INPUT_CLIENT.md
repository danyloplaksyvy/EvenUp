# AI Expense Input — Client Requirements

## Purpose

Add a faster way to create a shared expense from natural-language text or voice on the existing **Add expense** screen.

The user describes the expense in their own words. EvenUp extracts the relevant details, asks for missing information when necessary, shows a complete editable preview, and saves only after explicit confirmation.

This document defines product behavior and client-side requirements only. Implementation details are intentionally left to Codex.

---

## Product goals

- Make creating a common shared expense faster than scanning a receipt or entering every field manually.
- Let users speak or type naturally, including multiple sentences.
- Never invent prices, participants, payers, or split details.
- Ask only for information required to create a valid expense.
- Keep the user in control through an editable preview before saving.
- Preserve receipt scanning and manual entry as visible fallback options.

## Non-goals for the first version

- Automatically saving an expense without review.
- Estimating missing prices from typical market prices.
- Multiple payers.
- Uploading or retaining voice recordings.
- Full multilingual support.
- Replacing the existing receipt scanning or manual entry flows.

---

# 1. Add expense screen

## 1.1 Placement

The natural-language input must appear directly on the existing **Add expense** screen as the primary action.

The screen order should be:

1. Screen title.
2. Natural-language expense input.
3. Receipt scanning fallback.
4. Manual entry fallback.

Do not add example chips such as Dinner, Trip, Rent, or Taxi.

Do not add the “Secure & private” information card from the mockup.

## 1.2 Text input

The input must:

- Support multiple lines and multiple sentences.
- Accept natural phrasing rather than requiring a fixed command format.
- Remain editable at all times before submission.
- Preserve the entered text after validation or processing errors.
- Provide a clear send action.
- Use a white, outlined, high-contrast writing surface rather than a nested gray card.
- Keep normal text contrast while processing even though editing is temporarily read-only.

Example:

> John and Danya were at dinner. We had two pizzas and one litre of apple juice. Danya paid. Split everything equally.

## 1.3 Send action

- Processing begins only after the user taps the send button.
- The send button is disabled when the input is blank.
- Repeated taps must not create duplicate processing requests.
- The user must see a clear processing state.
- The original text remains visible and recoverable while processing.

---

# 2. Voice input

## 2.1 Recording interaction

- Tapping the microphone starts recording.
- Tapping it again stops recording.
- The screen must clearly indicate when recording is active.
- The user must be able to cancel recording.

## 2.2 Transcript behavior

- Voice is converted into text.
- When recording stops, the transcript is placed in the same editable input field.
- The transcript must not be submitted automatically.
- The user can edit the transcript before tapping send.
- Existing typed text must not be unexpectedly deleted when voice input is used.

## 2.3 Privacy

- Audio is used only to produce the transcript.
- Audio is discarded after transcription.
- Only the editable text is used to create the expense.

## 2.4 Voice failure

When voice recognition fails:

- Preserve any usable partial transcript.
- Show a clear, non-blocking error.
- Allow the user to retry recording.
- Allow the user to continue by typing.

---

# 3. Supported natural-language information

The first version should understand the following information when explicitly provided:

- Expense title or occasion.
- Participant names.
- The payer.
- Item names.
- Integer item quantities.
- Individual item prices.
- Overall expense total.
- Currency.
- Equal split across all participants.
- A whole item assigned to one participant.
- An item shared by named participants.
- Percentage, ratio, and arbitrary custom-amount item splits when explicitly stated.
- Explicit tax, tip, service fee, or discount.

Percentage, ratio, and custom-amount splits use integer minor units or basis-point domain rules and still require all necessary item prices. They can also be corrected manually from the preview.

## 3.1 Current user identity

Until a full profile feature exists:

- Use the locally stored personal name for phrases such as “I paid” or “split between me and John.”
- Do not display a generic “You” participant when a stored personal name is available.
- If no usable personal name exists, ask the user to provide one before the expense can be completed.

## 3.2 Saved participant matching

- An unambiguous exact match with a locally saved participant can be selected automatically.
- Similar or ambiguous names must require confirmation.
- Names such as “John” and “Johnny” must not be silently merged.
- The user must be able to choose separate people when the names refer to different participants.

---

# 4. Missing information and clarification

## 4.1 Required information before final preview

A final settlement preview requires:

- At least two participants.
- A payer.
- A currency.
- A positive total or sufficient item prices.
- A valid split arrangement.

Do not place an extracted-details card inline on the Add expense screen. When partial extraction exists, show a secondary **Review all details** action that opens the structured review hub. Never present an incomplete result as ready to save.

## 4.2 Clarification behavior

Use a hybrid clarification experience:

- Ask one clear conversational question at a time.
- Also provide a way to view and edit all extracted details.
- Accept clarification answers by text or voice.
- Accept natural multi-detail answers.

Example answer:

> Danya paid, each pizza was €12, and the juice was €4.

Previously extracted information must remain visible and must not be lost when a clarification answer is added.

## 4.3 Clarification order

Ask questions in this order when relevant:

1. Personal name, only for unresolved self-reference.
2. Payer.
3. Currency.
4. Total or required item prices.
5. Missing second participant or ambiguous participant identity.
6. Ambiguous item, fee, discount, or overall split intent.

## 4.4 Missing payer

When the payer is not specified, ask:

> Who paid for this expense?

Do not assume that the current user paid.

## 4.5 Missing currency

Currency should be resolved in this order:

1. Explicit currency symbol or code in the user’s text.
2. The user’s default currency.
3. A clarification question when ambiguity remains.

## 4.6 Missing prices

The app must never estimate or invent prices.

### Equal split

For an equal split, the user may provide either:

- Individual item prices, or
- One final total.

When only the final total is provided:

- Keep the named items as descriptive context when useful.
- Do not invent item-level prices.
- Base the calculation on the final total only.
- Clearly show that the expense is being split from the overall total rather than item-level prices.
- Allow the user to add item prices later from the preview.

### Item-specific or custom split

When the split depends on who had which items or on custom item allocation:

- Ask for the price of every item needed for the split.
- Do not continue to a ready-to-save preview while required item prices are missing.

---

# 5. Processing states

The input experience must support these visible states:

- Empty.
- Editing text.
- Recording.
- Transcript ready for editing.
- Processing.
- Needs clarification.
- Ready for preview.
- Recoverable error.
- Offline.

## 5.1 Processing feedback

During processing:

- Keep the user’s input visible.
- Prevent duplicate submission.
- Provide a way to cancel when practical.
- Do not navigate to an incomplete preview.
- Present a blocking root-level overlay with a translucent scrim, progress indicator, status copy, and one Cancel action.
- Keep the underlying layout in place while blocking composer, defaults, scan, manual entry, system Back, and accessibility focus behind the overlay.

## 5.2 Processing failure

When expense interpretation fails, preserve the original text and offer:

- Retry.
- Edit description.
- Enter manually.
- Scan receipt.

Use a clear user-facing message without technical details.

## 5.3 Offline behavior

When the device is offline:

- Natural-language expense processing is unavailable.
- The entered text should remain available.
- Manual entry remains available.
- Receipt scanning remains visible and available according to its own offline capabilities.
- Clearly explain that an internet connection is required for natural-language processing.

---

# 6. Expense preview

## 6.1 Preview destination

Use an expanded version of the existing **Review expense** experience rather than a separate unrelated review flow.

The preview must be reached only after enough information exists to build a valid expense.

## 6.2 Preview content

Before saving, show:

- Expense title.
- Date.
- Currency.
- Total.
- Payer.
- Participants.
- Items and quantities.
- Item prices when known.
- A clear total-only state when item prices are not known.
- Each item’s assigned participant or participants when item-level splitting is used.
- Fees and discounts.
- Per-person shares.
- Final settlement.

## 6.3 Editing

Every major section must have a direct edit action:

- Expense details.
- Payer and participants.
- Items and prices.
- Item assignments.
- Fees and discounts.
- Split configuration.

After completing an edit, return directly to the preview rather than forcing the user through the entire creation flow again.

**Review all details** opens a full-screen, button-first review hub using the same pinned top bar, section cards, tappable rows, and sticky action pattern as Manual Entry and Receipt Review. Expense, people, items, fees/discounts, and split edits open typed bottom sheets. Sheet changes apply only after tapping **Apply**; dismissing a sheet discards its local changes. When opened from Review expense, the hub uses **Save changes** and returns directly to Review.

## 6.4 Uncertain information

- Confidently understood information should appear normally.
- Only uncertain or ambiguous information should be visually marked.
- Use clear language such as “Check this” rather than technical confidence scores.
- The user must be able to correct uncertain values directly.
- A derived title, device-local current date, or default currency is not uncertainty. Show these values normally and keep them editable without a warning badge.
- Never expose internal property paths such as `transactionDate` in user-facing copy.

## 6.5 Regeneration

The user may return to the original description, edit it, and regenerate the expense.

If regeneration would replace manual changes made in the preview:

- Show a confirmation warning.
- Explain that current edits may be replaced.
- Allow the user to cancel and keep the existing preview.

## 6.6 Save behavior

- Never save automatically after processing.
- The user must explicitly tap **Save expense**.
- Saving is disabled while required information is missing or invalid.
- Existing final validation messaging remains visible when the expense cannot be saved.
- Readiness and save validation require at least two participants. This approved rule supersedes any older one-participant wording.
- After the Worker confirms the save, clear the expense draft and AI session before showing the terminal Saved screen. Failed saves preserve both.
- Back from Saved must not reopen the finalized editable review. **Add another** clears expense-specific state and opens a fresh Add expense instance while retaining personal-name and currency preferences.

---

# 7. Leaving the flow

When the user tries to close the flow with unsaved text, clarification answers, or preview edits:

- Ask for confirmation before discarding.
- Offer **Keep editing** and **Discard**.
- Do not silently discard work.

The original description and clarification answers must remain available until the expense is saved or explicitly discarded.

---

# 8. Language

For the first version:

- Support English input.
- Do not reject normal English variations, punctuation, or multiple sentences.
- Design copy and interaction states so additional languages can be introduced later without changing the core flow.

---

# 9. Accessibility requirements

- The microphone, send, retry, edit, cancel, and save actions need clear accessibility labels.
- Recording state changes must be announced.
- Processing, errors, and clarification questions must be announced.
- The interface must not communicate uncertainty or errors through color alone.
- All controls must remain usable with large text.
- Keyboard users must be able to submit and edit without using the microphone.

---

# 10. Acceptance criteria

## Text entry

- [ ] Natural-language input is available on the current Add expense screen.
- [ ] No example chips are displayed.
- [ ] The field accepts multiple lines and multiple sentences.
- [ ] Blank input cannot be submitted.
- [ ] The composer uses a white outlined surface with a black focused outline and solid-black send action.
- [ ] Processing starts only after the send action.
- [ ] The entered text survives recoverable failures.

## Voice entry

- [ ] One tap starts recording and another tap stops it.
- [ ] The final transcript appears in the editable text field.
- [ ] Voice input never submits automatically.
- [ ] Audio is not retained after transcription.
- [ ] Partial text remains available after a recognition failure when possible.

## Interpretation

- [ ] Participants, payer, items, quantities, prices, total, currency, equal split, basic item assignment, and explicit fees can be represented.
- [ ] “I” resolves to the stored personal name.
- [ ] Missing payer triggers a question rather than an assumption.
- [ ] Ambiguous participant matches require confirmation.
- [ ] No missing price is estimated or invented.
- [ ] A description with only one participant cannot reach ready state.
- [ ] Explicit percentage, ratio, and custom-amount split instructions can be represented.

## Clarification

- [ ] The app asks one primary question at a time.
- [ ] The user can also edit all extracted details.
- [ ] Partial extraction shows only a Review all details action, not an inline extracted-details card.
- [ ] Clarifications accept text and voice.
- [ ] One natural answer may provide multiple missing details.
- [ ] Existing extracted details remain intact across clarifications.

## Price handling

- [ ] Equal splits can proceed from either item prices or a final total.
- [ ] A total-only equal split does not display invented item prices.
- [ ] Item-specific or custom splits require the necessary item prices.
- [ ] The user can add item prices later from the preview.

## Preview

- [ ] The existing Review expense experience is expanded for AI-created expenses.
- [ ] The preview shows all expense, participant, item, split, fee, share, and settlement information available.
- [ ] Each major section can be edited directly.
- [ ] Review all details uses a full-screen review hub and typed Apply-only bottom sheets.
- [ ] Completing an edit returns to the preview.
- [ ] Only uncertain information is marked for review.
- [ ] Derived/defaulted title, date, and currency are shown without warnings.
- [ ] Regeneration warns before replacing manual edits.
- [ ] Saving always requires explicit confirmation.

## Recovery

- [ ] Processing errors preserve the description and expose retry, edit, manual entry, and receipt scan options.
- [ ] Offline state keeps manual alternatives available.
- [ ] Closing an unsaved flow asks for discard confirmation.
- [ ] Description and clarification answers persist until save or explicit discard.
- [ ] Successful save clears expense-specific draft/session state and Add another opens an empty composer.
