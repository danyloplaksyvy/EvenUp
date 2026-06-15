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

## Core participant formula

For each participant:

```text
assigned_item_total = sum of assigned item shares
allocated_fee_total = sum of allocated tax, tip, service fee, and other fees
person_share = assigned_item_total + allocated_fee_total
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
