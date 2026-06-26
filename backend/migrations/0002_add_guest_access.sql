ALTER TABLE expenses ADD COLUMN guest_passcode_hash TEXT;
ALTER TABLE expenses ADD COLUMN guest_passcode_salt TEXT;

CREATE TABLE guest_access_attempts (
  share_id TEXT NOT NULL,
  client_key_hash TEXT NOT NULL,
  failed_count INTEGER NOT NULL DEFAULT 0,
  locked_until TEXT,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (share_id, client_key_hash)
);
