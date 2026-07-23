export interface HyperdriveBinding {
  connectionString: string;
}

export interface R2BucketBinding {
  get(key: string): Promise<{
    body: ReadableStream;
    httpMetadata?: { contentType?: string };
  } | null>;
  put(
    key: string,
    value: ReadableStream | ArrayBuffer | ArrayBufferView | string,
    options?: { httpMetadata?: { contentType?: string } },
  ): Promise<unknown>;
  list(options?: { prefix?: string; cursor?: string }): Promise<{
    objects: Array<{ key: string }>;
    truncated: boolean;
    cursor?: string;
  }>;
  delete(keys: string | string[]): Promise<void>;
}

export interface WorkerEnv {
  FIREBASE_PROJECT_ID?: string;
  HYPERDRIVE?: HyperdriveBinding;
  PROFILE_AVATARS?: R2BucketBinding;
  PUBLIC_BASE_URL?: string;
  ALLOW_ANONYMOUS_V1_WRITES?: string;
  APP_CHECK_ENFORCED?: string;
  APP_CHECK_AUDIENCE?: string;
  APP_CHECK_ISSUER?: string;
  OPENAI_API_KEY?: string;
  OPENAI_RECEIPT_MODEL?: string;
  OPENAI_EXPENSE_MODEL?: string;
  RECEIPT_PARSE_AUDIT?: string;
  EXPENSES_DB?: unknown;
}

export interface VerifiedIdentity {
  firebaseUid: string;
  email: string;
  name?: string;
  picture?: string;
  issuedAt: number;
  authenticatedAt: number;
  provider: "GOOGLE" | "EMAIL_LINK";
  providers?: Array<"GOOGLE" | "EMAIL_LINK">;
}

export interface AccountContext {
  identity: VerifiedIdentity;
  userId: string;
  status: "ACTIVE" | "DELETION_PENDING" | "DELETED";
  tokenValidAfter?: Date;
}
