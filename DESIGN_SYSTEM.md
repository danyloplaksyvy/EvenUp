# EvenUp Design System

## Purpose

This file defines the normalized implementation design system for EvenUp.

The generated Stitch output under `docs/design/stitch/` is a visual reference. This file is the implementation contract that should guide native Android Compose components.

## Visual direction

EvenUp should feel like a premium, minimal fintech product.

Use:

- White background
- Near-black text
- Black primary actions
- Light gray cards and inputs
- Subtle borders
- Rounded cards and bottom sheets
- Green only for success states
- Red only for errors
- Amber only for warnings
- Minimal purple only as an optional tiny brand accent

Avoid:

- Heavy purple surfaces
- Overly colorful UI
- Dense tables
- Skeuomorphic receipt styling
- Payment-provider UI
- Social/friend/group visuals

## Design references

Primary design reference:

```text
docs/design/STITCH_REFERENCE.md
docs/design/stitch/
```

Original references:

```text
docs/design/original_refs/functionality_flow_reference.png
docs/design/original_refs/premium_ui_reference.png
```

The Stitch HTML files are not production code. Translate them into native Compose.

## Design tokens

### Color roles

Use semantic tokens. Do not hardcode raw colors inside feature screens.

Recommended token names:

```kotlin
EvenUpColors.background
EvenUpColors.surface
EvenUpColors.surfaceElevated
EvenUpColors.primary
EvenUpColors.onPrimary
EvenUpColors.textPrimary
EvenUpColors.textSecondary
EvenUpColors.textTertiary
EvenUpColors.border
EvenUpColors.divider
EvenUpColors.success
EvenUpColors.successContainer
EvenUpColors.error
EvenUpColors.errorContainer
EvenUpColors.warning
EvenUpColors.warningContainer
EvenUpColors.avatarPalette
```

Suggested visual values:

```text
background: #FFFFFF
surface: #F7F7F5
surfaceElevated: #FFFFFF
primary: #111111
onPrimary: #FFFFFF
textPrimary: #111111
textSecondary: #666666
textTertiary: #999999
border: #E6E6E2
divider: #EEEEEA
success: #1F7A4D
successContainer: #EAF7EF
error: #B42318
errorContainer: #FDECEC
warning: #A15C07
warningContainer: #FFF4E5
```

These values may be adjusted during implementation, but feature screens must use token names rather than raw colors.

### Typography roles

Recommended roles:

```text
DisplayLargeTotal
ScreenTitle
SectionTitle
CardTitle
Body
BodyStrong
BodySmall
Caption
Button
MoneyValue
ReceiptItemName
ReceiptItemMeta
```

Priorities:

- Large totals must be easy to read.
- Settlement rows must be scan-friendly.
- Receipt rows should be compact but readable.
- Labels should use secondary text color.

### Shape roles

Recommended shapes:

```text
ScreenCard: 24dp
Input: 16dp
Button: 16dp to 999dp depending on component
BottomSheet: 28dp top corners
Avatar: circle
Chip: 999dp
```

### Spacing roles

Use consistent spacing tokens:

```text
space4 = 4dp
space8 = 8dp
space12 = 12dp
space16 = 16dp
space20 = 20dp
space24 = 24dp
space32 = 32dp
space40 = 40dp
```

## Core components

Build reusable components in `:core:designsystem` before implementing feature-specific UI.

Required P0 components:

- `EvenUpTheme`
- `EvenUpPrimaryButton`
- `EvenUpSecondaryButton`
- `EvenUpTextButton`
- `EvenUpIconButton`
- `EvenUpTextField`
- `EvenUpMoneyField`
- `EvenUpCard`
- `EvenUpTopBar`
- `EvenUpBottomActionBar`
- `EvenUpBottomSheet`
- `EvenUpParticipantAvatar`
- `EvenUpParticipantChip`
- `EvenUpReceiptItemRow`
- `EvenUpSettlementRow`
- `EvenUpSummaryCard`
- `EvenUpValidationMessage`
- `EvenUpLoadingState`
- `EvenUpErrorState`
- `EvenUpSuccessState`

