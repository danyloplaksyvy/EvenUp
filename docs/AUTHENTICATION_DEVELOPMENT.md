# Authentication development setup

Authentication is intentionally inert until a flavor-specific Firebase configuration and
OAuth client ID are supplied. No Firebase, PostgreSQL, R2, or signing credentials belong in
source control.

## Android development flavor

1. Create the Android Firebase app for package `com.dps.evenup.dev`.
2. Register the local debug SHA-1 and SHA-256 fingerprints.
3. Enable Google and email-link authentication.
4. Configure Firebase Hosting and allow the narrow continuation URL
   `https://<development-host>/__/auth/links`.
5. Place the downloaded file at `app/src/dev/google-services.json`.
6. Add these values to the user-level `~/.gradle/gradle.properties` or inject them in CI:

   ```properties
   EVENUP_DEV_AUTH_WEB_CLIENT_ID=<oauth-web-client-id>
   EVENUP_DEV_AUTH_LINK_DOMAIN=<firebase-hosting-domain>
   ```

Use the corresponding `EVENUP_STAGING_*` and `EVENUP_PROD_*` properties and
flavor-specific `google-services.json` files for isolated higher environments. Debug builds
install the App Check debug provider. Register its emitted debug token in Firebase before
enforcing App Check on the development Worker. Staging and production use Play Integrity.

The app builds without these files for local expense-flow work; authentication then presents
a configuration error instead of attempting a partial sign-in.

## Worker and PostgreSQL

Provision environment-specific bindings without committing their values:

- `FIREBASE_PROJECT_ID`
- Hyperdrive binding `HYPERDRIVE`
- R2 binding `PROFILE_AVATARS`
- `PUBLIC_BASE_URL`
- existing D1 binding `EXPENSES_DB`
- existing OpenAI secrets

Set `APP_CHECK_AUDIENCE` and `APP_CHECK_ISSUER` when `APP_CHECK_ENFORCED=true`. Keep
`ALLOW_ANONYMOUS_V1_WRITES=true` during compatibility rollout; set it to `false` only after
supported Android builds use authenticated v2 writes.

Apply `backend/postgres-migrations/0001_accounts.sql` with the deployment system's
PostgreSQL migration role. The migration is additive and idempotent, so validate it both
against an empty database and a database where the file has already been applied.

The hourly Worker maintenance trigger copies initial Google avatars into R2 and finalizes
expired 14-day deletion requests. Final erasure emits `FINAL_ACCOUNT_ERASURE_QUEUED`; the
production outbox consumer must use privileged Firebase administration to delete the Firebase
identity after the Worker has removed application profile data.

## Local verification

```shell
./gradlew assembleDevDebug
./gradlew connectedDevDebugAndroidTest
./gradlew assembleProdRelease
cd backend
npm test
npm run typecheck
```

Live authentication, App Links, Hyperdrive, and R2 checks require the bindings above.
For email links, test both the same-device flow and a cross-device flow that asks the user to
confirm the original email address.
