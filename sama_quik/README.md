# Sama QUIK — independent prototype

Sama QUIK is an **experimental**, reproducible patch set for [QUIK SMS](https://github.com/quik-sms/quik) v4.3.7, pinned to upstream commit `02542049207bab8e467a90cc0e50d3febe2b6a8d`. QUIK and its derivative are GPLv3. Preserve attribution, source availability, and license notices.

## Goals
1. **Receive SMS** using QUIK's existing receiver/WorkManager pipeline; no new SMS code or altered receiver.
2. **Organize conversations into user-created categories** with tabs above the inbox and a bulk move action.
3. **Keep the Fossify-based Sama project untouched**, including its release package and signing keys.

## How to reproduce
```bash
curl -fL https://codeload.github.com/quik-sms/quik/tar.gz/02542049207bab8e467a90cc0e50d3febe2b6a8d -o quik.tar.gz
mkdir quik-source
tar xzf quik.tar.gz --strip-components=1 -C quik-source
python3 sama_quik/apply.py quik-source
cd quik-source
./gradlew :presentation:assembleDebug
```

The debug application ID becomes `dev.sama.quik.debug` so that the original QUIK and Fossify Sama packages are not overwritten. The CI signing key is disposable; builds are **not upgrade-compatible** until a stable test signing key is configured.

## Implementation
- Adds `samaCategoryId` to QUIK's Realm `Conversation` entity and migrates schema v15→16.
- Preserves that category ID across the full Telephony-to-Realm sync path when refreshing conversation metadata.
- Saves category names/IDs in a private JSON SharedPreferences setting. No SMS content is copied into category metadata.
- Filters the original reactive Realm conversation query by the chosen category and keeps the existing adapter.
- Adds category chips, create-category dialog, and move-selected-conversations menu. `0` means unassigned; `All` includes all categories.

## Current limits / tests
- This is a **prototype**: Android compilation and HONOR Magic V5 on-device SMS reception have not been established just by creating this patch.
- Category names cannot yet be renamed/deleted in UI; no foldable split-screen treatment in this initial experiment.
- Check category persistence after a full device SMS resync and after restart, move one/many threads, check empty categories and unread inbox view.
- Only one app can hold Android's default SMS role at a time. **Don't switch the default SMS app until installed build is verified**, and don't delete your existing Sama install.
- Android system SMS content survives an app change in normal operation, but application-specific category settings do not transfer automatically.

## Production gate
Before declaring this suitable for daily use, test receipt while foreground/background/force-stopped behavior (Android-imposed caveats), multipart SMS, reboot, duplicate notifications, category persistence, invalid/missing permissions, and clear rollback to the original SMS app.
