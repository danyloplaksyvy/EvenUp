export default {
  async fetch(request, env) {
    return handleRequest(request, env);
  }
};

const SHARE_ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
const SHARE_ID_LENGTH = 10;
const MAX_SHARE_ID_ATTEMPTS = 5;
const GUEST_ACCESS_COOKIE = "evenup_guest_access";
const GUEST_ACCESS_TTL_SECONDS = 7 * 24 * 60 * 60;
const GUEST_ACCESS_MAX_FAILURES = 5;
const GUEST_ACCESS_LOCKOUT_MS = 10 * 60 * 1000;
const EVENUP_LOGO_DATA_URI =
  "data:image/png;base64,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";
const OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
const DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";
const MAX_RECEIPT_IMAGE_BYTES = 7 * 1024 * 1024;
const SUPPORTED_RECEIPT_IMAGE_MIME_TYPES = new Set([
  "image/jpeg",
  "image/jpg",
  "image/png",
  "image/webp",
  "image/gif"
]);
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
const RECEIPT_IMAGE_UNSUPPORTED_ERROR = {
  error: {
    code: "RECEIPT_IMAGE_UNSUPPORTED",
    message: "Unsupported receipt image format. Please choose a JPEG, PNG, WEBP, or non-animated GIF image."
  }
};
const RECEIPT_IMAGE_TOO_LARGE_ERROR = {
  error: {
    code: "RECEIPT_IMAGE_TOO_LARGE",
    message: "Receipt image is too large. Please choose a smaller image."
  }
};
const MONTH_LABELS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

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
      return fetchExpenseResponse(shareId, request, env);
    });
  }

  if (url.pathname === "/v1/receipts/parse") {
    return methodGuard(request, "POST", () => parseReceipt(request, env));
  }

  if (url.pathname.startsWith("/e/")) {
    const pathRemainder = url.pathname.slice("/e/".length);
    const isAccessPost = pathRemainder.endsWith("/access");
    const shareIdSegment = isAccessPost ? pathRemainder.slice(0, -"/access".length) : pathRemainder;
    const shareId = decodeURIComponent(shareIdSegment);

    if (isAccessPost) {
      return methodGuard(request, "POST", () => verifyGuestAccessResponse(shareId, request, env));
    }

    if (request.method !== "GET") {
      return htmlResponse(renderGuestErrorPage("Link unavailable", "This link cannot be opened."), 405);
    }

    return renderGuestExpenseResponse(shareId, request, env);
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

  const guestPasscode = normalizeGuestPasscode(payload.guestAccess?.passcode);
  const passcodeMaterial = guestPasscode ? await createGuestPasscodeMaterial(guestPasscode) : null;
  const storedPayload = stripGuestAccess(payload);
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
            "(id, share_id, title, payload_json, guest_passcode_hash, guest_passcode_salt, created_at)",
            "VALUES (?, ?, ?, ?, ?, ?, ?)"
          ].join(" ")
        )
        .bind(
          expenseId,
          shareId,
          payload.title.trim(),
          JSON.stringify(storedPayload),
          passcodeMaterial?.hash ?? null,
          passcodeMaterial?.salt ?? null,
          createdAt
        )
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

async function fetchExpenseResponse(shareId, request, env) {
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

  if (isPasscodeProtected(row) && !(await hasGuestAccess(row, request, env))) {
    return jsonResponse(
      {
        error: {
          code: "GUEST_ACCESS_REQUIRED",
          message: "Enter the guest passcode to view this expense."
        }
      },
      401
    );
  }

  return jsonResponse(expenseApiResponse(row));
}

async function findExpenseByShareId(shareId, env) {
  if (!env.EXPENSES_DB || !isValidShareId(shareId)) {
    return null;
  }

  return env.EXPENSES_DB
    .prepare(
      [
        "SELECT id, share_id, title, payload_json,",
        "guest_passcode_hash, guest_passcode_salt, created_at",
        "FROM expenses WHERE share_id = ?"
      ].join(" ")
    )
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

  if (payload.guestAccess !== undefined) {
    if (!payload.guestAccess || typeof payload.guestAccess !== "object" || Array.isArray(payload.guestAccess)) {
      return "guestAccess must be an object.";
    }

    if (!normalizeGuestPasscode(payload.guestAccess.passcode)) {
      return "guestAccess.passcode must be exactly four letters.";
    }
  }

  return null;
}

