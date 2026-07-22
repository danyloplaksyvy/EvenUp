import assert from "node:assert/strict";
import test from "node:test";

import { handleRequest } from "../src/index.js";
import { aiExpenseExtractionSchema } from "../src/ai-expense.js";
import { jsonRequest } from "./fixtures.js";

test("POST /v1/expenses/interpret returns a strict complete extraction and echoes request id", async () => {
  const calls = [];
  const extraction = completeExtraction();
  const response = await interpretRequest(baseRequest(), {
    OPENAI_FETCH: async (url, options) => {
      calls.push({ url, options });
      return Response.json({ output_text: JSON.stringify(extraction) });
    }
  });

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    schemaVersion: 1,
    requestId: "request-1",
    extraction
  });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://api.openai.com/v1/responses");
  const openAiBody = JSON.parse(calls[0].options.body);
  assert.equal(openAiBody.model, "gpt-4.1-mini");
  assert.equal(openAiBody.text.format.strict, true);
  assert.deepEqual(openAiBody.text.format.schema, aiExpenseExtractionSchema);
  assert.equal(openAiBody.text.format.schema.additionalProperties, false);
});

test("interpretation allows incomplete facts for client-owned clarification", async () => {
  const extraction = completeExtraction({
    totalMinor: null,
    payerParticipantRef: null,
    participants: [participant("person-dana", "Dana")],
    warnings: [{ code: "MISSING_PAYER", path: "payerParticipantRef" }]
  });
  const response = await interpretRequest(baseRequest(), fakeExtraction(extraction));

  assert.equal(response.status, 200);
  assert.deepEqual((await response.json()).extraction, extraction);
});

test("clarification merge preserves unrelated prior facts and stable references", async () => {
  const prior = completeExtraction({
    title: "Team lunch",
    pricingMode: "Itemized",
    participants: [{ ...participant("person-dana", "Dana"), isSelf: true }, participant("person-lee", "Lee")],
    payerParticipantRef: null,
    items: [item("item-pizza", "Pizza", 2400)]
  });
  const clarificationOutput = completeExtraction({
    title: null,
    pricingMode: null,
    participants: [participant("changed-dana", "  dana  "), participant("person-lee", "Lee")],
    payerParticipantRef: "changed-dana",
    items: [],
    splitEverythingEqually: false,
    provenance: [provenance("payerParticipantRef", "Clarified")]
  });
  const response = await interpretRequest(
    baseRequest({
      priorExtraction: prior,
      activeClarification: { kind: "Payer", answer: "Dana paid" },
      clarificationHistory: [{ kind: "Payer", answer: "Dana paid" }]
    }),
    fakeExtraction(clarificationOutput)
  );

  assert.equal(response.status, 200);
  const extraction = (await response.json()).extraction;
  assert.equal(extraction.title, "Team lunch");
  assert.equal(extraction.payerParticipantRef, "person-dana");
  assert.deepEqual(extraction.participants.map((value) => value.ref), ["person-dana", "person-lee"]);
  assert.equal(extraction.participants[0].isSelf, true);
  assert.equal(extraction.items[0].ref, "item-pizza");
  assert.equal(extraction.splitEverythingEqually, true);
});

test("split clarification can explicitly replace prior split intent", async () => {
  const response = await interpretRequest(
    baseRequest({
      priorExtraction: completeExtraction({ splitEverythingEqually: true }),
      activeClarification: { kind: "SplitIntent", answer: "Do not split everything equally" }
    }),
    fakeExtraction(completeExtraction({ splitEverythingEqually: false }))
  );

  assert.equal(response.status, 200);
  assert.equal((await response.json()).extraction.splitEverythingEqually, false);
});

test("schema-valid semantic failures receive one repair attempt", async () => {
  const calls = [];
  const invalid = completeExtraction({ payerParticipantRef: "missing-person" });
  const repaired = completeExtraction();
  const response = await interpretRequest(baseRequest(), {
    OPENAI_FETCH: async (_url, options) => {
      calls.push(JSON.parse(options.body));
      return Response.json({ output_text: JSON.stringify(calls.length === 1 ? invalid : repaired) });
    }
  });

  assert.equal(response.status, 200);
  assert.equal(calls.length, 2);
  const repairContext = JSON.parse(calls[1].input[1].content[0].text);
  assert.match(repairContext.repair.reason, /payerParticipantRef/);
  assert.equal((await response.json()).extraction.payerParticipantRef, "person-dana");
});

