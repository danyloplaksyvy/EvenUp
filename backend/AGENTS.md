# Backend AGENTS.md

## Scope

These instructions apply to the Cloudflare Worker backend.

## Backend responsibilities

The Worker must:

- hide the OpenAI API key
- parse receipt images through OpenAI
- save finalized immutable expenses in D1
- return finalized expenses by public share ID
- render a simple public read-only guest page

## Security rules

Never expose the OpenAI API key to Android.
Do not log receipt image payloads.
Do not store receipt images unless explicitly requested.
Guest links are public but must be unguessable.
Use random base62 share IDs, preferably 8-12 characters.
Return safe error messages to clients.

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

## Required routes

```http
GET /health
POST /v1/receipts/parse
POST /v1/expenses
GET /v1/expenses/:shareId
GET /e/:shareId
```

## Validation commands

Use the repository's Worker commands when available. Prefer:

```bash
npm test
npm run typecheck
npm run dev
```

If commands do not exist yet, add minimal scripts and document them.
