import { createRemoteJWKSet, jwtVerify, type JWTPayload } from "jose";
import type { VerifiedIdentity, WorkerEnv } from "./types";

const firebaseKeys = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);
const appCheckKeys = createRemoteJWKSet(new URL("https://firebaseappcheck.googleapis.com/v1/jwks"));

export class AuthenticationError extends Error {
  constructor(
    readonly code: string,
    readonly status = 401,
  ) {
    super(code);
  }
}

export async function verifyFirebaseIdentity(
  request: Request,
  env: WorkerEnv,
  verifyToken: typeof jwtVerify = jwtVerify,
): Promise<VerifiedIdentity> {
  const projectId = env.FIREBASE_PROJECT_ID;
  if (!projectId) throw new AuthenticationError("AUTH_NOT_CONFIGURED", 503);
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) throw new AuthenticationError("AUTH_REQUIRED");
  const token = authorization.slice("Bearer ".length).trim();
  if (!token) throw new AuthenticationError("AUTH_REQUIRED");

  let payload: JWTPayload;
  try {
    ({ payload } = await verifyToken(token, firebaseKeys, {
      algorithms: ["RS256"],
      issuer: `https://securetoken.google.com/${projectId}`,
      audience: projectId,
      clockTolerance: 5,
      requiredClaims: ["sub", "iat", "exp", "aud", "iss"],
    }));
  } catch {
    throw new AuthenticationError("INVALID_TOKEN");
  }

  const now = Math.floor(Date.now() / 1000);
  if (!payload.sub || payload.sub.length > 128) throw new AuthenticationError("INVALID_SUBJECT");
  if (typeof payload.iat !== "number" || payload.iat > now + 5) {
    throw new AuthenticationError("INVALID_ISSUED_AT");
  }
  if (payload.email_verified !== true || typeof payload.email !== "string") {
    throw new AuthenticationError("VERIFIED_EMAIL_REQUIRED", 403);
  }
  const firebase = payload.firebase as {
    sign_in_provider?: string;
    identities?: Record<string, unknown>;
  } | undefined;
  const providers = new Set<VerifiedIdentity["provider"]>();
  if (firebase?.identities?.["google.com"]) providers.add("GOOGLE");
  if (firebase?.identities?.email || firebase?.identities?.password) providers.add("EMAIL_LINK");
  const provider = firebase?.sign_in_provider === "google.com" ? "GOOGLE" : "EMAIL_LINK";
  providers.add(provider);
  return {
    firebaseUid: payload.sub,
    email: payload.email,
    name: typeof payload.name === "string" ? payload.name : undefined,
    picture: typeof payload.picture === "string" ? payload.picture : undefined,
    issuedAt: payload.iat,
    authenticatedAt: typeof payload.auth_time === "number" ? payload.auth_time : payload.iat,
    provider,
    providers: [...providers],
  };
}

export async function verifyAppCheck(request: Request, env: WorkerEnv): Promise<void> {
  if (env.APP_CHECK_ENFORCED !== "true") return;
  const token = request.headers.get("X-Firebase-AppCheck");
  if (!token || !env.APP_CHECK_AUDIENCE || !env.APP_CHECK_ISSUER) {
    throw new AuthenticationError("APP_CHECK_REQUIRED", 403);
  }
  try {
    await jwtVerify(token, appCheckKeys, {
      algorithms: ["RS256"],
      audience: env.APP_CHECK_AUDIENCE,
      issuer: env.APP_CHECK_ISSUER,
      requiredClaims: ["sub", "iat", "exp", "aud", "iss"],
    });
  } catch {
    throw new AuthenticationError("INVALID_APP_CHECK_TOKEN", 403);
  }
}

export function requireRecentAuthentication(identity: VerifiedIdentity, maximumAgeSeconds = 300): void {
  if (Math.floor(Date.now() / 1000) - identity.authenticatedAt > maximumAgeSeconds) {
    throw new AuthenticationError("RECENT_AUTHENTICATION_REQUIRED", 401);
  }
}