function stripGuestAccess(payload) {
  const { guestAccess, ...storedPayload } = payload;
  return storedPayload;
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
  const validationFailure = receiptParseRequestValidationFailure(payload);
  if (validationFailure) {
    logReceiptParseTiming(env, requestId, "request_validation", validationStart, {
      ...receiptParseRequestMetadata(payload),
      valid: false,
      reason: validationFailure.result
    }, true);
    return receiptParseJsonResponse(env, requestId, validationFailure.body, validationFailure.status, totalStart, {
      result: validationFailure.result
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
    const domainValidation = validateAndroidCompatibleReceipt(reconciledReceipt);
    logReceiptParseTiming(env, requestId, "receipt_domain_validation", reconciliationStart, {
      ...receiptSummaryMetadata(reconciledReceipt),
      ...domainValidationLogMetadata(domainValidation),
      phase: "first"
    }, !domainValidation.valid);
    const auditEligible = !isReconciled || !domainValidation.valid;
    const auditInvoked = auditEligible && auditEnabled;
    logReceiptParseTiming(env, requestId, "deterministic_reconciliation", reconciliationStart, {
      ...receiptSummaryMetadata(reconciledReceipt),
      auditEligible,
      auditEnabled,
      auditInvoked,
      reconciled: isReconciled
    });
    if (!auditInvoked) {
      if (!domainValidation.valid) {
        return receiptParseJsonResponse(env, requestId, RECEIPT_PARSE_ERROR, 502, totalStart, {
          result: "invalid_receipt_domain",
          auditInvoked,
          ...domainValidationErrorMetadata(domainValidation),
          ...receiptSummaryMetadata(reconciledReceipt)
        });
      }
      return receiptParseJsonResponse(env, requestId, reconciledReceipt, 200, totalStart, {
        result: "success",
        auditInvoked,
        ...receiptSummaryMetadata(reconciledReceipt)
      });
    }

    const auditedReceipt = await auditReceiptIfNeeded(payload, reconciledReceipt, env, fetcher, requestId);
    const auditedDomainValidation = validateAndroidCompatibleReceipt(auditedReceipt);
    logReceiptParseTiming(env, requestId, "receipt_domain_validation", Date.now(), {
      ...receiptSummaryMetadata(auditedReceipt),
      ...domainValidationLogMetadata(auditedDomainValidation),
      phase: "audit"
    }, !auditedDomainValidation.valid);
    if (!auditedDomainValidation.valid) {
      return receiptParseJsonResponse(env, requestId, RECEIPT_PARSE_ERROR, 502, totalStart, {
        result: "invalid_receipt_domain",
        auditInvoked: true,
        ...domainValidationErrorMetadata(auditedDomainValidation),
        ...receiptSummaryMetadata(auditedReceipt)
      });
    }
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

function domainValidationLogMetadata(validation) {
  if (validation.valid) {
    return { valid: true };
  }

  return {
    valid: false,
    reason: validation.reason,
    fieldPath: validation.fieldPath
  };
}

function domainValidationErrorMetadata(validation) {
  if (validation.valid) return {};

  return {
    reason: validation.reason,
    fieldPath: validation.fieldPath
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

function receiptParseRequestValidationFailure(payload) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return {
      body: RECEIPT_PARSE_ERROR,
      status: 400,
      result: "invalid_request"
    };
  }

  if (typeof payload.imageBase64 !== "string" || payload.imageBase64.trim().length === 0) {
    return {
      body: RECEIPT_PARSE_ERROR,
      status: 400,
      result: "invalid_request"
    };
  }

  const mimeType = normalizedReceiptImageMimeType(payload.mimeType);
  if (!mimeType || !SUPPORTED_RECEIPT_IMAGE_MIME_TYPES.has(mimeType)) {
    return {
      body: RECEIPT_IMAGE_UNSUPPORTED_ERROR,
      status: 400,
      result: "unsupported_image_type"
    };
  }

  if (estimateBase64ByteLength(payload.imageBase64) > MAX_RECEIPT_IMAGE_BYTES) {
    return {
      body: RECEIPT_IMAGE_TOO_LARGE_ERROR,
      status: 413,
      result: "image_too_large"
    };
  }

  if (mimeType === "image/gif" && isAnimatedGif(payload.imageBase64)) {
    return {
      body: RECEIPT_IMAGE_UNSUPPORTED_ERROR,
      status: 400,
      result: "unsupported_animated_gif"
    };
  }

  return null;
}

function normalizedReceiptImageMimeType(value) {
  if (typeof value !== "string") return null;
  const mimeType = value.split(";")[0].trim().toLowerCase();
  if (mimeType === "image/jpg") return "image/jpeg";
  return mimeType;
}

function isAnimatedGif(imageBase64) {
  let binary;
  try {
    binary = atob(imageBase64.replace(/\s/g, ""));
  } catch {
    return false;
  }

  if (!binary.startsWith("GIF87a") && !binary.startsWith("GIF89a")) return false;
  if (binary.length < 13) return false;

  const byteAt = (index) => binary.charCodeAt(index) & 0xff;
  const packed = byteAt(10);
  let index = 13;
  if ((packed & 0x80) !== 0) {
    index += 3 * (2 ** ((packed & 0x07) + 1));
  }

  let imageDescriptorCount = 0;
  while (index < binary.length) {
    const blockType = byteAt(index);
    index += 1;

    if (blockType === 0x3b) return false;

    if (blockType === 0x21) {
      index += 1;
      index = skipGifSubBlocks(binary, index);
      continue;
    }

    if (blockType !== 0x2c) return false;

    imageDescriptorCount += 1;
    if (imageDescriptorCount > 1) return true;

    if (index + 9 > binary.length) return false;
    const imagePacked = byteAt(index + 8);
    index += 9;
    if ((imagePacked & 0x80) !== 0) {
      index += 3 * (2 ** ((imagePacked & 0x07) + 1));
    }
    index += 1;
    index = skipGifSubBlocks(binary, index);
  }

  return false;
}

function skipGifSubBlocks(binary, index) {
  while (index < binary.length) {
    const blockSize = binary.charCodeAt(index) & 0xff;
    index += 1;
    if (blockSize === 0) return index;
    index += blockSize;
  }

  return index;
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
            image_url: `data:${normalizedReceiptImageMimeType(payload.mimeType)};base64,${payload.imageBase64}`,
            detail: "high"
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
              "Receipt quantities must be positive whole numbers. Do not return fractional quantities.",
              "Merchant, item, and fee labels must be non-empty. Currency must be a three-letter ISO code.",
              "Item unitPriceMinor and totalPriceMinor must be positive. Discount fees must be negative and other fees must be positive.",
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
            image_url: `data:${normalizedReceiptImageMimeType(payload.mimeType)};base64,${payload.imageBase64}`,
            detail: "high"
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
        const candidates = Array.isArray(item?.candidatesMinor)
          ? item.candidatesMinor.filter((candidate) => Number.isInteger(candidate) && candidate > 0)
          : [];
        const candidateValues = totalPriceMinor > 0 ? [totalPriceMinor, ...candidates] : candidates;
        return {
          ...item,
          name: typeof item?.name === "string" ? item.name.trim() : item?.name,
          confidence: typeof item?.confidence === "number" ? item.confidence : receipt.confidence || 0,
          candidatesMinor: uniqueIntegers(candidateValues),
          needsReview: Boolean(item?.needsReview)
        };
      })
    : [];
  const fees = Array.isArray(receipt.fees)
    ? receipt.fees.map((fee) => ({
        ...fee,
        type: typeof fee?.type === "string" ? fee.type.trim().toUpperCase() : fee?.type,
        label: typeof fee?.label === "string" ? fee.label.trim() : fee?.label
      }))
    : [];

  return {
    ...receipt,
    merchantName: typeof receipt.merchantName === "string" ? receipt.merchantName.trim() : receipt.merchantName,
    currency: normalizeReceiptCurrency(receipt.currency),
    items,
    fees,
    corrections: Array.isArray(receipt.corrections) ? receipt.corrections : [],
    reviewWarnings: Array.isArray(receipt.reviewWarnings) ? receipt.reviewWarnings : []
  };
}

function uniqueIntegers(values) {
  return Array.from(new Set(values.filter(Number.isInteger)));
}

function normalizeReceiptCurrency(currency) {
  if (typeof currency !== "string") return currency;
  const trimmed = currency.trim();
  const uppercase = trimmed.toUpperCase();
  return /^[A-Z]{3}$/.test(uppercase) ? uppercase : trimmed;
}

function validateAndroidCompatibleReceipt(receipt) {
  if (!receipt || typeof receipt !== "object" || Array.isArray(receipt)) {
    return invalidReceiptDomain("not_object", "receipt");
  }

  if (typeof receipt.merchantName !== "string" || receipt.merchantName.trim().length === 0) {
    return invalidReceiptDomain("blank_merchant_name", "merchantName");
  }

  if (typeof receipt.currency !== "string" || !/^[A-Z]{3}$/.test(receipt.currency)) {
    return invalidReceiptDomain("invalid_currency", "currency");
  }

  if (!Array.isArray(receipt.items) || receipt.items.length === 0) {
    return invalidReceiptDomain("no_items", "items");
  }

  for (const [index, item] of receipt.items.entries()) {
    const itemPath = `items[${index}]`;
    if (!item || typeof item !== "object") {
      return invalidReceiptDomain("invalid_item", itemPath);
    }
    if (typeof item.name !== "string" || item.name.trim().length === 0) {
      return invalidReceiptDomain("blank_item_name", `${itemPath}.name`);
    }
    if (!Number.isInteger(item.quantity) || item.quantity <= 0) {
      return invalidReceiptDomain("invalid_item_quantity", `${itemPath}.quantity`);
    }
    if (!Number.isInteger(item.unitPriceMinor) || item.unitPriceMinor <= 0) {
      return invalidReceiptDomain("non_positive_unit_price", `${itemPath}.unitPriceMinor`);
    }
    if (!Number.isInteger(item.totalPriceMinor) || item.totalPriceMinor <= 0) {
      return invalidReceiptDomain("non_positive_total_price", `${itemPath}.totalPriceMinor`);
    }
    if (!Array.isArray(item.candidatesMinor) || !item.candidatesMinor.every((candidate) => Number.isInteger(candidate) && candidate > 0)) {
      return invalidReceiptDomain("invalid_item_candidates", `${itemPath}.candidatesMinor`);
    }
  }

  if (!Array.isArray(receipt.fees)) {
    return invalidReceiptDomain("invalid_fees", "fees");
  }

  for (const [index, fee] of receipt.fees.entries()) {
    const feePath = `fees[${index}]`;
    if (!fee || typeof fee !== "object") {
      return invalidReceiptDomain("invalid_fee", feePath);
    }
    if (typeof fee.label !== "string" || fee.label.trim().length === 0) {
      return invalidReceiptDomain("blank_fee_label", `${feePath}.label`);
    }
    if (!Number.isInteger(fee.amountMinor)) {
      return invalidReceiptDomain("invalid_fee_amount", `${feePath}.amountMinor`);
    }
    if (String(fee.type || "").toUpperCase() === "DISCOUNT") {
      if (fee.amountMinor >= 0) {
        return invalidReceiptDomain("invalid_discount_amount", `${feePath}.amountMinor`);
      }
    } else if (fee.amountMinor <= 0) {
      return invalidReceiptDomain("non_positive_fee_amount", `${feePath}.amountMinor`);
    }
  }

  if (!Number.isInteger(receipt.totalMinor) || receipt.totalMinor < 0) {
    return invalidReceiptDomain("invalid_total", "totalMinor");
  }

  if (receipt.subtotalMinor !== null && receipt.subtotalMinor !== undefined && !Number.isInteger(receipt.subtotalMinor)) {
    return invalidReceiptDomain("invalid_subtotal", "subtotalMinor");
  }

  return { valid: true };
}

function invalidReceiptDomain(reason, fieldPath) {
  return {
    valid: false,
    reason,
    fieldPath
  };
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
      candidatesMinor: uniquePositiveIntegers([replacement.toMinor, item.totalPriceMinor, ...item.candidatesMinor])
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

function uniquePositiveIntegers(values) {
  return uniqueIntegers(values).filter((value) => value > 0);
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

async function renderGuestExpenseResponse(shareId, request, env) {
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

  if (isPasscodeProtected(row) && !(await hasGuestAccess(row, request, env))) {
    const queryAccessResponse = await verifyGuestAccessQueryResponse(row, request, env);
    if (queryAccessResponse) {
      return queryAccessResponse;
    }
    return htmlResponse(renderGuestPasscodePage(shareId));
  }

  return htmlResponse(renderGuestExpensePage(expenseApiResponse(row)));
}

async function verifyGuestAccessQueryResponse(row, request, env) {
  const url = new URL(request.url);
  if (!url.searchParams.has("code")) {
    return null;
  }

  const rateLimit = await checkGuestAccessRateLimit(row.share_id, request, env);
  if (rateLimit.limited) {
    return htmlResponse(
      renderGuestPasscodePage(
        row.share_id,
        "Too many incorrect attempts. Please wait a few minutes and try again."
      ),
      429
    );
  }

  const passcode = normalizeGuestPasscode(url.searchParams.get("code"));
  const isValid = Boolean(passcode) && (await verifyGuestPasscode(passcode, row));

  if (!isValid) {
    await recordGuestAccessFailure(row.share_id, rateLimit.clientKeyHash, env);
    return htmlResponse(
      renderGuestPasscodePage(row.share_id, "That code does not match. Check the share message and try again."),
      401
    );
  }

  await clearGuestAccessFailures(row.share_id, rateLimit.clientKeyHash, env);
  return redirectToGuestExpense(row.share_id, {
    "Set-Cookie": await createGuestAccessCookie(row.share_id, request, env)
  });
}

async function verifyGuestAccessResponse(shareId, request, env) {
  const row = await findExpenseByShareId(shareId, env);
  if (!row) {
    return htmlResponse(
      renderGuestErrorPage(
        "Link unavailable",
        "This share link is missing, expired, or was typed incorrectly."
      ),
      404
    );
  }

  if (!isPasscodeProtected(row)) {
    return redirectToGuestExpense(shareId);
  }

  const rateLimit = await checkGuestAccessRateLimit(shareId, request, env);
  if (rateLimit.limited) {
    return htmlResponse(
      renderGuestPasscodePage(
        shareId,
        "Too many incorrect attempts. Please wait a few minutes and try again."
      ),
      429
    );
  }

  const formData = await request.formData();
  const passcode = normalizeGuestPasscode(formData.get("passcode"));
  const isValid = Boolean(passcode) && (await verifyGuestPasscode(passcode, row));

  if (!isValid) {
    await recordGuestAccessFailure(shareId, rateLimit.clientKeyHash, env);
    return htmlResponse(
      renderGuestPasscodePage(shareId, "That code does not match. Check the share message and try again."),
      401
    );
  }

  await clearGuestAccessFailures(shareId, rateLimit.clientKeyHash, env);
  return redirectToGuestExpense(shareId, {
    "Set-Cookie": await createGuestAccessCookie(shareId, request, env)
  });
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
  const itemAssignments = Array.isArray(expense.itemAssignments) ? expense.itemAssignments : [];
  const feeAllocations = Array.isArray(expense.feeAllocations) ? expense.feeAllocations : [];
  const currency = receipt.currency || expense.currency || "USD";
  const totalMinor = firstNumber(receipt.totalMinor, summary.totalMinor, summary.receiptTotalMinor);
  const participantDetails = buildParticipantDetails({
    participants,
    payerParticipantId: expense.payerParticipantId,
    participantSummaries,
    settlementRows,
    items,
    fees,
    itemAssignments,
    feeAllocations,
    currency
  });

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(expense.title || "EvenUp Expense")}</title>
  <style>${guestCss()}</style>
</head>
<body>
  ${renderGuestBrandHeader()}
  <main>
    <section class="hero">
      <div class="eyebrow">Shared Expense</div>
      <h1>${escapeHtml(formatMerchantName(receipt.merchantName || expense.title || "Shared expense"))}</h1>
      <div class="total">${formatMoney(totalMinor, currency)}</div>
      <p>${escapeHtml(formatDate(receipt.transactionDate || receipt.date))}</p>
      <div class="paid"><span>Paid by</span><strong>${escapeHtml(participantName(expense.payerParticipantId, participants))}</strong></div>
    </section>
    <section class="panel">
      <h2>Settlement Summary</h2>
      ${renderSettlementRows(settlementRows, participants, currency)}
    </section>
    <section class="panel">
      <h2>Breakdown by person</h2>
      ${renderParticipantDetails(participantDetails, currency)}
    </section>
    <section class="notice">This is a read-only guest view.</section>
  </main>
  ${renderGuestFooter()}
</body>
</html>`;
}

function renderGuestPasscodePage(shareId, errorMessage = "") {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Enter passcode - EvenUp</title>
  <style>${guestCss()}</style>
</head>
<body>
  ${renderGuestBrandHeader()}
  <main>
    <section class="hero compact">
      <div class="eyebrow">Shared Expense</div>
      <h1>Enter guest code</h1>
      <p class="muted">Use the four-letter code from the share message to view this expense.</p>
    </section>
    <section class="panel">
      <form class="passcode-form" method="post" action="/e/${encodeURIComponent(shareId)}/access">
        <label for="passcode">Guest code</label>
        <input id="passcode" name="passcode" type="text" inputmode="text" autocomplete="one-time-code" maxlength="4" pattern="[A-Za-z]{4}" required>
        ${errorMessage ? `<p class="error-text">${escapeHtml(errorMessage)}</p>` : ""}
        <button type="submit">Unlock expense</button>
      </form>
    </section>
  </main>
  ${renderGuestFooter()}
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
  ${renderGuestBrandHeader()}
  <main>
    <section class="error-card">
      <div class="icon">!</div>
      <h1>${escapeHtml(title)}</h1>
      <p>${escapeHtml(message)}</p>
    </section>
  </main>
  ${renderGuestFooter()}
</body>
</html>`;
}

function renderGuestBrandHeader() {
  return `<header class="topbar" aria-label="EvenUp">
    <div class="brand-lockup" aria-label="EvenUp">
      <img class="brand-logo" src="${EVENUP_LOGO_DATA_URI}" alt="" aria-hidden="true">
    </div>
  </header>`;
}

function renderGuestFooter() {
  return `<footer>Powered by EvenUp</footer>`;
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

function renderParticipantDetails(details, currency) {
  if (details.length === 0) {
    return `<p class="muted">Participant details are unavailable.</p>`;
  }

  return details
    .map((detail, index) => {
      const settlement = detail.settlementLines.length
        ? detail.settlementLines.map((line) => `<div class="mini-row">${escapeHtml(line)}</div>`).join("")
        : `<div class="mini-row muted">No payment action needed.</div>`;
      return `<details class="person" ${index === 0 ? "open" : ""}>
        <summary>
          <span>
            <strong>${escapeHtml(detail.name)}</strong>
            ${detail.isPayer ? `<small>Payer</small>` : ""}
          </span>
          <b>${formatMoney(detail.shareMinor, currency)}</b>
        </summary>
        <div class="person-body">
          <div class="totals-grid">
            <div><span>Items</span><b>${formatMoney(detail.itemTotalMinor, currency)}</b></div>
            <div><span>Fees</span><b>${formatMoney(detail.feeTotalMinor, currency)}</b></div>
            <div><span>Discounts</span><b>${formatMoney(detail.discountCreditMinor, currency)}</b></div>
            <div><span>Paid</span><b>${formatMoney(detail.paidMinor, currency)}</b></div>
          </div>
          ${renderDetailRows("Items", detail.items, currency, { emptyMessage: "No assigned items." })}
          ${renderDetailRows("Fees", detail.fees, currency, { hideWhenEmpty: true })}
          ${renderDetailRows("Discounts", detail.discounts, currency, { hideWhenEmpty: true })}
          <div class="subsection">
            <h3>Settlement</h3>
            ${settlement}
          </div>
        </div>
      </details>`;
    })
    .join("");
}

function renderDetailRows(title, rows, currency, options = {}) {
  if (!rows.length) {
    if (options.hideWhenEmpty) {
      return "";
    }

    return `<div class="subsection"><h3>${escapeHtml(title)}</h3><p class="muted">${escapeHtml(
      options.emptyMessage || "No details available."
    )}</p></div>`;
  }

  return `<div class="subsection">
    <h3>${escapeHtml(title)}</h3>
    ${rows
      .map(
        (row) =>
          `<div class="detail-row"><span>${escapeHtml(row.label)}<small>${escapeHtml(row.meta)}</small></span><b>${formatMoney(
            row.amountMinor,
            currency
          )}</b></div>`
      )
      .join("")}
  </div>`;
}

function buildParticipantDetails({
  participants,
  payerParticipantId,
  participantSummaries,
  settlementRows,
  items,
  fees,
  itemAssignments,
  feeAllocations,
  currency
}) {
  const participantIds = new Set([
    ...participants.map((participant) => participant.id),
    ...participantSummaries.map((summary) => summary.participantId)
  ]);

  return Array.from(participantIds)
    .map((participantId) => {
      const summary = participantSummaries.find((candidate) => candidate.participantId === participantId) || {};
      const itemRows = itemAssignments.flatMap((assignment) => {
        const item = items.find((candidate) => candidate.id === assignment.receiptItemId) || {};
        return assignmentSharesForParticipant(assignment, participantId).map((share) => ({
          label: item.name || assignment.receiptItemId || "Receipt item",
          meta: itemShareMeta(assignment, share),
          amountMinor: firstNumber(share.amountMinor, share.amount)
        }));
      });
      const feeRows = [];
      const discountRows = [];
      feeAllocations.forEach((allocation) => {
        const fee = fees.find((candidate) => candidate.id === allocation.feeId) || {};
        assignmentSharesForParticipant(allocation, participantId).forEach((share) => {
          const row = {
            label: fee.label || fee.type || allocation.feeId || "Fee",
            meta: feeAllocationMeta(allocation),
            amountMinor: firstNumber(share.amountMinor, share.amount)
          };
          if (isDiscountFee(fee) || row.amountMinor < 0) {
            discountRows.push({ ...row, amountMinor: -Math.abs(row.amountMinor) });
          } else {
            feeRows.push(row);
          }
        });
      });

      const settlementLines = settlementRows
        .filter((row) => row.fromParticipantId === participantId || row.toParticipantId === participantId)
        .map((row) => {
          const amount = formatMoney(firstNumber(row.amountMinor, row.amount), currency);
          if (row.fromParticipantId === participantId) {
            return `Owes ${participantName(row.toParticipantId, participants)} ${amount}`;
          }
          return `Receives ${amount} from ${participantName(row.fromParticipantId, participants)}`;
        });

      return {
        participantId,
        name: participantName(participantId, participants),
        isPayer: participantId === payerParticipantId,
        itemTotalMinor: firstNumber(summary.assignedItemTotalMinor, summary.assignedItemTotal),
        feeTotalMinor: firstNumber(summary.allocatedFeeTotalMinor, summary.allocatedFeeTotal),
        discountCreditMinor: firstNumber(summary.discountCreditTotalMinor, summary.discountCreditTotal),
        shareMinor: firstNumber(summary.shareMinor, summary.personShareMinor, summary.personShare),
        paidMinor: firstNumber(summary.paidMinor, summary.amountPaidMinor, summary.amountPaid),
        netBalanceMinor: firstNumber(summary.netBalanceMinor, summary.netBalance),
        items: itemRows,
        fees: feeRows,
        discounts: discountRows,
        settlementLines
      };
    })
    .sort((left, right) => {
      if (left.participantId === payerParticipantId) return -1;
      if (right.participantId === payerParticipantId) return 1;
      const leftOrder = participants.find((participant) => participant.id === left.participantId)?.creationOrder ?? 0;
      const rightOrder = participants.find((participant) => participant.id === right.participantId)?.creationOrder ?? 0;
      return leftOrder - rightOrder;
    });
}

function assignmentSharesForParticipant(assignment, participantId) {
  return Array.isArray(assignment.shares)
    ? assignment.shares.filter((share) => share.participantId === participantId)
    : [];
}

function itemShareMeta(assignment, share) {
  const mode = formatModeLabel(assignment.mode);
  const extras = [];
  if (Number.isFinite(share.quantity)) {
    extras.push(`${share.quantity} unit${share.quantity === 1 ? "" : "s"}`);
  }
  if (Number.isFinite(share.percentageBasisPoints)) {
    extras.push(`${formatBasisPoints(share.percentageBasisPoints)}`);
  }
  return [mode, ...extras].filter(Boolean).join(" · ");
}

function feeAllocationMeta(allocation) {
  return formatModeLabel(allocation.mode);
}

function formatModeLabel(value) {
  return String(value || "")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/_/g, " ")
    .trim()
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase()) || "Assigned";
}

function formatBasisPoints(value) {
  return `${(value / 100).toLocaleString("en", { maximumFractionDigits: 2 })}%`;
}

function redirectToGuestExpense(shareId, headers = {}) {
  return new Response(null, {
    status: 303,
    headers: {
      Location: `/e/${encodeURIComponent(shareId)}`,
      ...headers
    }
  });
}

function isPasscodeProtected(row) {
  return Boolean(row?.guest_passcode_hash && row?.guest_passcode_salt);
}

async function hasGuestAccess(row, request, env) {
  const headerPasscode = normalizeGuestPasscode(request.headers.get("X-EvenUp-Guest-Passcode"));
  if (headerPasscode && (await verifyGuestPasscode(headerPasscode, row))) {
    return true;
  }

  return verifyGuestAccessCookie(request, row.share_id, env);
}

function normalizeGuestPasscode(value) {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.trim().toUpperCase();
  return /^[A-Z]{4}$/.test(normalized) ? normalized : null;
}

async function createGuestPasscodeMaterial(passcode) {
  const saltBytes = new Uint8Array(16);
  crypto.getRandomValues(saltBytes);
  const salt = base64UrlEncode(saltBytes);
  return {
    salt,
    hash: await hashGuestPasscode(passcode, salt)
  };
}

async function verifyGuestPasscode(passcode, row) {
  const expected = row.guest_passcode_hash;
  const actual = await hashGuestPasscode(passcode, row.guest_passcode_salt);
  return constantTimeEqual(actual, expected);
}

async function hashGuestPasscode(passcode, salt) {
  const input = new TextEncoder().encode(`${salt}:${passcode}`);
  const digest = await crypto.subtle.digest("SHA-256", input);
  return base64UrlEncode(new Uint8Array(digest));
}

async function createGuestAccessCookie(shareId, request, env) {
  const expiresAt = Math.floor(Date.now() / 1000) + GUEST_ACCESS_TTL_SECONDS;
  const payload = base64UrlEncode(
    new TextEncoder().encode(
      JSON.stringify({
        shareId,
        exp: expiresAt
      })
    )
  );
  const signature = await signGuestAccessValue(payload, env);
  const secure = new URL(request.url).protocol === "https:" ? "; Secure" : "";
  return `${GUEST_ACCESS_COOKIE}=${payload}.${signature}; Max-Age=${GUEST_ACCESS_TTL_SECONDS}; Path=/; HttpOnly; SameSite=Lax${secure}`;
}

async function verifyGuestAccessCookie(request, shareId, env) {
  const cookieValue = parseCookie(request.headers.get("Cookie"))[GUEST_ACCESS_COOKIE];
  if (!cookieValue) {
    return false;
  }

  const [payload, signature] = cookieValue.split(".");
  if (!payload || !signature) {
    return false;
  }

  const expectedSignature = await signGuestAccessValue(payload, env);
  if (!constantTimeEqual(signature, expectedSignature)) {
    return false;
  }

  try {
    const parsed = JSON.parse(new TextDecoder().decode(base64UrlDecode(payload)));
    return parsed.shareId === shareId && Number.isFinite(parsed.exp) && parsed.exp > Math.floor(Date.now() / 1000);
  } catch {
    return false;
  }
}

async function signGuestAccessValue(value, env) {
  const secret = env.GUEST_ACCESS_COOKIE_SECRET || "evenup-local-guest-access-secret";
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value));
  return base64UrlEncode(new Uint8Array(signature));
}

