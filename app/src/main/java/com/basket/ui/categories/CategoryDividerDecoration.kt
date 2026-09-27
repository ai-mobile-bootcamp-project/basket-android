package com.basket.ui.categories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

/** Outline-variant line under every category row, inset 16 dp on both sides. The "Add category" footer gets none. */
class CategoryDividerDecoration(
    context: Context,
    private val categoryAdapter: CategoryAdapter,
) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val thickness = (density * THICKNESS_DP).roundToInt().coerceAtLeast(1)
    private val inset = density * INSET_DP
    private val paint = Paint().apply {
        color = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorOutlineVariant,
            CategoryDividerDecoration::class.java.simpleName,
        )
        style = Paint.Style.FILL
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.set(0, 0, 0, if (isCategoryRow(parent, view)) thickness else 0)
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft + inset
        val right = parent.width - parent.paddingRight - inset
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (!isCategoryRow(parent, child)) continue
            val top = child.bottom + child.translationY
            canvas.drawRect(left, top, right, top + thickness, paint)
        }
    }

    private fun isCategoryRow(parent: RecyclerView, view: View): Boolean {
        val holder = parent.getChildViewHolder(view) ?: return false
        return holder.bindingAdapter === categoryAdapter
    }

    private companion object {
        const val THICKNESS_DP = 1f
        const val INSET_DP = 16f
    }
}
