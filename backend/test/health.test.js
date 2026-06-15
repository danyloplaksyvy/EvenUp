import assert from "node:assert/strict";
import test from "node:test";

import { handleRequest } from "../src/index.js";

test("GET /health returns ok JSON", async () => {
  const response = await handleRequest(new Request("http://localhost/health"));

  assert.equal(response.status, 200);
  assert.equal(response.headers.get("Content-Type"), "application/json");
  assert.deepEqual(await response.json(), { ok: true });
});

test("unsupported /health method returns 405", async () => {
  const response = await handleRequest(
    new Request("http://localhost/health", { method: "POST" })
  );

  assert.equal(response.status, 405);
  assert.equal(response.headers.get("Allow"), "GET");
  assert.deepEqual(await response.json(), {
    error: {
      code: "METHOD_NOT_ALLOWED",
      message: "Method not allowed."
    }
  });
});

test("unknown route returns JSON 404", async () => {
  const response = await handleRequest(new Request("http://localhost/missing"));

  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), {
    error: {
      code: "NOT_FOUND",
      message: "Route not found."
    }
  });
});
