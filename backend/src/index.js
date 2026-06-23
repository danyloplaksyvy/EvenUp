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
const VISUALLY_SIMILAR_DIGITS = {
  "0": ["6", "8"],
  "3": ["8"],
  "5": ["6"],
  "6": ["0", "5", "8"],
  "8": ["0", "3", "6", "9"],
  "9": ["8"]
};
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
    "confidence",
    "corrections",
    "reviewWarnings"
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
        required: [
          "name",
          "quantity",
          "unitPriceMinor",
          "totalPriceMinor",
          "confidence",
          "candidatesMinor",
          "needsReview"
        ],
        properties: {
          name: { type: "string" },
          quantity: { type: "number" },
          unitPriceMinor: { type: "integer" },
          totalPriceMinor: { type: "integer" },
          confidence: { type: "number" },
          candidatesMinor: {
            type: "array",
            items: { type: "integer" }
          },
          needsReview: { type: "boolean" }
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
    confidence: { type: "number" },
    corrections: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["field", "itemName", "fromMinor", "toMinor", "reason"],
        properties: {
          field: { type: "string" },
          itemName: { type: ["string", "null"] },
          fromMinor: { type: "integer" },
          toMinor: { type: "integer" },
          reason: { type: "string" }
        }
      }
    },
    reviewWarnings: {
      type: "array",
      items: { type: "string" }
    }
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
  const requestId = receiptParseRequestId(request);
  const totalStart = Date.now();

  if (!env.OPENAI_API_KEY) {
    return receiptParseJsonResponse(
      env,
      requestId,
      {
        error: {
          code: "SERVER_MISCONFIGURED",
          message: "Receipt parsing is not configured."
        }
      },
      500,
      totalStart,
      { result: "server_misconfigured" }
    );
  }

  let payload;
  const requestJsonStart = Date.now();
  try {
    payload = await request.json();
  } catch {
    logReceiptParseTiming(env, requestId, "request_json", requestJsonStart, {
      result: "invalid_json"
    }, true);
    return receiptParseJsonResponse(env, requestId, RECEIPT_PARSE_ERROR, 400, totalStart, {
      result: "invalid_json"
    });
  }
  logReceiptParseTiming(env, requestId, "request_json", requestJsonStart, {
    result: "parsed"
  });

  const validationStart = Date.now();
  if (!isValidReceiptParseRequest(payload)) {
    logReceiptParseTiming(env, requestId, "request_validation", validationStart, {
      ...receiptParseRequestMetadata(payload),
      valid: false
    }, true);
    return receiptParseJsonResponse(env, requestId, RECEIPT_PARSE_ERROR, 400, totalStart, {
      result: "invalid_request"
    });
  }
  logReceiptParseTiming(env, requestId, "request_validation", validationStart, {
    ...receiptParseRequestMetadata(payload),
    valid: true
  });

  try {
    const fetcher = env.OPENAI_FETCH || fetch;
    const firstOpenAiStart = Date.now();
    const response = await fetcher(OPENAI_RESPONSES_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.OPENAI_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(openAiReceiptRequest(payload, env))
    });
    logReceiptParseTiming(env, requestId, "openai_first_request", firstOpenAiStart, {
      status: response.status,
      ok: response.ok
    }, !response.ok);

    const firstResponseJsonStart = Date.now();
    const responseJson = await response.json();
    logReceiptParseTiming(env, requestId, "openai_first_response_json", firstResponseJsonStart, {
      status: response.status,
      ok: response.ok
    }, !response.ok);
    if (!response.ok) {
      return receiptParseJsonResponse(env, requestId, RECEIPT_PARSE_ERROR, 502, totalStart, {
        result: "openai_error",
        auditInvoked: false
      });
    }

    const normalizeStart = Date.now();
    const outputText = extractOpenAiOutputText(responseJson);
    if (!outputText) {
      logReceiptParseTiming(env, requestId, "first_response_normalize", normalizeStart, {
        valid: false,
        reason: "missing_output_text"
      }, true);
      return receiptParseJsonResponse(env, requestId, RECEIPT_PARSE_ERROR, 502, totalStart, {
        result: "missing_output_text",
        auditInvoked: false
      });
    }

    const receipt = normalizeParsedReceipt(JSON.parse(outputText));
    if (!isValidParsedReceipt(receipt)) {
      logReceiptParseTiming(env, requestId, "first_response_normalize", normalizeStart, {
        ...receiptSummaryMetadata(receipt),
        valid: false
      }, true);
      return receiptParseJsonResponse(env, requestId, RECEIPT_PARSE_ERROR, 502, totalStart, {
        result: "invalid_receipt",
        auditInvoked: false
      });
    }
    logReceiptParseTiming(env, requestId, "first_response_normalize", normalizeStart, {
      ...receiptSummaryMetadata(receipt),
      valid: true
    });

    const reconciliationStart = Date.now();
    const reconciledReceipt = reconcileReceiptSubtotal(receipt);
    const isReconciled = isReceiptSubtotalReconciled(reconciledReceipt);
    const auditEnabled = isReceiptParseAuditEnabled(env);
    const auditEligible = !isReconciled;
    const auditInvoked = auditEligible && auditEnabled;
    logReceiptParseTiming(env, requestId, "deterministic_reconciliation", reconciliationStart, {
      ...receiptSummaryMetadata(reconciledReceipt),
      auditEligible,
      auditEnabled,
      auditInvoked,
      reconciled: isReconciled
    });
    if (!auditInvoked) {
      return receiptParseJsonResponse(env, requestId, reconciledReceipt, 200, totalStart, {
        result: "success",
        auditInvoked,
        ...receiptSummaryMetadata(reconciledReceipt)
      });
    }

    const auditedReceipt = await auditReceiptIfNeeded(payload, reconciledReceipt, env, fetcher, requestId);
    return receiptParseJsonResponse(env, requestId, auditedReceipt, 200, totalStart, {
      result: "success",
      auditInvoked: true,
      ...receiptSummaryMetadata(auditedReceipt)
    });
  } catch (error) {
    logReceiptParseTiming(env, requestId, "parse_exception", totalStart, {
      errorType: error?.name || "Error"
    }, true);
    return receiptParseJsonResponse(env, requestId, RECEIPT_PARSE_ERROR, 502, totalStart, {
      result: "exception"
    });
  }
}