## Screen-level patterns

### Primary screen layout

Use this general structure:

```text
Top area:
- optional back affordance
- title
- short helper text

Content:
- cards/lists/forms
- immediate validation where needed

Bottom action:
- fixed or sticky primary CTA
- optional secondary action
```

### Bottom action bars

Use bottom CTAs for forward progress:

- Continue
- Save expense
- Share link
- Add another expense

Buttons should be large, high contrast, and easy to tap.

### Cards

Use cards for:

- Receipt summary
- Participant groups
- Fee allocation
- Settlement summary
- Share link

Cards should have light surface color, rounded corners, and subtle border/shadow.

### Bottom sheets

Use bottom sheets for:

- Item edit
- Complex item split configuration
- Custom fee allocation if needed

Bottom sheets should include:

- Clear title
- Item/fee summary
- Inputs
- Validation message
- Save CTA

## Screen references and requirements

### New Expense

Reference:

```text
docs/design/stitch/new_expense/
```

Use:

- Premium hero layout
- Primary black `Scan receipt` CTA
- Secondary `Enter manually` CTA
- No history, groups, or AI text prompt

### Receipt Scan

Reference:

```text
docs/design/stitch/receipt_scan/
```

Known export issue:

- `screen.png` may be invalid. Use `code.html`, this design system, and original references if needed.

Use:

- Camera/gallery options
- Loading state
- Error state
- Manual fallback

### Receipt Review and Manual Entry

References:

```text
docs/design/stitch/receipt_review/
docs/design/stitch/manual_entry/
```

Use:

- Receipt-like card structure
- Editable item rows
- Fees section
- Total summary
- Continue CTA

### Choose People

Reference:

```text
docs/design/stitch/choose_people/
```

Use:

- Participant name input
- Avatar chips/cards
- Saved suggestions
- Delete affordance
- Payer selector

### Assign Items

Reference:

```text
docs/design/stitch/assign_items/
```

Use:

- Participant selector row
- Tap-person-then-tap-item behavior
- Assignment progress card
- Receipt item states
- Continue disabled until all assigned

### Item Split Bottom Sheet

Reference:

```text
docs/design/stitch/item_split/
```

Use:

- Segmented mode selector: Units, Shared, Custom, Percent
- Steppers for units
- Amount inputs for custom
- Percentage inputs for percent mode
- Remaining quantity/amount validation

### Fees Allocation

Reference:

```text
docs/design/stitch/fees_allocation/
```

Use:

- Fee cards
- Equal/proportional/custom mode selection
- Participant previews
- Default equal allocation

### Review Expense

Reference:

```text
docs/design/stitch/review_expense/
```

Use:

- Large settlement summary
- Payer and total
- Expandable calculation details
- Save CTA
- No payment button

### Expense Saved / Share

Reference:

```text
docs/design/stitch/expense_saved/
```

Use:

- Success state
- Share link card
- Share link CTA
- Copy link CTA if available
- Add another expense CTA

### Guest Web View

Reference:

```text
docs/design/stitch/guest_view_web/
```

Use for backend-rendered public page.

### Validation States

Reference:

```text
docs/design/stitch/validation_states/
```

Use for:

- Empty participant state
- Unassigned item warning
- Invalid custom split warning
- Receipt parse error
- Save error with retry
- No internet error
- Loading while saving

## Interaction rules

- Primary action should be obvious and reachable.
- Each screen should have one dominant task.
- Validation should be inline and immediate where possible.
- Complex split editing belongs in bottom sheets.
- Users must always understand what remains incomplete.
- Avoid dialogs unless confirmation is truly required.

## Accessibility basics

For MVP:

- Use sufficient contrast.
- Buttons and rows must have accessible tap targets.
- Important icons require content descriptions.
- Do not rely on color alone for errors or selection.
- Support dynamic text reasonably where practical.
