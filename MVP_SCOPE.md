# EvenUp MVP Scope

## Product definition

EvenUp MVP is an Android app for creating one-off shared expenses from an English natural-language description, a real receipt, or manual entry; allocating the expense transparently; and sharing a passcode-gated read-only web result link.

## Pitch narrative

EvenUp reduces money conflicts by making shared expenses transparent: describe or scan the expense, review exactly how it is divided, see who owes whom, and share a clear passcode-gated breakdown.

## Must have

- Android-only native app
- Natural-language text creation as the primary Add expense action
- Android `SpeechRecognizer` dictation that sends text only
- English AI clarification and editable extracted-details flow
- Receipt scan and manual-entry fallbacks
- Camera capture
- Gallery import
- Real OpenAI receipt scanning through Cloudflare Worker
- Manual fallback
- Receipt review and editing
- Manual participants by name
- Local saved reusable participant names
- Delete saved participant names
- Color avatars
- Payer selection from participants
- Item-level assignment
- Tap-person-then-tap-items interaction
- Quantity/unit-based split
- Shared item split
- Custom amount item split
- Percentage item split
- Tax/tip/fee allocation
- Equal fee allocation default
- Proportional fee allocation
- Custom fee allocation
- Final settlement summary
- Expandable calculation details
- Save finalized immutable expense
- Real share link
- App-generated four-letter guest passcode for every new shared expense
- Share message containing both the guest link and passcode as separate values
- Passcode-gated read-only guest web page for new shares
- Legacy no-passcode guest links remain public
- Guest page remembers successful passcode entry
- Guest page rate-limits repeated passcode failures
- Person-level guest breakdown showing each participant's items, fees, discounts, paid amount, share, and settlement result
- Total-only equal expenses with unpriced descriptive items and explicit base allocation
- A minimum of two participants before AI readiness or final save
- No login required for guest page

## Out of scope

- Groups
- Friends/social graph
- In-app payments
- Expense history screen
- Contacts import
- Notifications
- Payment reminders
- Multi-expense settlement
- Editing finalized expense after save
- Full profile management
- Bank integration

## Primary screens

1. New Expense / AI Composer
2. AI Clarification / Extracted Details
3. Receipt Scan
4. Receipt Review
5. Manual Receipt Entry
6. Choose People
7. Assign Receipt
8. Item Detail Bottom Sheet
9. Fees Allocation
10. Review Expense
11. Saved / Share
12. Guest Web View

## Demo critical path

```text
New Expense
|-- Describe by text/voice
|   -> Clarify missing AI facts when needed
|   -> Review Expense
|-- Scan receipt or enter manually
|   -> Receipt Review
|   -> Choose People
|   -> Assign Items
|   -> Allocate Fees
|   -> Review Settlement
-> Save Expense
-> Share Link + Passcode
-> Guest Enters Passcode
-> Guest Web View
```

## Delivery priority

### P0

- Manual full flow
- AI text/voice expense input with review
- Calculation engine
- Save/share backend
- Guest page
- Receipt scanner integration
- Premium UI polish for main flow

### P1

- Google sign-in
- Receipt image preview
- Copy link polish
- Parsing confidence UI
- More polished error recovery

### P2

Everything listed as out of scope.

---

## Design reference package

The generated Stitch design is included under:

```text
docs/design/stitch/
```

Use `docs/design/STITCH_REFERENCE.md` to map screens to implementation tasks.

The Stitch design is the visual reference for the MVP screens, while this file remains the product scope source of truth. If any generated design includes out-of-scope UI, do not implement that out-of-scope UI.
