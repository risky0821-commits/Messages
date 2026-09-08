package org.fossify.messages.extensions

import android.content.Context
import org.fossify.messages.interfaces.CategoriesDao
import org.fossify.messages.models.CategoryConversation
import org.fossify.messages.models.MessageCategory

val Context.categoriesDB: CategoriesDao
    get() = getMessagesDB().CategoriesDao()

fun Context.createMessageCategory(name: String, sortOrder: Int): Long {
    return categoriesDB.insertCategory(
        MessageCategory(
            name = name.trim(),
            sortOrder = sortOrder,
        )
    )
}

fun Context.assignConversationToCategory(categoryId: Long, threadId: Long) {
    categoriesDB.addConversationToCategory(
        CategoryConversation(
            categoryId = categoryId,
            threadId = threadId,
        )
    )
}

fun Context.removeConversationFromCategory(categoryId: Long, threadId: Long) {
    categoriesDB.removeConversationFromCategory(categoryId, threadId)
}

fun Context.getCategoryUnreadCounts(): Map<Long, Int> {
    return categoriesDB.getUnreadCountsByCategory().associate { it.categoryId to it.unreadCount }
}

fun Context.getTotalUnreadCount(): Int {
    val hiddenThreadIds = categoriesDB.getThreadIdsHiddenFromAll().toHashSet()
    return conversationsDB.getNonArchived()
        .filterNot { it.threadId in hiddenThreadIds }
        .sumOf { it.unreadCount }
}