test("semantic failure after repair returns a safe error", async () => {
  const invalid = completeExtraction({ payerParticipantRef: "missing-person" });
  const response = await interpretRequest(baseRequest(), fakeExtraction(invalid));

  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), {
    error: {
      code: "INVALID_INTERPRETATION",
      message: "The expense details could not be interpreted safely. Please try again.",
      requestId: "request-1"
    }
  });
});

test("refusal and incomplete model responses use machine-readable safe errors", async () => {
  const refusal = await interpretRequest(baseRequest(), {
    OPENAI_FETCH: async () => Response.json({ output: [{ content: [{ type: "refusal", refusal: "unsupported" }] }] })
  });
  assert.equal(refusal.status, 422);
  assert.equal((await refusal.json()).error.code, "INTERPRETATION_REFUSED");

  const incomplete = await interpretRequest(baseRequest(), {
    OPENAI_FETCH: async () => Response.json({ status: "incomplete", incomplete_details: { reason: "max_output_tokens" } })
  });
  assert.equal(incomplete.status, 502);
  assert.equal((await incomplete.json()).error.code, "UPSTREAM_INCOMPLETE");
});

test("interpretation enforces language, description, answer, and session limits before OpenAI", async () => {
  let calls = 0;
  const env = { OPENAI_API_KEY: "test-key", OPENAI_FETCH: async () => { calls += 1; } };
  const unsupported = await interpretRequest(baseRequest({ language: "de" }), env);
  assert.equal(unsupported.status, 400);
  assert.equal((await unsupported.json()).error.code, "UNSUPPORTED_LANGUAGE");

  const description = await interpretRequest(baseRequest({ description: "x".repeat(4_001) }), env);
  assert.equal((await description.json()).error.code, "INPUT_TOO_LARGE");

  const answer = await interpretRequest(
    baseRequest({ activeClarification: { kind: "Payer", answer: "x".repeat(1_001) } }),
    env
  );
  assert.equal((await answer.json()).error.code, "INPUT_TOO_LARGE");

  const turns = await interpretRequest(
    baseRequest({ clarificationHistory: Array.from({ length: 11 }, () => ({ kind: "Payer", answer: "Dana" })) }),
    env
  );
  assert.equal((await turns.json()).error.code, "INPUT_TOO_LARGE");
  assert.equal(calls, 0);
});

test("AI interpretation logs only safe request metadata", async () => {
  const entries = [];
  const secretDescription = "A private dinner for Dana and Lee";
  const response = await interpretRequest(baseRequest({ description: secretDescription }), {
    AI_EXPENSE_LOGGER: { log: (entry) => entries.push(entry) },
    OPENAI_FETCH: async () => Response.json({ output_text: JSON.stringify(completeExtraction()) })
  });

  assert.equal(response.status, 200);
  assert.equal(entries.length, 1);
  assert.equal(entries[0].descriptionCharacters, secretDescription.length);
  assert.equal(JSON.stringify(entries).includes(secretDescription), false);
  assert.equal(JSON.stringify(entries).includes("Dana"), false);
});

function interpretRequest(body, env = {}) {
  return handleRequest(
    jsonRequest("http://localhost/v1/expenses/interpret", body),
    { OPENAI_API_KEY: "test-key", ...env }
  );
}

function fakeExtraction(extraction) {
  return { OPENAI_FETCH: async () => Response.json({ output_text: JSON.stringify(extraction) }) };
}

function baseRequest(overrides = {}) {
  return {
    schemaVersion: 1,
    sessionId: "session-1",
    requestId: "request-1",
    language: "en",
    locale: "en-US",
    defaultCurrency: "USD",
    personalName: null,
    description: "Dana paid $48 for dinner with Lee and they split everything equally.",
    activeClarification: null,
    clarificationHistory: [],
    priorExtraction: null,
    ...overrides
  };
}

function completeExtraction(overrides = {}) {
  return {
    title: "Dinner",
    transactionDate: null,
    currency: "USD",
    totalMinor: 4800,
    pricingMode: "TotalOnly",
    participants: [participant("person-dana", "Dana"), participant("person-lee", "Lee")],
    payerParticipantRef: "person-dana",
    items: [],
    fees: [],
    splitEverythingEqually: true,
    provenance: [provenance("totalMinor", "Explicit")],
    warnings: [],
    ...overrides
  };
}

function participant(ref, name) {
  return { ref, name, isSelf: false };
}

function item(ref, name, totalPriceMinor) {
  return {
    ref,
    name,
    quantity: 1,
    unitPriceMinor: totalPriceMinor,
    totalPriceMinor,
    assignment: null
  };
}

function provenance(path, source) {
  return { path, source, needsReview: false, reason: null };
}
