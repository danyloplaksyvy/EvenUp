# Backend AGENTS.md

## Scope

These instructions apply to the Cloudflare Worker backend.

## Required context for backend work

Before backend implementation, read:

- root `AGENTS.md`
- `API_CONTRACT.md`
- `MVP_SCOPE.md`
- `docs/design/STITCH_REFERENCE.md` only when rendering the guest web page

## Backend responsibilities

The Worker must:

- hide the OpenAI API key
- parse receipt images through OpenAI
- interpret English expense descriptions through stateless OpenAI Structured Outputs
- save finalized immutable expenses in D1
- return finalized expenses by public share ID
- verify guest passcodes for protected share links
- render a simple passcode-gated read-only guest page

## Security rules

Never expose the OpenAI API key to Android.
Do not log receipt image payloads.
Do not log AI descriptions, clarification answers, transcripts, prompts, or model output.
Do not store receipt images unless explicitly requested.
Guest links must be unguessable and passcode-gated for new shares.
Legacy rows without passcode metadata remain public.
Do not store plaintext guest passcodes; store salted hashes only.
Rate-limit repeated guest passcode failures.
Remember successful guest access with a secure cookie.
Use random base62 share IDs, preferably 8-12 characters.
Return safe error messages to clients.

## Guest web design rules

For `GET /e/:shareId`, use the guest view reference:

- `docs/design/stitch/guest_view_web/screen.png`
- `docs/design/stitch/guest_view_web/code.html`

The guest page must be mobile-first, read-only, white/black, and visually consistent with the Android app. It does not need to use the Android design system, but it should preserve the same product hierarchy and tone.

For protected shares, first render a simple four-letter passcode gate. After access is verified, render a person-first breakdown where expanding a participant shows their items or total-only base split, unpriced descriptive items, split methods, fee allocations, discounts, paid amount, total share, and settlement result.

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

Use nullable passcode columns so existing no-passcode rows remain public.
Use a separate rate-limit table or equivalent Worker storage for failed guest passcode attempts.

## Required routes

```http
GET /health
POST /v1/receipts/parse
POST /v1/expenses/interpret
POST /v1/expenses
GET /v1/expenses/:shareId
POST /e/:shareId/access
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

## Local Worker commands

Run Worker commands from `backend/`:

```bash
npm install
npm test
npm run typecheck
npm run db:migrate:local
npm run dev
```

Local health check:

```bash
curl http://localhost:8787/health
```

Secrets and bindings for later milestones:

- Configure `OPENAI_API_KEY` as a Worker secret when implementing receipt parsing in T054.
- `EXPENSES_DB` is the D1 binding for saved finalized expenses.
- Replace the local placeholder `database_id` in `wrangler.toml` with the Cloudflare D1 database ID before production deploy.
