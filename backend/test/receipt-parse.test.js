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
      totalPriceMinor: 3600
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
  confidence: 0.92
};

test("POST /v1/receipts/parse calls OpenAI and returns strict receipt JSON", async () => {
  const calls = [];
  const response = await handleRequest(
    jsonRequest("http://localhost/v1/receipts/parse", {
      imageBase64: "ZmFrZS1pbWFnZQ==",
      mimeType: "image/jpeg",
      localeHint: "en",
      currencyHint: "EUR"
    }),
    {
      OPENAI_API_KEY: "test-key",
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
