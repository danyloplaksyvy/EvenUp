import { z } from "zod";

export const bootstrapSchema = z.object({
  locale: z.string().trim().min(2).max(35),
  defaultCurrency: z.string().regex(/^[A-Z]{3}$/),
  legalAcceptance: z.object({
    termsVersion: z.string().trim().min(1).max(80),
    privacyVersion: z.string().trim().min(1).max(80),
  }),
});

export const profilePatchSchema = z.object({
  displayName: z.string().trim().min(1).max(80),
  username: z.string().trim().toLowerCase().regex(/^[a-z][a-z0-9_]{1,22}[a-z0-9]$/),
  defaultCurrency: z.string().regex(/^[A-Z]{3}$/),
  locale: z.string().trim().min(2).max(35),
}).refine(({ username }) => !username.includes("__"), {
  path: ["username"],
  message: "Consecutive underscores are not allowed",
});

export const deletionSchema = z.object({
  confirmation: z.literal("DELETE"),
});

export const avatarIntentSchema = z.object({
  contentType: z.enum(["image/jpeg", "image/png", "image/webp"]),
  contentLength: z.number().int().positive().max(5 * 1024 * 1024),
});
