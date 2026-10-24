package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the independent Sama implementation; not parity with Samsung's unknown runtime. */
class ConversationSelectionBatchTest {
    @Test fun preservesMultiSelectionOrder() {
        assertEquals(listOf(2L, 5L, 9L), ConversationSelectionBatch.snapshot(listOf(2L, 5L, 9L)))
    }

    @Test fun removesDuplicateThreadIds() {
        assertEquals(listOf(2L, 5L), ConversationSelectionBatch.snapshot(listOf(2L, 5L, 2L)))
    }

    @Test fun remainsStableWhenUiSelectionIsCleared() {
        val selected = mutableListOf(3L, 7L)
        val captured = ConversationSelectionBatch.snapshot(selected)
        selected.clear()
        assertEquals(listOf(3L, 7L), captured)
    }

    @Test fun supportsEmptySelection() {
        assertTrue(ConversationSelectionBatch.snapshot(emptyList()).isEmpty())
    }

    @Test fun supportsOneSelectedConversation() {
        assertEquals(listOf(42L), ConversationSelectionBatch.snapshot(listOf(42L)))
    }
}
