const OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
const DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";
const MAX_DESCRIPTION_CHARACTERS = 4_000;
const MAX_ANSWER_CHARACTERS = 1_000;
const MAX_CLARIFICATION_TURNS = 10;
const DEFAULT_OPENAI_TIMEOUT_MILLIS = 30_000;

const nullableInteger = { type: ["integer", "null"] };
const nullableString = { type: ["string", "null"] };

const shareSchema = {
  type: "object",
  additionalProperties: false,
  required: ["participantRef", "amountMinor", "quantity", "percentageBasisPoints", "ratioWeight"],
  properties: {
    participantRef: { type: "string" },
    amountMinor: nullableInteger,
    quantity: nullableInteger,
    percentageBasisPoints: nullableInteger,
    ratioWeight: nullableInteger
  }
};

export const aiExpenseExtractionSchema = {
  type: "object",
  additionalProperties: false,
  required: [
    "title",
    "transactionDate",
    "currency",
    "totalMinor",
    "pricingMode",
    "participants",
    "payerParticipantRef",
    "items",
    "fees",
    "splitEverythingEqually",
    "provenance",
    "warnings"
  ],
  properties: {
    title: nullableString,
    transactionDate: nullableString,
    currency: nullableString,
    totalMinor: nullableInteger,
    pricingMode: {
      type: ["string", "null"],
      enum: ["Itemized", "TotalOnly", null]
    },
    participants: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["ref", "name", "isSelf"],
        properties: {
          ref: { type: "string" },
          name: { type: "string" },
          isSelf: { type: "boolean" }
        }
      }
    },
    payerParticipantRef: nullableString,
    items: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["ref", "name", "quantity", "unitPriceMinor", "totalPriceMinor", "assignment"],
        properties: {
          ref: { type: "string" },
          name: { type: "string" },
          quantity: nullableInteger,
          unitPriceMinor: nullableInteger,
          totalPriceMinor: nullableInteger,
          assignment: {
            type: ["object", "null"],
            additionalProperties: false,
            required: ["mode", "shares"],
            properties: {
              mode: {
                type: "string",
                enum: ["Full", "ByUnits", "SharedEqual", "CustomAmount", "Percentage", "Ratio"]
              },
              shares: { type: "array", items: shareSchema }
            }
          }
        }
      }
    },
    fees: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["ref", "type", "label", "amountMinor", "allocationMode", "participantRefs", "shares"],
        properties: {
          ref: { type: "string" },
          type: {
            type: "string",
            enum: ["TAX", "TIP", "SERVICE_FEE", "DISCOUNT", "OTHER"]
          },
          label: { type: "string" },
          amountMinor: { type: "integer" },
          allocationMode: {
            type: ["string", "null"],
            enum: ["Equal", "Proportional", "Custom", null]
          },
          participantRefs: { type: "array", items: { type: "string" } },
          shares: { type: "array", items: shareSchema }
        }
      }
    },
    splitEverythingEqually: { type: "boolean" },
    provenance: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["path", "source", "needsReview", "reason"],
        properties: {
          path: { type: "string" },
          source: {
            type: "string",
            enum: ["Explicit", "Clarified", "Derived", "Defaulted"]
          },
          needsReview: { type: "boolean" },
          reason: nullableString
        }
      }
    },
    warnings: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["code", "path"],
        properties: {
          code: { type: "string" },
          path: nullableString
        }
      }
    }
  }
};

