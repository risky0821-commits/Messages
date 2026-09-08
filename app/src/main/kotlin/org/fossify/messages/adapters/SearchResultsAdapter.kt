package org.fossify.messages.adapters

import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemSearchResultBinding
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.launchConversationDetails
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.extensions.markThreadMessagesUnread
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.MUTED_NOTIFICATION_CHANNEL_PREFIX
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.models.SearchResult

class SearchResultsAdapter(
    activity: SimpleActivity,
    var searchResults: ArrayList<SearchResult>,
    recyclerView: MyRecyclerView,
    highlightText: String,
    itemClick: (Any) -> Unit,
    private val refreshResults: () -> Unit = {},
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private var fontSize = activity.getTextSize()
    private var textToHighlight = highlightText

    init {
        sortResultsByRealDate()
    }

    override fun getActionMenuId() = R.menu.cab_conversations

    override fun prepareActionMode(menu: Menu) {
        val selected = getSelectedResult() ?: return
        val threadId = selected.threadId
        val isPinned = activity.config.pinnedConversations.contains(threadId.toString())
        val isMuted = activity.config.isConversationMuted(threadId)

        menu.apply {
            findItem(R.id.cab_pin_conversation).isVisible = !isPinned
            findItem(R.id.cab_unpin_conversation).isVisible = isPinned
            findItem(R.id.cab_mute_conversation).apply {
                isVisible = true
                title = activity.getString(
                    if (isMuted) R.string.unmute_conversation else R.string.mute_conversation
                )
            }
            findItem(R.id.cab_move_to_category).isVisible = true
            findItem(R.id.cab_mark_as_read).isVisible = true
            findItem(R.id.cab_mark_as_unread).isVisible = true
            findItem(R.id.cab_archive).isVisible = activity.config.isArchiveAvailable
            findItem(R.id.cab_delete).isVisible = true
            findItem(R.id.cab_conversation_details).isVisible = true

            findItem(R.id.cab_dial_number).isVisible = false
            findItem(R.id.cab_add_number_to_contact).isVisible = false
            findItem(R.id.cab_copy_number).isVisible = false
            findItem(R.id.cab_rename_conversation).isVisible = false
            findItem(R.id.cab_block_number).isVisible = false
            findItem(R.id.cab_select_all).isVisible = false
        }
    }

    override fun actionItemPressed(id: Int) {
        val selected = getSelectedResult() ?: return
        val threadId = selected.threadId

        when (id) {
            R.id.cab_pin_conversation -> {
                activity.config.addPinnedConversationByThreadId(threadId)
                finishAndRefresh()
            }

            R.id.cab_unpin_conversation -> {
                activity.config.removePinnedConversationByThreadId(threadId)
                finishAndRefresh()
            }

            R.id.cab_mute_conversation -> {
                val muted = !activity.config.isConversationMuted(threadId)
                activity.config.setConversationMuted(threadId, muted)
                activity.notificationManager.cancel(threadId.hashCode())
                if (!muted) {
                    activity.notificationManager.deleteNotificationChannel(
                        MUTED_NOTIFICATION_CHANNEL_PREFIX + threadId
                    )
                }
                finishAndRefresh()
            }

            R.id.cab_move_to_category -> showMoveToCategoryDialog(threadId)

            R.id.cab_mark_as_read -> {
                ensureBackgroundThread {
                    activity.markThreadMessagesRead(threadId)
                    activity.runOnUiThread { finishAndRefresh() }
                }
            }

            R.id.cab_mark_as_unread -> {
                ensureBackgroundThread {
                    activity.markThreadMessagesUnread(threadId)
                    activity.runOnUiThread { finishAndRefresh() }
                }
            }

            R.id.cab_archive -> {
                ensureBackgroundThread {
                    activity.updateConversationArchivedStatus(threadId, true)
                    activity.notificationManager.cancel(threadId.hashCode())
                    activity.runOnUiThread { finishAndRefresh() }
                }
            }

            R.id.cab_delete -> askConfirmDelete(threadId)

            R.id.cab_conversation_details -> {
                finishActMode()
                activity.launchConversationDetails(threadId)
            }
        }
    }

    override fun getSelectableItemCount() = searchResults.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = searchResults.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = searchResults.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val searchResult = searchResults[position]
        holder.bindView(searchResult, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, searchResult)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = searchResults.size

    fun updateItems(newItems: ArrayList<SearchResult>, highlightText: String = "") {
        if (newItems.hashCode() != searchResults.hashCode()) {
            searchResults = newItems.clone() as ArrayList<SearchResult>
            textToHighlight = highlightText
            sortResultsByRealDate()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun sortResultsByRealDate() {
        val snapshot = searchResults.toList()
        ensureBackgroundThread {
            val dated = snapshot.mapIndexed { index, result ->
                val timestamp = if (result.messageId == -1L) {
                    activity.conversationsDB.getConversationWithThreadId(result.threadId)?.date ?: 0
                } else {
                    activity.messagesDB.getThreadMessages(result.threadId)
                        .firstOrNull { it.id == result.messageId }
                        ?.date ?: 0
                }
                Triple(result, timestamp, index)
            }
                .sortedWith(
                    compareByDescending<Triple<SearchResult, Int, Int>> { it.second }
                        .thenBy { it.third }
                )
                .map { it.first }

            activity.runOnUiThread {
                if (searchResults.toSet() == snapshot.toSet()) {
                    searchResults = ArrayList(dated)
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun getSelectedResult(): SearchResult? {
        return searchResults.firstOrNull { selectedKeys.contains(it.hashCode()) }
    }

    private fun showMoveToCategoryDialog(threadId: Long) {
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
                        ensureBackgroundThread {
                            if (which == 0) {
                                activity.categoriesDB.removeConversationFromAllCategories(threadId)
                            } else {
                                activity.categoriesDB.moveConversationToCategory(
                                    categoryId = categories[which - 1].id,
                                    threadId = threadId,
                                )
                            }
                            activity.runOnUiThread { finishAndRefresh() }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun askConfirmDelete(threadId: Long) {
        val item = resources.getQuantityString(R.plurals.delete_conversations, 1, 1)
        val question = String.format(
            resources.getString(org.fossify.commons.R.string.deletion_confirmation),
            item
        )
        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                activity.categoriesDB.removeConversationFromAllCategories(threadId)
                activity.deleteConversation(threadId)
                activity.notificationManager.cancel(threadId.hashCode())
                activity.runOnUiThread { finishAndRefresh() }
            }
        }
    }

    private fun finishAndRefresh() {
        refreshConversations()
        finishActMode()
        refreshResults()
    }

    private fun setupView(view: View, searchResult: SearchResult) {
        ItemSearchResultBinding.bind(view).apply {
            searchResultHolder.isSelected = selectedKeys.contains(searchResult.hashCode())

            searchResultTitle.apply {
                text = searchResult.title.highlightTextPart(textToHighlight, properPrimaryColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
            }

            searchResultSnippet.apply {
                text = searchResult.snippet.highlightTextPart(textToHighlight, properPrimaryColor)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            searchResultDate.apply {
                text = searchResult.date
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            SimpleContactsHelper(activity).loadContactImage(
                searchResult.photoUri,
                searchResultImage,
                searchResult.title
            )
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = ItemSearchResultBinding.bind(holder.itemView)
            Glide.with(activity).clear(binding.searchResultImage)
        }
    }
}
