import assert from "node:assert/strict";
import test from "node:test";

import { handleRequest } from "../src/index.js";
import { jsonRequest } from "./fixtures.js";

const parsedReceipt = {
  merchantName: "Bella Roma",
  transactionDate: "2026-06-15",
  currency: "EUR",
  items: [
    {
      name: "Pasta",
      quantity: 2,
      unitPriceMinor: 1800,
      totalPriceMinor: 3600,
      confidence: 0.92,
      candidatesMinor: [3600],
      needsReview: false
    }
  ],
  fees: [
    {
      type: "TAX",
      label: "Tax",
      amountMinor: 600
    }
  ],
  subtotalMinor: 3600,
  totalMinor: 4200,
  confidence: 0.92,
  corrections: [],
  reviewWarnings: []
};

test("POST /v1/receipts/parse calls OpenAI and returns strict receipt JSON", async () => {
  const calls = [];
  const logger = testLogger();
  const response = await handleRequest(
    jsonRequest(
      "http://localhost/v1/receipts/parse",
      {
        imageBase64: "ZmFrZS1pbWFnZQ==",
        mimeType: "image/jpeg",
        localeHint: "en",
        currencyHint: "EUR"
      },
      {
        "X-EvenUp-Request-Id": "receipt-scan-test"
      }
    ),
    {
      OPENAI_API_KEY: "test-key",
      RECEIPT_PARSE_LOGGER: logger,
      OPENAI_FETCH: async (url, options) => {
        calls.push({ url, options });
        return Response.json({
          output_text: JSON.stringify(parsedReceipt)
        });
      }
    }
  );

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), parsedReceipt);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://api.openai.com/v1/responses");
  assert.equal(calls[0].options.headers.Authorization, "Bearer test-key");

  const requestBody = JSON.parse(calls[0].options.body);
  assert.equal(requestBody.text.format.name, "receipt_parse");
  assert.match(JSON.stringify(requestBody), /data:image\/jpeg;base64,ZmFrZS1pbWFnZQ==/);
  assert.ok(logger.entries.every((entry) => entry.requestId === "receipt-scan-test"));
});

test("POST /v1/receipts/parse reconciles a single candidate against subtotal", async () => {
  const receiptWithMisreadItem = {
    merchantName: "Taberna do Mercado",
    transactionDate: "2016-09-06",
    currency: "GBP",
    items: [
      item("Super Bock", 2, 500, [500], 0.94, false),
      item("Sparkling Water", 1, 300, [300], 0.94, false),
      item("Cappucino", 1, 260, [260], 0.92, false),
      item("Espresso", 1, 200, [200], 0.92, false),
      item("Rissol", 2, 580, [580, 560], 0.62, true),
      item("Serra Da Estrela", 1, 890, [890], 0.92, false),
      item("Chourico Vinho Tinto", 1, 790, [790], 0.92, false),
      item("Octopus Peppers", 1, 900, [900], 0.92, false),
      item("Dorset Char", 1, 600, [600], 0.92, false),
      item("Bifana", 1, 800, [800], 0.92, false)
    ],
    fees: [
      {
        type: "SERVICE_FEE",
        label: "Service Charge",
        amountMinor: 725
      }
    ],
    subtotalMinor: 5800,
    totalMinor: 6525,
    confidence: 0.84,
    corrections: [],
    reviewWarnings: []
  };

  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg",
      localeHint: "en-GB",
      currencyHint: "GBP"
    }),
    {
      OPENAI_API_KEY: "test-key",
      OPENAI_FETCH: async () =>
        Response.json({
          output_text: JSON.stringify(receiptWithMisreadItem)
        })
    }
  );

  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.items[4].name, "Rissol");
  assert.equal(body.items[4].totalPriceMinor, 560);
  assert.equal(body.items[4].unitPriceMinor, 280);
  assert.deepEqual(body.items[4].candidatesMinor, [560, 580]);
  assert.deepEqual(body.corrections, [
    {
      field: "items[4].totalPriceMinor",
      itemName: "Rissol",
      fromMinor: 580,
      toMinor: 560,
      reason: "Corrected to match expected item subtotal 5800."
    }
  ]);
});

