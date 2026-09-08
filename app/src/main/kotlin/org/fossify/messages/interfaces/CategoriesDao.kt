@file:Suppress("TooManyFunctions")

package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.fossify.messages.models.CategoryConversation
import org.fossify.messages.models.CategoryUnread
import org.fossify.messages.models.MessageCategory

@Dao
interface CategoriesDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertCategory(category: MessageCategory): Long

    @Query("SELECT * FROM message_categories ORDER BY sort_order ASC, id ASC")
    fun getCategories(): List<MessageCategory>

    @Query("UPDATE message_categories SET name = :name WHERE id = :categoryId")
    fun renameCategory(categoryId: Long, name: String)

    @Query("UPDATE message_categories SET sort_order = :sortOrder WHERE id = :categoryId")
    fun updateCategorySortOrder(categoryId: Long, sortOrder: Int)

    @Query("UPDATE message_categories SET show_in_all = :showInAll WHERE id = :categoryId")
    fun setCategoryShowInAll(categoryId: Long, showInAll: Boolean)

    @Query("DELETE FROM message_categories WHERE id = :categoryId")
    fun deleteCategory(categoryId: Long)

    @Query("DELETE FROM category_conversations WHERE category_id = :categoryId")
    fun deleteCategoryMappings(categoryId: Long)

    @Transaction
    fun deleteCategoryAndMappings(categoryId: Long) {
        deleteCategoryMappings(categoryId)
        deleteCategory(categoryId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addConversationToCategory(mapping: CategoryConversation)

    @Query("DELETE FROM category_conversations WHERE category_id = :categoryId AND thread_id = :threadId")
    fun removeConversationFromCategory(categoryId: Long, threadId: Long)

    @Query("DELETE FROM category_conversations WHERE thread_id = :threadId")
    fun removeConversationFromAllCategories(threadId: Long)

    @Transaction
    fun moveConversationToCategory(categoryId: Long, threadId: Long) {
        removeConversationFromAllCategories(threadId)
        addConversationToCategory(CategoryConversation(categoryId = categoryId, threadId = threadId))
    }

    @Query("SELECT category_id FROM category_conversations WHERE thread_id = :threadId LIMIT 1")
    fun getCategoryIdForThread(threadId: Long): Long?

    @Query("SELECT thread_id FROM category_conversations WHERE category_id = :categoryId")
    fun getThreadIdsForCategory(categoryId: Long): List<Long>

    @Query(
        """
        SELECT DISTINCT cc.thread_id
        FROM category_conversations cc
        INNER JOIN message_categories mc ON mc.id = cc.category_id
        WHERE mc.show_in_all = 0
        """
    )
    fun getThreadIdsHiddenFromAll(): List<Long>

    @Query(
        """
        SELECT cc.category_id AS categoryId, COALESCE(SUM(c.unread_count), 0) AS unreadCount
        FROM category_conversations cc
        LEFT JOIN conversations c ON c.thread_id = cc.thread_id AND c.archived = 0
        GROUP BY cc.category_id
        """
    )
    fun getUnreadCountsByCategory(): List<CategoryUnread>

    @Query(
        """
        SELECT COALESCE(SUM(c.unread_count), 0)
        FROM category_conversations cc
        INNER JOIN conversations c ON c.thread_id = cc.thread_id AND c.archived = 0
        WHERE cc.category_id = :categoryId
        """
    )
    fun getUnreadCount(categoryId: Long): Int
}