function isReceiptParseAuditEnabled(env) {
  return env.RECEIPT_PARSE_AUDIT === true || env.RECEIPT_PARSE_AUDIT === "true";
}

function receiptParseRequestId(request) {
  const headerValue = request.headers.get("X-EvenUp-Request-Id");
  if (typeof headerValue === "string" && /^[0-9A-Za-z._:-]{1,96}$/.test(headerValue)) {
    return headerValue;
  }

  return `receipt-worker-${crypto.randomUUID()}`;
}

function receiptParseRequestMetadata(payload) {
  const imageBase64 = typeof payload?.imageBase64 === "string" ? payload.imageBase64 : "";
  const mimeType = typeof payload?.mimeType === "string" ? payload.mimeType : "unknown";
  return {
    imageBytes: imageBase64 ? estimateBase64ByteLength(imageBase64) : 0,
    base64Length: imageBase64.length,
    mimeType
  };
}

function receiptSummaryMetadata(receipt) {
  return {
    itemCount: Array.isArray(receipt?.items) ? receipt.items.length : 0,
    feeCount: Array.isArray(receipt?.fees) ? receipt.fees.length : 0,
    warningCount: Array.isArray(receipt?.reviewWarnings) ? receipt.reviewWarnings.length : 0
  };
}

function receiptParseJsonResponse(env, requestId, body, status, totalStart, metadata = {}) {
  const responseStart = Date.now();
  const response = jsonResponse(body, status);
  logReceiptParseTiming(env, requestId, "final_response", responseStart, {
    status,
    ...metadata
  }, status >= 400);
  logReceiptParseTiming(env, requestId, "parse_total", totalStart, {
    status,
    ...metadata
  }, status >= 400);
  return response;
}