export async function interpretExpense(request, env = {}) {
  const startedAt = Date.now();
  let payload;
  try {
    payload = await request.json();
  } catch {
    return interpretationError("INVALID_INPUT", "Request body must be valid JSON.", 400, null);
  }

  const requestId = validIdentifier(payload?.requestId) || validIdentifier(request.headers.get("X-EvenUp-Request-Id"));
  const validation = validateInterpretRequest(payload);
  if (validation) {
    logInterpretation(env, requestId, validation.code, startedAt, payload, 400);
    return interpretationError(validation.code, validation.message, 400, requestId);
  }

  if (!env.OPENAI_API_KEY) {
    logInterpretation(env, requestId, "SERVER_MISCONFIGURED", startedAt, payload, 500);
    return interpretationError(
      "SERVER_MISCONFIGURED",
      "AI expense interpretation is not configured.",
      500,
      requestId
    );
  }

  try {
    const first = await requestInterpretation(payload, env, null);
    if (!first.ok) {
      logInterpretation(env, requestId, first.code, startedAt, payload, first.status);
      return interpretationError(first.code, first.message, first.status, requestId);
    }

    let extraction = mergeAiExtraction(
      payload.priorExtraction,
      normalizeExtraction(first.extraction),
      payload.activeClarification?.kind
    );
    let semanticFailure = validateExtraction(extraction);
    if (semanticFailure) {
      const repaired = await requestInterpretation(payload, env, semanticFailure, extraction);
      if (!repaired.ok) {
        logInterpretation(env, requestId, repaired.code, startedAt, payload, repaired.status);
        return interpretationError(repaired.code, repaired.message, repaired.status, requestId);
      }
      extraction = mergeAiExtraction(
        payload.priorExtraction,
        normalizeExtraction(repaired.extraction),
        payload.activeClarification?.kind
      );
      semanticFailure = validateExtraction(extraction);
    }

    if (semanticFailure) {
      logInterpretation(env, requestId, "INVALID_INTERPRETATION", startedAt, payload, 502);
      return interpretationError(
        "INVALID_INTERPRETATION",
        "The expense details could not be interpreted safely. Please try again.",
        502,
        requestId
      );
    }

    logInterpretation(env, requestId, "SUCCESS", startedAt, payload, 200);
    return jsonResponse({ schemaVersion: 1, requestId, extraction }, 200);
  } catch (error) {
    const timeout = error?.name === "AbortError" || error?.name === "TimeoutError";
    const code = timeout ? "UPSTREAM_TIMEOUT" : "UPSTREAM_UNAVAILABLE";
    const status = timeout ? 504 : 503;
    logInterpretation(env, requestId, code, startedAt, payload, status);
    return interpretationError(
      code,
      timeout ? "Expense interpretation timed out. Please retry." : "Expense interpretation is temporarily unavailable.",
      status,
      requestId
    );
  }
}

function validateInterpretRequest(payload) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return { code: "INVALID_INPUT", message: "Request body must be an object." };
  }
  if (payload.schemaVersion !== 1 || !validIdentifier(payload.sessionId) || !validIdentifier(payload.requestId)) {
    return { code: "INVALID_INPUT", message: "schemaVersion, sessionId, and requestId are required." };
  }
  if (payload.language !== "en") {
    return { code: "UNSUPPORTED_LANGUAGE", message: "Only English expense descriptions are supported." };
  }
  if (typeof payload.description !== "string" || payload.description.trim().length === 0) {
    return { code: "INVALID_INPUT", message: "description is required." };
  }
  if (payload.description.length > MAX_DESCRIPTION_CHARACTERS) {
    return { code: "INPUT_TOO_LARGE", message: "description must not exceed 4,000 characters." };
  }
  if (!Array.isArray(payload.clarificationHistory) || payload.clarificationHistory.length > MAX_CLARIFICATION_TURNS) {
    return { code: "INPUT_TOO_LARGE", message: "A session supports at most 10 clarification turns." };
  }
  const turns = [...payload.clarificationHistory, ...(payload.activeClarification ? [payload.activeClarification] : [])];
  if (turns.length > MAX_CLARIFICATION_TURNS) {
    return { code: "INPUT_TOO_LARGE", message: "A session supports at most 10 clarification turns." };
  }
  if (turns.some((turn) => !turn || typeof turn.kind !== "string" || typeof turn.answer !== "string")) {
    return { code: "INVALID_INPUT", message: "Clarification turns must include a kind and answer." };
  }
  if (turns.some((turn) => turn.answer.length > MAX_ANSWER_CHARACTERS)) {
    return { code: "INPUT_TOO_LARGE", message: "Clarification answers must not exceed 1,000 characters." };
  }
  if (payload.priorExtraction != null && (typeof payload.priorExtraction !== "object" || Array.isArray(payload.priorExtraction))) {
    return { code: "INVALID_INPUT", message: "priorExtraction must be an object." };
  }
  return null;
}

