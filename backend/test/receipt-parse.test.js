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
  const imageInput = requestBody.input[1].content.find((content) => content.type === "input_image");
  assert.equal(imageInput.image_url, "data:image/jpeg;base64,ZmFrZS1pbWFnZQ==");
  assert.equal(imageInput.detail, "high");
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

test("POST /v1/receipts/parse rejects fractional quantity before returning success", async () => {
  const logger = testLogger();
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg"
    }),
    {
      OPENAI_API_KEY: "test-key",
      RECEIPT_PARSE_LOGGER: logger,
      OPENAI_FETCH: openAiFetchSequence([
        {
          ...parsedReceipt,
          items: [{ ...parsedReceipt.items[0], quantity: 1.5 }]
        }
      ])
    }
  );

  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), receiptParseError());
  assert.ok(
    logger.entries.some(
      (entry) =>
        entry.stage === "receipt_domain_validation" &&
        entry.valid === false &&
        entry.reason === "invalid_item_quantity" &&
        entry.fieldPath === "items[0].quantity"
    )
  );
});

test("POST /v1/receipts/parse rejects domain-incompatible successful OpenAI output", async () => {
  const invalidReceipts = [
    ["blank_merchant_name", "merchantName", { ...parsedReceipt, merchantName: "  " }],
    ["no_items", "items", { ...parsedReceipt, items: [] }],
    [
      "blank_item_name",
      "items[0].name",
      {
        ...parsedReceipt,
        items: [{ ...parsedReceipt.items[0], name: " " }]
      }
    ],
    [
      "blank_fee_label",
      "fees[0].label",
      {
        ...parsedReceipt,
        fees: [{ ...parsedReceipt.fees[0], label: " " }]
      }
    ],
    ["invalid_currency", "currency", { ...parsedReceipt, currency: "EURO" }],
    [
      "non_positive_unit_price",
      "items[0].unitPriceMinor",
      {
        ...parsedReceipt,
        items: [{ ...parsedReceipt.items[0], unitPriceMinor: 0 }]
      }
    ],
    [
      "non_positive_total_price",
      "items[0].totalPriceMinor",
      {
        ...parsedReceipt,
        items: [{ ...parsedReceipt.items[0], quantity: 1, totalPriceMinor: 0, candidatesMinor: [] }]
      }
    ],
    [
      "invalid_discount_amount",
      "fees[0].amountMinor",
      {
        ...parsedReceipt,
        fees: [{ type: "DISCOUNT", label: "Discount", amountMinor: 100 }]
      }
    ],
    [
      "non_positive_fee_amount",
      "fees[0].amountMinor",
      {
        ...parsedReceipt,
        fees: [{ type: "TAX", label: "Tax", amountMinor: -100 }]
      }
    ]
  ];

  for (const [reason, fieldPath, receipt] of invalidReceipts) {
    const logger = testLogger();
    const response = await handleRequest(
      jsonRequest("http://localhost/v1/receipts/parse", {
        imageBase64: "ZmFrZS1pbWFnZQ==",
        mimeType: "image/jpeg"
      }),
      {
        OPENAI_API_KEY: "test-key",
        RECEIPT_PARSE_LOGGER: logger,
        OPENAI_FETCH: openAiFetchSequence([receipt])
      }
    );

    assert.equal(response.status, 502, reason);
    assert.deepEqual(await response.json(), receiptParseError(), reason);
    assert.ok(
      logger.entries.some(
        (entry) =>
          entry.stage === "receipt_domain_validation" &&
          entry.valid === false &&
          entry.reason === reason &&
          entry.fieldPath === fieldPath
      ),
      reason
    );
  }
});

test("POST /v1/receipts/parse normalizes safe domain-compatible values", async () => {
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg"
    }),
    {
      OPENAI_API_KEY: "test-key",
      OPENAI_FETCH: openAiFetchSequence([
        {
          ...parsedReceipt,
          merchantName: "  Bella Roma  ",
          currency: " eur ",
          items: [
            {
              ...parsedReceipt.items[0],
              name: "  Pasta  ",
              candidatesMinor: [3600, -1, 0, 3600]
            }
          ],
          fees: [{ ...parsedReceipt.fees[0], type: "tax", label: "  Tax  " }]
        }
      ])
    }
  );

  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.merchantName, "Bella Roma");
  assert.equal(body.currency, "EUR");
  assert.equal(body.items[0].name, "Pasta");
  assert.deepEqual(body.items[0].candidatesMinor, [3600]);
  assert.equal(body.fees[0].type, "TAX");
  assert.equal(body.fees[0].label, "Tax");
});

test("POST /v1/receipts/parse uses audit for domain violations only when enabled", async () => {
  const calls = [];
  const logger = testLogger();
  const invalidReceipt = {
    ...parsedReceipt,
    items: [{ ...parsedReceipt.items[0], quantity: 1.5 }]
  };
  const repairedReceipt = {
    ...parsedReceipt,
    items: [{ ...parsedReceipt.items[0], quantity: 1 }]
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
      OPENAI_FETCH: openAiFetchSequence([invalidReceipt, repairedReceipt], calls)
    }
  );

  const body = await response.json();

  assert.equal(response.status, 200);
  assert.deepEqual(calls, ["receipt_parse", "receipt_parse_audit"]);
  assert.equal(body.items[0].quantity, 1);
  assert.ok(
    logger.entries.some(
      (entry) =>
        entry.stage === "receipt_domain_validation" &&
        entry.phase === "first" &&
        entry.valid === false &&
        entry.reason === "invalid_item_quantity"
    )
  );
  assert.ok(
    logger.entries.some(
      (entry) =>
        entry.stage === "receipt_domain_validation" &&
        entry.phase === "audit" &&
        entry.valid === true
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

test("POST /v1/receipts/parse rejects unsupported image MIME before OpenAI", async () => {
  let openAiCalls = 0;
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/heic"
    }),
    {
      OPENAI_API_KEY: "test-key",
      OPENAI_FETCH: async () => {
        openAiCalls += 1;
        return Response.json({ output_text: JSON.stringify(parsedReceipt) });
      }
    }
  );

  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), receiptUnsupportedImageError());
  assert.equal(openAiCalls, 0);
});

test("POST /v1/receipts/parse rejects oversized image before OpenAI", async () => {
  let openAiCalls = 0;
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: Buffer.alloc(7 * 1024 * 1024 + 1).toString("base64"),
      mimeType: "image/jpeg"
    }),
    {
      OPENAI_API_KEY: "test-key",
      OPENAI_FETCH: async () => {
        openAiCalls += 1;
        return Response.json({ output_text: JSON.stringify(parsedReceipt) });
      }
    }
  );

  assert.equal(response.status, 413);
  assert.deepEqual(await response.json(), receiptImageTooLargeError());
  assert.equal(openAiCalls, 0);
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

function receiptUnsupportedImageError() {
  return {
    error: {
      code: "RECEIPT_IMAGE_UNSUPPORTED",
      message: "Unsupported receipt image format. Please choose a JPEG, PNG, WEBP, or non-animated GIF image."
    }
  };
}

function receiptImageTooLargeError() {
  return {
    error: {
      code: "RECEIPT_IMAGE_TOO_LARGE",
      message: "Receipt image is too large. Please choose a smaller image."
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

function openAiFetchSequence(receipts, calls = []) {
  return async (_url, options) => {
    calls.push(JSON.parse(options.body).text.format.name);
    const receipt = receipts[Math.min(calls.length - 1, receipts.length - 1)];
    return Response.json({
      output_text: JSON.stringify(receipt)
    });
  };
}
