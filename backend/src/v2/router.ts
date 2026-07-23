import postgres, { type Sql } from "postgres";
import { AuthenticationError, requireRecentAuthentication, verifyAppCheck, verifyFirebaseIdentity } from "./auth";
import { avatarIntentSchema, bootstrapSchema, deletionSchema, profilePatchSchema } from "./schemas";
import type { AccountContext, VerifiedIdentity, WorkerEnv } from "./types";

type LegacyHandler = (request: Request, env: WorkerEnv) => Promise<Response>;

export interface LegacyExpenseRow {
  id: string;
  share_id: string;
  title: string;
  payload_json: string;
  guest_passcode_hash: string | null;
  guest_passcode_salt: string | null;
  created_at: string;
}

export async function findPostgresExpense(
  shareId: string,
  env: WorkerEnv,
): Promise<LegacyExpenseRow | null> {
  if (!env.HYPERDRIVE?.connectionString || !/^[0-9A-Za-z]{10}$/.test(shareId)) return null;
  const sql = postgres(env.HYPERDRIVE.connectionString, { prepare: false, max: 1 });
  try {
    const [row] = await sql`
      SELECT legacy_expense_id, share_id, title, payload,
             guest_passcode_hash, guest_passcode_salt, created_at
      FROM account_expenses WHERE share_id = ${shareId}
    `;
    if (!row) return null;
    return {
      id: String(row.legacy_expense_id),
      share_id: String(row.share_id),
      title: String(row.title),
      payload_json: JSON.stringify(row.payload),
      guest_passcode_hash: row.guest_passcode_hash ? String(row.guest_passcode_hash) : null,
      guest_passcode_salt: row.guest_passcode_salt ? String(row.guest_passcode_salt) : null,
      created_at: new Date(row.created_at).toISOString(),
    };
  } finally {
    await sql.end();
  }
}

export async function handleV2Request(
  request: Request,
  env: WorkerEnv,
  legacyHandler: LegacyHandler,
): Promise<Response | null> {
  const url = new URL(request.url);
  if (!url.pathname.startsWith("/v2/")) return null;
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();

  try {
    await verifyAppCheck(request, env);
    const identity = await verifyFirebaseIdentity(request, env);
    if (!env.HYPERDRIVE?.connectionString) return problem(503, "DATABASE_NOT_CONFIGURED", requestId);
    const sql = postgres(env.HYPERDRIVE.connectionString, { prepare: false, max: 2 });
    try {
      if (url.pathname === "/v2/account/bootstrap" && request.method === "POST") {
        return await bootstrap(request, sql, identity, requestId);
      }

      const account = await resolveAccount(sql, identity);
      enforceRevocation(account);

      if (url.pathname === "/v2/profile" && request.method === "GET") {
        return await getProfile(sql, account, requestId);
      }
      if (url.pathname === "/v2/profile" && request.method === "PATCH") {
        return await patchProfile(request, sql, account, requestId);
      }
      if (url.pathname === "/v2/profile/avatar-upload-intents" && request.method === "POST") {
        return await createAvatarIntent(request, sql, account, env, requestId);
      }
      if (url.pathname.startsWith("/v2/profile/avatar-uploads/") && request.method === "PUT") {
        return await uploadAvatar(request, sql, account, env, requestId);
      }
      if (url.pathname.startsWith("/v2/usernames/") && url.pathname.endsWith("/availability") && request.method === "GET") {
        const username = decodeURIComponent(
          url.pathname.slice("/v2/usernames/".length, -"/availability".length),
        ).toLowerCase();
        return await usernameAvailability(sql, username, account.userId, requestId);
      }
      if (url.pathname === "/v2/account" && request.method === "DELETE") {
        requireRecentAuthentication(identity);
        return await requestDeletion(request, sql, account, requestId);
      }
      if (url.pathname === "/v2/account/deletion/cancel" && request.method === "POST") {
        requireRecentAuthentication(identity);
        return await cancelDeletion(request, sql, account, requestId);
      }
      if (url.pathname === "/v2/expenses" && request.method === "POST") {
        return await createExpense(request, sql, account, env, legacyHandler, requestId);
      }
      if (url.pathname === "/v2/ai/jobs" && request.method === "POST") {
        return legacyHandler(rewritePath(request, "/v1/expenses/interpret"), env);
      }
      if (url.pathname === "/v2/receipts/parse" && request.method === "POST") {
        return legacyHandler(rewritePath(request, "/v1/receipts/parse"), env);
      }
      return problem(404, "NOT_FOUND", requestId);
    } finally {
      await sql.end();
    }
  } catch (error) {
    if (error instanceof AuthenticationError) return problem(error.status, error.code, requestId);
    console.error(JSON.stringify({ event: "v2_request_failed", requestId, path: url.pathname }));
    return problem(500, "INTERNAL_ERROR", requestId);
  }
}

