export default {
  async fetch(request, env) {
    return handleRequest(request, env);
  }
};

export async function handleRequest(request, env = {}) {
  const url = new URL(request.url);

  if (url.pathname === "/health") {
    if (request.method !== "GET") {
      return jsonResponse(
        {
          error: {
            code: "METHOD_NOT_ALLOWED",
            message: "Method not allowed."
          }
        },
        405,
        {
          Allow: "GET"
        }
      );
    }

    return jsonResponse({ ok: true });
  }

  return jsonResponse(
    {
      error: {
        code: "NOT_FOUND",
        message: "Route not found."
      }
    },
    404
  );
}

export function jsonResponse(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...headers
    }
  });
}