async function checkGuestAccessRateLimit(shareId, request, env) {
  const clientKeyHash = await guestAccessClientKeyHash(request, env);
  const attempt = await findGuestAccessAttempt(shareId, clientKeyHash, env);
  if (attempt?.locked_until && new Date(attempt.locked_until).getTime() > Date.now()) {
    return {
      clientKeyHash,
      limited: true
    };
  }

  return {
    clientKeyHash,
    limited: false
  };
}

async function recordGuestAccessFailure(shareId, clientKeyHash, env) {
  if (!env.EXPENSES_DB) {
    return;
  }

  const current = await findGuestAccessAttempt(shareId, clientKeyHash, env);
  const updatedAt = new Date().toISOString();
  const previousUpdatedAt = current?.updated_at ? new Date(current.updated_at).getTime() : 0;
  const countBase = Date.now() - previousUpdatedAt > GUEST_ACCESS_LOCKOUT_MS ? 0 : Number(current?.failed_count || 0);
  const failedCount = countBase + 1;
  const lockedUntil =
    failedCount >= GUEST_ACCESS_MAX_FAILURES
      ? new Date(Date.now() + GUEST_ACCESS_LOCKOUT_MS).toISOString()
      : null;

  await env.EXPENSES_DB
    .prepare(
      [
        "INSERT INTO guest_access_attempts",
        "(share_id, client_key_hash, failed_count, locked_until, updated_at)",
        "VALUES (?, ?, ?, ?, ?)",
        "ON CONFLICT(share_id, client_key_hash) DO UPDATE SET",
        "failed_count = excluded.failed_count,",
        "locked_until = excluded.locked_until,",
        "updated_at = excluded.updated_at"
      ].join(" ")
    )
    .bind(shareId, clientKeyHash, failedCount, lockedUntil, updatedAt)
    .run();
}