export async function finalizeExpiredAccountDeletions(env: WorkerEnv): Promise<void> {
  if (!env.HYPERDRIVE?.connectionString) return;
  const sql = postgres(env.HYPERDRIVE.connectionString, { prepare: false, max: 1 });
  try {
    const erasedUserIds = await sql.begin(async (tx) => {
      const expired = await tx`
        SELECT user_id::text
        FROM account_deletion_requests
        WHERE cancelled_at IS NULL AND erased_at IS NULL AND recover_until <= now()
        FOR UPDATE SKIP LOCKED
        LIMIT 100
      `;
      const userIds: string[] = [];
      for (const row of expired) {
        const userId = String(row.user_id);
        await tx`DELETE FROM account_expenses WHERE owner_user_id = ${userId}::uuid`;
        await tx`DELETE FROM avatar_upload_intents WHERE user_id = ${userId}::uuid`;
        await tx`DELETE FROM legal_acceptances WHERE user_id = ${userId}::uuid`;
        await tx`DELETE FROM idempotency_records WHERE user_id = ${userId}::uuid`;
        await tx`DELETE FROM profiles WHERE user_id = ${userId}::uuid`;
        await tx`DELETE FROM username_reservations WHERE user_id = ${userId}::uuid`;
        await tx`
          UPDATE users
          SET firebase_uid = ${`deleted:${userId}`},
              verified_email = ${`deleted+${userId}@invalid.evenup`},
              status = 'DELETED',
              token_valid_after = now(),
              updated_at = now()
          WHERE id = ${userId}::uuid
        `;
        await tx`
          UPDATE account_deletion_requests SET erased_at = now()
          WHERE user_id = ${userId}::uuid
        `;
        await tx`
          INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
          VALUES ('ACCOUNT', ${userId}::uuid, 'FINAL_ACCOUNT_ERASURE_QUEUED', '{}'::jsonb)
        `;
        userIds.push(userId);
      }
      return userIds;
    });
    if (env.PROFILE_AVATARS) {
      for (const userId of erasedUserIds) {
        await deleteR2Prefix(env.PROFILE_AVATARS, `profiles/${userId}/`);
      }
    }
  } finally {
    await sql.end();
  }
}

export async function copyPendingGoogleAvatars(env: WorkerEnv): Promise<void> {
  if (!env.HYPERDRIVE?.connectionString || !env.PROFILE_AVATARS) return;
  const sql = postgres(env.HYPERDRIVE.connectionString, { prepare: false, max: 1 });
  try {
    const events = await sql`
      SELECT id::text, aggregate_id::text AS user_id, payload
      FROM outbox_events
      WHERE event_type = 'GOOGLE_AVATAR_COPY_REQUESTED' AND processed_at IS NULL
      ORDER BY created_at
      LIMIT 20
    `;
    for (const event of events) {
      const sourceUrl = typeof event.payload?.sourceUrl === "string" ? event.payload.sourceUrl : "";
      try {
        const source = new URL(sourceUrl);
        if (source.protocol !== "https:" ||
            !(source.hostname === "googleusercontent.com" || source.hostname.endsWith(".googleusercontent.com"))) {
          await markOutboxProcessed(sql, String(event.id));
          continue;
        }
        const response = await fetch(source, { redirect: "follow" });
        const contentType = response.headers.get("Content-Type")?.split(";")[0] ?? "";
        const bytes = await response.arrayBuffer();
        if (!response.ok ||
            !["image/jpeg", "image/png", "image/webp"].includes(contentType) ||
            bytes.byteLength < 1 ||
            bytes.byteLength > 5 * 1024 * 1024) {
          await markOutboxProcessed(sql, String(event.id));
          continue;
        }
        const objectKey = `profiles/${event.user_id}/google-${crypto.randomUUID()}`;
        await env.PROFILE_AVATARS.put(objectKey, bytes, { httpMetadata: { contentType } });
        const avatarUrl = `${env.PUBLIC_BASE_URL ?? ""}/profile-avatars/${objectKey}`;
        await sql.begin(async (tx) => {
          await tx`
            UPDATE profiles SET avatar_url = ${avatarUrl}, version = version + 1, updated_at = now()
            WHERE user_id = ${String(event.user_id)}::uuid AND avatar_url = ${sourceUrl}
          `;
          await tx`UPDATE outbox_events SET processed_at = now() WHERE id = ${String(event.id)}::uuid`;
        });
      } catch {
        // Leave transient failures unprocessed so a later scheduled run can retry.
      }
    }
  } finally {
    await sql.end();
  }
}

