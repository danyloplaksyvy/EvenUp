export default {
  async fetch(request, env) {
    return handleRequest(request, env);
  }
};

const SHARE_ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
const SHARE_ID_LENGTH = 10;
const MAX_SHARE_ID_ATTEMPTS = 5;
const OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
const DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";
const MAX_RECEIPT_IMAGE_BYTES = 7 * 1024 * 1024;
const RECEIPT_PARSE_ERROR = {
  error: {
    code: "RECEIPT_PARSE_FAILED",
    message: "Could not read this receipt. Please try again or enter it manually."
  }
};

const receiptResponseSchema = {
  type: "object",
  additionalProperties: false,
  required: [
    "merchantName",
    "transactionDate",
    "currency",
    "items",
    "fees",
    "subtotalMinor",
    "totalMinor",
    "confidence"
  ],
  properties: {
    merchantName: { type: "string" },
    transactionDate: { type: ["string", "null"] },
    currency: { type: "string" },
    items: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["name", "quantity", "unitPriceMinor", "totalPriceMinor"],
        properties: {
          name: { type: "string" },
          quantity: { type: "number" },
          unitPriceMinor: { type: "integer" },
          totalPriceMinor: { type: "integer" }
        }
      }
    },
    fees: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["type", "label", "amountMinor"],
        properties: {
          type: {
            type: "string",
            enum: ["TAX", "TIP", "SERVICE_FEE", "DISCOUNT", "OTHER"]
          },
          label: { type: "string" },
          amountMinor: { type: "integer" }
        }
      }
    },
    subtotalMinor: { type: "integer" },
    totalMinor: { type: "integer" },
    confidence: { type: "number" }
  }
};

