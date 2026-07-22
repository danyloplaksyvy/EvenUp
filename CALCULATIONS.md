# EvenUp Calculations

## Money representation

Use integer minor units for all money.

Examples:

```text
EUR 12.34 -> 1234
USD 10.00 -> 1000
```

Never use Float or Double for money.

## Percentages

Use basis points.

```text
10000 = 100 percent
5000 = 50 percent
2500 = 25 percent
1 = 0.01 percent
```

AI ratio inputs are normalized to 10,000 basis points before allocation. For
positive ratio weights, first calculate each participant's floored basis-point
share:

```text
participant_bps = floor(participant_weight * 10000 / total_weight)
```

Distribute any remaining basis points in stable participant order, then use the
normal percentage-allocation rules. Ratio weights are interpretation input only;
the persisted assignment remains a percentage assignment in basis points.

## Core participant formula

For each participant:

```text
assigned_item_total = sum of assigned item shares
base_share = explicit total-only base allocation, otherwise 0
allocated_fee_total = sum of allocated tax, tip, service fee, and other fees
discount_credit_total = sum of allocated discount credits
person_share = assigned_item_total + base_share + allocated_fee_total - discount_credit_total
amount_paid = receipt_total if participant is payer else 0
net_balance = amount_paid - person_share
```

Interpretation:

```text
net_balance > 0 means participant should receive money
net_balance < 0 means participant owes money
net_balance = 0 means settled
```

For MVP, there is one payer, so settlement rows should generally be:

```text
debtor -> payer
```

## Rounding and remainder distribution

All split methods must preserve totals exactly.

When splitting a value evenly:

```text
base_share = total / participant_count
remainder = total % participant_count
```

Distribute one extra minor unit to participants in stable order until remainder is exhausted.

Stable participant order:

1. Payer first if included.
2. Then participant creation order.

Examples:

```text
1000 / 3 -> 334, 333, 333
1001 / 3 -> 334, 334, 333
1002 / 3 -> 334, 334, 334
```

## Item assignment modes

### FULL

One participant receives the full item amount.

Validation:

```text
sum(shares.amount) == item.totalPrice
shares.size == 1
```

### BY_UNITS

Participants receive item units.

Validation:

```text
sum(shares.quantity) == item.quantity
sum(shares.amount) == item.totalPrice
```

For each participant:

```text
participant_amount = item.totalPrice * participant_quantity / item.quantity
```

Apply deterministic rounding if needed.

### SHARED_EQUAL

Selected participants split the item evenly.

Validation:

```text
participant_count >= 2
sum(shares.amount) == item.totalPrice
```

### CUSTOM_AMOUNT

User provides exact amount per participant.

Validation:

```text
sum(shares.amount) == item.totalPrice
no share amount < 0
```

### PERCENTAGE

User provides percentages in basis points.

Validation:

```text
sum(shares.percentageBasisPoints) == 10000
sum(shares.amount) == item.totalPrice
```

Amount calculation:

```text
raw_amount = item.totalPrice * basisPoints / 10000
```

Use deterministic remainder distribution.

## Fee allocation modes

### EQUAL

Split fee between all participants equally.

Validation:

```text
sum(shares.amount) == fee.amount
```

### PROPORTIONAL

Split fee according to each participant's assigned item subtotal.

For each participant:

```text
participant_fee = fee.amount * participant_item_subtotal / total_item_subtotal
```

Rules:

- Participants with zero item subtotal receive zero share.
- If total item subtotal is zero, fall back to equal split or return validation error. Prefer equal fallback for MVP only if explicitly documented in code.
- Remainder cents distributed by stable order.

### CUSTOM

User provides exact fee amount per participant.

Validation:

```text
sum(shares.amount) == fee.amount
no share amount < 0
```

For discounts, allocation shares use the same modes but are stored as negative amounts. Their absolute values are presented as credits. Every share must have the same sign as the fee and the shares must sum exactly to the signed fee amount.

Legacy itemized payloads without explicit discount allocations keep the existing proportional-to-item-subtotal behavior, with equal fallback when item subtotals are unavailable.

## Total-only pricing mode

Total-only mode is valid only for an explicitly equal split across at least two participants. It may contain unpriced descriptive items but no priced item assignments.

```text
signed_fee_total = sum(tax + tip + service fees + other fees + negative discounts)
base_total = receipt_total - signed_fee_total
person_share = base_share + allocated_fee_total - discount_credit_total
```

Rules:

- `base_total` must be non-negative.
- `baseAllocation.mode` is `EQUAL` and includes every participant exactly once.
- Base remainder cents use payer-first, then participant creation order.
- “Split everything equally” applies equal allocation to the base, every fee, and every discount.
- A fee or discount with a separate stated allocation must use that allocation.
- Total-only base shares plus signed fee/discount effects must equal the receipt total exactly.

## Final settlement example

Receipt total:

```text
12050
```

Payer:

```text
Anna
```

Participant shares:

```text
Anna: 3500
Ben: 4000
Chris: 4550
```

Paid amounts:

```text
Anna: 12050
Ben: 0
Chris: 0
```

Net balances:

```text
Anna: +8550
Ben: -4000
Chris: -4550
```

Settlement:

```text
Ben owes Anna 4000
Chris owes Anna 4550
```

## Guest transparency breakdown

The guest web view must explain each participant's share from the saved immutable expense payload.

Display must be person-first:

```text
participant
-> assigned item shares
-> total-only base share when applicable
-> allocated fee shares
-> discount credits
-> total share
-> amount paid
-> net balance
-> settlement action
```

Rules:

- Do not recalculate money in the guest renderer with a different algorithm.
- Use saved `itemAssignments` to show which receipt items belong to each participant.
- Use saved `feeAllocations` to show each participant's tax, tip, service fee, and other fee shares.
- Show the assignment or allocation mode when available, such as full, units, shared equally, custom amount, percentage, equal fee, proportional fee, or custom fee.
- Show discounts or negative fees as credits, not as positive charges.
- Show the payer's own consumed share separately from what others owe the payer.
- Preserve exact totals from saved minor-unit amounts.
- If an older saved payload lacks assignment details, show a safe unavailable state instead of inventing rows.

## Required tests

Calculation engine must test:

- equal item split with remainder
- quantity assignment 2 units as 1 and 1
- quantity assignment 3 units as 2 and 1
- shared equal item split
- custom amount item split
- percentage item split
- invalid custom split
- invalid percentage split
- equal fee allocation
- proportional fee allocation
- custom fee allocation
- final settlement with one payer
- sum of participant shares equals receipt total
- sum of net balances equals zero
- unassigned item blocks save
- invalid fee allocation blocks save