function logReceiptParseTiming(env, requestId, stage, startMs, metadata = {}, warning = false) {
  const event = {
    type: "receipt_parse_timing",
    requestId,
    stage,
    durationMs: Math.max(0, Date.now() - startMs),
    ...metadata
  };

  const logger = env.RECEIPT_PARSE_LOGGER;
  if (logger && typeof logger.log === "function") {
    logger.log(event);
    return;
  }

  const message = JSON.stringify(event);
  if (warning) {
    console.warn(message);
  } else {
    console.log(message);
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
              "Do not return VAT, IVA, GST, or tax summary rows as fees when they are already included in the printed total.",
              "Only return true extra charges as additive fees. If a tax summary row shows both tax amount and gross total, never use the gross or receipt total as the tax amount.",
              "Do not invent items or prices.",
              "unitPriceMinor is the per-unit price. totalPriceMinor is the printed line total for that item.",
              "For quantity greater than 1, totalPriceMinor must be the line total, usually quantity times unitPriceMinor. Do not copy the unit price into totalPriceMinor when a separate line total is visible.",
              "For each item, return candidatesMinor with the parsed totalPriceMinor first and any visually plausible alternatives after it.",
              "Set item confidence between 0 and 1 and needsReview true for ambiguous item prices.",
              "Return empty corrections and reviewWarnings arrays in the first extraction pass."
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

function openAiReceiptAuditRequest(payload, extractedReceipt, env) {
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
              "You are a receipt parsing auditor for a shared expense app.",
              "Return strict JSON only through the requested schema.",
              "Validate that item totals sum to subtotalMinor and subtotalMinor plus fees equals totalMinor.",
              "If there is a mismatch, identify likely OCR price mistakes from the image.",
              "Remove VAT, IVA, GST, or tax rows that are already included in the printed total instead of treating them as additive fees.",
              "For rows with quantity greater than 1, check whether the extracted line total incorrectly used the unit price.",
              "Do not invent items. Prefer corrections where digits are visually similar.",
              "Return corrected JSON, corrections, and reviewWarnings."
            ].join(" ")
          }
        ]
      },
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: [
              `Audit this extracted receipt. Locale hint: ${localeHint}. Currency hint: ${currencyHint}.`,
              "Extracted JSON:",
              JSON.stringify(extractedReceipt)
            ].join("\n")
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
        name: "receipt_parse_audit",
        strict: true,
        schema: receiptResponseSchema
      }
    }
  };
}

function normalizeParsedReceipt(receipt) {
  if (!receipt || typeof receipt !== "object" || Array.isArray(receipt)) {
    return receipt;
  }

  const items = Array.isArray(receipt.items)
    ? receipt.items.map((item) => {
        const totalPriceMinor = Number.isInteger(item?.totalPriceMinor) ? item.totalPriceMinor : 0;
        const candidates = Array.isArray(item?.candidatesMinor) ? item.candidatesMinor.filter(Number.isInteger) : [];
        return {
          ...item,
          confidence: typeof item?.confidence === "number" ? item.confidence : receipt.confidence || 0,
          candidatesMinor: uniqueIntegers([totalPriceMinor, ...candidates]),
          needsReview: Boolean(item?.needsReview)
        };
      })
    : [];

  return {
    ...receipt,
    items,
    fees: Array.isArray(receipt.fees) ? receipt.fees : [],
    corrections: Array.isArray(receipt.corrections) ? receipt.corrections : [],
    reviewWarnings: Array.isArray(receipt.reviewWarnings) ? receipt.reviewWarnings : []
  };
}

function uniqueIntegers(values) {
  return Array.from(new Set(values.filter(Number.isInteger)));
}

function reconcileReceiptSubtotal(receipt) {
  if (!isValidParsedReceipt(receipt) || !Number.isInteger(receipt.subtotalMinor)) {
    return receipt;
  }

  receipt = sanitizeIncludedTaxFees(receipt);

  const itemSum = sumItemTotals(receipt.items);
  const feeSum = sumFeeAmounts(receipt.fees);
  if (itemSum === receipt.subtotalMinor && receipt.subtotalMinor + feeSum === receipt.totalMinor) {
    return withTotalMismatchWarningIfNeeded(receipt);
  }

  const replacement = chooseSingleCandidateReplacement(receipt.items, itemSubtotalTargets(receipt));
  if (!replacement) {
    return withTotalMismatchWarningIfNeeded(
      withReviewWarning(
        receipt,
        `Receipt item sum ${itemSum} does not match expected item subtotal.`
      )
    );
  }

  const items = receipt.items.map((item, index) => {
    if (index !== replacement.index) return item;

    return {
      ...item,
      totalPriceMinor: replacement.toMinor,
      unitPriceMinor: correctedUnitPrice(item, replacement.toMinor),
      needsReview: true,
      candidatesMinor: uniqueIntegers([replacement.toMinor, item.totalPriceMinor, ...item.candidatesMinor])
    };
  });

  const correction = {
    field: `items[${replacement.index}].totalPriceMinor`,
    itemName: receipt.items[replacement.index].name || null,
    fromMinor: replacement.fromMinor,
    toMinor: replacement.toMinor,
    reason: correctionReason(replacement)
  };

  return withTotalMismatchWarningIfNeeded({
    ...receipt,
    subtotalMinor: replacement.targetSubtotal,
    items,
    corrections: [...receipt.corrections, correction]
  });
}