async function bootstrap(
  request: Request,
  sql: Sql,
  identity: VerifiedIdentity,
  requestId: string,
): Promise<Response> {
  const body = bootstrapSchema.safeParse(await safeJson(request));
  if (!body.success) return problem(400, "INVALID_REQUEST", requestId);
  const result = await sql.begin(async (tx) => {
    const [user] = await tx`
      INSERT INTO users (firebase_uid, verified_email)
      VALUES (${identity.firebaseUid}, ${identity.email})
      ON CONFLICT (firebase_uid) DO UPDATE
      SET verified_email = EXCLUDED.verified_email, updated_at = now()
      RETURNING id::text, status, first_login_imported_at
    `;

    let [profile] = await tx`SELECT * FROM profiles WHERE user_id = ${user.id}::uuid`;
    if (!profile) {
      const base = normalizeUsername(identity.name ?? identity.email.split("@")[0] ?? "evenup");
      const username = await availableUsername(tx, base);
      const displayName = cleanDisplayName(identity.name ?? identity.email.split("@")[0] ?? "EvenUp user");
      [profile] = await tx`
        INSERT INTO profiles (user_id, username, display_name, avatar_url, default_currency, locale)
        VALUES (
          ${user.id}::uuid, ${username}, ${displayName}, ${identity.picture ?? null},
          ${body.data.defaultCurrency}, ${body.data.locale}
        )
        RETURNING *
      `;
      await tx`
        UPDATE users SET first_login_imported_at = now(), updated_at = now()
        WHERE id = ${user.id}::uuid AND first_login_imported_at IS NULL
      `;
      await tx`
        INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
        VALUES ('ACCOUNT', ${user.id}::uuid, 'ACCOUNT_CREATED', ${tx.json({ provider: identity.provider })})
      `;
      if (identity.picture) {
        await tx`
          INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
          VALUES (
            'ACCOUNT', ${user.id}::uuid, 'GOOGLE_AVATAR_COPY_REQUESTED',
            ${tx.json({ sourceUrl: identity.picture })}
          )
        `;
      }
    }
    await tx`
      INSERT INTO legal_acceptances (user_id, document_type, version)
      VALUES
        (${user.id}::uuid, 'TERMS', ${body.data.legalAcceptance.termsVersion}),
        (${user.id}::uuid, 'PRIVACY', ${body.data.legalAcceptance.privacyVersion})
      ON CONFLICT DO NOTHING
    `;
    return accountResponse(user.id, user.status, profile, identity.providers ?? [identity.provider]);
  });
  return json(result, 200, requestId);
}

async function resolveAccount(sql: Sql, identity: VerifiedIdentity): Promise<AccountContext> {
  const rows = await sql`
    SELECT id::text, status, token_valid_after
    FROM users WHERE firebase_uid = ${identity.firebaseUid}
  `;
  if (!rows[0]) throw new AuthenticationError("ACCOUNT_BOOTSTRAP_REQUIRED", 409);
  return {
    identity,
    userId: rows[0].id,
    status: rows[0].status,
    tokenValidAfter: rows[0].token_valid_after ? new Date(rows[0].token_valid_after) : undefined,
  };
}

