package org.fossify.messages.views

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class CategoryPagerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private data class Page(
        val categoryId: Long?,
        val conversations: List<Conversation>,
    )

    private val pager = ViewPager2(context)
    private val pageAdapter = PagesAdapter()
    private var pages: List<Page> = emptyList()
    private var registeredToBus = false
    private var unreadOnly = false
    private var requestedCategoryId: Long? = null

    init {
        pager.id = View.generateViewId()
        pager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        pager.offscreenPageLimit = 1
        pager.adapter = pageAdapter
        pager.setPageTransformer { page, position ->
            page.alpha = 1f - (kotlin.math.abs(position) * 0.035f).coerceAtMost(0.035f)
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position !in pages.indices) return
                unreadOnly = false
                requestedCategoryId = pages[position].categoryId
                rootView.findViewById<CategoryTabsView>(R.id.category_tabs)
                    ?.onPagerCategorySelected(requestedCategoryId)
            }
        })
        addView(
            pager,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!registeredToBus) {
            EventBus.getDefault().register(this)
            registeredToBus = true
        }
        refreshPages()
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
        refreshPages()
    }

    fun refreshNow() {
        refreshPages()
    }

    fun selectCategory(categoryId: Long?, smooth: Boolean = true) {
        unreadOnly = false
        requestedCategoryId = categoryId
        val index = pages.indexOfFirst { it.categoryId == categoryId }
        if (index >= 0 && pager.currentItem != index) {
            pager.setCurrentItem(index, smooth)
        }
    }

    fun showUnreadOnly() {
        unreadOnly = true
        requestedCategoryId = null
        refreshPages()
    }

    fun showAll() {
        unreadOnly = false
        requestedCategoryId = null
        refreshPages()
    }

    fun getCurrentCategoryId(): Long? = requestedCategoryId

    private fun refreshPages() {
        ensureBackgroundThread {
            val categories = context.categoriesDB.getCategories()
            val all = context.conversationsDB.getNonArchived().sortedWith(conversationComparator())
            val mappings = categories.associate { category ->
                category.id to context.categoriesDB.getThreadIdsForCategory(category.id).toHashSet()
            }
            val built = ArrayList<Page>()
            val firstPage = if (unreadOnly) all.filter { !it.read } else all
            built.add(Page(null, firstPage))
            categories.forEach { category ->
                val ids = mappings[category.id].orEmpty()
                built.add(Page(category.id, all.filter { it.threadId in ids }))
            }

            post {
                pages = built
                pageAdapter.notifyDataSetChanged()
                val target = pages.indexOfFirst { it.categoryId == requestedCategoryId }
                    .let { if (it >= 0) it else 0 }
                if (pager.currentItem != target) {
                    pager.setCurrentItem(target, false)
                }
            }
        }
    }

    private fun conversationComparator(): Comparator<Conversation> {
        return compareByDescending<Conversation> {
            context.config.pinnedConversations.contains(it.threadId.toString())
        }.thenByDescending { it.date }
    }

    private inner class PagesAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun getItemCount(): Int = pages.size

        override fun getItemId(position: Int): Long {
            return pages.getOrNull(position)?.categoryId ?: Long.MIN_VALUE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val recycler = MyRecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                layoutManager = LinearLayoutManager(parent.context)
                clipToPadding = false
                overScrollMode = OVER_SCROLL_NEVER
                itemAnimator = null
                setPadding(0, dp(2), 0, dp(20))
            }
            recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    rootView.findViewById<OneUiUnreadSummaryView>(R.id.unread_summary_card)
                        ?.onConversationScrolled(dy, recyclerView.canScrollVertically(-1))
                }
            })
            return PageHolder(recycler)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.bind(pages[position])
        }
    }

    private inner class PageHolder(
        private val recycler: MyRecyclerView,
    ) : RecyclerView.ViewHolder(recycler) {
        private var adapter: ConversationsAdapter? = null

        fun bind(page: Page) {
            val activity = context as? SimpleActivity ?: return
            val currentAdapter = adapter ?: ConversationsAdapter(
                activity = activity,
                recyclerView = recycler,
                onRefresh = { refreshPages() },
                itemClick = { item ->
                    val conversation = item as Conversation
                    context.startActivity(
                        Intent(context, ThreadActivity::class.java).apply {
                            putExtra(THREAD_ID, conversation.threadId)
                            putExtra(THREAD_TITLE, conversation.title)
                        }
                    )
                },
            ).also {
                recycler.adapter = it
                adapter = it
            }
            currentAdapter.updateConversations(ArrayList(page.conversations))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
