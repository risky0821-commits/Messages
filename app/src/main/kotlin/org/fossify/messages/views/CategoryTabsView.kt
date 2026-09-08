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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.createMessageCategory
import org.fossify.messages.extensions.getCategoryUnreadCounts
import org.fossify.messages.extensions.getTotalUnreadCount
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
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        setPadding(dp(10), 0, dp(10), 0)
    }

    private var selectedCategoryId: Long? = null
    private var registeredToBus = false
    private var currentCategories: List<MessageCategory> = emptyList()

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFillViewport = false
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
            val unreadCounts = context.getCategoryUnreadCounts()
            val totalUnreadCount = context.getTotalUnreadCount()
            post {
                currentCategories = categories
                tabs.removeAllViews()

                tabs.addView(
                    makeTab(
                        title = context.getString(R.string.category_all),
                        unreadCount = totalUnreadCount,
                        selected = selectedCategoryId == null,
                        onClick = {
                            selectedCategoryId = null
                            rootView.findViewById<CategoryPagerView>(R.id.category_pager)?.selectCategory(null)
                            refreshTabs()
                        },
                    )
                )

                categories.forEach { category ->
                    tabs.addView(
                        makeTab(
                            title = category.name,
                            unreadCount = unreadCounts[category.id] ?: 0,
                            selected = selectedCategoryId == category.id,
                            onClick = {
                                selectedCategoryId = category.id
                                rootView.findViewById<CategoryPagerView>(R.id.category_pager)
                                    ?.selectCategory(category.id)
                                refreshTabs()
                            },
                            onLongClick = { showManageCategoryDialog(category) },
                        )
                    )
                }

                tabs.addView(makeAddButton())
                scrollSelectedIntoView()
            }
        }
    }

    private fun makeTab(
        title: String,
        unreadCount: Int,
        selected: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
    ): View {
        val textColor = context.getProperTextColor()
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(6))
        }

        val label = TextView(context).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (selected) textColor else withAlpha(textColor, 140))
            includeFontPadding = false
        }
        row.addView(label)

        if (unreadCount > 0) {
            row.addView(
                TextView(context).apply {
                    text = if (unreadCount > 99) "99+" else unreadCount.toString()
                    gravity = Gravity.CENTER
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    minWidth = dp(20)
                    minHeight = dp(20)
                    setPadding(dp(5), 0, dp(5), 0)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(10).toFloat()
                        setColor(context.getProperPrimaryColor())
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(20),
                    ).apply {
                        marginStart = dp(6)
                    }
                }
            )
        }

        val underline = View(context).apply {
            setBackgroundColor(if (selected) textColor else Color.TRANSPARENT)
        }

        holder.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0,
                1f,
            ),
        )
        holder.addView(
            underline,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3),
            ).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            },
        )
        return holder
    }

    private fun makeAddButton(): TextView {
        val textColor = context.getProperTextColor()
        return TextView(context).apply {
            text = "+"
            gravity = Gravity.CENTER
            textSize = 28f
            setTextColor(withAlpha(textColor, 150))
            isClickable = true
            isFocusable = true
            setOnClickListener { showCreateCategoryDialog() }
            layoutParams = LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun scrollSelectedIntoView() {
        val index = if (selectedCategoryId == null) {
            0
        } else {
            currentCategories.indexOfFirst { it.id == selectedCategoryId }.let { if (it >= 0) it + 1 else 0 }
        }
        val selectedView = tabs.getChildAt(index) ?: return
        post {
            val center = selectedView.left + selectedView.width / 2 - width / 2
            smoothScrollTo(center.coerceAtLeast(0), 0)
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

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