test("POST /v1/receipts/parse reconciles quantity unit price parsed as line total", async () => {
  const receiptWithQuantityLineTotalError = {
    merchantName: "Crowne Plaza Barcelona - Fira Center",
    transactionDate: "2019-03-10",
    currency: "EUR",
    items: [
      item("Burrata", 1, 1600, [1600], 0.94, false),
      item("Pan de Coca", 1, 350, [350], 0.94, false),
      {
        name: "Solomillo a la Sal",
        quantity: 2,
        unitPriceMinor: 2200,
        totalPriceMinor: 2200,
        confidence: 0.84,
        candidatesMinor: [2200],
        needsReview: false
      },
      item("100% Chocolate fondant", 1, 850, [850], 0.94, false),
      item("Agua 1/2", 1, 450, [450], 0.94, false),
      item("Copa Aphrodisiaque T.", 2, 1200, [1200], 0.94, false)
    ],
    fees: [
      {
        type: "DISCOUNT",
        label: "Portal Reserva 30%",
        amountMinor: -2655
      }
    ],
    subtotalMinor: 6650,
    totalMinor: 6195,
    confidence: 0.84,
    corrections: [],
    reviewWarnings: []
  };

  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg",
      localeHint: "es-ES",
      currencyHint: "EUR"
    }),
    {
      OPENAI_API_KEY: "test-key",
      OPENAI_FETCH: async () =>
        Response.json({
          output_text: JSON.stringify(receiptWithQuantityLineTotalError)
        })
    }
  );

  const body = await response.json();
  const solomillo = body.items[2];

  assert.equal(response.status, 200);
  assert.equal(body.subtotalMinor, 8850);
  assert.equal(solomillo.totalPriceMinor, 4400);
  assert.equal(solomillo.unitPriceMinor, 2200);
  assert.equal(solomillo.needsReview, true);
  assert.deepEqual(solomillo.candidatesMinor, [4400, 2200]);
  assert.deepEqual(body.reviewWarnings, []);
  assert.equal(body.corrections[0].field, "items[2].totalPriceMinor");
  assert.equal(body.corrections[0].itemName, "Solomillo a la Sal");
  assert.equal(body.corrections[0].fromMinor, 2200);
  assert.equal(body.corrections[0].toMinor, 4400);
  assert.match(body.corrections[0].reason, /quantity line total/i);
});

test("POST /v1/receipts/parse removes tax fee duplicated from receipt total", async () => {
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg",
      localeHint: "es-ES",
      currencyHint: "EUR"
    }),
    {
      OPENAI_API_KEY: "test-key",
      OPENAI_FETCH: async () =>
        Response.json({
          output_text: JSON.stringify(crownePlazaReceiptWithFees([
            {
              type: "TAX",
              label: "Tax",
              amountMinor: 6195
            },
            {
              type: "DISCOUNT",
              label: "Portal Reserva 30%",
              amountMinor: -2655
            }
          ]))
        })
    }
  );

  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(body.fees, [
    {
      type: "DISCOUNT",
      label: "Portal Reserva 30%",
      amountMinor: -2655
    }
  ]);
  assert.deepEqual(body.reviewWarnings, []);
  assert.deepEqual(body.corrections, [
    {
      field: "fees[0].amountMinor",
      itemName: null,
      fromMinor: 6195,
      toMinor: 0,
      reason: "Removed included VAT/tax duplicated from receipt total."
    }
  ]);
});

test("POST /v1/receipts/parse removes included IVA when discount already reconciles total", async () => {
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg",
      localeHint: "es-ES",
      currencyHint: "EUR"
    }),
    {
      OPENAI_API_KEY: "test-key",
      OPENAI_FETCH: async () =>
        Response.json({
          output_text: JSON.stringify(crownePlazaReceiptWithFees([
            {
              type: "TAX",
              label: "IVA 10%",
              amountMinor: 563
            },
            {
              type: "DISCOUNT",
              label: "Portal Reserva 30%",
              amountMinor: -2655
            }
          ]))
        })
    }
  );

  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(body.fees, [
    {
      type: "DISCOUNT",
      label: "Portal Reserva 30%",
      amountMinor: -2655
    }
  ]);
  assert.deepEqual(body.reviewWarnings, []);
  assert.equal(body.corrections[0].field, "fees[0].amountMinor");
  assert.equal(body.corrections[0].fromMinor, 563);
  assert.equal(body.corrections[0].toMinor, 0);
});

test("POST /v1/receipts/parse keeps true additive tax", async () => {
  const receiptWithAdditiveTax = {
    merchantName: "Bella Roma",
    transactionDate: "2026-06-15",
    currency: "EUR",
    items: [item("Pasta", 1, 1000, [1000], 0.92, false)],
    fees: [
      {
        type: "TAX",
        label: "Tax",
        amountMinor: 100
      }
    ],
    subtotalMinor: 1000,
    totalMinor: 1100,
    confidence: 0.92,
    corrections: [],
    reviewWarnings: []
  };

  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg"
    }),
    {
      OPENAI_API_KEY: "test-key",
      OPENAI_FETCH: async () =>
        Response.json({
          output_text: JSON.stringify(receiptWithAdditiveTax)
        })
    }
  );

  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(body.fees, receiptWithAdditiveTax.fees);
  assert.deepEqual(body.corrections, []);
  assert.deepEqual(body.reviewWarnings, []);
});

