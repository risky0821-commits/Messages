package org.fossify.messages.models

data class CategoryUnread(
    val categoryId: Long,
    val unreadCount: Int,
)
