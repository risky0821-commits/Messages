# Sama QUIK — signing and category-data preservation

## Current constraint (important)
The tested `dev.sama.quik.debug.directswipe` APK comes from GitHub Actions' default debug keystore, which is generated per fresh runner. New builds cannot be installed as an in-place update of that app unless they use the *same private signing key* and *same package name*. Do not silently change the package ID or try to override signatures; Android prevents that.

## Before promoting a production build
1. Generate a **new persistent private Android upload/signing keystore** outside this public repository with `keytool`. Keep an offline encrypted backup of it and the passwords. Never commit the keystore or credentials to GitHub, even in source history.
2. Configure protected GitHub Actions secrets for the base64 keystore bytes, keystore password, alias, and key password; limit which workflow runs can access them. Avoid distributing signing material in workflow artifacts or logs.
3. Use one final production `applicationId` (proposed: `dev.sama.quik`) without changing it across releases, and increment `versionCode` for every update.
4. In the build workflow, fail closed if production signing secrets are absent. Use the private keystore to sign every release and verify the APK certificate fingerprint.
5. Keep tests on a distinct package. Upgrade-test two consecutively built releases with the same key and package, and verify their category database and SMS receive pipeline after an update.
6. Back up the category file from **the exact app instance** before migration. Existing `Direct Swipe` APK does **not** have an export option and uses a different package; a backup/export option added to a new test APK cannot magically read the older app's protected data.
7. Exported JSON contains recipient phone numbers/sender addresses and category names. Store it privately; it does **not** contain message bodies or attachments.

## Proposed backup format
`format: sama-quik-categories`; `version: 1`; `categories: [{id, name}]`; `assignments: [{participants, categoryId}]`. Assignments use normalized sorted participant addresses rather than system SMS thread IDs. Incompatible/unknown participants stay uncategorized on a different device. Restore validates before applying and never deletes SMS/MMS.

**Do not announce stable signing as completed until a persistent key is configured and two builds are proven upgrade-compatible.**