async function clearGuestAccessFailures(shareId, clientKeyHash, env) {
  if (!env.EXPENSES_DB) {
    return;
  }

  await env.EXPENSES_DB
    .prepare(
      [
        "INSERT INTO guest_access_attempts",
        "(share_id, client_key_hash, failed_count, locked_until, updated_at)",
        "VALUES (?, ?, 0, NULL, ?)",
        "ON CONFLICT(share_id, client_key_hash) DO UPDATE SET",
        "failed_count = 0, locked_until = NULL, updated_at = excluded.updated_at"
      ].join(" ")
    )
    .bind(shareId, clientKeyHash, new Date().toISOString())
    .run();
}

async function findGuestAccessAttempt(shareId, clientKeyHash, env) {
  if (!env.EXPENSES_DB) {
    return null;
  }

  return env.EXPENSES_DB
    .prepare(
      [
        "SELECT failed_count, locked_until, updated_at",
        "FROM guest_access_attempts",
        "WHERE share_id = ? AND client_key_hash = ?"
      ].join(" ")
    )
    .bind(shareId, clientKeyHash)
    .first();
}

async function guestAccessClientKeyHash(request, env) {
  const ip =
    request.headers.get("CF-Connecting-IP") ||
    request.headers.get("X-Forwarded-For") ||
    request.headers.get("Fastly-Client-IP") ||
    "unknown";
  const userAgent = request.headers.get("User-Agent") || "unknown";
  const secret = env.GUEST_ACCESS_COOKIE_SECRET || "evenup-local-guest-access-secret";
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`${secret}:${ip}:${userAgent}`)
  );
  return base64UrlEncode(new Uint8Array(digest));
}