async function requestInterpretation(payload, env, repairReason, rejectedExtraction = null) {
  const fetcher = env.OPENAI_FETCH || fetch;
  const response = await fetcher(OPENAI_RESPONSES_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.OPENAI_API_KEY}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(openAiInterpretationRequest(payload, env, repairReason, rejectedExtraction)),
    signal: AbortSignal.timeout(positiveInteger(env.OPENAI_TIMEOUT_MS) || DEFAULT_OPENAI_TIMEOUT_MILLIS)
  });

  let responseJson;
  try {
    responseJson = await response.json();
  } catch {
    return upstreamFailure(response.status);
  }
  if (!response.ok) return upstreamFailure(response.status);
  if (responseJson?.status === "incomplete") {
    return { ok: false, code: "UPSTREAM_INCOMPLETE", message: "Expense interpretation was incomplete. Please retry.", status: 502 };
  }
  if (hasRefusal(responseJson)) {
    return { ok: false, code: "INTERPRETATION_REFUSED", message: "This description cannot be interpreted.", status: 422 };
  }

  const outputText = extractOutputText(responseJson);
  if (!outputText) {
    return { ok: false, code: "INVALID_INTERPRETATION", message: "The interpretation response was invalid.", status: 502 };
  }
  try {
    return { ok: true, extraction: JSON.parse(outputText) };
  } catch {
    return { ok: false, code: "INVALID_INTERPRETATION", message: "The interpretation response was invalid.", status: 502 };
  }
}

function openAiInterpretationRequest(payload, env, repairReason, rejectedExtraction) {
  const context = {
    schemaVersion: payload.schemaVersion,
    language: payload.language,
    locale: payload.locale,
    defaultCurrency: payload.defaultCurrency,
    personalName: payload.personalName ?? null,
    description: payload.description,
    activeClarification: payload.activeClarification ?? null,
    clarificationHistory: payload.clarificationHistory,
    priorExtraction: payload.priorExtraction ?? null,
    repair: repairReason ? { reason: repairReason, rejectedExtraction } : null
  };

  return {
    model: env.OPENAI_MODEL || DEFAULT_OPENAI_MODEL,
    input: [
      {
        role: "developer",
        content: [{ type: "input_text", text: interpretationInstructions() }]
      },
      {
        role: "user",
        content: [{ type: "input_text", text: JSON.stringify(context) }]
      }
    ],
    text: {
      format: {
        type: "json_schema",
        name: "ai_expense_interpretation",
        strict: true,
        schema: aiExpenseExtractionSchema
      }
    }
  };
}

function interpretationInstructions() {
  return [
    "Interpret English shared-expense facts into the supplied strict schema.",
    "Return a complete merged extraction, preserving unrelated prior facts during clarification.",
    "Extract only facts explicitly stated in the description or clarification answers.",
    "Never estimate or invent money, item prices, people, payer, currency, or split intent.",
    "Use integer minor units for money and 10,000 basis points for 100 percent.",
    "Keep participant, item, and fee refs stable across turns; reuse prior refs for the same entity.",
    "Represent first-person references as a participant with isSelf true. Use personalName only to resolve that participant.",
    "Use Itemized only when priced item lines are intended. Use TotalOnly for an overall total with optional unpriced descriptive items.",
    "splitEverythingEqually is true only when the user explicitly applies equal splitting to the whole expense.",
    "Do not derive a title or date and do not apply default currency; Android owns defaults and readiness.",
    "For provenance, use Explicit for the original description and Clarified for clarification answers.",
    "Use warnings for ambiguity; do not generate user-facing clarification question text."
  ].join(" ");
}