function enforceRevocation(account: AccountContext): void {
  if (account.tokenValidAfter && account.identity.issuedAt * 1000 < account.tokenValidAfter.getTime()) {
    throw new AuthenticationError("TOKEN_REVOKED");
  }
}

async function getProfile(sql: Sql, account: AccountContext, requestId: string): Promise<Response> {
  const [profile] = await sql`SELECT * FROM profiles WHERE user_id = ${account.userId}::uuid`;
  return profile ? json(profileResponse(profile), 200, requestId) : problem(404, "PROFILE_NOT_FOUND", requestId);
}

async function patchProfile(
  request: Request,
  sql: Sql,
  account: AccountContext,
  requestId: string,
): Promise<Response> {
  if (account.status !== "ACTIVE") return problem(409, "ACCOUNT_NOT_ACTIVE", requestId);
  const expectedVersion = Number(request.headers.get("If-Match"));
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 1) {
    return problem(428, "PROFILE_VERSION_REQUIRED", requestId);
  }
  const body = profilePatchSchema.safeParse(await safeJson(request));
  if (!body.success) return problem(400, "INVALID_PROFILE", requestId);
  try {
    return await sql.begin(async (tx) => {
      const [current] = await tx`
        SELECT username, version FROM profiles
        WHERE user_id = ${account.userId}::uuid FOR UPDATE
      `;
      if (!current || Number(current.version) !== expectedVersion) {
        return problem(409, "PROFILE_VERSION_CONFLICT", requestId);
      }
      if (current.username !== body.data.username) {
        const unavailable = await tx`
          SELECT 1 FROM username_reservations
          WHERE username = ${body.data.username} AND user_id <> ${account.userId}::uuid
            AND expires_at > now()
          LIMIT 1
        `;
        if (unavailable.length > 0) return problem(409, "USERNAME_UNAVAILABLE", requestId);
      }
      const [updated] = await tx`
        UPDATE profiles
        SET display_name = ${body.data.displayName}, username = ${body.data.username},
            default_currency = ${body.data.defaultCurrency}, locale = ${body.data.locale},
            version = version + 1, updated_at = now()
        WHERE user_id = ${account.userId}::uuid AND version = ${expectedVersion}
        RETURNING *
      `;
      if (current.username !== body.data.username) {
        await tx`
          INSERT INTO username_reservations (username, user_id, expires_at)
          VALUES (${String(current.username)}, ${account.userId}::uuid, now() + interval '30 days')
          ON CONFLICT (username) DO UPDATE
          SET user_id = excluded.user_id, expires_at = excluded.expires_at
        `;
      }
      return json(profileResponse(updated), 200, requestId);
    });
  } catch (error) {
    if (isUniqueViolation(error)) return problem(409, "USERNAME_UNAVAILABLE", requestId);
    throw error;
  }
}

async function usernameAvailability(
  sql: Sql,
  username: string,
  userId: string,
  requestId: string,
): Promise<Response> {
  if (!/^[a-z][a-z0-9_]{1,22}[a-z0-9]$/.test(username) || username.includes("__")) {
    return json({ available: false, reason: "INVALID" }, 200, requestId);
  }
  const rows = await sql`
    SELECT 1 FROM profiles WHERE username = ${username} AND user_id <> ${userId}::uuid
    UNION ALL
    SELECT 1 FROM username_reservations
      WHERE username = ${username} AND user_id <> ${userId}::uuid AND expires_at > now()
    LIMIT 1
  `;
  return json({ available: rows.length === 0 }, 200, requestId);
}