function parseCookie(header) {
  if (!header) {
    return {};
  }

  return Object.fromEntries(
    header.split(";").map((cookie) => {
      const [name, ...valueParts] = cookie.trim().split("=");
      return [name, valueParts.join("=")];
    })
  );
}

function base64UrlEncode(bytes) {
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("");
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlDecode(value) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(base64);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function constantTimeEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string") {
    return false;
  }

  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let index = 0; index < length; index += 1) {
    diff |= (left.charCodeAt(index) || 0) ^ (right.charCodeAt(index) || 0);
  }

  return diff === 0;
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
  if (typeof value !== "string" || value.trim().length === 0) {
    return "Date not set";
  }

  const normalized = value.trim();
  const isoDateMatch = normalized.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (isoDateMatch) {
    const [, yearText, monthText, dayText] = isoDateMatch;
    const year = Number(yearText);
    const month = Number(monthText);
    const day = Number(dayText);
    if (isValidCalendarDate(year, month, day)) {
      return `${day} ${MONTH_LABELS[month - 1]} ${year}`;
    }
  }

  const parsed = new Date(normalized);
  if (!Number.isNaN(parsed.getTime())) {
    return `${parsed.getUTCDate()} ${MONTH_LABELS[parsed.getUTCMonth()]} ${parsed.getUTCFullYear()}`;
  }

  return "Date not set";
}

