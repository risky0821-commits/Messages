package org.fossify.messages.adapters

import android.content.Intent
import android.text.TextUtils
import android.view.Menu
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FeatureLockedDialog
import org.fossify.commons.extensions.addBlockedNumber
import org.fossify.commons.extensions.addLockedLabelIfNeeded
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.isOrWasThankYouInstalled
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.dialogs.RenameConversationDialog
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.dialNumber
import org.fossify.messages.extensions.launchConversationDetails
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.extensions.markThreadMessagesUnread
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.MUTED_NOTIFICATION_CHANNEL_PREFIX
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.models.Conversation

class ConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override fun getActionMenuId() = R.menu.cab_conversations

    override fun usesDirectLongPressActions() = true

    override fun onConversationLongPressed(conversation: Conversation, position: Int) {
        showConversationActions(conversation, position)
    }

    private fun showConversationActions(conversation: Conversation, position: Int) {
        val isPinned = activity.config.pinnedConversations.contains(conversation.threadId.toString())
        val isMuted = activity.config.isConversationMuted(conversation.threadId)
        val actions = ArrayList<Pair<Int?, String>>().apply {
            add(
                (if (isPinned) R.id.cab_unpin_conversation else R.id.cab_pin_conversation) to
                    activity.getString(if (isPinned) R.string.unpin_conversation else R.string.pin_conversation)
            )
            add(
                R.id.cab_mute_conversation to
                    activity.getString(if (isMuted) R.string.unmute_conversation else R.string.mute_conversation)
            )
            add(
                (if (conversation.read) R.id.cab_mark_as_unread else R.id.cab_mark_as_read) to
                    activity.getString(if (conversation.read) R.string.mark_as_unread else R.string.mark_as_read)
            )
            add(R.id.cab_move_to_category to activity.getString(R.string.move_to_category))
            if (activity.config.isArchiveAvailable) {
                add(R.id.cab_archive to activity.getString(R.string.archive))
            }
            add(R.id.cab_delete to activity.getString(org.fossify.commons.R.string.delete))
            add(R.id.cab_conversation_details to activity.getString(R.string.conversation_details))
            add(null to activity.getString(R.string.select_conversations))
        }

        AlertDialog.Builder(activity)
            .setTitle(conversation.title)
            .setItems(actions.map { it.second }.toTypedArray()) { _, which ->
                val actionId = actions[which].first
                startConversationSelection(position)
                if (actionId != null) {
                    actionItemPressed(actionId)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun prepareActionMode(menu: Menu) {
        val selectedItems = getSelectedItems()
        val isSingleSelection = isOneItemSelected()
        val selectedConversation = selectedItems.firstOrNull() ?: return
        val isGroupConversation = selectedConversation.isGroupConversation
        val archiveAvailable = activity.config.isArchiveAvailable

        menu.apply {
            findItem(R.id.cab_block_number).title =
                activity.addLockedLabelIfNeeded(org.fossify.commons.R.string.block_number)
            findItem(R.id.cab_add_number_to_contact).isVisible =
                isSingleSelection && !isGroupConversation
            findItem(R.id.cab_dial_number).isVisible =
                isSingleSelection && !isGroupConversation &&
                        !isShortCodeWithLetters(selectedConversation.phoneNumber)
            findItem(R.id.cab_copy_number).isVisible = isSingleSelection && !isGroupConversation
            findItem(R.id.cab_rename_conversation).isVisible =
                isSingleSelection && isGroupConversation
            findItem(R.id.cab_conversation_details).isVisible = isSingleSelection
            findItem(R.id.cab_mute_conversation).apply {
                isVisible = isSingleSelection
                title = if (activity.config.isConversationMuted(selectedConversation.threadId)) {
                    activity.getString(R.string.unmute_conversation)
                } else {
                    activity.getString(R.string.mute_conversation)
                }
            }
            findItem(R.id.cab_mark_as_read).isVisible = selectedItems.any { !it.read }
            findItem(R.id.cab_mark_as_unread).isVisible = selectedItems.any { it.read }
            findItem(R.id.cab_archive).isVisible = archiveAvailable
            findItem(R.id.cab_move_to_category).isVisible = true
            checkPinBtnVisibility(this)
        }
    }

    override fun actionItemPressed(id: Int) {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_number_to_contact -> addNumberToContact()
            R.id.cab_block_number -> tryBlocking()
            R.id.cab_dial_number -> dialNumber()
            R.id.cab_copy_number -> copyNumberToClipboard()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_archive -> askConfirmArchive()
            R.id.cab_move_to_category -> showMoveToCategoryDialog()
            R.id.cab_mute_conversation -> toggleMuteConversation()
            R.id.cab_rename_conversation -> renameConversation(selectedItems.first())
            R.id.cab_conversation_details -> {
                activity.launchConversationDetails(selectedItems.first().threadId)
                finishActMode()
            }

            R.id.cab_mark_as_read -> markAsRead()
            R.id.cab_mark_as_unread -> markAsUnread()
            R.id.cab_pin_conversation -> pinConversation(true)
            R.id.cab_unpin_conversation -> pinConversation(false)
            R.id.cab_select_all -> selectAll()
        }
    }

    private fun showMoveToCategoryDialog() {
        ensureBackgroundThread {
            val categories = activity.categoriesDB.getCategories()
            activity.runOnUiThread {
                val labels = ArrayList<String>().apply {
                    add(activity.getString(R.string.no_category))
                    addAll(categories.map { it.name })
                }.toTypedArray()

                AlertDialog.Builder(activity)
                    .setTitle(R.string.move_to_category)
                    .setItems(labels) { _, which ->
                        val conversations = getSelectedItems()
                        ensureBackgroundThread {
                            if (which == 0) {
                                conversations.forEach { conversation ->
                                    activity.categoriesDB.removeConversationFromAllCategories(conversation.threadId)
                                }
                            } else {
                                val category = categories[which - 1]
                                conversations.forEach { conversation ->
                                    activity.categoriesDB.moveConversationToCategory(
                                        categoryId = category.id,
                                        threadId = conversation.threadId,
                                    )
                                }
                            }
                            refreshConversationsAndFinishActMode()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun toggleMuteConversation() {
        val conversation = getSelectedItems().singleOrNull() ?: return
        val threadId = conversation.threadId
        val muted = !activity.config.isConversationMuted(threadId)

        activity.config.setConversationMuted(threadId, muted)
        activity.notificationManager.cancel(threadId.hashCode())
        if (!muted) {
            activity.notificationManager.deleteNotificationChannel(
                MUTED_NOTIFICATION_CHANNEL_PREFIX + threadId
            )
        }
        refreshVisualIndicators()
        finishActMode()
    }

    private fun tryBlocking() {
        if (activity.isOrWasThankYouInstalled()) {
            askConfirmBlock()
        } else {
            FeatureLockedDialog(activity) { }
        }
    }

    private fun askConfirmBlock() {
        val numbers = getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber }
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(
            resources.getString(org.fossify.commons.R.string.block_confirmation),
            numbersString
        )

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val numbersToBlock = getSelectedItems()
        val newList = currentList.toMutableList().apply { removeAll(numbersToBlock) }

        ensureBackgroundThread {
            numbersToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                submitList(newList)
                finishActMode()
            }
        }
    }

    private fun dialNumber() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        activity.dialNumber(conversation.phoneNumber) {
            finishActMode()
        }
    }

    private fun copyNumberToClipboard() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(conversation.phoneNumber)
        finishActMode()
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)
        val baseString = org.fossify.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteConversations()
            }
        }
    }

    private fun askConfirmArchive() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)
        val baseString = R.string.archive_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                archiveConversations()
            }
        }
    }

    private fun archiveConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove =
            currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.updateConversationArchivedStatus(it.threadId, true)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        val newList = try {
            currentList.toMutableList().apply { removeAll(conversationsToRemove) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshConversations()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }

    private fun deleteConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove =
            currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.categoriesDB.removeConversationFromAllCategories(it.threadId)
            activity.deleteConversation(it.threadId)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        val newList = try {
            currentList.toMutableList().apply { removeAll(conversationsToRemove) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshConversations()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }

    private fun renameConversation(conversation: Conversation) {
        RenameConversationDialog(activity, conversation) {
            ensureBackgroundThread {
                val updatedConv = activity.renameConversation(conversation, newTitle = it)
                activity.runOnUiThread {
                    finishActMode()
                    currentList.toMutableList().apply {
                        set(indexOf(conversation), updatedConv)
                        updateConversations(this as ArrayList<Conversation>)
                    }
                }
            }
        }
    }

    private fun markAsRead() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsRead =
            currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        ensureBackgroundThread {
            conversationsMarkedAsRead.filter { conversation -> !conversation.read }.forEach {
                activity.markThreadMessagesRead(it.threadId)
            }
            refreshConversationsAndFinishActMode()
        }
    }

    private fun markAsUnread() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsUnread =
            currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        ensureBackgroundThread {
            conversationsMarkedAsUnread.filter { conversation -> conversation.read }.forEach {
                activity.markThreadMessagesUnread(it.threadId)
            }
            refreshConversationsAndFinishActMode()
        }
    }

    private fun addNumberToContact() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, conversation.phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun pinConversation(pin: Boolean) {
        val conversations = getSelectedItems()
        if (conversations.isEmpty()) {
            return
        }

        if (pin) {
            activity.config.addPinnedConversations(conversations)
        } else {
            activity.config.removePinnedConversations(conversations)
        }

        getSelectedItemPositions().forEach {
            notifyItemChanged(it)
        }
        refreshConversationsAndFinishActMode()
    }

    private fun checkPinBtnVisibility(menu: Menu) {
        val pinnedConversations = activity.config.pinnedConversations
        val selectedConversations = getSelectedItems()
        menu.findItem(R.id.cab_pin_conversation).isVisible =
            selectedConversations.any { !pinnedConversations.contains(it.threadId.toString()) }
        menu.findItem(R.id.cab_unpin_conversation).isVisible =
            selectedConversations.all { pinnedConversations.contains(it.threadId.toString()) }
    }

    private fun refreshConversationsAndFinishActMode() {
        activity.runOnUiThread {
            refreshConversations()
            finishActMode()
        }
    }
}