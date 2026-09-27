package com.basket.ui.categories

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.basket.R
import com.basket.databinding.ItemCategoryBinding
import com.basket.databinding.ItemCategoryFooterBinding
import com.basket.ui.common.displayName
import com.basket.ui.common.emoji
import com.google.android.material.color.MaterialColors

/**
 * The reorderable category rows. Keeps its own copy of the rows so a drag can move them one step at a time;
 * new data from the database is applied with DiffUtil.
 */
class CategoryAdapter(
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onMenu: (anchor: View, position: Int) -> Unit,
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private val rows = mutableListOf<CategoryRow>()

    val currentRows: List<CategoryRow> get() = rows

    /** Rows that can be dragged or moved: every category except "Other". */
    val movableCount: Int get() = rows.count { !it.category.isOther }

    fun submit(newRows: List<CategoryRow>) {
        val result = DiffUtil.calculateDiff(RowDiff(rows.toList(), newRows))
        rows.clear()
        rows.addAll(newRows)
        result.dispatchUpdatesTo(this)
    }

    fun rowAt(position: Int): CategoryRow? = rows.getOrNull(position)

    fun canMove(position: Int): Boolean = rows.getOrNull(position)?.category?.isOther == false

    fun move(from: Int, to: Int) {
        if (from == to || !canMove(from) || !canMove(to)) return
        rows.add(to, rows.removeAt(from))
        notifyItemMoved(from, to)
    }

    override fun getItemCount(): Int = rows.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = CategoryViewHolder(binding)
        binding.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && canMove(holder.bindingAdapterPosition)) {
                onStartDrag(holder)
            }
            false
        }
        binding.overflow.setOnClickListener { anchor ->
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onMenu(anchor, position)
        }
        return holder
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    class CategoryViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {

        private var restingBackground: Drawable? = null

        fun bind(row: CategoryRow) {
            val context = binding.root.context
            val name = row.category.displayName(context)
            val locked = row.category.isOther
            val emoji = row.category.emoji
            binding.name.text = name
            binding.emoji.text = emoji.orEmpty()
            binding.emoji.visibility = if (emoji == null) View.INVISIBLE else View.VISIBLE
            binding.itemCount.text = if (row.itemCount == 0) {
                context.getString(R.string.category_no_items)
            } else {
                context.resources.getQuantityString(R.plurals.category_item_count, row.itemCount, row.itemCount)
            }
            binding.dragHandle.isVisible = !locked
            binding.dragHandle.contentDescription = context.getString(R.string.cd_reorder_category, name)
            binding.lockIcon.isVisible = locked
            binding.overflow.isVisible = !locked
        }

        /** Lifts the row while it is dragged. */
        fun setLifted(lifted: Boolean) {
            val view = binding.root
            if (lifted) {
                restingBackground = view.background
                view.background = ColorDrawable(
                    MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh),
                )
                view.translationZ = view.resources.displayMetrics.density * LIFT_DP
            } else {
                restingBackground?.let { view.background = it }
                restingBackground = null
                view.translationZ = 0f
            }
        }
    }

    private class RowDiff(
        private val old: List<CategoryRow>,
        private val new: List<CategoryRow>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
            old[oldPosition].category.id == new[newPosition].category.id
        override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = old[oldPosition] == new[newPosition]
    }

    private companion object {
        const val LIFT_DP = 6f
    }
}

/** "Add category" under the list. */
class AddCategoryFooterAdapter(
    private val onAdd: () -> Unit,
) : RecyclerView.Adapter<AddCategoryFooterAdapter.FooterViewHolder>() {

    var visible: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) notifyItemInserted(0) else notifyItemRemoved(0)
        }

    override fun getItemCount(): Int = if (visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FooterViewHolder {
        val binding = ItemCategoryFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.addCategoryButton.setOnClickListener { onAdd() }
        return FooterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FooterViewHolder, position: Int) = Unit

    class FooterViewHolder(binding: ItemCategoryFooterBinding) : RecyclerView.ViewHolder(binding.root)
}