function normalizeExtraction(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return value;
  return {
    ...value,
    title: trimNullable(value.title),
    transactionDate: trimNullable(value.transactionDate),
    currency: trimNullable(value.currency)?.toUpperCase() ?? null,
    payerParticipantRef: trimNullable(value.payerParticipantRef),
    participants: array(value.participants).map((participant) => ({
      ...participant,
      ref: trim(participant.ref),
      name: trim(participant.name),
      isSelf: Boolean(participant.isSelf)
    })),
    items: array(value.items).map((item) => ({
      ...item,
      ref: trim(item.ref),
      name: trim(item.name),
      assignment: item.assignment ? {
        ...item.assignment,
        shares: array(item.assignment.shares).map(normalizeShare)
      } : null
    })),
    fees: array(value.fees).map((fee) => ({
      ...fee,
      ref: trim(fee.ref),
      type: trim(fee.type).toUpperCase(),
      label: trim(fee.label),
      participantRefs: array(fee.participantRefs).map(trim),
      shares: array(fee.shares).map(normalizeShare)
    })),
    provenance: array(value.provenance).map((fact) => ({ ...fact, path: trim(fact.path) })),
    warnings: array(value.warnings).map((warning) => ({ ...warning, code: trim(warning.code) }))
  };
}

function normalizeShare(share) {
  return { ...share, participantRef: trim(share.participantRef) };
}

export function mergeAiExtraction(priorValue, nextValue, activeClarificationKind = null) {
  const prior = priorValue && typeof priorValue === "object" ? priorValue : emptyExtraction();
  const next = nextValue && typeof nextValue === "object" ? nextValue : emptyExtraction();
  const participantMerge = mergeEntities(array(prior.participants), array(next.participants), "participant", "name");
  const participantRefMap = participantMerge.refMap;
  const participants = participantMerge.entities;
  const items = mergeEntities(array(prior.items), array(next.items), "item", "name").entities.map((item) => ({
    ...item,
    assignment: item.assignment ? {
      ...item.assignment,
      shares: array(item.assignment.shares).map((share) => remapShare(share, participantRefMap))
    } : null
  }));
  const fees = mergeEntities(array(prior.fees), array(next.fees), "fee", "label").entities.map((fee) => ({
    ...fee,
    participantRefs: array(fee.participantRefs).map((ref) => participantRefMap.get(ref) || ref),
    shares: array(fee.shares).map((share) => remapShare(share, participantRefMap))
  }));

  return {
    title: next.title ?? prior.title ?? null,
    transactionDate: next.transactionDate ?? prior.transactionDate ?? null,
    currency: next.currency ?? prior.currency ?? null,
    totalMinor: next.totalMinor ?? prior.totalMinor ?? null,
    pricingMode: next.pricingMode ?? prior.pricingMode ?? null,
    participants,
    payerParticipantRef: participantRefMap.get(next.payerParticipantRef) || next.payerParticipantRef || prior.payerParticipantRef || null,
    items,
    fees,
    splitEverythingEqually: activeClarificationKind === "SplitIntent"
      ? Boolean(next.splitEverythingEqually)
      : Boolean(prior.splitEverythingEqually || next.splitEverythingEqually),
    provenance: mergeByKey(array(prior.provenance), array(next.provenance), (value) => value.path),
    warnings: mergeByKey(array(prior.warnings), array(next.warnings), (value) => `${value.code}:${value.path || ""}`)
  };
}

function mergeEntities(prior, next, prefix, nameField) {
  const entities = prior.map((entity) => ({ ...entity }));
  const refMap = new Map();
  next.forEach((incoming, index) => {
    const matchIndex = entities.findIndex((existing) =>
      (incoming.ref && existing.ref === incoming.ref) ||
      normalizeName(existing[nameField]) === normalizeName(incoming[nameField])
    );
    const existing = matchIndex >= 0 ? entities[matchIndex] : null;
    const ref = existing?.ref || trim(incoming.ref) || stableRef(prefix, incoming[nameField], entities.length + index);
    if (incoming.ref) refMap.set(incoming.ref, ref);
    const merged = mergeEntityValues(existing, incoming, ref);
    if (matchIndex >= 0) entities[matchIndex] = merged;
    else entities.push(merged);
  });
  entities.forEach((entity) => refMap.set(entity.ref, entity.ref));
  return { entities, refMap };
}

