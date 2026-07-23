import assert from "node:assert/strict";
import test from "node:test";
import { AuthenticationError, requireRecentAuthentication, verifyFirebaseIdentity } from "../../src/v2/auth";
import { normalizeUsername } from "../../src/v2/router";

const env = { FIREBASE_PROJECT_ID: "evenup-dev" };

test("authentication rejects a missing bearer token", async () => {
  await assert.rejects(
    verifyFirebaseIdentity(new Request("https://example.test/v2/profile"), env),
    (error: unknown) => error instanceof AuthenticationError && error.code === "AUTH_REQUIRED",
  );
});

test("authentication validates verified email and provider-neutral identity", async () => {
  const fakeVerify = async () => ({
    payload: {
      sub: "firebase-user",
      email: "person@example.com",
      email_verified: true,
      name: "Person",
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600,
      auth_time: Math.floor(Date.now() / 1000),
      aud: "evenup-dev",
      iss: "https://securetoken.google.com/evenup-dev",
      firebase: { sign_in_provider: "google.com" },
    },
    protectedHeader: { alg: "RS256" },
  });
  const identity = await verifyFirebaseIdentity(
    new Request("https://example.test/v2/profile", {
      headers: { Authorization: "Bearer valid" },
    }),
    env,
    fakeVerify as never,
  );
  assert.equal(identity.firebaseUid, "firebase-user");
  assert.equal(identity.provider, "GOOGLE");
});

for (const tokenCase of ["malformed", "expired", "wrong-algorithm", "wrong-issuer", "wrong-audience", "unknown-key"]) {
  test(`authentication safely rejects ${tokenCase} tokens`, async () => {
    const rejectVerify = async () => { throw new Error(tokenCase); };
    await assert.rejects(
      verifyFirebaseIdentity(
        new Request("https://example.test/v2/profile", {
          headers: { Authorization: `Bearer ${tokenCase}` },
        }),
        env,
        rejectVerify as never,
      ),
      (error: unknown) => error instanceof AuthenticationError && error.code === "INVALID_TOKEN",
    );
  });
}

test("sensitive operations require recent authentication", () => {
  assert.throws(() => requireRecentAuthentication({
    firebaseUid: "uid",
    email: "person@example.com",
    issuedAt: 1,
    authenticatedAt: 1,
    provider: "EMAIL_LINK",
  }), /RECENT_AUTHENTICATION_REQUIRED/);
});

test("username normalization is deterministic and safe", () => {
  assert.equal(normalizeUsername("  Álex Smith  "), "alex_smith");
  assert.equal(normalizeUsername("42"), "user_42");
});
