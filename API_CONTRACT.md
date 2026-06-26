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
      "totalPriceMinor": 3600,
      "confidence": 0.92,
      "candidatesMinor": [3600, 3500],
      "needsReview": false
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
  "confidence": 0.92,
  "corrections": [
    {
      "field": "items[0].totalPriceMinor",
      "itemName": "Pasta",
      "fromMinor": 3500,
      "toMinor": 3600,
      "reason": "Corrected to match printed subtotal."
    }
  ],
  "reviewWarnings": []
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
- item totals must be reconciled against subtotal when possible
- ambiguous item prices should include candidate minor-unit totals
- automatic corrections must be returned in `corrections`

Client behavior:

- Parsed receipt must be editable by the user.
- Parse failure must allow retry or manual fallback.
- Parse corrections should be shown as non-blocking review notes.

## POST /v1/expenses

Purpose:

Save a finalized immutable expense and return a share link.
New shared expenses require an app-generated four-letter guest passcode.

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
  "summary": {},
  "guestAccess": {
    "passcode": "KTRQ"
  }
}
```

Guest access request rules:

- Android generates `guestAccess.passcode` before save.
- Passcode format is exactly four letters after normalization.
- Normalize by trimming whitespace and uppercasing.
- Do not put the passcode in the share URL.
- Do not store plaintext passcodes in D1.
- Existing rows without passcode metadata remain public for backward compatibility.

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
- Store guest passcode as a salted hash outside `payload_json`.
- Do not return the plaintext passcode from the Worker; Android already has the generated value.
- Payload is immutable for MVP.

## GET /v1/expenses/:shareId

Purpose:

Fetch finalized expense by share ID.

Auth:

For rows with passcode metadata, require either:

- A valid remembered guest-access cookie set by `POST /e/:shareId/access`.
- A valid `X-EvenUp-Guest-Passcode` header.

Rows without passcode metadata remain public legacy rows.

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

Missing or invalid passcode:

```http
401 Unauthorized
```

Repeated failed passcode attempts:

```http
429 Too Many Requests
```

Do not reveal whether the share ID exists when returning passcode errors.

## POST /e/:shareId/access

Purpose:

Verify the four-letter guest passcode for the web guest view and remember successful access.

Request:

Use an HTML form post from the guest gate.

```http
passcode=KTRQ
```

Success behavior:

```http
303 See Other
Set-Cookie: evenup_guest_access=...
Location: /e/:shareId
```

Cookie requirements:

- HttpOnly.
- Secure in non-local environments.
- SameSite=Lax.
- Signed or otherwise tamper-resistant.
- Scoped narrowly enough that one share does not automatically unlock unrelated shares.
- Short-lived but practical for demo use; recommended MVP duration is 7 days.

Failure behavior:

- Render the passcode gate again with a safe inline error.
- Count failed attempts for rate limiting.
- Return `429` with a safe message when locked out.

Rate limiting:

- Track failures by share ID and a privacy-preserving client key.
- Recommended MVP threshold: 5 failed attempts per 10 minutes.
- Reset or decay failures after successful verification or timeout.

## GET /e/:shareId

Purpose:

Render read-only guest web page.

Requirements:

- No login.
- For new shares, require the four-letter passcode before showing expense details.
- Remember successful access with the guest-access cookie.
- Legacy no-passcode rows remain public.
- Mobile-friendly.
- Read-only.
- White/black styling.
- Show everyone balances.
- Show transparent person-level breakdown.

Page sections:

- Expense title
- Merchant/date/total
- Payer
- Settlement summary
- Participant breakdown as the primary interaction
- Expandable participant details
- Per-person item shares with split method labels
- Per-person fee allocations with allocation method labels
- Per-person discount credits
- Paid amount, total share, and net settlement result
- Powered by EvenUp

Guest gate sections:

- EvenUp brand
- Short prompt to enter the four-letter code from the share message
- Four-letter passcode input
- Submit button
- Safe inline error or lockout message

## D1 MVP schema

```sql
CREATE TABLE expenses (
  id TEXT PRIMARY KEY,
  share_id TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  guest_passcode_hash TEXT,
  guest_passcode_salt TEXT,
  created_at TEXT NOT NULL
);

CREATE INDEX idx_expenses_share_id ON expenses(share_id);
```

Passcode migration for an existing database:

```sql
ALTER TABLE expenses ADD COLUMN guest_passcode_hash TEXT;
ALTER TABLE expenses ADD COLUMN guest_passcode_salt TEXT;
```

Rate-limit storage can be D1 or another Worker-compatible store. D1 shape:

```sql
CREATE TABLE guest_access_attempts (
  share_id TEXT NOT NULL,
  client_key_hash TEXT NOT NULL,
  failed_count INTEGER NOT NULL DEFAULT 0,
  locked_until TEXT,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (share_id, client_key_hash)
);
```

## Security requirements

- OpenAI API key only in Worker secret.
- Do not store receipt images for MVP.
- Do not log image payloads.
- Share IDs must be unguessable.
- New guest links must be passcode-gated.
- Guest passcodes are four letters, generated in Android, and shared separately from the URL.
- Store only salted passcode hashes in backend storage.
- Rate-limit repeated passcode failures.
- Successful guest access should be remembered with a secure cookie.
- Legacy links without passcode metadata remain public.
- Do not store phone numbers or emails for participants.
