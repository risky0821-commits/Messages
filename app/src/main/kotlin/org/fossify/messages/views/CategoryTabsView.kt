@file:Suppress("MagicNumber", "TooManyFunctions", "UnusedParameter")

package org.fossify.messages.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MySearchMenu
import org.fossify.messages.R
import org.fossify.messages.adapters.BaseConversationsAdapter
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.createMessageCategory
import org.fossify.messages.extensions.getCategoryUnreadCounts
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
        setPadding(dp(14), dp(10), dp(14), dp(8))
    }

    private var selectedCategoryId: Long? = null
    private var showUnreadOnly = false
    private var registeredToBus = false
    private var swipeNavigationAttached = false
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
        post { attachSwipeNavigation() }
        refreshTabsAndList()
    }

    override fun onDetachedFromWindow() {
        if (registeredToBus) {
            EventBus.getDefault().unregister(this)
            registeredToBus = false
        }
        swipeNavigationAttached = false
        super.onDetachedFromWindow()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConversationsChanged(event: Events.RefreshConversations) {
        refreshTabsAndList()
    }

    fun showUnreadOnlyConversations() {
        showUnreadOnly = !showUnreadOnly
        selectedCategoryId = null
        refreshTabsAndList()
    }

    fun showAllConversations() {
        showUnreadOnly = false
        selectedCategoryId = null
        refreshTabsAndList()
    }

    fun selectCategory(categoryId: Long) {
        if (currentCategories.none { it.id == categoryId }) return
        showUnreadOnly = false
        selectedCategoryId = categoryId
        refreshTabsAndList()
    }

    fun refreshTabsAndList() {
        ensureBackgroundThread {
            val categories = context.categoriesDB.getCategories()
            val unread = context.getCategoryUnreadCounts()
            val allConversations = context.conversationsDB.getNonArchived()

            post {
                currentCategories = categories
                attachSwipeNavigation()
                tabs.removeAllViews()

                categories.forEach { category ->
                    val isSelected = !showUnreadOnly && selectedCategoryId == category.id
                    tabs.addView(
                        makeTab(
                            title = category.name,
                            unreadCount = unread[category.id] ?: 0,
                            selected = isSelected,
                            onClick = {
                                showUnreadOnly = false
                                selectedCategoryId = if (isSelected) null else category.id
                                refreshTabsAndList()
                            },
                            onLongClick = { showManageCategoryDialog(category) },
                        )
                    )
                }

                tabs.addView(makeSearchButton())
                tabs.addView(makeOverflowButton())
                tabs.addView(makeAddButton())
                rootView.findViewById<OneUiUnreadSummaryView>(R.id.unread_summary_card)?.refreshSummary()
                applyCurrentSelectionIfPossible(allConversations)
                scrollSelectedTabIntoView()
            }
        }
    }

    private fun attachSwipeNavigation() {
        if (swipeNavigationAttached) return
        val recycler = rootView.findViewById<RecyclerView>(R.id.conversations_list) ?: return
        val contentArea = rootView.findViewById<View>(R.id.conversations_fastscroller) ?: return
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val interpolator = DecelerateInterpolator(1.8f)

        var downX = 0f
        var downY = 0f
        var horizontalDrag = false

        recycler.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    horizontalDrag = false
                    contentArea.animate().cancel()
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (!horizontalDrag && abs(dx) > touchSlop * 1.35f && abs(dx) > abs(dy) * 1.55f) {
                        horizontalDrag = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    if (horizontalDrag) {
                        val maxDrag = recycler.width * 0.55f
                        val damped = dx.coerceIn(-maxDrag, maxDrag) * 0.72f
                        contentArea.translationX = damped
                        contentArea.alpha = 1f - (abs(damped) / maxDrag * 0.10f)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!horizontalDrag) {
                        false
                    } else {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        val dx = event.x - downX
                        val threshold = recycler.width * 0.18f
                        val logicalStep = logicalStepForDx(dx)
                        val canMove = canMove(logicalStep)

                        if (event.actionMasked == MotionEvent.ACTION_UP && abs(dx) >= threshold && canMove) {
                            val direction = if (dx < 0f) -1f else 1f
                            val exitX = direction * recycler.width * 0.34f
                            contentArea.animate()
                                .translationX(exitX)
                                .alpha(0.88f)
                                .setInterpolator(interpolator)
                                .setDuration(130L)
                                .withEndAction {
                                    moveSelection(logicalStep)
                                    contentArea.translationX = -direction * recycler.width * 0.16f
                                    contentArea.alpha = 0.92f
                                    contentArea.animate()
                                        .translationX(0f)
                                        .alpha(1f)
                                        .setInterpolator(interpolator)
                                        .setDuration(170L)
                                        .start()
                                }
                                .start()
                        } else {
                            contentArea.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setInterpolator(interpolator)
                                .setDuration(150L)
                                .start()
                        }
                        horizontalDrag = false
                        true
                    }
                }

                else -> false
            }
        }
        swipeNavigationAttached = true
    }

    private fun logicalStepForDx(dx: Float): Int {
        var step = if (dx < 0f) 1 else -1
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) step *= -1
        return step
    }

    private fun canMove(step: Int): Boolean {
        val totalItems = currentCategories.size + 1
        return currentSelectionIndex() + step in 0 until totalItems
    }

    private fun currentSelectionIndex(): Int {
        if (showUnreadOnly || selectedCategoryId == null) return 0
        val categoryIndex = currentCategories.indexOfFirst { it.id == selectedCategoryId }
        return if (categoryIndex >= 0) categoryIndex + 1 else 0
    }

    private fun moveSelection(step: Int) {
        val totalItems = currentCategories.size + 1
        if (totalItems <= 1) return
        val targetIndex = (currentSelectionIndex() + step).coerceIn(0, totalItems - 1)
        if (targetIndex == currentSelectionIndex()) return

        if (targetIndex == 0) {
            showUnreadOnly = false
            selectedCategoryId = null
        } else {
            showUnreadOnly = false
            selectedCategoryId = currentCategories[targetIndex - 1].id
        }
        refreshTabsAndList()
    }

    private fun scrollSelectedTabIntoView() {
        if (showUnreadOnly) return
        val categoryId = selectedCategoryId ?: return
        val selectedIndex = currentCategories.indexOfFirst { it.id == categoryId }
        if (selectedIndex < 0) return
        val selectedView = tabs.getChildAt(selectedIndex) ?: return
        post { smoothScrollTo((selectedView.left - dp(14)).coerceAtLeast(0), 0) }
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
        if (categoryId == null) applyConversationFilter(allConversations) else applySelectedCategory(categoryId)
    }

    private fun applyConversationFilter(conversations: List<Conversation>) {
        val recycler = rootView.findViewById<RecyclerView>(R.id.conversations_list) ?: return
        val adapter = recycler.adapter as? BaseConversationsAdapter ?: return
        val sorted = conversations.sortedWith(
            compareByDescending<Conversation> {
                context.config.pinnedConversations.contains(it.threadId.toString())
            }.thenByDescending { it.date }
        )
        adapter.updateConversations(ArrayList(sorted))
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
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            minHeight = dp(38)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setTextColor(if (selected) contrastTextColor(primary) else context.getProperTextColor())
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            textSize = 14f
            background = pillBackground(selected, primary)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(8)
            }
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
            contentDescription = context.getString(org.fossify.commons.R.string.more)
            background = pillBackground(false, primary)
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
                menu.animate().alpha(0f).setDuration(100L).withEndAction { menu.visibility = View.GONE }.start()
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
            setOnClickListener { showCreateCategoryDialog() }
        }
    }

    private fun pillBackground(selected: Boolean, primary: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        if (selected) setColor(primary) else {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), withAlpha(context.getProperTextColor(), 54))
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun contrastTextColor(background: Int): Int {
        val luminance = (0.299 * Color.red(background)) + (0.587 * Color.green(background)) + (0.114 * Color.blue(background))
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
                if (name.isNotEmpty()) ensureBackgroundThread {
                    runCatching { context.createMessageCategory(name, context.categoriesDB.getCategories().size) }
                    post { refreshTabsAndList() }
                }
            }
            .show()
    }

    private fun showManageCategoryDialog(category: MessageCategory) {
        val options = arrayOf(context.getString(R.string.rename_category), context.getString(R.string.delete_category))
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
                if (name.isNotEmpty()) ensureBackgroundThread {
                    runCatching { context.categoriesDB.renameCategory(category.id, name) }
                    post { refreshTabsAndList() }
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
                    post { refreshTabsAndList() }
                }
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val COMPACT_SEARCH_TAG = "sama_compact_search"
    }
}