export async function handleRequest(request, env = {}) {
  const url = new URL(request.url);

  if (url.pathname === "/health") {
    return methodGuard(request, "GET", () => jsonResponse({ ok: true }));
  }

  if (url.pathname === "/v1/expenses") {
    return methodGuard(request, "POST", () => saveExpense(request, env));
  }

  if (url.pathname.startsWith("/v1/expenses/")) {
    return methodGuard(request, "GET", () => {
      const shareId = decodeURIComponent(url.pathname.slice("/v1/expenses/".length));
      return fetchExpenseResponse(shareId, env);
    });
  }

  if (url.pathname === "/v1/receipts/parse") {
    return methodGuard(request, "POST", () => parseReceipt(request, env));
  }

  if (url.pathname.startsWith("/e/")) {
    if (request.method !== "GET") {
      return htmlResponse(renderGuestErrorPage("Link unavailable", "This link cannot be opened."), 405);
    }

    const shareId = decodeURIComponent(url.pathname.slice("/e/".length));
    return renderGuestExpenseResponse(shareId, env);
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

async function methodGuard(request, method, handler) {
  if (request.method !== method) {
    return jsonResponse(
      {
        error: {
          code: "METHOD_NOT_ALLOWED",
          message: "Method not allowed."
        }
      },
      405,
      {
        Allow: method
      }
    );
  }

  return handler();
}

async function saveExpense(request, env) {
  if (!env.EXPENSES_DB) {
    return jsonResponse(
      {
        error: {
          code: "SERVER_MISCONFIGURED",
          message: "Expense storage is not configured."
        }
      },
      500
    );
  }

  let payload;
  try {
    payload = await request.json();
  } catch {
    return validationError("Request body must be valid JSON.");
  }

  const validationMessage = validateExpensePayload(payload);
  if (validationMessage) {
    return validationError(validationMessage);
  }

  const expenseId = `expense_${crypto.randomUUID()}`;
  const createdAt = new Date().toISOString();
  const publicBaseUrl = env.PUBLIC_BASE_URL || new URL(request.url).origin;

  for (let attempt = 0; attempt < MAX_SHARE_ID_ATTEMPTS; attempt += 1) {
    const shareId = generateShareId();

    try {
      await env.EXPENSES_DB
        .prepare(
          [
            "INSERT INTO expenses",
            "(id, share_id, title, payload_json, created_at)",
            "VALUES (?, ?, ?, ?, ?)"
          ].join(" ")
        )
        .bind(expenseId, shareId, payload.title.trim(), JSON.stringify(payload), createdAt)
        .run();

      return jsonResponse(
        {
          expenseId,
          shareId,
          shareUrl: new URL(`/e/${shareId}`, publicBaseUrl).toString()
        },
        201
      );
    } catch (error) {
      if (!isUniqueConstraintError(error) || attempt === MAX_SHARE_ID_ATTEMPTS - 1) {
        return jsonResponse(
          {
            error: {
              code: "EXPENSE_SAVE_FAILED",
              message: "Could not save this expense. Please try again."
            }
          },
          500
        );
      }
    }
  }

  return jsonResponse(
    {
      error: {
        code: "EXPENSE_SAVE_FAILED",
        message: "Could not save this expense. Please try again."
      }
    },
    500
  );
}

async function fetchExpenseResponse(shareId, env) {
  const row = await findExpenseByShareId(shareId, env);
  if (!row) {
    return jsonResponse(
      {
        error: {
          code: "EXPENSE_NOT_FOUND",
          message: "Expense not found."
        }
      },
      404
    );
  }

  return jsonResponse(expenseApiResponse(row));
}

async function findExpenseByShareId(shareId, env) {
  if (!env.EXPENSES_DB || !isValidShareId(shareId)) {
    return null;
  }

  return env.EXPENSES_DB
    .prepare("SELECT id, share_id, title, payload_json, created_at FROM expenses WHERE share_id = ?")
    .bind(shareId)
    .first();
}

function expenseApiResponse(row) {
  const payload = JSON.parse(row.payload_json);
  return {
    ...payload,
    expenseId: row.id,
    shareId: row.share_id,
    title: payload.title || row.title,
    createdAt: row.created_at
  };
}

function validateExpensePayload(payload) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return "Request body must be a finalized expense object.";
  }

  if (payload.schemaVersion !== 1) {
    return "schemaVersion must be 1.";
  }

  if (typeof payload.title !== "string" || payload.title.trim().length === 0) {
    return "title is required.";
  }

  if (!payload.receipt || typeof payload.receipt !== "object" || Array.isArray(payload.receipt)) {
    return "receipt is required.";
  }

  if (!Array.isArray(payload.participants)) {
    return "participants must be an array.";
  }

  if (
    typeof payload.payerParticipantId !== "string" ||
    payload.payerParticipantId.trim().length === 0
  ) {
    return "payerParticipantId is required.";
  }

  if (!Array.isArray(payload.itemAssignments)) {
    return "itemAssignments must be an array.";
  }

  if (!Array.isArray(payload.feeAllocations)) {
    return "feeAllocations must be an array.";
  }

  if (!payload.summary || typeof payload.summary !== "object" || Array.isArray(payload.summary)) {
    return "summary is required.";
  }

  return null;
}

function validationError(message) {
  return jsonResponse(
    {
      error: {
        code: "INVALID_EXPENSE_PAYLOAD",
        message
      }
    },
    400
  );
}

function generateShareId() {
  const randomBytes = new Uint8Array(SHARE_ID_LENGTH);
  crypto.getRandomValues(randomBytes);

  return Array.from(randomBytes, (byte) => SHARE_ID_ALPHABET[byte % SHARE_ID_ALPHABET.length]).join(
    ""
  );
}

function isUniqueConstraintError(error) {
  const message = String(error?.message || error || "").toLowerCase();
  return message.includes("unique") || message.includes("constraint");
}