async function createAvatarIntent(
  request: Request,
  sql: Sql,
  account: AccountContext,
  env: WorkerEnv,
  requestId: string,
): Promise<Response> {
  if (!env.PROFILE_AVATARS) return problem(503, "AVATAR_STORAGE_NOT_CONFIGURED", requestId);
  const body = avatarIntentSchema.safeParse(await safeJson(request));
  if (!body.success) return problem(400, "INVALID_AVATAR", requestId);
  const token = randomToken();
  const tokenHash = await sha256(token);
  const objectKey = `profiles/${account.userId}/${crypto.randomUUID()}`;
  await sql`
    INSERT INTO avatar_upload_intents
      (token_hash, user_id, object_key, content_type, maximum_bytes, expires_at)
    VALUES (
      ${tokenHash}, ${account.userId}::uuid, ${objectKey}, ${body.data.contentType},
      ${body.data.contentLength}, now() + interval '10 minutes'
    )
  `;
  return json({
    uploadUrl: `${env.PUBLIC_BASE_URL ?? ""}/v2/profile/avatar-uploads/${token}`,
    expiresInSeconds: 600,
    maximumBytes: 5 * 1024 * 1024,
    allowedContentTypes: ["image/jpeg", "image/png", "image/webp"],
  }, 201, requestId);
}

async function requestDeletion(
  request: Request,
  sql: Sql,
  account: AccountContext,
  requestId: string,
): Promise<Response> {
  const body = deletionSchema.safeParse(await safeJson(request));
  if (!body.success) return problem(400, "DELETE_CONFIRMATION_REQUIRED", requestId);
  const response = await idempotent(sql, account.userId, "DELETE_ACCOUNT", request, async (tx) => {
    const [row] = await tx`
      INSERT INTO account_deletion_requests (user_id, requested_at, recover_until)
      VALUES (${account.userId}::uuid, now(), now() + interval '14 days')
      ON CONFLICT (user_id) DO UPDATE
        SET requested_at = CASE WHEN account_deletion_requests.cancelled_at IS NULL
          THEN account_deletion_requests.requested_at ELSE now() END,
            recover_until = CASE WHEN account_deletion_requests.cancelled_at IS NULL
          THEN account_deletion_requests.recover_until ELSE now() + interval '14 days' END,
            cancelled_at = NULL
      RETURNING recover_until
    `;
    await tx`UPDATE users SET status = 'DELETION_PENDING', token_valid_after = now() WHERE id = ${account.userId}::uuid`;
    await tx`
      INSERT INTO username_reservations (username, user_id, expires_at)
      SELECT username, user_id, now() + interval '30 days' FROM profiles WHERE user_id = ${account.userId}::uuid
      ON CONFLICT (username) DO UPDATE SET user_id = excluded.user_id, expires_at = excluded.expires_at
    `;
    await tx`
      INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
      VALUES ('ACCOUNT', ${account.userId}::uuid, 'ACCOUNT_DELETION_REQUESTED', '{}'::jsonb)
    `;
    return { state: "DELETION_PENDING", recoveryEndsAt: new Date(row.recover_until).toISOString() };
  });
  return json(response.body, response.status, requestId);
}

async function uploadAvatar(
  request: Request,
  sql: Sql,
  account: AccountContext,
  env: WorkerEnv,
  requestId: string,
): Promise<Response> {
  if (!env.PROFILE_AVATARS) return problem(503, "AVATAR_STORAGE_NOT_CONFIGURED", requestId);
  const token = decodeURIComponent(new URL(request.url).pathname.slice("/v2/profile/avatar-uploads/".length));
  const tokenHash = await sha256(token);
  const [intent] = await sql`
    SELECT object_key, content_type, maximum_bytes FROM avatar_upload_intents
    WHERE token_hash = ${tokenHash} AND user_id = ${account.userId}::uuid
      AND expires_at > now() AND consumed_at IS NULL
  `;
  if (!intent) return problem(404, "UPLOAD_INTENT_NOT_FOUND", requestId);
  const contentType = request.headers.get("Content-Type")?.split(";")[0];
  if (!contentType ||
      contentType !== intent.content_type ||
      !request.body) {
    return problem(400, "INVALID_AVATAR_UPLOAD", requestId);
  }
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.byteLength < 1 ||
      bytes.byteLength > Number(intent.maximum_bytes) ||
      bytes.byteLength > 5 * 1024 * 1024 ||
      !matchesImageSignature(contentType, bytes)) {
    return problem(400, "INVALID_AVATAR_UPLOAD", requestId);
  }
  await env.PROFILE_AVATARS.put(intent.object_key, bytes, {
    httpMetadata: { contentType },
  });
  const avatarUrl = `${env.PUBLIC_BASE_URL ?? ""}/profile-avatars/${intent.object_key}`;
  await sql.begin(async (tx) => {
    await tx`UPDATE avatar_upload_intents SET consumed_at = now() WHERE token_hash = ${tokenHash}`;
    await tx`
      UPDATE profiles SET avatar_url = ${avatarUrl}, version = version + 1, updated_at = now()
      WHERE user_id = ${account.userId}::uuid
    `;
  });
  return json({ avatarUrl }, 200, requestId);
}