function sanitizeIncludedTaxFees(receipt) {
  const keptFees = [];
  const corrections = [];

  receipt.fees.forEach((fee, index) => {
    if (shouldRemoveIncludedTaxFee(receipt, fee, index)) {
      corrections.push({
        field: `fees[${index}].amountMinor`,
        itemName: null,
        fromMinor: fee.amountMinor,
        toMinor: 0,
        reason: "Removed included VAT/tax duplicated from receipt total."
      });
      return;
    }

    keptFees.push(fee);
  });

  if (corrections.length === 0) return receipt;

  return {
    ...receipt,
    fees: keptFees,
    corrections: [...receipt.corrections, ...corrections]
  };
}

function shouldRemoveIncludedTaxFee(receipt, fee, feeIndex) {
  if (!isTaxLikeFee(fee) || fee.amountMinor <= 0) return false;
  if (isIncludedTaxLabel(fee.label)) return true;
  if (fee.amountMinor === receipt.totalMinor || fee.amountMinor === receipt.subtotalMinor) return true;

  const feesWithoutCurrent = receipt.fees.filter((_, index) => index !== feeIndex);
  return isReceiptReconciledWithFees(receipt, feesWithoutCurrent);
}

function isReceiptReconciledWithFees(receipt, fees) {
  const itemSum = sumItemTotals(receipt.items);
  const feeSum = sumFeeAmounts(fees);
  if (Number.isInteger(receipt.subtotalMinor)) {
    return itemSum === receipt.subtotalMinor && receipt.subtotalMinor + feeSum === receipt.totalMinor;
  }

  return itemSum + feeSum === receipt.totalMinor;
}

function isTaxLikeFee(fee) {
  const label = normalizeText(fee.label);
  return String(fee.type || "").toUpperCase() === "TAX" || /\b(tax|vat|iva|gst|mwst|tva)\b/.test(label);
}

function isIncludedTaxLabel(label) {
  const normalized = normalizeText(label);
  return [
    "di cui iva",
    "iva inclusa",
    "vat included",
    "incl vat",
    "includes tax",
    "tax included",
    "mwst enthalten",
    "tva incluse",
    "iva incluido"
  ].some((phrase) => normalized.includes(phrase));
}

