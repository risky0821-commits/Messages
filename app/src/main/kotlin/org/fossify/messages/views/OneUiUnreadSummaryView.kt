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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt
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
    private var recyclerAttached = false
    private var expandedHeight = 0
    private var expandedTopMargin = 0
    private var collapseOffset = 0f

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
            addView(
                categoriesRow,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
            )
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
        if (!registeredToBus) {
            EventBus.getDefault().register(this)
            registeredToBus = true
        }
        post {
            captureExpandedSize()
            attachRecyclerScroll()
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

    // Kept for compatibility with CategoryTabsView; the summary now always shows every category.
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
                    val unread = unreadCounts[category.id] ?: 0
                    categoriesRow.addView(
                        makeCategoryChip(
                            title = category.name,
                            unreadCount = unread,
                            categoryId = category.id,
                        )
                    )
                }
                categoriesScroll.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun makeCategoryChip(title: String, unreadCount: Int, categoryId: Long): TextView {
        val primary = context.getProperPrimaryColor()
        val textColor = context.getProperTextColor()
        val label = "$title  $unreadCount"

        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textColor)
            minHeight = dp(38)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(19).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), withAlpha(primary, 150))
            }
            isClickable = true
            isFocusable = true
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

    private fun captureExpandedSize() {
        if (expandedHeight != 0 || height <= 0) return
        expandedHeight = height
        expandedTopMargin = (layoutParams as? RelativeLayout.LayoutParams)?.topMargin ?: 0
        applyCollapseProgress()
    }

    private fun attachRecyclerScroll() {
        if (recyclerAttached) return
        val recycler = rootView.findViewById<RecyclerView>(R.id.conversations_list) ?: return
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (expandedHeight <= 0) {
                    captureExpandedSize()
                    return
                }

                if (!recyclerView.canScrollVertically(-1) && dy <= 0) {
                    collapseOffset = 0f
                } else {
                    collapseOffset = (collapseOffset + dy).coerceIn(0f, collapseDistance())
                }
                applyCollapseProgress()
            }
        })
        recyclerAttached = true
    }

    private fun collapseDistance(): Float = (expandedHeight + expandedTopMargin).coerceAtLeast(1).toFloat()

    private fun applyCollapseProgress() {
        if (expandedHeight <= 0) return
        val rawProgress = (collapseOffset / collapseDistance()).coerceIn(0f, 1f)
        val progress = rawProgress * rawProgress * (3f - 2f * rawProgress)
        val params = layoutParams as? RelativeLayout.LayoutParams ?: return

        val newHeight = (expandedHeight * (1f - progress)).roundToInt().coerceAtLeast(0)
        val newTopMargin = (expandedTopMargin * (1f - progress)).roundToInt().coerceAtLeast(0)
        if (params.height != newHeight || params.topMargin != newTopMargin) {
            params.height = newHeight
            params.topMargin = newTopMargin
            layoutParams = params
        }

        pivotX = width / 2f
        pivotY = height / 2f
        alpha = (1f - progress * 1.08f).coerceIn(0f, 1f)
        translationY = -dp(24) * progress
        scaleX = 1f - (0.16f * progress)
        scaleY = 1f - (0.16f * progress)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
