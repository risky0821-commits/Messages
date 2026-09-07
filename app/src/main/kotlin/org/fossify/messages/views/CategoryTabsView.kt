@file:Suppress("MagicNumber", "TooManyFunctions", "UnusedParameter")

package org.fossify.messages.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.createMessageCategory
import org.fossify.messages.extensions.getCategoryUnreadCounts
import org.fossify.messages.extensions.getTotalUnreadCount
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.fossify.messages.models.MessageCategory
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
        setPadding(dp(12), dp(8), dp(12), dp(10))
    }

    private var selectedCategoryId: Long? = null
    private var showUnreadOnly = false
    private var registeredToBus = false

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
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
                tabs.addView(
                    makeTab(
                        title = context.getString(R.string.category_all),
                        unreadCount = totalUnread,
                        selected = !showUnreadOnly && selectedCategoryId == null,
                        onClick = {
                            showUnreadOnly = false
                            selectedCategoryId = null
                            applyConversationFilter(allConversations)
                            refreshTabsAndList()
                        },
                    )
                )

                tabs.addView(
                    makeTab(
                        title = context.getString(R.string.category_unread),
                        unreadCount = totalUnread,
                        selected = showUnreadOnly,
                        onClick = {
                            showUnreadOnly = true
                            selectedCategoryId = null
                            applyConversationFilter(allConversations.filter { !it.read })
                            refreshTabsAndList()
                        },
                    )
                )

                categories.forEach { category ->
                    tabs.addView(
                        makeTab(
                            title = category.name,
                            unreadCount = unread[category.id] ?: 0,
                            selected = !showUnreadOnly && selectedCategoryId == category.id,
                            onClick = {
                                showUnreadOnly = false
                                selectedCategoryId = category.id
                                applySelectedCategory(category.id)
                                refreshTabsAndList()
                            },
                            onLongClick = { showManageCategoryDialog(category) },
                        )
                    )
                }

                tabs.addView(makeAddButton())
                applyCurrentSelectionIfPossible(allConversations)
            }
        }
    }

    private fun applySelectedCategory(categoryId: Long) {
        ensureBackgroundThread {
            val threadIds = context.categoriesDB.getThreadIdsForCategory(categoryId).toHashSet()
            val filtered = context.conversationsDB.getNonArchived().filter { it.threadId in threadIds }
            post { applyConversationFilter(filtered) }
        }
    }

    private fun applyCurrentSelectionIfPossible(allConversations: List<Conversation>) {
        if (showUnreadOnly) {
            applyConversationFilter(allConversations.filter { !it.read })
            return
        }

        val categoryId = selectedCategoryId
        if (categoryId == null) {
            applyConversationFilter(allConversations)
            return
        }
        applySelectedCategory(categoryId)
    }

    private fun applyConversationFilter(conversations: List<Conversation>) {
        val recycler = rootView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.conversations_list)
            ?: return
        val adapter = recycler.adapter as? BaseConversationsAdapter ?: return
        val sortedConversations = conversations.sortedWith(
            compareByDescending<Conversation> {
                context.config.pinnedConversations.contains(it.threadId.toString())
            }.thenByDescending { it.date }
        )
        adapter.updateConversations(ArrayList(sortedConversations))
    }

    private fun makeTab(
        title: String,
        unreadCount: Int,
        selected: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
    ): TextView {
        val label = if (unreadCount > 0) "$title  $unreadCount" else title
        val primary = context.getProperPrimaryColor()
        val textColor = if (selected) contrastTextColor(primary) else context.getProperTextColor()

        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            minHeight = dp(38)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setTextColor(textColor)
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            textSize = 14f
            background = pillBackground(selected = selected, primary = primary)
            isClickable = true
            isFocusable = true
            elevation = if (selected) dp(1).toFloat() else 0f
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(8)
            }
        }
    }

    private fun makeAddButton(): TextView {
        val primary = context.getProperPrimaryColor()
        return TextView(context).apply {
            text = "+"
            gravity = Gravity.CENTER
            minWidth = dp(38)
            minHeight = dp(38)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTextColor(primary)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            background = pillBackground(selected = false, primary = primary)
            isClickable = true
            isFocusable = true
            setOnClickListener { showCreateCategoryDialog() }
        }
    }

    private fun pillBackground(selected: Boolean, primary: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            if (selected) {
                setColor(primary)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), withAlpha(context.getProperTextColor(), 54))
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun contrastTextColor(background: Int): Int {
        val luminance = (0.299 * Color.red(background)) +
            (0.587 * Color.green(background)) +
            (0.114 * Color.blue(background))
        return if (luminance > 160) Color.BLACK else Color.WHITE
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

    private fun showManageCategoryDialog(category: MessageCategory) {
        val options = arrayOf(
            context.getString(R.string.rename_category),
            context.getString(R.string.delete_category),
        )
        AlertDialog.Builder(context)
            .setTitle(category.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameCategoryDialog(category)
                    1 -> showDeleteCategoryDialog(category)
                }
            }
            .show()
    }

    private fun showRenameCategoryDialog(category: MessageCategory) {
        val input = EditText(context).apply {
            setText(category.name)
            setSelection(text.length)
            setSingleLine(true)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.rename_category)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    ensureBackgroundThread {
                        runCatching { context.categoriesDB.renameCategory(category.id, name) }
                        post { refreshTabsAndList() }
                    }
                }
            }
            .show()
    }

    private fun showDeleteCategoryDialog(category: MessageCategory) {
        AlertDialog.Builder(context)
            .setTitle(R.string.delete_category)
            .setMessage(context.getString(R.string.delete_category_confirmation, category.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ensureBackgroundThread {
                    context.categoriesDB.deleteCategoryAndMappings(category.id)
                    if (selectedCategoryId == category.id) {
                        selectedCategoryId = null
                    }
                    post { refreshTabsAndList() }
                }
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
