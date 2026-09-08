package org.fossify.messages.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.views.MySearchMenu
import org.fossify.messages.R

class OneUiUnreadSummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val titleView = TextView(context)
    private val actions = LinearLayout(context)
    private var collapseOffset = 0f

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false

        val holder = RelativeLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        titleView.apply {
            text = context.getString(R.string.messages_title)
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_RTL
            setTextColor(context.getProperTextColor())
            includeFontPadding = false
        }

        actions.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(makeOverflowButton())
            addView(makeSearchButton())
        }

        holder.addView(
            titleView,
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                dp(58),
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                topMargin = dp(10)
                rightMargin = dp(8)
            },
        )

        holder.addView(
            actions,
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                dp(58),
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                topMargin = dp(10)
                leftMargin = dp(2)
            },
        )

        addView(holder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        post { applyCollapseProgress() }
    }

    fun refreshSummary() = Unit

    fun setCategorySummary(categoryId: Long?, title: String?) = Unit

    fun onConversationScrolled(dy: Int, canScrollUp: Boolean) {
        val distance = collapseDistance()
        collapseOffset = if (!canScrollUp && dy <= 0) {
            (collapseOffset + dy).coerceAtLeast(0f)
        } else {
            (collapseOffset + dy).coerceIn(0f, distance)
        }
        applyCollapseProgress()
    }

    private fun makeSearchButton(): ImageView {
        return ImageView(context).apply {
            setImageResource(org.fossify.commons.R.drawable.ic_search_vector)
            setColorFilter(context.getProperTextColor())
            contentDescription = context.getString(org.fossify.commons.R.string.search)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            setOnClickListener { openCompactSearch() }
        }
    }

    private fun makeOverflowButton(): TextView {
        return TextView(context).apply {
            text = "⋮"
            gravity = Gravity.CENTER
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getProperTextColor())
            contentDescription = context.getString(R.string.more_options)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            setOnClickListener {
                val menu = rootView.findViewById<MySearchMenu>(R.id.main_menu) ?: return@setOnClickListener
                menu.visibility = View.INVISIBLE
                menu.requireToolbar().showOverflowMenu()
            }
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

    private fun collapseDistance(): Float = dp(150).toFloat()

    private fun applyCollapseProgress() {
        val inbox = rootView.findViewById<View>(R.id.inbox_card) ?: return
        val raw = (collapseOffset / collapseDistance()).coerceIn(0f, 1f)
        val progress = raw * raw * (3f - 2f * raw)

        // Keep the right edge of the Arabic title fixed while scaling, matching Samsung's header.
        titleView.pivotX = titleView.width.toFloat()
        titleView.pivotY = titleView.height / 2f
        titleView.translationY = dp(70) * (1f - progress)
        titleView.scaleX = 1f + (0.20f * (1f - progress))
        titleView.scaleY = 1f + (0.20f * (1f - progress))

        actions.translationY = dp(78) * (1f - progress)

        // Tabs/list rise smoothly, while the compact header remains fixed below the status bar.
        val expandedTop = dp(194).toFloat()
        val compactTop = dp(76).toFloat()
        inbox.translationY = expandedTop - ((expandedTop - compactTop) * progress)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val COMPACT_SEARCH_TAG = "sama_compact_search"
    }
}
