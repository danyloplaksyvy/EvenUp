# EvenUp MVP Scope

## Product definition

EvenUp MVP is an Android app for creating one-off shared expenses from real receipts, assigning receipt items to people, allocating fees transparently, and sharing a read-only web result link.

## Pitch narrative

EvenUp reduces money conflicts by making shared expenses transparent: scan the receipt, assign what each person had, see exactly who owes whom, and share a clear breakdown.

## Must have

- Android-only native app
- Receipt-first creation flow
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
- Public read-only guest web page
- No login required for guest page

## Out of scope

- Groups
- Friends/social graph
- In-app payments
- Expense history screen
- AI natural language expense creation
- Contacts import
- Notifications
- Payment reminders
- Multi-expense settlement
- Editing finalized expense after save
- Full profile management
- Bank integration

## Primary screens

1. New Expense
2. Receipt Scan
3. Receipt Review
4. Manual Receipt Entry
5. Choose People
6. Assign Receipt
7. Item Detail Bottom Sheet
8. Fees Allocation
9. Review Expense
10. Saved / Share
11. Guest Web View

## Demo critical path

```text
New Expense
-> Scan or Manual Entry
-> Receipt Review
-> Choose People
-> Assign Items
-> Allocate Fees
-> Review Settlement
-> Save Expense
-> Share Link
-> Guest Web View
```

## Delivery priority

### P0

- Manual full flow
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