async function parseReceipt(request, env) {
  if (!env.OPENAI_API_KEY) {
    return jsonResponse(
      {
        error: {
          code: "SERVER_MISCONFIGURED",
          message: "Receipt parsing is not configured."
        }
      },
      500
    );
  }

  let payload;
  try {
    payload = await request.json();
  } catch {
    return jsonResponse(RECEIPT_PARSE_ERROR, 400);
  }

  if (!isValidReceiptParseRequest(payload)) {
    return jsonResponse(RECEIPT_PARSE_ERROR, 400);
  }

  try {
    const fetcher = env.OPENAI_FETCH || fetch;
    const response = await fetcher(OPENAI_RESPONSES_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.OPENAI_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(openAiReceiptRequest(payload, env))
    });

    const responseJson = await response.json();
    if (!response.ok) {
      return jsonResponse(RECEIPT_PARSE_ERROR, 502);
    }

    const outputText = extractOpenAiOutputText(responseJson);
    if (!outputText) {
      return jsonResponse(RECEIPT_PARSE_ERROR, 502);
    }

    const receipt = JSON.parse(outputText);
    if (!isValidParsedReceipt(receipt)) {
      return jsonResponse(RECEIPT_PARSE_ERROR, 502);
    }

    return jsonResponse(receipt);
  } catch {
    return jsonResponse(RECEIPT_PARSE_ERROR, 502);
  }
}

function isValidReceiptParseRequest(payload) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return false;
  }

  if (typeof payload.imageBase64 !== "string" || payload.imageBase64.trim().length === 0) {
    return false;
  }

  if (typeof payload.mimeType !== "string" || !payload.mimeType.startsWith("image/")) {
    return false;
  }

  return estimateBase64ByteLength(payload.imageBase64) <= MAX_RECEIPT_IMAGE_BYTES;
}

function estimateBase64ByteLength(value) {
  const normalized = value.replace(/\s/g, "");
  const padding = normalized.endsWith("==") ? 2 : normalized.endsWith("=") ? 1 : 0;
  return Math.floor((normalized.length * 3) / 4) - padding;
}

function openAiReceiptRequest(payload, env) {
  const currencyHint = typeof payload.currencyHint === "string" ? payload.currencyHint : "unknown";
  const localeHint = typeof payload.localeHint === "string" ? payload.localeHint : "unknown";

  return {
    model: env.OPENAI_MODEL || DEFAULT_OPENAI_MODEL,
    input: [
      {
        role: "system",
        content: [
          {
            type: "input_text",
            text: [
              "You are a receipt parsing engine for a shared expense app.",
              "Return strict JSON only through the requested schema.",
              "Use integer minor units for every money field.",
              "Preserve visible receipt item order.",
              "Use null for transactionDate if no date is visible.",
              "Use fee type DISCOUNT for discounts and keep discount amountMinor negative.",
              "Do not invent items or prices."
            ].join(" ")
          }
        ]
      },
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: `Parse this receipt. Locale hint: ${localeHint}. Currency hint: ${currencyHint}.`
          },
          {
            type: "input_image",
            image_url: `data:${payload.mimeType};base64,${payload.imageBase64}`
          }
        ]
      }
    ],
    text: {
      format: {
        type: "json_schema",
        name: "receipt_parse",
        strict: true,
        schema: receiptResponseSchema
      }
    }
  };
}

function extractOpenAiOutputText(responseJson) {
  if (typeof responseJson.output_text === "string") {
    return responseJson.output_text;
  }

  for (const output of responseJson.output || []) {
    for (const content of output.content || []) {
      if (typeof content.text === "string") {
        return content.text;
      }
    }
  }

  return null;
}

function isValidParsedReceipt(receipt) {
  return (
    receipt &&
    typeof receipt === "object" &&
    !Array.isArray(receipt) &&
    typeof receipt.merchantName === "string" &&
    typeof receipt.currency === "string" &&
    Array.isArray(receipt.items) &&
    typeof receipt.totalMinor === "number"
  );
}

async function renderGuestExpenseResponse(shareId, env) {
  const row = await findExpenseByShareId(shareId, env);
  if (!row) {
    return htmlResponse(
      renderGuestErrorPage(
        "Expense not found",
        "This share link is missing, expired, or was typed incorrectly."
      ),
      404
    );
  }

  return htmlResponse(renderGuestExpensePage(expenseApiResponse(row)));
}

