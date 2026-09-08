package org.fossify.messages.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.getCategoryUnreadCounts
import org.fossify.messages.extensions.getTotalUnreadCount
import org.fossify.messages.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class OneUiUnreadSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialCardView(context, attrs) {

    private val summaryText = TextView(context)
    private val categoriesRow = LinearLayout(context)
    private val categoriesScroll = HorizontalScrollView(context)
    private val actionText = TextView(context)
    private var registeredToBus = false
    private var collapseOffset = 0f
    private var expandedHeight = 0
    private var expandedTopMargin = 0
    private var collapsedLayoutCommitted = false

    init {
        radius = dp(32).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.DKGRAY)
        )
        setContentPadding(dp(24), dp(30), dp(24), dp(28))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        summaryText.apply {
            gravity = Gravity.CENTER
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
            )
        }

        categoriesRow.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), 0)
        }

        categoriesScroll.apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(categoriesRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        actionText.apply {
            text = context.getString(R.string.view_unread_messages)
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(26), dp(11), dp(26), dp(11))
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(23).toFloat()
                setColor(
                    MaterialColors.getColor(
                        this@OneUiUnreadSummaryView,
                        com.google.android.material.R.attr.colorSurfaceVariant,
                        Color.GRAY,
                    )
                )
            }
            setOnClickListener { openUnreadFilter() }
        }

        content.addView(
            summaryText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(
            categoriesScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply { topMargin = dp(18) },
        )
        content.addView(
            actionText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) },
        )
        addView(content)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        captureExpandedLayout()
        if (!registeredToBus) {
            EventBus.getDefault().register(this)
            registeredToBus = true
        }
        refreshSummary()
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
        refreshSummary()
    }

    fun setCategorySummary(categoryId: Long?, title: String?) {
        refreshSummary()
    }

    fun refreshSummary() {
        ensureBackgroundThread {
            val totalUnread = context.getTotalUnreadCount()
            val categories = context.categoriesDB.getCategories()
            val unreadCounts = context.getCategoryUnreadCounts()

            post {
                summaryText.text = context.getString(R.string.unread_messages_summary, totalUnread)
                actionText.visibility = if (totalUnread > 0) View.VISIBLE else View.GONE
                categoriesRow.removeAllViews()
                categories.forEach { category ->
                    categoriesRow.addView(
                        makeCategoryChip(
                            title = category.name,
                            unreadCount = unreadCounts[category.id] ?: 0,
                            categoryId = category.id,
                        )
                    )
                }
                categoriesScroll.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    fun onConversationScrolled(dy: Int, canScrollUp: Boolean) {
        captureExpandedLayout()
        val inboxCard = rootView.findViewById<View>(R.id.inbox_card) ?: return
        val distance = collapseDistance()

        if (collapsedLayoutCommitted) {
            if (!canScrollUp && dy < 0) {
                restoreExpandedLayoutForAnimation(inboxCard, distance)
            } else {
                return
            }
        }

        collapseOffset = if (!canScrollUp && dy <= 0) {
            (collapseOffset + dy).coerceAtLeast(0f)
        } else {
            (collapseOffset + dy).coerceIn(0f, distance)
        }

        applyCollapseProgress(inboxCard, distance)

        if (!collapsedLayoutCommitted && collapseOffset >= distance - 0.5f) {
            commitCollapsedLayout(inboxCard)
        }
    }

    private fun captureExpandedLayout() {
        if (expandedHeight > 0) return
        val params = layoutParams as? RelativeLayout.LayoutParams ?: return
        if (params.height > 0) {
            expandedHeight = params.height
            expandedTopMargin = params.topMargin
        }
    }

    private fun restoreExpandedLayoutForAnimation(inboxCard: View, distance: Float) {
        val params = layoutParams as? RelativeLayout.LayoutParams ?: return
        inboxCard.translationY = -distance
        params.height = expandedHeight
        params.topMargin = expandedTopMargin
        layoutParams = params
        collapsedLayoutCommitted = false
        collapseOffset = distance
        alpha = 0f
        scaleX = 0.82f
        scaleY = 0.82f
        translationY = -dp(36).toFloat()
    }

    private fun commitCollapsedLayout(inboxCard: View) {
        val params = layoutParams as? RelativeLayout.LayoutParams ?: return
        params.height = 0
        params.topMargin = 0
        layoutParams = params
        inboxCard.translationY = 0f
        collapseOffset = collapseDistance()
        collapsedLayoutCommitted = true
        alpha = 0f
        scaleX = 0.82f
        scaleY = 0.82f
        translationY = 0f
    }

    private fun makeCategoryChip(title: String, unreadCount: Int, categoryId: Long): TextView {
        val primary = context.getProperPrimaryColor()
        return TextView(context).apply {
            text = "$title  $unreadCount"
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getProperTextColor())
            minHeight = dp(38)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(19).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), withAlpha(primary, 150))
            }
            setOnClickListener {
                rootView.findViewById<CategoryTabsView>(R.id.category_tabs)?.selectCategory(categoryId)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(8) }
        }
    }

    private fun openUnreadFilter() {
        rootView.findViewById<CategoryTabsView>(R.id.category_tabs)?.showUnreadOnlyConversations()
    }

    private fun collapseDistance(): Float {
        captureExpandedLayout()
        return (expandedHeight + expandedTopMargin).coerceAtLeast(dp(250)).toFloat()
    }

    private fun applyCollapseProgress(inboxCard: View, distance: Float) {
        val raw = (collapseOffset / distance).coerceIn(0f, 1f)
        val progress = raw * raw * (3f - 2f * raw)

        pivotX = width / 2f
        pivotY = height / 2f
        alpha = (1f - progress * 1.05f).coerceIn(0f, 1f)
        translationY = -dp(36) * progress
        scaleX = 1f - (0.18f * progress)
        scaleY = 1f - (0.18f * progress)

        inboxCard.translationY = -(distance * progress)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
