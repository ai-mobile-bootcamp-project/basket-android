package com.basket.ui.item

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import com.basket.R
import kotlin.math.roundToInt

/** A row of the Category dropdown. [toString] is what the field shows once the row is picked. */
data class CategoryOption(val id: Long, val emoji: String?, val name: String) {
    override fun toString(): String = name
}

/**
 * Category dropdown rows: the aisle emoji (decorative, hidden from TalkBack) and the name. The list is never filtered,
 * so every category stays visible whatever the field shows.
 */
class CategoryOptionAdapter(
    context: Context,
    private val inflater: LayoutInflater,
) : ArrayAdapter<CategoryOption>(context, R.layout.item_category_option) {

    private var options: List<CategoryOption> = emptyList()

    fun submit(newOptions: List<CategoryOption>) {
        options = newOptions
        setNotifyOnChange(false)
        clear()
        addAll(newOptions)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_category_option, parent, false)
        val option = getItem(position) ?: return view
        view.findViewById<TextView>(R.id.optionEmoji).apply {
            text = option.emoji
            isVisible = option.emoji != null
        }
        view.findViewById<TextView>(R.id.optionName).text = option.name
        return view
    }

    override fun getFilter(): Filter = allOptions

    private val allOptions = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults = FilterResults().apply {
            val current = options
            values = current
            count = current.size
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) = notifyDataSetChanged()

        override fun convertResultToString(resultValue: Any?): CharSequence =
            (resultValue as? CategoryOption)?.name.orEmpty()
    }
}

/**
 * The category's emoji drawn as the field's start icon, with the system emoji font (through an AppCompat text view,
 * so EmojiCompat applies). The start icon is not clickable, so TalkBack skips it and reads only the category name.
 * Tinting is ignored: the emoji keeps its own colours.
 */
class EmojiDrawable(context: Context, emoji: String) : Drawable() {

    private val size = (ICON_DP * context.resources.displayMetrics.density).roundToInt()

    private val label = AppCompatTextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, EMOJI_DP)
        includeFontPadding = false
        gravity = Gravity.CENTER
        text = emoji
        val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        measure(spec, spec)
        layout(0, 0, size, size)
    }

    private var drawAlpha = 255

    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.saveLayerAlpha(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            drawAlpha,
        )
        canvas.translate(bounds.left + (bounds.width() - size) / 2f, bounds.top + (bounds.height() - size) / 2f)
        label.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    override fun getIntrinsicWidth(): Int = size

    override fun getIntrinsicHeight(): Int = size

    override fun setAlpha(alpha: Int) {
        if (alpha != drawAlpha) {
            drawAlpha = alpha
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = drawAlpha

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    override fun setTintList(tint: ColorStateList?) = Unit

    override fun setTintMode(tintMode: PorterDuff.Mode?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val ICON_DP = 24
        const val EMOJI_DP = 18f
    }
}
