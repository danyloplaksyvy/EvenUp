# EvenUp Design System

## Visual direction

EvenUp should look premium, minimal, and trustworthy.

Use:

- White background
- Black primary actions
- Light gray surfaces
- Rounded cards
- Clean typography
- Subtle borders
- Subtle green success states
- Minimal purple, only for optional brand moments

Avoid:

- Heavy purple UI
- Dense screens
- Excessive shadows
- Hardcoded colors in feature screens
- Overly playful styling

## Design principles

1. One task per screen.
2. Primary action visible at the bottom where appropriate.
3. Complex split controls live in bottom sheets.
4. Validation appears near the issue.
5. Final balances are large and readable.
6. Calculations are expandable, not always visible.
7. Use calm, high-trust language.

## Core tokens

### Colors

Use semantic tokens rather than raw colors in feature screens.

Suggested tokens:

```text
Background
Surface
SurfaceElevated
Primary
OnPrimary
TextPrimary
TextSecondary
TextTertiary
Border
Divider
Success
SuccessContainer
Error
ErrorContainer
Warning
WarningContainer
```

### Typography

Suggested roles:

```text
DisplayTotal
TitleLarge
TitleMedium
BodyLarge
BodyMedium
BodySmall
LabelLarge
LabelMedium
Caption
```

Use large, clear totals.

### Shape

Suggested roles:

```text
CardRadius
ButtonRadius
InputRadius
SheetRadius
AvatarRadius
```

Cards and buttons should feel rounded but not cartoonish.

### Spacing

Suggested scale:

```text
4, 8, 12, 16, 20, 24, 32
```

## Required components

### Buttons

- EvenUpPrimaryButton
- EvenUpSecondaryButton
- EvenUpTextButton
- EvenUpIconButton

Primary button:

- black background
- white text
- rounded corners
- high contrast

### Inputs

- EvenUpTextField
- EvenUpMoneyField
- EvenUpQuantityStepper
- EvenUpPercentageField

### Layout

- EvenUpScaffold
- EvenUpTopBar
- EvenUpBottomActionBar
- EvenUpCard
- EvenUpSectionHeader
- EvenUpBottomSheet

### Feedback

- EvenUpLoadingState
- EvenUpErrorState
- EvenUpSuccessState
- EvenUpValidationMessage

### Expense components

- ReceiptItemRow
- EditableReceiptItemRow
- ParticipantAvatar
- ParticipantChip
- PayerBadge
- AssignmentProgressCard
- FeeAllocationCard
- SettlementSummaryCard
- CalculationDetailsCard
- ShareLinkCard

## Screen design notes

### New Expense

- Minimal hero.
- Primary CTA: Scan receipt.
- Secondary CTA: Enter manually.

### Receipt Review

- Receipt data in editable cards.
- Items should be readable.
- Total mismatch should be a warning.

### Choose People

- Use chips and avatars.
- Payer should be visually clear.
- Saved names should be secondary, not dominant.

### Assign Receipt

- Participant row at top.
- Receipt items below.
- Selected participant clearly highlighted.
- Unassigned state visible.
- Continue disabled until complete.

### Fees Allocation

- One card per fee.
- Mode selector per fee.
- Preview participant allocations.

### Review Expense

- Settlement first.
- Details collapsed by default.
- Save CTA strong and clear.

### Saved / Share

- Success state.
- Share link card.
- Share button.
- Add another expense button.

## Copy style

Use short, clear labels.

Examples:

```text
Scan receipt
Enter manually
Review receipt
Add people
Who paid?
Assign items
All items assigned
Split tax and tip
Review split
Save expense
Share link
Add another expense
```

Error examples:

```text
All items must be assigned before continuing.
Choose who paid.
Custom split must equal the item total.
Fee allocation must match the fee amount.
Could not read this receipt. Try again or enter it manually.
```
