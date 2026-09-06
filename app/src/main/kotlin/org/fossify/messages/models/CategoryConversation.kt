package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "category_conversations",
    primaryKeys = ["category_id", "thread_id"],
    indices = [Index(value = ["thread_id"])]
)
data class CategoryConversation(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "thread_id")
    val threadId: Long,
)
