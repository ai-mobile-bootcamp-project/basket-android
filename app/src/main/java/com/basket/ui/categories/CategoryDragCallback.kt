package com.basket.ui.categories

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Drag to reorder, started from the drag handle only. "Other" cannot be picked up, and nothing can be dropped
 * on or below it. [onDrop] gets the new order when a drag that moved something ends.
 */
class CategoryDragCallback(
    private val adapter: CategoryAdapter,
    private val onDragStateChanged: (dragging: Boolean) -> Unit,
    private val onDrop: (orderedIds: List<Long>) -> Unit,
) : ItemTouchHelper.Callback() {

    private var moved = false

    override fun isLongPressDragEnabled(): Boolean = false

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        if (viewHolder !is CategoryAdapter.CategoryViewHolder) return 0
        if (!adapter.canMove(viewHolder.bindingAdapterPosition)) return 0
        return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = target is CategoryAdapter.CategoryViewHolder && adapter.canMove(target.bindingAdapterPosition)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        if (!adapter.canMove(from) || !adapter.canMove(to)) return false
        adapter.move(from, to)
        moved = true
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder is CategoryAdapter.CategoryViewHolder) {
            moved = false
            viewHolder.setLifted(true)
            onDragStateChanged(true)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder !is CategoryAdapter.CategoryViewHolder) return
        viewHolder.setLifted(false)
        val wasMoved = moved
        moved = false
        if (wasMoved) onDrop(adapter.currentRows.map { it.category.id })
        onDragStateChanged(false)
    }
}
