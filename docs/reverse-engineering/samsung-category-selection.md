# Samsung Messages category selection — evidence log

## Scope
- Target: Samsung Messages APK 16.3.21.1, SHA-256 `5c661cc4f20517046883819494b09742a0f5e4f1b48603bd5aad4a59248ae0a2`.
- Behavior under investigation: selecting one or more conversations and confirming a category action.
- Rebuild target: independent Kotlin selection snapshot in Sama Messages, *not* Samsung's proprietary implementation.

## Tools and limitations
- An offline DEX string/method-ID/code-offset scanner was used for static inspection of the user's supplied APK. No Samsung app was run.
- **REA MCP was unavailable** and `rea update` / `npx rea-agents setup` could not run in this environment due to unavailable npm network connectivity. Therefore none of these findings are claimed to be produced by REA.
- DEX offsets below are offsets inside the named DEX file, not process memory addresses.
- Evidence identifies call sites but **does not prove the entire UI-to-database execution chain**.

## Observed references (proven by DEX inspection)
1. `classes3.dex`: `SelectCategoryActivity.onOptionsItemSelected`, code offset `0x366034`. Its method body references `Lzy/x;.t2` at approximately `0x36607C` and has the log string `onOptionsItemSelected : done`.
2. `classes3.dex`: obfuscated `Lzy/x;.t2`, code offset `0x73EF44`, includes `handleDone()` and a call to `MessageThreadPool.getThreadPool` at approximately `0x73F0B6`.
3. `classes3.dex`: `CategoryConversationPickerActivity.onCreate` `0x3541E0`, reads the intent extra `category_id`, and logs `onCreate: invalid category id` on one path.
4. `classes2.dex`: `MessageContentContractConversationCategories.<init>` `0x36B80C`, references table `conversation_categories` and fields `conversation_id`, `category_id`, `category_type`.
5. `classes2.dex` strings: provider paths `conversation_categories_bulk`, `conversation_categories_by_multi_ids`, and `conversation_categories_by_conversation_id`. Their presence is proved; whether any particular UI action uses them is **not** established.

## Interpretation, not proof
The `Done` action probably delegates to a fragment handler that prepares a background operation. The specific method that writes category mappings, its transaction behavior, and its selection-lifecycle rules remain unknown. The provider paths are not a proven call graph.

## Sama implementation
- Source: `app/src/main/kotlin/org/fossify/messages/helpers/ConversationSelectionBatch.kt`.
- On selecting a target category, Sama snapshots the selected thread IDs **before** clearing action mode, and performs the database operation on the snapshot.
- Storage: Sama's own Room `category_conversations` mapping, not Samsung's `conversation_categories`.
- Separate `CategoriesDao.moveConversationToCategory` wraps per-thread removal and insertion in a Room transaction. Cross-thread batch atomicity has **not** been demonstrated.
- Network: none established as necessary for this feature. Samsung side effects/network telemetry not traced.

## Verification
- Original standalone Kotlin regression tests: PASS locally using `kotlinc` + `java -jar`; cases: multiple IDs, duplicates, clearing selection after capture, empty input.
- This branch adds JUnit tests at `app/src/test/kotlin/org/fossify/messages/helpers/ConversationSelectionBatchTest.kt` and a CI step invoking `testFossDebugUnitTest`.
- **A CI green build is not proof of parity** with Samsung; HONOR Magic V5 on-device validation is pending.
- On-device cases required: one/multiple selected, existing category, none/unassign, cancellation, rapidly switching tabs, background return, simulated database failure.

## Security / intellectual property
This branch contains only independent Sama Kotlin implementation and public structural observations. No extracted proprietary Samsung assets, code, credentials, or application data are redistributed.

## Next evidence required
Install and connect REA locally (`rea update`), follow `Lzy/x;.t2` to the downstream worker, validate every call transition, record actual call-site offsets, and compare against an authorized on-device behavior trace. Until then this remains a **partial static feature trace**.
