package org.fossify.messages.dialogs

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.config
import org.fossify.messages.models.Conversation
import java.util.Collections

class ReorderPinnedConversationsDialog(
    private val activity: SimpleActivity,
    conversations: List<Conversation>,
    private val onSaved: () -> Unit,
) {
    private val items = conversations.toMutableList()

    init {
        val recycler = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = PinnedAdapter(items)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from !in items.indices || to !in items.indices) return false
                Collections.swap(items, from, to)
                recycler.adapter?.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        helper.attachToRecyclerView(recycler)

        AlertDialog.Builder(activity)
            .setTitle(R.string.reorder_pinned_conversations)
            .setView(recycler)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                activity.config.setPinnedConversationOrder(items.map { it.threadId })
                onSaved()
            }
            .show()
    }

    private inner class PinnedAdapter(
        private val conversations: List<Conversation>,
    ) : RecyclerView.Adapter<PinnedHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinnedHolder {
            val view = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(56),
                )
                gravity = Gravity.CENTER_VERTICAL
                textSize = 17f
                setPadding(dp(16), 0, dp(16), 0)
            }
            return PinnedHolder(view)
        }

        override fun onBindViewHolder(holder: PinnedHolder, position: Int) {
            holder.label.text = "≡   ${conversations[position].title}"
        }

        override fun getItemCount() = conversations.size
    }

    private class PinnedHolder(val label: TextView) : RecyclerView.ViewHolder(label)

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
