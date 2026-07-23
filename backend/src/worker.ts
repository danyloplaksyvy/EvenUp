import legacyWorker, { handleRequest as handleLegacyRequest } from "./index.js";
import {
  copyPendingGoogleAvatars,
  findPostgresExpense,
  finalizeExpiredAccountDeletions,
  handleV2Request,
  type LegacyExpenseRow,
} from "./v2/router";
import type { WorkerEnv } from "./v2/types";

export default {
  async fetch(request: Request, env: WorkerEnv): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname.startsWith("/profile-avatars/")) {
      return profileAvatarResponse(url.pathname.slice("/profile-avatars/".length), env);
    }
    const v2Response = await handleV2Request(request, env, handleLegacyRequest);
    if (v2Response) return v2Response;

    const guestShareId = guestShareIdFrom(url, request.method);
    if (guestShareId) {
      const postgresExpense = await findPostgresExpense(guestShareId, env);
      if (postgresExpense) {
        return handleLegacyRequest(
          request,
          { ...env, EXPENSES_DB: postgresFirstExpenseDatabase(env.EXPENSES_DB, postgresExpense) },
        );
      }
    }
    const isAnonymousWrite = request.method === "POST" &&
      ["/v1/expenses", "/v1/expenses/interpret", "/v1/receipts/parse"].includes(url.pathname);
    if (isAnonymousWrite && env.ALLOW_ANONYMOUS_V1_WRITES === "false") {
      return new Response(JSON.stringify({
        error: { code: "AUTHENTICATED_V2_REQUIRED", message: "Upgrade the app to continue." },
      }), { status: 401, headers: { "Content-Type": "application/json" } });
    }
    return legacyWorker.fetch(request, env);
  },
  async scheduled(
    _controller: unknown,
    env: WorkerEnv,
    context: { waitUntil(promise: Promise<unknown>): void },
  ): Promise<void> {
    context.waitUntil(Promise.all([
      copyPendingGoogleAvatars(env),
      finalizeExpiredAccountDeletions(env),
    ]));
  },
};

async function profileAvatarResponse(key: string, env: WorkerEnv): Promise<Response> {
  if (!env.PROFILE_AVATARS || !/^profiles\/[0-9a-f-]{36}\/[0-9A-Za-z-]+$/u.test(key)) {
    return new Response("Not found", { status: 404 });
  }
  const object = await env.PROFILE_AVATARS.get(key);
  if (!object) return new Response("Not found", { status: 404 });
  return new Response(object.body, {
    headers: {
      "Content-Type": object.httpMetadata?.contentType ?? "application/octet-stream",
      "Cache-Control": "public, max-age=31536000, immutable",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function guestShareIdFrom(url: URL, method: string): string | null {
  if (method === "GET" && url.pathname.startsWith("/v1/expenses/")) {
    return decodeURIComponent(url.pathname.slice("/v1/expenses/".length));
  }
  if ((method === "GET" || method === "POST") && url.pathname.startsWith("/e/")) {
    const remainder = url.pathname.slice("/e/".length);
    return decodeURIComponent(remainder.endsWith("/access") ? remainder.slice(0, -"/access".length) : remainder);
  }
  return null;
}

function postgresFirstExpenseDatabase(
  fallback: unknown,
  expense: LegacyExpenseRow,
): { prepare(query: string): unknown } {
  return {
    prepare(query: string): unknown {
      if (query.includes("FROM expenses WHERE share_id = ?")) {
        return {
          bind(shareId: string) {
            return {
              async first(): Promise<LegacyExpenseRow | null> {
                return shareId === expense.share_id ? expense : null;
              },
            };
          },
        };
      }
      const database = fallback as { prepare?(statement: string): unknown } | undefined;
      if (!database?.prepare) throw new Error("D1 fallback is not configured");
      return database.prepare(query);
    },
  };
}
