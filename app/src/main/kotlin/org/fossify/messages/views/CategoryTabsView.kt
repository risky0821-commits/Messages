@file:Suppress("MagicNumber", "TooManyFunctions", "UnusedParameter")

package org.fossify.messages.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MySearchMenu
import org.fossify.messages.R
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.createMessageCategory
import org.fossify.messages.extensions.getCategoryUnreadCounts
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
        setPadding(dp(14), dp(10), dp(14), dp(8))
    }

    private var selectedCategoryId: Long? = null
    private var registeredToBus = false
    private var currentCategories: List<MessageCategory> = emptyList()

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(tabs)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!registeredToBus) {
            EventBus.getDefault().register(this)
            registeredToBus = true
        }
        refreshTabs()
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
        refreshTabs()
    }

    fun showUnreadOnlyConversations() {
        selectedCategoryId = null
        rootView.findViewById<CategoryPagerView>(R.id.category_pager)?.showUnreadOnly()
        refreshTabs()
    }

    fun showAllConversations() {
        selectedCategoryId = null
        rootView.findViewById<CategoryPagerView>(R.id.category_pager)?.showAll()
        refreshTabs()
    }

    fun selectCategory(categoryId: Long) {
        if (currentCategories.none { it.id == categoryId }) return
        selectedCategoryId = categoryId
        rootView.findViewById<CategoryPagerView>(R.id.category_pager)?.selectCategory(categoryId)
        refreshTabs()
    }

    fun onPagerCategorySelected(categoryId: Long?) {
        if (selectedCategoryId == categoryId) return
        selectedCategoryId = categoryId
        refreshTabs()
    }

    private fun refreshTabs() {
        ensureBackgroundThread {
            val categories = context.categoriesDB.getCategories()
            val unread = context.getCategoryUnreadCounts()
            post {
                currentCategories = categories
                tabs.removeAllViews()

                val current = categories.firstOrNull { it.id == selectedCategoryId }
                tabs.addView(
                    makeCurrentFilterPill(
                        title = current?.name ?: context.getString(R.string.category_all),
                        unreadCount = current?.let { unread[it.id] ?: 0 },
                        onLongClick = current?.let { category -> { showManageCategoryDialog(category) } },
                    )
                )
                tabs.addView(makeSearchButton())
                tabs.addView(makeOverflowButton())
                tabs.addView(makeAddButton())

                rootView.findViewById<OneUiUnreadSummaryView>(R.id.unread_summary_card)?.refreshSummary()
                post { smoothScrollTo(0, 0) }
            }
        }
    }

    private fun makeCurrentFilterPill(
        title: String,
        unreadCount: Int?,
        onLongClick: (() -> Unit)?,
    ): TextView {
        val primary = context.getProperPrimaryColor()
        val label = if (unreadCount != null && unreadCount > 0) "$title  $unreadCount" else title
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            minHeight = dp(38)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            setTextColor(contrastTextColor(primary))
            setTypeface(typeface, Typeface.BOLD)
            textSize = 15f
            background = pillBackground(true, primary)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (selectedCategoryId != null) {
                    selectedCategoryId = null
                    rootView.findViewById<CategoryPagerView>(R.id.category_pager)?.selectCategory(null)
                    refreshTabs()
                }
            }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(10) }
        }
    }

    private fun makeSearchButton(): ImageView {
        val primary = context.getProperPrimaryColor()
        return ImageView(context).apply {
            setImageResource(org.fossify.commons.R.drawable.ic_search_vector)
            setColorFilter(context.getProperTextColor())
            contentDescription = context.getString(org.fossify.commons.R.string.search)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = pillBackground(false, primary)
            isClickable = true
            isFocusable = true
            setOnClickListener { openCompactSearch() }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(8) }
        }
    }

    private fun makeOverflowButton(): TextView {
        val primary = context.getProperPrimaryColor()
        return TextView(context).apply {
            text = "⋮"
            gravity = Gravity.CENTER
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getProperTextColor())
            contentDescription = context.getString(R.string.more_options)
            background = pillBackground(false, primary)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val menu = rootView.findViewById<MySearchMenu>(R.id.main_menu) ?: return@setOnClickListener
                menu.visibility = View.INVISIBLE
                menu.requireToolbar().showOverflowMenu()
            }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(8) }
        }
    }

    private fun openCompactSearch() {
        val menu = rootView.findViewById<MySearchMenu>(R.id.main_menu) ?: return
        if (menu.tag != COMPACT_SEARCH_TAG) {
            val previousCloseListener = menu.onSearchClosedListener
            menu.onSearchClosedListener = {
                previousCloseListener?.invoke()
                menu.animate()
                    .alpha(0f)
                    .setDuration(100L)
                    .withEndAction { menu.visibility = View.GONE }
                    .start()
            }
            menu.tag = COMPACT_SEARCH_TAG
        }
        menu.visibility = View.VISIBLE
        menu.alpha = 0f
        menu.animate().alpha(1f).setDuration(120L).start()
        menu.post { menu.focusView() }
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
            background = pillBackground(false, primary)
            isClickable = true
            isFocusable = true
            setOnClickListener { showCreateCategoryDialog() }
        }
    }

    private fun pillBackground(selected: Boolean, primary: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        if (selected) {
            setColor(primary)
        } else {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), withAlpha(context.getProperTextColor(), 54))
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
                        post {
                            refreshTabs()
                            rootView.findViewById<CategoryPagerView>(R.id.category_pager)?.showAll()
                        }
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
                        post { refreshTabs() }
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
                    if (selectedCategoryId == category.id) selectedCategoryId = null
                    post {
                        refreshTabs()
                        rootView.findViewById<CategoryPagerView>(R.id.category_pager)?.showAll()
                    }
                }
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val COMPACT_SEARCH_TAG = "sama_compact_search"
    }
}
