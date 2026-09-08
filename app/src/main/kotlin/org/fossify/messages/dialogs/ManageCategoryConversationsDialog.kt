package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.models.MessageCategory

class ManageCategoryConversationsDialog(
    private val activity: SimpleActivity,
    private val category: MessageCategory,
    private val onSaved: () -> Unit,
) {
    init {
        ensureBackgroundThread {
            val conversations = activity.conversationsDB.getNonArchived()
                .sortedByDescending { it.date }
            val currentIds = activity.categoriesDB.getThreadIdsForCategory(category.id).toHashSet()

            activity.runOnUiThread {
                if (conversations.isEmpty()) {
                    AlertDialog.Builder(activity)
                        .setTitle(category.name)
                        .setMessage(R.string.no_conversations_available)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@runOnUiThread
                }

                val selectedIds = currentIds.toMutableSet()
                val labels = conversations.map { conversation ->
                    if (conversation.phoneNumber.isNotBlank() && conversation.phoneNumber != conversation.title) {
                        "${conversation.title}\n${conversation.phoneNumber}"
                    } else {
                        conversation.title
                    }
                }.toTypedArray()
                val checked = BooleanArray(conversations.size) { index ->
                    conversations[index].threadId in currentIds
                }

                AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.manage_category_conversations, category.name))
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                        val threadId = conversations[which].threadId
                        if (isChecked) {
                            selectedIds.add(threadId)
                        } else {
                            selectedIds.remove(threadId)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        saveSelection(currentIds, selectedIds)
                    }
                    .show()
            }
        }
    }

    private fun saveSelection(
        originalIds: Set<Long>,
        selectedIds: Set<Long>,
    ) {
        ensureBackgroundThread {
            originalIds
                .filterNot { it in selectedIds }
                .forEach { threadId ->
                    activity.categoriesDB.removeConversationFromCategory(category.id, threadId)
                }

            selectedIds
                .filterNot { it in originalIds }
                .forEach { threadId ->
                    activity.categoriesDB.moveConversationToCategory(category.id, threadId)
                }

            activity.runOnUiThread(onSaved)
        }
    }
}