function mergeEntityValues(existing, incoming, ref) {
  if (!existing) return { ...incoming, ref };
  const merged = { ...existing, ...incoming, ref };
  Object.keys(merged).forEach((key) => {
    if (
      incoming[key] == null ||
      (Array.isArray(incoming[key]) && incoming[key].length === 0 && Array.isArray(existing[key]) && existing[key].length > 0)
    ) {
      merged[key] = existing[key] ?? incoming[key];
    }
  });
  if ("isSelf" in merged) merged.isSelf = Boolean(existing.isSelf || incoming.isSelf);
  return merged;
}

function remapShare(share, refMap) {
  return { ...share, participantRef: refMap.get(share.participantRef) || share.participantRef };
}

function mergeByKey(prior, next, keyOf) {
  const merged = new Map(prior.map((value) => [keyOf(value), value]));
  next.forEach((value) => merged.set(keyOf(value), value));
  return Array.from(merged.values());
}

function validateExtraction(extraction) {
  if (!extraction || typeof extraction !== "object" || Array.isArray(extraction)) return "Extraction is not an object.";
  if (extraction.totalMinor != null && (!Number.isInteger(extraction.totalMinor) || extraction.totalMinor <= 0)) {
    return "totalMinor must be a positive integer when present.";
  }
  if (extraction.currency != null && !/^[A-Z]{3}$/.test(extraction.currency)) return "currency must be an ISO code.";
  if (extraction.transactionDate != null && !/^\d{4}-\d{2}-\d{2}$/.test(extraction.transactionDate)) {
    return "transactionDate must use ISO format.";
  }
  const participantRefs = new Set();
  for (const participant of array(extraction.participants)) {
    if (!participant.ref || !participant.name || participantRefs.has(participant.ref)) return "Participant refs and names must be unique and non-empty.";
    participantRefs.add(participant.ref);
  }
  if (extraction.payerParticipantRef && !participantRefs.has(extraction.payerParticipantRef)) {
    return "payerParticipantRef must identify an extracted participant.";
  }
  const entityRefs = new Set();
  for (const item of array(extraction.items)) {
    if (!item.ref || !item.name || entityRefs.has(item.ref)) return "Item refs and names must be valid.";
    entityRefs.add(item.ref);
    if (item.quantity != null && (!Number.isInteger(item.quantity) || item.quantity <= 0)) return "Item quantity must be positive.";
    if ([item.unitPriceMinor, item.totalPriceMinor].some((amount) => amount != null && (!Number.isInteger(amount) || amount <= 0))) {
      return "Item prices must be positive integer minor units.";
    }
    if (extraction.pricingMode === "TotalOnly" && (item.unitPriceMinor != null || item.totalPriceMinor != null)) {
      return "Total-only descriptive items cannot have prices.";
    }
    const shareFailure = validateShares(item.assignment?.shares, participantRefs);
    if (shareFailure) return shareFailure;
  }
  for (const fee of array(extraction.fees)) {
    if (!fee.ref || !fee.label || entityRefs.has(fee.ref) || !Number.isInteger(fee.amountMinor)) return "Fees must be valid and uniquely referenced.";
    entityRefs.add(fee.ref);
    if (fee.type === "DISCOUNT" ? fee.amountMinor >= 0 : fee.amountMinor <= 0) return "Fee amount sign is inconsistent with its type.";
    if (array(fee.participantRefs).some((ref) => !participantRefs.has(ref))) return "Fee participant reference is invalid.";
    const shareFailure = validateShares(fee.shares, participantRefs);
    if (shareFailure) return shareFailure;
  }
  return null;
}