function renderGuestExpensePage(expense) {
  const participants = Array.isArray(expense.participants) ? expense.participants : [];
  const receipt = expense.receipt && typeof expense.receipt === "object" ? expense.receipt : {};
  const summary = expense.summary && typeof expense.summary === "object" ? expense.summary : {};
  const settlementRows = Array.isArray(summary.settlementRows) ? summary.settlementRows : [];
  const participantSummaries = Array.isArray(summary.participantSummaries)
    ? summary.participantSummaries
    : [];
  const items = Array.isArray(receipt.items) ? receipt.items : [];
  const fees = Array.isArray(receipt.fees) ? receipt.fees : [];
  const currency = receipt.currency || expense.currency || "USD";
  const totalMinor = firstNumber(receipt.totalMinor, summary.totalMinor, summary.receiptTotalMinor);

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(expense.title || "EvenUp Expense")}</title>
  <style>${guestCss()}</style>
</head>
<body>
  <header class="topbar"><span>EvenUp</span></header>
  <main>
    <section class="hero">
      <div class="eyebrow">Shared Expense</div>
      <h1>${escapeHtml(receipt.merchantName || expense.title || "Shared expense")}</h1>
      <div class="total">${formatMoney(totalMinor, currency)}</div>
      <p>${escapeHtml(formatDate(receipt.transactionDate || receipt.date))}</p>
      <div class="paid">Paid by <strong>${escapeHtml(participantName(expense.payerParticipantId, participants))}</strong></div>
    </section>
    <section class="panel">
      <h2>Settlement Summary</h2>
      ${renderSettlementRows(settlementRows, participants, currency)}
    </section>
    <section class="panel">
      <h2>Participant Breakdown</h2>
      ${renderParticipantSummaries(participantSummaries, participants, currency)}
    </section>
    <section class="panel">
      <h2>Item Breakdown</h2>
      ${renderItems(items, currency)}
      ${renderFees(fees, currency)}
    </section>
    <section class="notice">This is a read-only guest view.</section>
  </main>
  <footer>Powered by <strong>EvenUp</strong></footer>
</body>
</html>`;
}

function renderGuestErrorPage(title, message) {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(title)} - EvenUp</title>
  <style>${guestCss()}</style>
</head>
<body>
  <header class="topbar"><span>EvenUp</span></header>
  <main>
    <section class="error-card">
      <div class="icon">!</div>
      <h1>${escapeHtml(title)}</h1>
      <p>${escapeHtml(message)}</p>
    </section>
  </main>
  <footer>Powered by <strong>EvenUp</strong></footer>
</body>
</html>`;
}

function renderSettlementRows(rows, participants, currency) {
  if (rows.length === 0) {
    return `<p class="muted">No settlement payments needed.</p>`;
  }

  return rows
    .map((row) => {
      const from = participantName(row.fromParticipantId, participants);
      const to = participantName(row.toParticipantId, participants);
      const amount = firstNumber(row.amountMinor, row.amount);
      return `<div class="row strong"><span>${escapeHtml(from)} owes ${escapeHtml(to)}</span><b>${formatMoney(
        amount,
        currency
      )}</b></div>`;
    })
    .join("");
}

function renderParticipantSummaries(summaries, participants, currency) {
  if (summaries.length === 0) {
    return `<p class="muted">Participant shares are unavailable.</p>`;
  }

  return summaries
    .map((summary) => {
      const name = participantName(summary.participantId, participants);
      const share = firstNumber(summary.shareMinor, summary.personShareMinor, summary.personShare);
      const paid = firstNumber(summary.paidMinor, summary.amountPaidMinor, summary.amountPaid);
      return `<div class="row"><span>${escapeHtml(name)}</span><span>Share ${formatMoney(
        share,
        currency
      )} · Paid ${formatMoney(paid, currency)}</span></div>`;
    })
    .join("");
}