async function cancelDeletion(
  request: Request,
  sql: Sql,
  account: AccountContext,
  requestId: string,
): Promise<Response> {
  const response = await idempotent(sql, account.userId, "CANCEL_DELETION", request, async (tx) => {
    await tx`
      UPDATE account_deletion_requests SET cancelled_at = now()
      WHERE user_id = ${account.userId}::uuid AND erased_at IS NULL AND recover_until > now()
    `;
    await tx`
      UPDATE users SET status = 'ACTIVE', token_valid_after = NULL, updated_at = now()
      WHERE id = ${account.userId}::uuid AND status = 'DELETION_PENDING'
    `;
    await tx`DELETE FROM username_reservations WHERE user_id = ${account.userId}::uuid`;
    const [profile] = await tx`SELECT * FROM profiles WHERE user_id = ${account.userId}::uuid`;
    return accountResponse(
      account.userId,
      "ACTIVE",
      profile,
      account.identity.providers ?? [account.identity.provider],
    );
  });
  return json(response.body, response.status, requestId);
}

async function createExpense(
  request: Request,
  sql: Sql,
  account: AccountContext,
  env: WorkerEnv,
  legacyHandler: LegacyHandler,
  requestId: string,
): Promise<Response> {
  if (account.status !== "ACTIVE") return problem(409, "ACCOUNT_NOT_ACTIVE", requestId);
  const legacyRequest = rewritePath(request.clone(), "/v1/expenses");
  const payload = await safeJson(request);
  if (!payload || typeof payload !== "object") return problem(400, "INVALID_EXPENSE", requestId);
  const legacyResponse = await legacyHandler(legacyRequest, env);
  if (!legacyResponse.ok) return legacyResponse;
  const legacyBody = await legacyResponse.clone().json() as {
    expenseId: string;
    shareId: string;
    shareUrl: string;
  };
  const expensePayload = payload as Record<string, unknown>;
  const guestAccess = expensePayload.guestAccess as { passcode?: unknown } | undefined;
  const passcode = typeof guestAccess?.passcode === "string"
    ? guestAccess.passcode.trim().toUpperCase()
    : null;
  const passcodeMaterial = passcode && /^[A-Z]{4}$/.test(passcode)
    ? await createGuestPasscodeMaterial(passcode)
    : null;
  const { guestAccess: _guestAccess, ...storedPayload } = expensePayload;
  await sql`
    INSERT INTO account_expenses (
      owner_user_id, legacy_expense_id, share_id, title, payload,
      guest_passcode_hash, guest_passcode_salt
    )
    VALUES (
      ${account.userId}::uuid, ${legacyBody.expenseId}, ${legacyBody.shareId},
      ${String(expensePayload.title ?? "")}, ${sql.json(storedPayload as postgres.JSONValue)},
      ${passcodeMaterial?.hash ?? null}, ${passcodeMaterial?.salt ?? null}
    )
  `;
  return json(legacyBody, 201, requestId);
}

async function idempotent<T>(
  sql: Sql,
  userId: string,
  operation: string,
  request: Request,
  work: (tx: postgres.TransactionSql) => Promise<T>,
): Promise<{ status: number; body: T }> {
  const key = request.headers.get("Idempotency-Key");
  if (!key || key.length > 128) throw new AuthenticationError("IDEMPOTENCY_KEY_REQUIRED", 400);
  return sql.begin(async (tx) => {
    const existing = await tx`
      SELECT response_status, response_json FROM idempotency_records
      WHERE user_id = ${userId}::uuid AND operation = ${operation} AND idempotency_key = ${key}
      FOR UPDATE
    `;
    if (existing[0]) return { status: existing[0].response_status, body: existing[0].response_json as T };
    const body = await work(tx);
    await tx`
      INSERT INTO idempotency_records
        (user_id, operation, idempotency_key, response_status, response_json)
      VALUES (${userId}::uuid, ${operation}, ${key}, 200, ${tx.json(body as postgres.JSONValue)})
    `;
    return { status: 200, body };
  });
}

