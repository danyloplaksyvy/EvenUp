import assert from "node:assert/strict";
import test from "node:test";

import { handleRequest } from "../src/index.js";
import { FakeD1Database, jsonRequest, validExpensePayload } from "./fixtures.js";

test("POST /v1/expenses stores payload and returns generated share link", async () => {
  const database = new FakeD1Database();
  const payload = validExpensePayload();

  const response = await handleRequest(
    jsonRequest("http://localhost:8787/v1/expenses", payload),
    {
      EXPENSES_DB: database,
      PUBLIC_BASE_URL: "https://evenup.example"
    }
  );

  assert.equal(response.status, 201);
  const body = await response.json();
  assert.match(body.expenseId, /^expense_[0-9a-f-]{36}$/);
  assert.match(body.shareId, /^[0-9A-Za-z]{10}$/);
  assert.equal(body.shareUrl, `https://evenup.example/e/${body.shareId}`);

  assert.equal(database.rows.length, 1);
  assert.equal(database.rows[0].id, body.expenseId);
  assert.equal(database.rows[0].share_id, body.shareId);
  assert.equal(database.rows[0].title, payload.title);
  assert.deepEqual(JSON.parse(database.rows[0].payload_json), payload);
  assert.match(database.rows[0].created_at, /^\d{4}-\d{2}-\d{2}T/);
});

test("POST /v1/expenses rejects invalid finalized payload", async () => {
  const response = await handleRequest(
    jsonRequest("http://localhost:8787/v1/expenses", {
      schemaVersion: 1,
      title: ""
    }),
    {
      EXPENSES_DB: new FakeD1Database()
    }
  );

  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), {
    error: {
      code: "INVALID_EXPENSE_PAYLOAD",
      message: "title is required."
    }
  });
});

test("unsupported /v1/expenses method returns 405", async () => {
  const response = await handleRequest(new Request("http://localhost:8787/v1/expenses"));

  assert.equal(response.status, 405);
  assert.equal(response.headers.get("Allow"), "POST");
});