function isValidCalendarDate(year, month, day) {
  if (month < 1 || month > 12 || day < 1 || day > 31) return false;
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

function formatMerchantName(value) {
  const name = String(value || "").trim().replace(/\s+/g, " ");
  if (!name) return "Shared expense";

  const withPolishedSeparators = name.replace(/\s+-\s+/g, " – ");
  if (!isMostlyUppercase(withPolishedSeparators)) {
    return withPolishedSeparators;
  }

  return withPolishedSeparators.replace(/[A-Z0-9]+(?:'[A-Z0-9]+)?/g, (word) => titleCaseWord(word));
}

function isMostlyUppercase(value) {
  const letters = value.replace(/[^A-Za-z]/g, "");
  return letters.length >= 4 && letters === letters.toUpperCase();
}

function titleCaseWord(word) {
  if (/^\d+$/.test(word)) return word;
  return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
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
    .topbar { height: 64px; display: grid; place-items: center; border-bottom: 1px solid #e2e2e2; background: #f9f9f9; }
    .brand-lockup { display: inline-flex; align-items: center; justify-content: center; color: #111; }
    .brand-logo { width: 44px; height: 44px; display: block; object-fit: contain; }
    main { width: min(100%, 480px); margin: 0 auto; padding: 24px 20px 32px; }
    .hero { text-align: center; padding: 12px 0 8px; }
    .eyebrow { display: inline-flex; border: 1px solid #e2e2e2; border-radius: 999px; padding: 6px 12px; color: #5f5e5e; font-size: 12px; font-weight: 700; text-transform: uppercase; }
    h1 { margin: 14px auto 8px; max-width: 18ch; font-size: 30px; line-height: 36px; letter-spacing: 0; overflow-wrap: anywhere; }
    h2 { margin: 0 0 12px; font-size: 16px; line-height: 22px; }
    h3 { margin: 0 0 8px; font-size: 13px; line-height: 18px; text-transform: uppercase; letter-spacing: 0.04em; color: #5f5e5e; }
    .total { font-size: 42px; line-height: 48px; font-weight: 900; }
    .paid { display: inline-flex; align-items: center; gap: 4px; max-width: 100%; margin-top: 16px; padding: 9px 14px; border: 1px solid #e2e2e2; border-radius: 999px; background: #fff; }
    .paid strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .panel, .error-card { margin-top: 24px; border: 1px solid #e2e2e2; border-radius: 18px; background: #fff; padding: 18px; box-shadow: 0 12px 30px rgba(0,0,0,0.04); }
    .row { display: flex; justify-content: space-between; gap: 16px; padding: 12px 0; border-top: 1px solid #eeeeee; }
    .row:first-of-type { border-top: 0; }
    .strong { font-size: 18px; font-weight: 700; }
    .muted { color: #5f5e5e; }
    .compact { padding-top: 36px; }
    .fees { margin-top: 12px; border-top: 1px solid #000; }
    .passcode-form { display: grid; gap: 12px; }
    .passcode-form label { color: #5f5e5e; font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
    .passcode-form input { width: 100%; border: 1px solid #cfcfcf; border-radius: 14px; padding: 14px 16px; font-size: 28px; line-height: 36px; font-weight: 800; letter-spacing: 0.32em; text-align: center; text-transform: uppercase; }
    .passcode-form button { border: 0; border-radius: 999px; padding: 14px 18px; background: #111; color: #fff; font-weight: 800; font-size: 15px; }
    .error-text { margin: 0; color: #93000a; font-weight: 700; font-size: 14px; }
    .person { border-top: 1px solid #eeeeee; }
    .person:first-of-type { border-top: 0; }
    .person summary { cursor: pointer; list-style: none; display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 14px 0; }
    .person summary::-webkit-details-marker { display: none; }
    .person summary span { display: grid; gap: 3px; }
    .person summary small { color: #5f5e5e; font-weight: 700; font-size: 12px; }
    .person-body { padding: 0 0 16px; }
    .totals-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-bottom: 16px; }
    .totals-grid div { border: 1px solid #eeeeee; border-radius: 12px; padding: 10px; background: #f9f9f9; display: grid; gap: 3px; }
    .totals-grid span { color: #5f5e5e; font-size: 12px; font-weight: 700; }
    .subsection { margin-top: 14px; }
    .subsection:empty { display: none; }
    .detail-row, .mini-row { display: flex; justify-content: space-between; gap: 14px; padding: 9px 0; border-top: 1px solid #eeeeee; }
    .detail-row span { display: grid; gap: 2px; }
    .detail-row small { color: #5f5e5e; font-size: 12px; }
    .notice { margin-top: 24px; text-align: center; border: 1px solid #e2e2e2; border-radius: 10px; padding: 12px; color: #5f5e5e; background: #f3f3f3; font-size: 13px; }
    .error-card { text-align: center; padding: 32px 22px; }
    .icon { width: 56px; height: 56px; margin: 0 auto 14px; border-radius: 999px; display: grid; place-items: center; background: #ffdad6; color: #93000a; font-weight: 900; }
    footer { padding: 24px; text-align: center; color: #777; font-size: 13px; }
    @media (min-width: 760px) {
      main { width: min(100%, 560px); padding-top: 28px; }
      h1 { max-width: 22ch; }
    }
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
