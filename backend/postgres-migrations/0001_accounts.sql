CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid TEXT NOT NULL UNIQUE,
    verified_email TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DELETION_PENDING', 'DELETED')),
    token_valid_after TIMESTAMPTZ,
    first_login_imported_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    username TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    default_currency CHAR(3) NOT NULL,
    locale TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS legal_acceptances (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_type TEXT NOT NULL CHECK (document_type IN ('TERMS', 'PRIVACY')),
    version TEXT NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, document_type, version)
);

CREATE TABLE IF NOT EXISTS username_reservations (
    username TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS account_deletion_requests (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    requested_at TIMESTAMPTZ NOT NULL,
    recover_until TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    erased_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS idempotency_records (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    response_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, operation, idempotency_key)
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS account_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    legacy_expense_id TEXT NOT NULL,
    share_id TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    payload JSONB NOT NULL,
    guest_passcode_hash TEXT,
    guest_passcode_salt TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE account_expenses ADD COLUMN IF NOT EXISTS legacy_expense_id TEXT;
ALTER TABLE account_expenses ADD COLUMN IF NOT EXISTS title TEXT;
ALTER TABLE account_expenses ADD COLUMN IF NOT EXISTS guest_passcode_hash TEXT;
ALTER TABLE account_expenses ADD COLUMN IF NOT EXISTS guest_passcode_salt TEXT;
UPDATE account_expenses
SET legacy_expense_id = COALESCE(legacy_expense_id, id::text),
    title = COALESCE(title, payload ->> 'title', 'Expense')
WHERE legacy_expense_id IS NULL OR title IS NULL;
ALTER TABLE account_expenses ALTER COLUMN legacy_expense_id SET NOT NULL;
ALTER TABLE account_expenses ALTER COLUMN title SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_account_expenses_owner
    ON account_expenses(owner_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS avatar_upload_intents (
    token_hash TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    object_key TEXT NOT NULL UNIQUE,
    content_type TEXT NOT NULL,
    maximum_bytes INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);
