package org.fossify.messages.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
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

class OneUiUnreadSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialCardView(context, attrs) {

    private val summaryText = TextView(context)
    private val actionText = TextView(context)
    private var registeredToBus = false
    private var recyclerAttached = false
    private var collapsed = false

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
        post { attachRecyclerScroll() }
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
        val categoryTabs = rootView.findViewById<CategoryTabsView>(R.id.category_tabs) ?: return
        val tabsContainer = categoryTabs.getChildAt(0) as? android.view.ViewGroup ?: return
        tabsContainer.getChildAt(1)?.performClick()
    }

    private fun attachRecyclerScroll() {
        if (recyclerAttached) return
        val recycler = rootView.findViewById<RecyclerView>(R.id.conversations_list) ?: return
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                when {
                    dy > 3 && !collapsed -> collapse()
                    !recyclerView.canScrollVertically(-1) && collapsed -> expand()
                }
            }
        })
        recyclerAttached = true
    }

    private fun collapse() {
        if (collapsed) return
        collapsed = true
        animate().cancel()
        animate()
            .alpha(0f)
            .translationY(-height * 0.35f)
            .setDuration(180L)
            .withEndAction {
                visibility = View.GONE
                translationY = 0f
            }
            .start()
    }

    private fun expand() {
        if (!collapsed) return
        collapsed = false
        animate().cancel()
        visibility = View.VISIBLE
        alpha = 0f
        translationY = -dp(24).toFloat()
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