function renderItems(items, currency) {
  if (items.length === 0) {
    return `<p class="muted">Item details are unavailable.</p>`;
  }

  return items
    .map((item) => {
      const quantity = item.quantity && item.quantity !== 1 ? ` x ${escapeHtml(String(item.quantity))}` : "";
      const amount = firstNumber(item.totalPriceMinor, item.amountMinor, item.totalMinor);
      return `<div class="row"><span>${escapeHtml(item.name || "Receipt item")}${quantity}</span><span>${formatMoney(
        amount,
        currency
      )}</span></div>`;
    })
    .join("");
}

function renderFees(fees, currency) {
  if (fees.length === 0) {
    return "";
  }

  return `<div class="fees">${fees
    .map(
      (fee) =>
        `<div class="row muted"><span>${escapeHtml(fee.label || fee.type || "Fee")}</span><span>${formatMoney(
          firstNumber(fee.amountMinor),
          currency
        )}</span></div>`
    )
    .join("")}</div>`;
}

function participantName(participantId, participants) {
  const participant = participants.find((person) => person.id === participantId);
  return participant?.name || participantId || "Someone";
}

function firstNumber(...values) {
  return values.find((value) => Number.isFinite(value)) ?? 0;
}

function formatMoney(minor, currency) {
  return new Intl.NumberFormat("en", {
    style: "currency",
    currency,
    currencyDisplay: "narrowSymbol"
  }).format(minor / 100);
}

function formatDate(value) {
  return value || "Receipt details";
}

function isValidShareId(value) {
  return typeof value === "string" && /^[0-9A-Za-z]{8,12}$/.test(value);
}

function htmlResponse(body, status = 200) {
  return new Response(body, {
    status,
    headers: {
      "Content-Type": "text/html; charset=utf-8"
    }
  });
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => {
    switch (character) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      default:
        return "&#39;";
    }
  });
}

function guestCss() {
  return `
    :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #f9f9f9; color: #1a1c1c; }
    * { box-sizing: border-box; }
    body { margin: 0; min-height: 100vh; background: #f9f9f9; }
    .topbar { height: 64px; display: grid; place-items: center; border-bottom: 1px solid #e2e2e2; background: #f9f9f9; font-weight: 900; font-size: 24px; }
    main { width: min(100%, 480px); margin: 0 auto; padding: 24px 20px 32px; }
    .hero { text-align: center; padding: 16px 0 8px; }
    .eyebrow { display: inline-flex; border: 1px solid #e2e2e2; border-radius: 999px; padding: 6px 12px; color: #5f5e5e; font-size: 12px; font-weight: 700; text-transform: uppercase; }
    h1 { margin: 14px 0 8px; font-size: 32px; line-height: 40px; }
    h2 { margin: 0 0 12px; font-size: 16px; line-height: 22px; }
    .total { font-size: 42px; line-height: 48px; font-weight: 900; }
    .paid { display: inline-flex; margin-top: 16px; padding: 9px 14px; border: 1px solid #e2e2e2; border-radius: 999px; background: #fff; }
    .panel, .error-card { margin-top: 24px; border: 1px solid #e2e2e2; border-radius: 18px; background: #fff; padding: 18px; box-shadow: 0 12px 30px rgba(0,0,0,0.04); }
    .row { display: flex; justify-content: space-between; gap: 16px; padding: 12px 0; border-top: 1px solid #eeeeee; }
    .row:first-of-type { border-top: 0; }
    .strong { font-size: 18px; font-weight: 700; }
    .muted { color: #5f5e5e; }
    .fees { margin-top: 12px; border-top: 1px solid #000; }
    .notice { margin-top: 24px; text-align: center; border: 1px solid #e2e2e2; border-radius: 10px; padding: 12px; color: #5f5e5e; background: #f3f3f3; font-size: 13px; }
    .error-card { text-align: center; padding: 32px 22px; }
    .icon { width: 56px; height: 56px; margin: 0 auto 14px; border-radius: 999px; display: grid; place-items: center; background: #ffdad6; color: #93000a; font-weight: 900; }
    footer { padding: 24px; text-align: center; color: #5f5e5e; }
  `;
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
