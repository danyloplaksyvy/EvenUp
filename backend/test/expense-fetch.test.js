import assert from "node:assert/strict";
import test from "node:test";

import { handleRequest } from "../src/index.js";
import { FakeD1Database, validExpensePayload } from "./fixtures.js";

test("GET /v1/expenses/:shareId returns saved payload with server fields", async () => {
  const database = new FakeD1Database([
    expenseRow({
      shareId: "A8xQ2Lm9",
      payload: validExpensePayload()
    })
  ]);

  const response = await handleRequest(new Request("http://localhost/v1/expenses/A8xQ2Lm9"), {
    EXPENSES_DB: database
  });

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    ...validExpensePayload(),
    expenseId: "expense_123",
    shareId: "A8xQ2Lm9",
    createdAt: "2026-06-15T12:00:00Z"
  });
});

test("GET /v1/expenses/:shareId returns safe 404 for missing expense", async () => {
  const response = await handleRequest(new Request("http://localhost/v1/expenses/Missing123"), {
    EXPENSES_DB: new FakeD1Database()
  });

  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), {
    error: {
      code: "EXPENSE_NOT_FOUND",
      message: "Expense not found."
    }
  });
});

test("GET /e/:shareId renders read-only guest page", async () => {
  const response = await handleRequest(new Request("http://localhost/e/A8xQ2Lm9"), {
    EXPENSES_DB: new FakeD1Database([
      expenseRow({
        shareId: "A8xQ2Lm9",
        payload: validExpensePayload()
      })
    ])
  });

  assert.equal(response.status, 200);
  assert.equal(response.headers.get("Content-Type"), "text/html; charset=utf-8");
  const html = await response.text();
  assert.match(html, /EvenUp/);
  assert.match(html, /Bella Roma/);
  assert.match(html, /Settlement Summary/);
  assert.match(html, /Dana/);
  assert.match(html, /read-only guest view/);
});

test("GET /e/:shareId renders safe guest error page for missing expense", async () => {
  const response = await handleRequest(new Request("http://localhost/e/Missing123"), {
    EXPENSES_DB: new FakeD1Database()
  });

  assert.equal(response.status, 404);
  assert.equal(response.headers.get("Content-Type"), "text/html; charset=utf-8");
  const html = await response.text();
  assert.match(html, /Expense not found/);
  assert.doesNotMatch(html, /Error:/);
});

function expenseRow({ shareId, payload }) {
  return {
    id: "expense_123",
    share_id: shareId,
    title: payload.title,
    payload_json: JSON.stringify(payload),
    created_at: "2026-06-15T12:00:00Z"
  };
}
