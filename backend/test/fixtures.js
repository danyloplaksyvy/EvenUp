import assert from "node:assert/strict";

export function jsonRequest(url, body, headers = {}) {
  return new Request(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...headers
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
      items: [
        {
          id: "item_1",
          name: "Pizza Margherita",
          quantity: 1,
          unitPriceMinor: 10000,
          totalPriceMinor: 10000
        }
      ],
      fees: [
        {
          id: "fee_1",
          type: "TAX",
          label: "Tax",
          amountMinor: 2050
        }
      ],
      totalMinor: 12050
    },
    participants: [
      {
        id: "participant_1",
        name: "Dana",
        creationOrder: 0
      },
      {
        id: "participant_2",
        name: "Lee",
        creationOrder: 1
      }
    ],
    payerParticipantId: "participant_1",
    itemAssignments: [
      {
        receiptItemId: "item_1",
        mode: "SharedEqual",
        shares: [
          {
            participantId: "participant_1",
            amountMinor: 5000
          },
          {
            participantId: "participant_2",
            amountMinor: 5000
          }
        ]
      }
    ],
    feeAllocations: [
      {
        feeId: "fee_1",
        mode: "Equal",
        shares: [
          {
            participantId: "participant_1",
            amountMinor: 1025
          },
          {
            participantId: "participant_2",
            amountMinor: 1025
          }
        ]
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
          assignedItemTotalMinor: 5000,
          allocatedFeeTotalMinor: 1025,
          discountCreditTotalMinor: 0,
          shareMinor: 6025,
          paidMinor: 12050,
          netBalanceMinor: 6025
        },
        {
          participantId: "participant_2",
          assignedItemTotalMinor: 5000,
          allocatedFeeTotalMinor: 1025,
          discountCreditTotalMinor: 0,
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
  guestAccessAttempts = [];

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
    if (/INSERT INTO expenses/.test(this.sql)) {
      const [
        id,
        shareId,
        title,
        payloadJson,
        guestPasscodeHash,
        guestPasscodeSalt,
        createdAt
      ] = this.values;
      const row = {
        id,
        share_id: shareId,
        title,
        payload_json: payloadJson,
        guest_passcode_hash: guestPasscodeHash,
        guest_passcode_salt: guestPasscodeSalt,
        created_at: createdAt
      };
      this.database.rows.push(row);

      return {
        success: true
      };
    }

    if (/INSERT INTO guest_access_attempts/.test(this.sql)) {
      const [shareId, clientKeyHash, failedCountOrUpdatedAt, lockedUntil, updatedAt] = this.values;
      const existing = this.database.guestAccessAttempts.find(
        (attempt) => attempt.share_id === shareId && attempt.client_key_hash === clientKeyHash
      );
      const failedCount = this.values.length === 3 ? 0 : failedCountOrUpdatedAt;
      const finalLockedUntil = this.values.length === 3 ? null : lockedUntil;
      const finalUpdatedAt = this.values.length === 3 ? failedCountOrUpdatedAt : updatedAt;
      const row = {
        share_id: shareId,
        client_key_hash: clientKeyHash,
        failed_count: failedCount,
        locked_until: finalLockedUntil,
        updated_at: finalUpdatedAt
      };

      if (existing) {
        Object.assign(existing, row);
      } else {
        this.database.guestAccessAttempts.push(row);
      }

      return {
        success: true
      };
    }

    throw new Error(`Unsupported fake SQL: ${this.sql}`);
  }

  async first() {
    if (/FROM expenses/.test(this.sql)) {
      const [shareId] = this.values;
      return this.database.rows.find((row) => row.share_id === shareId) ?? null;
    }

    if (/FROM guest_access_attempts/.test(this.sql)) {
      const [shareId, clientKeyHash] = this.values;
      return (
        this.database.guestAccessAttempts.find(
          (attempt) => attempt.share_id === shareId && attempt.client_key_hash === clientKeyHash
        ) ?? null
      );
    }

    throw new Error(`Unsupported fake SQL: ${this.sql}`);
  }
}