function validateShares(shares, participantRefs) {
  const seenParticipantRefs = new Set();
  for (const share of array(shares)) {
    if (!participantRefs.has(share.participantRef)) return "Assignment participant reference is invalid.";
    if (seenParticipantRefs.has(share.participantRef)) return "Assignment participant references must be unique.";
    seenParticipantRefs.add(share.participantRef);
    if (share.amountMinor != null && !Number.isInteger(share.amountMinor)) return "Share amount must use integer minor units.";
    if (share.quantity != null && (!Number.isInteger(share.quantity) || share.quantity <= 0)) return "Share quantity must be positive.";
    if (share.percentageBasisPoints != null && (!Number.isInteger(share.percentageBasisPoints) || share.percentageBasisPoints <= 0 || share.percentageBasisPoints > 10_000)) return "Share percentage is invalid.";
    if (share.ratioWeight != null && (!Number.isInteger(share.ratioWeight) || share.ratioWeight <= 0)) return "Share ratio is invalid.";
  }
  return null;
}

function hasRefusal(responseJson) {
  return array(responseJson?.output).some((output) =>
    array(output?.content).some((content) => content?.type === "refusal" || typeof content?.refusal === "string")
  );
}

function extractOutputText(responseJson) {
  if (typeof responseJson?.output_text === "string") return responseJson.output_text;
  for (const output of array(responseJson?.output)) {
    for (const content of array(output?.content)) {
      if (typeof content?.text === "string") return content.text;
    }
  }
  return null;
}

function upstreamFailure(status) {
  if (status === 429) return { ok: false, code: "RATE_LIMITED", message: "Too many requests. Please wait and retry.", status: 429 };
  if (status === 408 || status === 504) return { ok: false, code: "UPSTREAM_TIMEOUT", message: "Expense interpretation timed out. Please retry.", status: 504 };
  return { ok: false, code: "UPSTREAM_UNAVAILABLE", message: "Expense interpretation is temporarily unavailable.", status: 503 };
}

function interpretationError(code, message, status, requestId) {
  return jsonResponse({ error: { code, message, ...(requestId ? { requestId } : {}) } }, status);
}

function logInterpretation(env, requestId, result, startedAt, payload, status) {
  const event = {
    type: "ai_expense_interpretation",
    requestId,
    status,
    result,
    latencyMs: Math.max(0, Date.now() - startedAt),
    descriptionCharacters: typeof payload?.description === "string" ? payload.description.length : 0,
    clarificationTurns: Array.isArray(payload?.clarificationHistory) ? payload.clarificationHistory.length : 0
  };
  const logger = env.AI_EXPENSE_LOGGER;
  if (logger && typeof logger.log === "function") logger.log(event);
}

function emptyExtraction() {
  return {
    title: null,
    transactionDate: null,
    currency: null,
    totalMinor: null,
    pricingMode: null,
    participants: [],
    payerParticipantRef: null,
    items: [],
    fees: [],
    splitEverythingEqually: false,
    provenance: [],
    warnings: []
  };
}

function stableRef(prefix, value, index) {
  const slug = normalizeName(value).replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 32);
  return `${prefix}-${slug || index + 1}`;
}

function normalizeName(value) {
  return trim(value).toLocaleLowerCase("en").replace(/\s+/g, " ");
}

function validIdentifier(value) {
  return typeof value === "string" && /^[0-9A-Za-z._:-]{1,96}$/.test(value) ? value : null;
}

function trim(value) {
  return typeof value === "string" ? value.trim().replace(/\s+/g, " ") : "";
}

function trimNullable(value) {
  if (value == null) return null;
  const result = trim(value);
  return result || null;
}

function array(value) {
  return Array.isArray(value) ? value : [];
}

function positiveInteger(value) {
  const number = Number(value);
  return Number.isInteger(number) && number > 0 ? number : null;
}

function jsonResponse(body, status) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}
