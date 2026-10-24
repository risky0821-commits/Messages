package org.fossify.messages.helpers

// Standalone JVM regression check: compile alongside the production helper using kotlinc.
fun main() {
    check(ConversationSelectionBatch.snapshot(listOf(2L, 5L, 9L)) == listOf(2L, 5L, 9L))
    check(ConversationSelectionBatch.snapshot(listOf(2L, 5L, 2L)) == listOf(2L, 5L))
    val mutableSelection = mutableListOf(3L, 7L)
    val snapshot = ConversationSelectionBatch.snapshot(mutableSelection)
    mutableSelection.clear()
    check(snapshot == listOf(3L, 7L))
    check(ConversationSelectionBatch.snapshot(emptyList()).isEmpty())
    println("PASS: multi-select, duplicate, clear-after-snapshot, empty-selection")
}