test("POST /v1/receipts/parse returns warning without audit by default when mismatch remains unresolved", async () => {
  const calls = [];
  const logger = testLogger();
  const mismatchedReceipt = {
    ...parsedReceipt,
    subtotalMinor: 3400,
    totalMinor: 4000
  };

  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg"
    }),
    {
      OPENAI_API_KEY: "test-key",
      RECEIPT_PARSE_LOGGER: logger,
      OPENAI_FETCH: async (url, options) => {
        calls.push(JSON.parse(options.body).text.format.name);
        return Response.json({
          output_text: JSON.stringify(mismatchedReceipt)
        });
      }
    }
  );

  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(calls, ["receipt_parse"]);
  assert.ok(logger.entries.every((entry) => entry.stage !== "audit_openai_request"));
  assert.ok(
    logger.entries.some(
      (entry) =>
        entry.stage === "deterministic_reconciliation" &&
        entry.auditEligible === true &&
        entry.auditEnabled === false &&
        entry.auditInvoked === false
    )
  );
  assert.match(body.reviewWarnings[0], /does not match expected item subtotal/);
});

test("POST /v1/receipts/parse uses auditor for unresolved mismatch when explicitly enabled", async () => {
  const calls = [];
  const logger = testLogger();
  const mismatchedReceipt = {
    ...parsedReceipt,
    subtotalMinor: 3400,
    totalMinor: 4000
  };

  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg"
    }),
    {
      OPENAI_API_KEY: "test-key",
      RECEIPT_PARSE_AUDIT: "true",
      RECEIPT_PARSE_LOGGER: logger,
      OPENAI_FETCH: async (url, options) => {
        calls.push(JSON.parse(options.body).text.format.name);
        return Response.json({
          output_text: JSON.stringify(mismatchedReceipt)
        });
      }
    }
  );

  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(calls, ["receipt_parse", "receipt_parse_audit"]);
  assert.ok(logger.entries.some((entry) => entry.stage === "audit_openai_request"));
  assert.ok(
    logger.entries.some(
      (entry) =>
        entry.stage === "deterministic_reconciliation" &&
        entry.auditEligible === true &&
        entry.auditEnabled === true &&
        entry.auditInvoked === true
    )
  );
  assert.match(body.reviewWarnings[0], /does not match expected item subtotal/);
});

test("POST /v1/receipts/parse uses auditor only when deterministic reconciliation fails", async () => {
  const calls = [];
  const logger = testLogger();
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg"
    }),
    {
      OPENAI_API_KEY: "test-key",
      RECEIPT_PARSE_AUDIT: "true",
      RECEIPT_PARSE_LOGGER: logger,
      OPENAI_FETCH: async (url, options) => {
        calls.push(JSON.parse(options.body).text.format.name);
        return Response.json({
          output_text: JSON.stringify(parsedReceipt)
        });
      }
    }
  );

  assert.equal(response.status, 200);
  assert.deepEqual(calls, ["receipt_parse"]);
  assert.ok(logger.entries.every((entry) => entry.stage !== "audit_openai_request"));
  assert.ok(
    logger.entries.some(
      (entry) =>
        entry.stage === "deterministic_reconciliation" &&
        entry.auditEligible === false &&
        entry.auditEnabled === true &&
        entry.auditInvoked === false
    )
  );
});

test("POST /v1/receipts/parse returns retry-friendly error for invalid request", async () => {
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "",
      mimeType: "image/jpeg"
    }),
    {
      OPENAI_API_KEY: "test-key"
    }
  );

  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), receiptParseError());
});

test("POST /v1/receipts/parse returns safe error when OpenAI fails", async () => {
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/png"
    }),
    {
      OPENAI_API_KEY: "test-key",
      OPENAI_FETCH: async () =>
        Response.json(
          {
            error: "upstream failure"
          },
          {
            status: 500
          }
        )
    }
  );

  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), receiptParseError());
});

function receiptParseError() {
  return {
    error: {
      code: "RECEIPT_PARSE_FAILED",
      message: "Could not read this receipt. Please try again or enter it manually."
    }
  };
}

function crownePlazaReceiptWithFees(fees) {
  return {
    merchantName: "Crowne Plaza Barcelona - Fira Center",
    transactionDate: "2019-03-10",
    currency: "EUR",
    items: [
      item("Burrata", 1, 1600, [1600], 0.94, false),
      item("Pan de Coca", 1, 350, [350], 0.94, false),
      item("Solomillo a la Sal", 2, 4400, [4400], 0.94, false),
      item("100% Chocolate fondant", 1, 850, [850], 0.94, false),
      item("Agua 1/2", 1, 450, [450], 0.94, false),
      item("Copa Aphrodisiaque T.", 2, 1200, [1200], 0.94, false)
    ],
    fees,
    subtotalMinor: 8850,
    totalMinor: 6195,
    confidence: 0.84,
    corrections: [],
    reviewWarnings: []
  };
}

function item(name, quantity, totalPriceMinor, candidatesMinor, confidence, needsReview) {
  return {
    name,
    quantity,
    unitPriceMinor: totalPriceMinor / quantity,
    totalPriceMinor,
    confidence,
    candidatesMinor,
    needsReview
  };
}

function testLogger() {
  const entries = [];
  return {
    entries,
    log: (entry) => entries.push(entry)
  };
}
