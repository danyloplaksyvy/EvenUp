# EvenUp API Contract

## Base URL

Use environment-specific Worker base URL.

Android should receive only the Worker URL. Android must never contain the OpenAI API key.

## Common response behavior

Use JSON responses for API endpoints.
Use safe error messages.
Do not expose OpenAI internal error details directly to the client.

Suggested error shape:

```json
{
  "error": {
    "code": "RECEIPT_PARSE_FAILED",
    "message": "Could not read this receipt. Please try again or enter it manually."
  }
}
```

## GET /health

Purpose:

Verify Worker is running.

Response:

```json
{
  "ok": true
}
```

## POST /v1/receipts/parse

Purpose:

Parse a receipt image through OpenAI and return structured receipt JSON.

Android request:

```json
{
  "imageBase64": "base64-image-data",
  "mimeType": "image/jpeg",
  "localeHint": "en",
  "currencyHint": "EUR"
}
```

Response:

```json
{
  "merchantName": "Bella Roma",
  "transactionDate": "2026-06-15",
  "currency": "EUR",
  "items": [
    {
      "name": "Pasta",
      "quantity": 2,
      "unitPriceMinor": 1800,
      "totalPriceMinor": 3600
    }
  ],
  "fees": [
    {
      "type": "TAX",
      "label": "Tax",
      "amountMinor": 600
    },
    {
      "type": "TIP",
      "label": "Tip",
      "amountMinor": 1100
    }
  ],
  "subtotalMinor": 10350,
  "totalMinor": 12050,
  "confidence": 0.92
}
```

Fee types:

```text
TAX
TIP
SERVICE_FEE
DISCOUNT
OTHER
```

Validation requirements:

- imageBase64 required
- mimeType required
- image size limit required
- items array required
- totalMinor required
- currency required or inferred
- all money values returned in minor units

Client behavior:

- Parsed receipt must be editable by the user.
- Parse failure must allow retry or manual fallback.

## POST /v1/expenses

Purpose:

Save a finalized immutable expense and return a public share link.

Request shape:

```json
{
  "schemaVersion": 1,
  "title": "Dinner at Bella Roma",
  "receipt": {},
  "participants": [],
  "payerParticipantId": "participant_1",
  "itemAssignments": [],
  "feeAllocations": [],
  "summary": {}
}
```

Response:

```json
{
  "expenseId": "expense_123",
  "shareId": "A8xQ2Lm9",
  "shareUrl": "https://evenup.app/e/A8xQ2Lm9"
}
```

Backend rules:

- Generate expense ID server-side.
- Generate share ID server-side.
- Share ID must be random, URL-safe, unique, and hard to guess.
- Store finalized payload JSON in D1.
- Payload is immutable for MVP.

## GET /v1/expenses/:shareId

Purpose:

Fetch finalized expense by public share ID.

Auth:

No auth required for MVP.

Response:

```json
{
  "schemaVersion": 1,
  "expenseId": "expense_123",
  "shareId": "A8xQ2Lm9",
  "title": "Dinner at Bella Roma",
  "receipt": {},
  "participants": [],
  "payerParticipantId": "participant_1",
  "itemAssignments": [],
  "feeAllocations": [],
  "summary": {},
  "createdAt": "2026-06-15T12:00:00Z"
}
```

Missing expense:

```http
404 Not Found
```

## GET /e/:shareId

Purpose:

Render public read-only guest web page.

Requirements:

- No login.
- Mobile-friendly.
- Read-only.
- White/black styling.
- Show everyone balances.
- Show transparent breakdown.

Page sections:

- Expense title
- Merchant/date/total
- Payer
- Settlement summary
- Participant breakdown
- Item breakdown
- Fee breakdown
- Powered by EvenUp

## D1 MVP schema

```sql
CREATE TABLE expenses (
  id TEXT PRIMARY KEY,
  share_id TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX idx_expenses_share_id ON expenses(share_id);
```

## Security requirements

- OpenAI API key only in Worker secret.
- Do not store receipt images for MVP.
- Do not log image payloads.
- Public links are unguessable but not private-authenticated.
- Do not store phone numbers or emails for participants.