function normalizeText(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[._-]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function itemSubtotalTargets(receipt) {
  return uniqueIntegers([
    receipt.subtotalMinor,
    receipt.totalMinor - sumFeeAmounts(receipt.fees)
  ]).filter((target) => target > 0);
}

function chooseSingleCandidateReplacement(items, targetSubtotals) {
  const currentSum = sumItemTotals(items);
  const options = [];

  items.forEach((item, index) => {
    const fromMinor = item.totalPriceMinor;
    for (const candidate of correctionCandidates(item)) {
      if (candidate.amountMinor === fromMinor) continue;
      for (const targetSubtotal of targetSubtotals) {
        if (currentSum - fromMinor + candidate.amountMinor !== targetSubtotal) continue;
        options.push({
          index,
          fromMinor,
          toMinor: candidate.amountMinor,
          targetSubtotal,
          reason: candidate.reason,
          priority: candidate.priority,
          confidence: typeof item.confidence === "number" ? item.confidence : 0,
          delta: Math.abs(candidate.amountMinor - fromMinor),
          needsReview: Boolean(item.needsReview)
        });
      }
    }
  });

  options.sort((left, right) => {
    if (left.needsReview !== right.needsReview) return left.needsReview ? -1 : 1;
    if (left.priority !== right.priority) return left.priority - right.priority;
    if (left.confidence !== right.confidence) return right.confidence - left.confidence;
    return left.delta - right.delta;
  });

  return options.length === 1 ? options[0] : null;
}

function correctionCandidates(item) {
  const candidates = [];
  for (const amountMinor of quantityLineTotalCandidates(item)) {
    candidates.push({ amountMinor, reason: "quantity_line_total", priority: 0 });
  }
  for (const amountMinor of item.candidatesMinor || []) {
    candidates.push({ amountMinor, reason: "candidate", priority: 1 });
  }
  for (const amountMinor of visuallySimilarSingleDigitTotals(item.totalPriceMinor)) {
    candidates.push({ amountMinor, reason: "visual_digit", priority: 2 });
  }

  const seen = new Set();
  return candidates.filter((candidate) => {
    if (!Number.isInteger(candidate.amountMinor) || candidate.amountMinor <= 0) return false;
    if (seen.has(candidate.amountMinor)) return false;
    seen.add(candidate.amountMinor);
    return true;
  });
}

function quantityLineTotalCandidates(item) {
  if (!Number.isInteger(item.quantity) || item.quantity <= 1) return [];
  return uniqueIntegers([
    item.totalPriceMinor * item.quantity,
    item.unitPriceMinor * item.quantity
  ]);
}

function visuallySimilarSingleDigitTotals(value) {
  const digits = String(value);
  return Array.from(digits).flatMap((digit, index) =>
    (VISUALLY_SIMILAR_DIGITS[digit] || [])
      .map((replacement) => Number(digits.slice(0, index) + replacement + digits.slice(index + 1)))
      .filter(Number.isInteger)
  );
}

function correctionReason(replacement) {
  if (replacement.reason === "quantity_line_total") {
    return `Corrected quantity line total to match expected item subtotal ${replacement.targetSubtotal}; unit price was likely parsed as the line total.`;
  }
  if (replacement.reason === "visual_digit") {
    return `Corrected to match expected item subtotal ${replacement.targetSubtotal}; digit likely misread.`;
  }
  return `Corrected to match expected item subtotal ${replacement.targetSubtotal}.`;
}

function sumItemTotals(items) {
  return items.reduce((sum, item) => sum + item.totalPriceMinor, 0);
}

function sumFeeAmounts(fees) {
  return fees.reduce((sum, fee) => sum + fee.amountMinor, 0);
}

function correctedUnitPrice(item, totalPriceMinor) {
  const quantity = item.quantity;
  if (Number.isInteger(quantity) && quantity > 0 && totalPriceMinor % quantity === 0) {
    return totalPriceMinor / quantity;
  }

  return item.unitPriceMinor;
}

function withReviewWarning(receipt, warning) {
  if (receipt.reviewWarnings.includes(warning)) return receipt;
  return {
    ...receipt,
    reviewWarnings: [...receipt.reviewWarnings, warning]
  };
}

function withTotalMismatchWarningIfNeeded(receipt) {
  const feeSum = sumFeeAmounts(receipt.fees);
  const expectedTotal = receipt.subtotalMinor + feeSum;
  if (expectedTotal === receipt.totalMinor) {
    return receipt;
  }

  return withReviewWarning(
    receipt,
    `Receipt subtotal plus fees ${expectedTotal} does not match printed total ${receipt.totalMinor}.`
  );
}

function isReceiptSubtotalReconciled(receipt) {
  return (
    !Number.isInteger(receipt.subtotalMinor) ||
    (sumItemTotals(receipt.items) === receipt.subtotalMinor &&
      receipt.subtotalMinor + sumFeeAmounts(receipt.fees) === receipt.totalMinor)
  );
}

async function auditReceiptIfNeeded(payload, receipt, env, fetcher, requestId) {
  try {
    const auditRequestStart = Date.now();
    const response = await fetcher(OPENAI_RESPONSES_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.OPENAI_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(openAiReceiptAuditRequest(payload, receipt, env))
    });
    logReceiptParseTiming(env, requestId, "audit_openai_request", auditRequestStart, {
      status: response.status,
      ok: response.ok
    }, !response.ok);

    const auditResponseJsonStart = Date.now();
    const responseJson = await response.json();
    logReceiptParseTiming(env, requestId, "audit_response_json", auditResponseJsonStart, {
      status: response.status,
      ok: response.ok
    }, !response.ok);
    if (!response.ok) {
      return receipt;
    }

    const auditNormalizeStart = Date.now();
    const outputText = extractOpenAiOutputText(responseJson);
    if (!outputText) {
      logReceiptParseTiming(env, requestId, "audit_response_normalize", auditNormalizeStart, {
        valid: false,
        reason: "missing_output_text"
      }, true);
      return receipt;
    }

    const auditedReceipt = normalizeParsedReceipt(JSON.parse(outputText));
    if (!isValidParsedReceipt(auditedReceipt)) {
      logReceiptParseTiming(env, requestId, "audit_response_normalize", auditNormalizeStart, {
        ...receiptSummaryMetadata(auditedReceipt),
        valid: false
      }, true);
      return receipt;
    }
    logReceiptParseTiming(env, requestId, "audit_response_normalize", auditNormalizeStart, {
      ...receiptSummaryMetadata(auditedReceipt),
      valid: true
    });

    const auditReconciliationStart = Date.now();
    const reconciledReceipt = reconcileReceiptSubtotal(auditedReceipt);
    logReceiptParseTiming(env, requestId, "audit_reconciliation", auditReconciliationStart, {
      ...receiptSummaryMetadata(reconciledReceipt),
      reconciled: isReceiptSubtotalReconciled(reconciledReceipt)
    });
    return reconciledReceipt;
  } catch (error) {
    logReceiptParseTiming(env, requestId, "audit_exception", Date.now(), {
      errorType: error?.name || "Error"
    }, true);
    return receipt;
  }
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
    receipt.items.every(isValidParsedReceiptItem) &&
    Array.isArray(receipt.fees) &&
    receipt.fees.every(isValidParsedReceiptFee) &&
    Number.isInteger(receipt.subtotalMinor) &&
    Number.isInteger(receipt.totalMinor) &&
    typeof receipt.confidence === "number" &&
    Array.isArray(receipt.corrections) &&
    receipt.corrections.every(isValidParsedCorrection) &&
    Array.isArray(receipt.reviewWarnings) &&
    receipt.reviewWarnings.every((warning) => typeof warning === "string")
  );
}

