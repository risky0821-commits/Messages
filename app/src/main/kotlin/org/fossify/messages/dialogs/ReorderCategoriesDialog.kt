package org.fossify.messages.dialogs

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.categoriesDB
import org.fossify.messages.models.MessageCategory
import java.util.Collections

class ReorderCategoriesDialog(
    private val activity: SimpleActivity,
    categories: List<MessageCategory>,
    private val onSaved: () -> Unit,
) {
    private val items = categories.toMutableList()

    init {
        val recycler = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = CategoriesAdapter(items)
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
            .setTitle(R.string.reorder_categories)
            .setView(recycler)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> saveOrder() }
            .show()
    }

    private fun saveOrder() {
        ensureBackgroundThread {
            items.forEachIndexed { index, category ->
                activity.categoriesDB.updateCategorySortOrder(category.id, index)
            }
            activity.runOnUiThread(onSaved)
        }
    }

    private inner class CategoriesAdapter(
        private val categories: List<MessageCategory>,
    ) : RecyclerView.Adapter<CategoryHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryHolder {
            val view = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(56),
                )
                gravity = Gravity.CENTER_VERTICAL
                textSize = 17f
                setPadding(dp(16), 0, dp(16), 0)
                compoundDrawablePadding = dp(12)
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_drag_handle_vector, 0)
            }
            return CategoryHolder(view)
        }

        override fun onBindViewHolder(holder: CategoryHolder, position: Int) {
            holder.label.text = categories[position].name
        }

        override fun getItemCount() = categories.size
    }

    private class CategoryHolder(val label: TextView) : RecyclerView.ViewHolder(label)

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
