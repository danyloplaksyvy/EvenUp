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
  assert.match(html, /Who pays for what/);
  assert.match(html, /Dana/);
  assert.match(html, /Pizza Margherita/);
  assert.match(html, /Shared Equal/);
  assert.match(html, /Tax/);
  assert.match(html, /read-only guest view/);
});

test("protected guest page requires passcode and remembers successful access", async () => {
  const database = new FakeD1Database();
  const saveResponse = await handleRequest(
    new Request("http://localhost/v1/expenses", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(
        validExpensePayload({
          guestAccess: {
            passcode: "KTRQ"
          }
        })
      )
    }),
    {
      EXPENSES_DB: database,
      PUBLIC_BASE_URL: "https://evenup.example"
    }
  );
  const saved = await saveResponse.json();

  const gatedResponse = await handleRequest(new Request(`http://localhost/e/${saved.shareId}`), {
    EXPENSES_DB: database
  });
  const gatedHtml = await gatedResponse.text();

  assert.equal(gatedResponse.status, 200);
  assert.match(gatedHtml, /Enter guest code/);
  assert.doesNotMatch(gatedHtml, /Who pays for what/);

  const accessResponse = await handleRequest(
    new Request(`http://localhost/e/${saved.shareId}/access`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded"
      },
      body: "passcode=KTRQ"
    }),
    {
      EXPENSES_DB: database,
      GUEST_ACCESS_COOKIE_SECRET: "test-secret"
    }
  );

  assert.equal(accessResponse.status, 303);
  const cookie = accessResponse.headers.get("Set-Cookie");
  assert.match(cookie, /evenup_guest_access=/);

  const unlockedResponse = await handleRequest(
    new Request(`http://localhost/e/${saved.shareId}`, {
      headers: {
        Cookie: cookie
      }
    }),
    {
      EXPENSES_DB: database,
      GUEST_ACCESS_COOKIE_SECRET: "test-secret"
    }
  );
  const unlockedHtml = await unlockedResponse.text();

  assert.equal(unlockedResponse.status, 200);
  assert.match(unlockedHtml, /Who pays for what/);
  assert.match(unlockedHtml, /Pizza Margherita/);
});

test("protected guest page accepts QR access code query and redirects to clean link", async () => {
  const database = new FakeD1Database();
  const saveResponse = await handleRequest(
    new Request("http://localhost/v1/expenses", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(
        validExpensePayload({
          guestAccess: {
            passcode: "KTRQ"
          }
        })
      )
    }),
    {
      EXPENSES_DB: database,
      PUBLIC_BASE_URL: "https://evenup.example"
    }
  );
  const saved = await saveResponse.json();

  const qrAccessResponse = await handleRequest(
    new Request(`http://localhost/e/${saved.shareId}?code=ktrq`),
    {
      EXPENSES_DB: database,
      GUEST_ACCESS_COOKIE_SECRET: "test-secret"
    }
  );

  assert.equal(qrAccessResponse.status, 303);
  assert.equal(qrAccessResponse.headers.get("Location"), `/e/${saved.shareId}`);
  const cookie = qrAccessResponse.headers.get("Set-Cookie");
  assert.match(cookie, /evenup_guest_access=/);

  const unlockedResponse = await handleRequest(
    new Request(`http://localhost/e/${saved.shareId}`, {
      headers: {
        Cookie: cookie
      }
    }),
    {
      EXPENSES_DB: database,
      GUEST_ACCESS_COOKIE_SECRET: "test-secret"
    }
  );
  const unlockedHtml = await unlockedResponse.text();

  assert.equal(unlockedResponse.status, 200);
  assert.match(unlockedHtml, /Who pays for what/);
});

test("protected API fetch accepts guest passcode header", async () => {
  const database = new FakeD1Database();
  const saveResponse = await handleRequest(
    new Request("http://localhost/v1/expenses", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(
        validExpensePayload({
          guestAccess: {
            passcode: "KTRQ"
          }
        })
      )
    }),
    {
      EXPENSES_DB: database
    }
  );
  const saved = await saveResponse.json();

  const blockedResponse = await handleRequest(new Request(`http://localhost/v1/expenses/${saved.shareId}`), {
    EXPENSES_DB: database
  });
  assert.equal(blockedResponse.status, 401);

  const allowedResponse = await handleRequest(
    new Request(`http://localhost/v1/expenses/${saved.shareId}`, {
      headers: {
        "X-EvenUp-Guest-Passcode": "KTRQ"
      }
    }),
    {
      EXPENSES_DB: database
    }
  );

  assert.equal(allowedResponse.status, 200);
  const body = await allowedResponse.json();
  assert.equal(body.shareId, saved.shareId);
  assert.equal(body.guestAccess, undefined);
});

test("wrong guest passcodes are rate limited", async () => {
  const database = new FakeD1Database();
  const saveResponse = await handleRequest(
    new Request("http://localhost/v1/expenses", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "User-Agent": "test-agent"
      },
      body: JSON.stringify(
        validExpensePayload({
          guestAccess: {
            passcode: "KTRQ"
          }
        })
      )
    }),
    {
      EXPENSES_DB: database
    }
  );
  const saved = await saveResponse.json();

  for (let index = 0; index < 5; index += 1) {
    await handleRequest(
      new Request(`http://localhost/e/${saved.shareId}/access`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "User-Agent": "test-agent"
        },
        body: "passcode=FAIL"
      }),
      {
        EXPENSES_DB: database,
        GUEST_ACCESS_COOKIE_SECRET: "test-secret"
      }
    );
  }

  const lockedResponse = await handleRequest(
    new Request(`http://localhost/e/${saved.shareId}/access`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "User-Agent": "test-agent"
      },
      body: "passcode=FAIL"
    }),
    {
      EXPENSES_DB: database,
      GUEST_ACCESS_COOKIE_SECRET: "test-secret"
    }
  );

  assert.equal(lockedResponse.status, 429);
  assert.match(await lockedResponse.text(), /Too many incorrect attempts/);
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
