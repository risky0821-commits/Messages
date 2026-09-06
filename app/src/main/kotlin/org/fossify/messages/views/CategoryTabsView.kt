package org.fossify.messages.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.BaseConversationsAdapter
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.createMessageCategory
import org.fossify.messages.extensions.getCategoryUnreadCounts
import org.fossify.messages.extensions.getTotalUnreadCount
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class CategoryTabsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    private val tabs = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private var selectedCategoryId: Long? = null
    private var registeredToBus = false

    init {
        isHorizontalScrollBarEnabled = false
        addView(tabs)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!registeredToBus) {
            EventBus.getDefault().register(this)
            registeredToBus = true
        }
        refreshTabsAndList()
    }

    override fun onDetachedFromWindow() {
        if (registeredToBus) {
            EventBus.getDefault().unregister(this)
            registeredToBus = false
        }
        super.onDetachedFromWindow()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConversationsChanged(event: Events.RefreshConversations) {
        refreshTabsAndList()
    }

    fun refreshTabsAndList() {
        ensureBackgroundThread {
            val categories = context.categoriesDB.getCategories()
            val unread = context.getCategoryUnreadCounts()
            val totalUnread = context.getTotalUnreadCount()
            val allConversations = context.conversationsDB.getNonArchived()

            post {
                tabs.removeAllViews()
                tabs.addView(makeTab(
                    title = context.getString(R.string.category_all),
                    unreadCount = totalUnread,
                    selected = selectedCategoryId == null,
                ) {
                    selectedCategoryId = null
                    applyConversationFilter(allConversations)
                    refreshTabsAndList()
                })

                categories.forEach { category ->
                    tabs.addView(makeTab(
                        title = category.name,
                        unreadCount = unread[category.id] ?: 0,
                        selected = selectedCategoryId == category.id,
                    ) {
                        selectedCategoryId = category.id
                        ensureBackgroundThread {
                            val threadIds = context.categoriesDB.getThreadIdsForCategory(category.id).toHashSet()
                            val filtered = context.conversationsDB.getNonArchived().filter { it.threadId in threadIds }
                            post {
                                applyConversationFilter(filtered)
                                refreshTabsAndList()
                            }
                        }
                    })
                }

                tabs.addView(makeAddButton())
                applyCurrentSelectionIfPossible(allConversations)
            }
        }
    }

    private fun applyCurrentSelectionIfPossible(allConversations: List<Conversation>) {
        val categoryId = selectedCategoryId
        if (categoryId == null) {
            applyConversationFilter(allConversations)
            return
        }

        ensureBackgroundThread {
            val threadIds = context.categoriesDB.getThreadIdsForCategory(categoryId).toHashSet()
            val filtered = context.conversationsDB.getNonArchived().filter { it.threadId in threadIds }
            post { applyConversationFilter(filtered) }
        }
    }

    private fun applyConversationFilter(conversations: List<Conversation>) {
        val recycler = rootView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.conversations_list)
            ?: return
        val adapter = recycler.adapter as? BaseConversationsAdapter ?: return
        adapter.updateConversations(ArrayList(conversations))
    }

    private fun makeTab(
        title: String,
        unreadCount: Int,
        selected: Boolean,
        onClick: () -> Unit,
    ): TextView {
        val label = if (unreadCount > 0) "$title  $unreadCount" else title
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setTextColor(if (selected) context.getProperPrimaryColor() else context.getProperTextColor())
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            textSize = 14f
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(4)
            }
        }
    }

    private fun makeAddButton(): TextView {
        return TextView(context).apply {
            text = "+"
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setTextColor(context.getProperPrimaryColor())
            textSize = 20f
            isClickable = true
            isFocusable = true
            setOnClickListener { showCreateCategoryDialog() }
        }
    }

    private fun showCreateCategoryDialog() {
        val input = EditText(context).apply {
            hint = context.getString(R.string.category_name_hint)
            setSingleLine(true)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.create_category)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    ensureBackgroundThread {
                        runCatching {
                            context.createMessageCategory(name, context.categoriesDB.getCategories().size)
                        }
                        post { refreshTabsAndList() }
                    }
                }
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
