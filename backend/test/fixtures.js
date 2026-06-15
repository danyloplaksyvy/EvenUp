import assert from "node:assert/strict";

export function jsonRequest(url, body) {
  return new Request(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });
}

export function validExpensePayload(overrides = {}) {
  return {
    schemaVersion: 1,
    title: "Dinner at Bella Roma",
    receipt: {
      merchantName: "Bella Roma",
      transactionDate: "2026-06-15",
      currency: "EUR",
      totalMinor: 12050
    },
    participants: [
      {
        id: "participant_1",
        name: "Dana"
      },
      {
        id: "participant_2",
        name: "Lee"
      }
    ],
    payerParticipantId: "participant_1",
    itemAssignments: [
      {
        receiptItemId: "item_1",
        participantIds: ["participant_1", "participant_2"],
        totalMinor: 10000
      }
    ],
    feeAllocations: [
      {
        feeId: "fee_1",
        label: "Tax",
        amountMinor: 2050
      }
    ],
    summary: {
      totalMinor: 12050,
      settlementRows: [
        {
          fromParticipantId: "participant_2",
          toParticipantId: "participant_1",
          amountMinor: 6025
        }
      ],
      participantSummaries: [
        {
          participantId: "participant_1",
          shareMinor: 6025,
          paidMinor: 12050,
          netBalanceMinor: 6025
        },
        {
          participantId: "participant_2",
          shareMinor: 6025,
          paidMinor: 0,
          netBalanceMinor: -6025
        }
      ]
    },
    ...overrides
  };
}

export class FakeD1Database {
  rows = [];

  constructor(rows = []) {
    this.rows = rows;
  }

  prepare(sql) {
    this.sql = sql;
    return new FakeD1Statement(this, sql);
  }
}

class FakeD1Statement {
  constructor(database, sql) {
    this.database = database;
    this.sql = sql;
  }

  bind(...values) {
    this.values = values;
    return this;
  }

  async run() {
    assert.match(this.sql, /INSERT INTO expenses/);

    const [id, shareId, title, payloadJson, createdAt] = this.values;
    const row = {
      id,
      share_id: shareId,
      title,
      payload_json: payloadJson,
      created_at: createdAt
    };
    this.database.rows.push(row);

    return {
      success: true
    };
  }

  async first() {
    assert.match(this.sql, /SELECT id, share_id, title, payload_json, created_at FROM expenses/);

    const [shareId] = this.values;
    return this.database.rows.find((row) => row.share_id === shareId) ?? null;
  }
}
