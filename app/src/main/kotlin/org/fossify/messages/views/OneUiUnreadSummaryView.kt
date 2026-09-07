package org.fossify.messages.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.getTotalUnreadCount
import org.fossify.messages.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class OneUiUnreadSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialCardView(context, attrs) {

    private val categoryTitleText = TextView(context)
    private val summaryText = TextView(context)
    private val actionText = TextView(context)
    private var activeCategoryId: Long? = null
    private var activeCategoryTitle: String? = null
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

        categoryTitleText.apply {
            gravity = Gravity.CENTER
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            visibility = View.GONE
            alpha = 0.82f
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
            )
        }

        summaryText.apply {
            gravity = Gravity.CENTER
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
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
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
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
            categoryTitleText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) },
        )
        content.addView(
            summaryText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(
            actionText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) },
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
        refreshUnreadCount()
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
        refreshUnreadCount()
    }

    fun setCategorySummary(categoryId: Long?, title: String?) {
        activeCategoryId = categoryId
        activeCategoryTitle = title
        refreshUnreadCount()
    }

    private fun refreshUnreadCount() {
        ensureBackgroundThread {
            val categoryId = activeCategoryId
            val unread = if (categoryId == null) {
                context.getTotalUnreadCount()
            } else {
                context.categoriesDB.getUnreadCount(categoryId)
            }
            val title = activeCategoryTitle

            post {
                categoryTitleText.visibility = if (title.isNullOrBlank()) View.GONE else View.VISIBLE
                categoryTitleText.text = title.orEmpty()
                summaryText.text = context.getString(R.string.unread_messages_summary, unread)
                actionText.visibility = if (unread > 0) View.VISIBLE else View.GONE
            }
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
