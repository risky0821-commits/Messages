package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class MessageCategory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "show_in_all")
    val showInAll: Boolean = true,
)
