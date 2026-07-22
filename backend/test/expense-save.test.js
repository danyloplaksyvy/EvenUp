import assert from "node:assert/strict";
import test from "node:test";

import { handleRequest } from "../src/index.js";
import { FakeD1Database, jsonRequest, validExpensePayload, validTotalOnlyExpensePayload } from "./fixtures.js";

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
  assert.equal(database.rows[0].guest_passcode_hash, null);
  assert.equal(database.rows[0].guest_passcode_salt, null);
  assert.match(database.rows[0].created_at, /^\d{4}-\d{2}-\d{2}T/);
});

test("POST /v1/expenses stores passcode metadata without plaintext payload", async () => {
  const database = new FakeD1Database();
  const payload = validExpensePayload({
    guestAccess: {
      passcode: "KTRQ"
    }
  });

  const response = await handleRequest(
    jsonRequest("http://localhost:8787/v1/expenses", payload),
    {
      EXPENSES_DB: database,
      PUBLIC_BASE_URL: "https://evenup.example"
    }
  );

  assert.equal(response.status, 201);
  assert.equal(database.rows.length, 1);
  const storedPayload = JSON.parse(database.rows[0].payload_json);
  assert.equal(storedPayload.guestAccess, undefined);
  assert.match(database.rows[0].guest_passcode_hash, /^[0-9A-Za-z_-]+$/);
  assert.match(database.rows[0].guest_passcode_salt, /^[0-9A-Za-z_-]+$/);
});

test("POST /v1/expenses accepts and stores a schema v2 total-only expense", async () => {
  const database = new FakeD1Database();
  const payload = validTotalOnlyExpensePayload();
  const response = await handleRequest(jsonRequest("http://localhost:8787/v1/expenses", payload), {
    EXPENSES_DB: database
  });

  assert.equal(response.status, 201);
  assert.deepEqual(JSON.parse(database.rows[0].payload_json), payload);
});

test("POST /v1/expenses rejects inconsistent schema v2 base allocation", async () => {
  const payload = validTotalOnlyExpensePayload({
    baseAllocation: {
      mode: "EQUAL",
      shares: [
        { participantId: "participant_1", amountMinor: 451 },
        { participantId: "participant_2", amountMinor: 450 }
      ]
    }
  });
  const response = await handleRequest(jsonRequest("http://localhost:8787/v1/expenses", payload), {
    EXPENSES_DB: new FakeD1Database()
  });

  assert.equal(response.status, 400);
  assert.equal((await response.json()).error.message, "baseAllocation shares must sum to total minus fees and discounts.");
});

test("POST /v1/expenses rejects duplicate and non-equal schema v2 base shares", async () => {
  for (const shares of [
    [
      { participantId: "participant_1", amountMinor: 500 },
      { participantId: "participant_1", amountMinor: 500 }
    ],
    [
      { participantId: "participant_1", amountMinor: 600 },
      { participantId: "participant_2", amountMinor: 400 }
    ]
  ]) {
    const payload = validTotalOnlyExpensePayload({ baseAllocation: { mode: "EQUAL", shares } });
    const response = await handleRequest(jsonRequest("http://localhost:8787/v1/expenses", payload), {
      EXPENSES_DB: new FakeD1Database()
    });
    assert.equal(response.status, 400);
  }
});

test("POST /v1/expenses rejects invalid guest passcode", async () => {
  const response = await handleRequest(
    jsonRequest(
      "http://localhost:8787/v1/expenses",
      validExpensePayload({
        guestAccess: {
          passcode: "12"
        }
      })
    ),
    {
      EXPENSES_DB: new FakeD1Database()
    }
  );

  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), {
    error: {
      code: "INVALID_EXPENSE_PAYLOAD",
      message: "guestAccess.passcode must be exactly four letters."
    }
  });
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
