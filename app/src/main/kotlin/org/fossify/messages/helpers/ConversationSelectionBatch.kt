package org.fossify.messages.helpers

/** Immutable ordered IDs for a bulk operation, independent of subsequent UI selection changes. */
object ConversationSelectionBatch {
    fun snapshot(threadIds: Iterable<Long>): List<Long> = threadIds.distinct().toList()
}
