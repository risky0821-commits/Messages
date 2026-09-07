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
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.getTotalUnreadCount
import org.fossify.messages.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.roundToInt

class OneUiUnreadSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialCardView(context, attrs) {

    private val summaryText = TextView(context)
    private val actionText = TextView(context)
    private var registeredToBus = false
    private var recyclerAttached = false
    private var expandedHeight = 0
    private var expandedTopMargin = 0
    private var collapseOffset = 0f

    init {
        radius = dp(30).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.DKGRAY)
        )
        setContentPadding(dp(22), dp(20), dp(22), dp(18))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        summaryText.apply {
            gravity = Gravity.CENTER
            textSize = 22f
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
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setTextColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
            )
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
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
            actionText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) },
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

    private fun refreshUnreadCount() {
        ensureBackgroundThread {
            val unread = context.getTotalUnreadCount()
            post {
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
        val progress = (collapseOffset / collapseDistance()).coerceIn(0f, 1f)
        val params = layoutParams as? RelativeLayout.LayoutParams ?: return

        val newHeight = (expandedHeight * (1f - progress)).roundToInt().coerceAtLeast(0)
        val newTopMargin = (expandedTopMargin * (1f - progress)).roundToInt().coerceAtLeast(0)
        if (params.height != newHeight || params.topMargin != newTopMargin) {
            params.height = newHeight
            params.topMargin = newTopMargin
            layoutParams = params
        }

        alpha = (1f - progress * 1.15f).coerceIn(0f, 1f)
        translationY = -dp(12) * progress
        scaleX = 1f - (0.025f * progress)
        scaleY = 1f - (0.025f * progress)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