function isValidParsedReceiptItem(item) {
  return (
    item &&
    typeof item === "object" &&
    typeof item.name === "string" &&
    typeof item.quantity === "number" &&
    Number.isInteger(item.unitPriceMinor) &&
    Number.isInteger(item.totalPriceMinor) &&
    typeof item.confidence === "number" &&
    Array.isArray(item.candidatesMinor) &&
    item.candidatesMinor.every(Number.isInteger) &&
    typeof item.needsReview === "boolean"
  );
}

function isValidParsedReceiptFee(fee) {
  return (
    fee &&
    typeof fee === "object" &&
    typeof fee.type === "string" &&
    typeof fee.label === "string" &&
    Number.isInteger(fee.amountMinor)
  );
}

function isValidParsedCorrection(correction) {
  return (
    correction &&
    typeof correction === "object" &&
    typeof correction.field === "string" &&
    (typeof correction.itemName === "string" || correction.itemName === null) &&
    Number.isInteger(correction.fromMinor) &&
    Number.isInteger(correction.toMinor) &&
    typeof correction.reason === "string"
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

  const additiveFees = fees.filter((fee) => !isDiscountFee(fee) && firstNumber(fee.amountMinor) > 0);
  const discounts = fees.filter(isDiscountFee);
  const feeRows = additiveFees
    .map(
      (fee) =>
        `<div class="row muted"><span>${escapeHtml(fee.label || fee.type || "Fee")}</span><span>${formatMoney(
          firstNumber(fee.amountMinor),
          currency
        )}</span></div>`
    )
    .join("");
  const discountRows = discounts
    .map((fee) => {
      const amount = Math.abs(firstNumber(fee.amountMinor));
      return `<div class="row muted"><span>${escapeHtml(fee.label || "Discount")}</span><span>-${formatMoney(
        amount,
        currency
      )}</span></div>`;
    })
    .join("");

  return `<div class="fees">${feeRows}${discountRows}</div>`;
}

function isDiscountFee(fee) {
  return String(fee?.type || "").toUpperCase() === "DISCOUNT" || firstNumber(fee?.amountMinor) < 0;
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