function accountResponse(
  userId: string,
  status: string,
  profile: postgres.Row,
  providers: Array<VerifiedIdentity["provider"]>,
) {
  return {
    account: { id: userId, state: status },
    profile: profile ? profileResponse(profile) : null,
    providers,
    bootstrapStatus: profile ? "READY" : "USERNAME_REQUIRED",
  };
}

function profileResponse(profile: postgres.Row) {
  return {
    username: profile.username,
    displayName: profile.display_name,
    avatarUrl: profile.avatar_url,
    defaultCurrency: profile.default_currency,
    locale: profile.locale,
    version: Number(profile.version),
  };
}

async function availableUsername(sql: postgres.TransactionSql, proposed: string): Promise<string> {
  const base = proposed.slice(0, 19);
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const candidate = attempt === 0 ? base : `${base}_${randomSuffix()}`.slice(0, 24);
    const found = await sql`SELECT 1 FROM profiles WHERE username = ${candidate}`;
    if (found.length === 0) return candidate;
  }
  return `evenup_${randomSuffix()}${randomSuffix()}`.slice(0, 24);
}

export function normalizeUsername(value: string): string {
  const normalized = value.toLowerCase().normalize("NFKD")
    .replace(/\p{M}+/gu, "")
    .replace(/[^a-z0-9_]+/g, "_").replace(/_+/g, "_").replace(/^_+|_+$/g, "");
  const prefixed = /^[a-z]/.test(normalized) ? normalized : `user_${normalized}`;
  const sized = prefixed.slice(0, 24).replace(/_+$/g, "");
  return sized.length >= 3 ? sized : "evenup_user";
}

function cleanDisplayName(value: string): string {
  return value.trim().replace(/\s+/g, " ").slice(0, 80) || "EvenUp user";
}

function randomSuffix(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(3));
  return Array.from(bytes, (value) => value.toString(36)).join("").slice(0, 4);
}

function randomToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

async function createGuestPasscodeMaterial(passcode: string): Promise<{ salt: string; hash: string }> {
  const saltBytes = new Uint8Array(16);
  crypto.getRandomValues(saltBytes);
  const salt = base64UrlEncode(saltBytes);
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`${salt}:${passcode}`),
  );
  return { salt, hash: base64UrlEncode(new Uint8Array(digest)) };
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}

function matchesImageSignature(contentType: string, bytes: Uint8Array): boolean {
  if (contentType === "image/jpeg") {
    return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  }
  if (contentType === "image/png") {
    const signature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
    return bytes.length >= signature.length && signature.every((value, index) => bytes[index] === value);
  }
  return contentType === "image/webp" &&
    bytes.length >= 12 &&
    String.fromCharCode(...bytes.slice(0, 4)) === "RIFF" &&
    String.fromCharCode(...bytes.slice(8, 12)) === "WEBP";
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function rewritePath(request: Request, path: string): Request {
  const url = new URL(request.url);
  url.pathname = path;
  return new Request(url, request);
}

async function safeJson(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

function json(body: unknown, status: number, requestId: string): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "X-Request-Id": requestId },
  });
}

function problem(status: number, code: string, requestId: string): Response {
  return json({ error: { code, message: "The request could not be completed." } }, status, requestId);
}

function isUniqueViolation(error: unknown): boolean {
  return typeof error === "object" && error !== null && "code" in error && error.code === "23505";
}

async function deleteR2Prefix(bucket: NonNullable<WorkerEnv["PROFILE_AVATARS"]>, prefix: string): Promise<void> {
  let cursor: string | undefined;
  do {
    const page = await bucket.list({ prefix, cursor });
    if (page.objects.length > 0) await bucket.delete(page.objects.map((object) => object.key));
    cursor = page.truncated ? page.cursor : undefined;
  } while (cursor);
}

async function markOutboxProcessed(sql: Sql, eventId: string): Promise<void> {
  await sql`UPDATE outbox_events SET processed_at = now() WHERE id = ${eventId}::uuid`;
}
