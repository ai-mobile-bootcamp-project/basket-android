package com.basket.ui.product

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.children
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.basket.R
import com.basket.data.MessageCenter
import com.basket.data.UserMessage
import com.basket.data.catalog.CatalogProduct
import com.basket.databinding.FragmentProductDetailBinding
import com.basket.domain.Availability
import com.basket.domain.Catalog
import com.basket.domain.Locales
import com.basket.ui.common.appLocale
import com.basket.ui.common.emoji
import com.basket.ui.common.formatMoney
import com.basket.ui.common.labelRes
import com.basket.ui.navigation.BasketNavigator
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import javax.inject.Inject
import kotlin.math.roundToInt

/** Screen 5 — Product detail: one catalog product in full, added to the open list with a quantity. */
@AndroidEntryPoint
class ProductDetailFragment : Fragment() {

    @Inject lateinit var messageCenter: MessageCenter

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProductDetailViewModel by viewModels()
    private val navigator: BasketNavigator get() = requireActivity() as BasketNavigator

    private var imageUrl: String? = null
    private var savingIcon: Drawable? = null
    private var description: DescriptionText? = null
    private var descriptionSource: Pair<String, Boolean>? = null

    /** What the description view shows, so it is only rebuilt when the text, the state or the width changes. */
    private data class DescriptionText(val text: String, val expanded: Boolean, val width: Int, val toggle: String?)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savingIcon = IndeterminateDrawable.createCircularDrawable(
            requireContext(),
            CircularProgressIndicatorSpec(
                requireContext(),
                null,
                0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall,
            ).apply {
                indicatorColors = intArrayOf(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface))
            },
        )

        // The image runs under the status bar; the back button sits just below it.
        view.doOnAttach { attached ->
            val density = attached.resources.displayMetrics.density
            val statusBar = ViewCompat.getRootWindowInsets(attached)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top
                ?: (FALLBACK_STATUS_BAR_DP * density).roundToInt()
            _binding?.backButton?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBar + (BACK_BUTTON_GAP_DP * density).roundToInt()
            }
        }

        val stepperTint = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(
                ColorUtils.setAlphaComponent(
                    MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface),
                    (DISABLED_ALPHA * 255).roundToInt(),
                ),
                MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary),
            ),
        )
        ImageViewCompat.setImageTintList(binding.decreaseButton, stepperTint)
        ImageViewCompat.setImageTintList(binding.increaseButton, stepperTint)

        binding.backButton.setOnClickListener { navigator.navigateBack() }
        binding.retryButton.setOnClickListener { viewModel.retry() }
        binding.description.setOnClickListener { viewModel.toggleDescription() }
        binding.description.isClickable = false
        binding.description.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) updateDescription()
        }
        ViewCompat.setAccessibilityDelegate(binding.description, DescriptionAccessibility())
        binding.decreaseButton.setOnClickListener { viewModel.step(-1) }
        binding.increaseButton.setOnClickListener { viewModel.step(1) }
        binding.addButton.setOnClickListener { viewModel.addToList() }
        binding.originalPrice.paintFlags = binding.originalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    private fun render(state: ProductDetailUiState) {
        val product = state.product
        binding.loading.isVisible = product == null && state.status == ProductStatus.LOADING
        binding.errorState.isVisible = product == null &&
            (state.status == ProductStatus.OFFLINE || state.status == ProductStatus.SERVER_ERROR)
        binding.errorMessage.setText(
            if (state.status == ProductStatus.OFFLINE) R.string.error_offline_message else R.string.error_server_message,
        )
        binding.content.isVisible = product != null
        binding.bottomBar.isVisible = product != null
        binding.bottomDivider.isVisible = product != null
        if (product == null) return

        renderProduct(product, state)
        renderBottomBar(state)
    }

    private fun renderProduct(product: CatalogProduct, state: ProductDetailUiState) {
        val context = requireContext()

        if (product.imageUrl != imageUrl || binding.image.drawable == null) {
            imageUrl = product.imageUrl
            binding.image.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.image.load(product.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.product_image_placeholder)
                error(R.drawable.product_image_placeholder)
                listener(onSuccess = { _, _ -> _binding?.image?.scaleType = ImageView.ScaleType.FIT_CENTER })
            }
        }
        binding.image.contentDescription = product.title

        binding.name.text = product.title

        // Category tag: the emoji is decorative (not important for accessibility), TalkBack reads the name.
        binding.categoryEmoji.text = product.categoryKey.emoji
        binding.category.setText(product.categoryKey.labelRes)

        // Price block: the price you pay, and the original struck through only when there is a visible discount.
        binding.price.text = context.formatMoney(product.priceYouPayCents)
        val badge = product.discountBadgePercent
        binding.originalPrice.isVisible = badge != null
        binding.discountBadge.isVisible = badge != null
        if (badge != null) {
            val original = context.formatMoney(product.originalPriceCents)
            binding.originalPrice.text = original
            binding.originalPrice.contentDescription = getString(R.string.price_was, original)
            binding.discountBadge.text = getString(R.string.save_percent, badge)
        }

        when (product.availability) {
            Availability.IN_STOCK -> bindStock(R.string.stock_in, R.drawable.product_ic_stock_in, R.attr.colorSuccess)
            Availability.LOW_STOCK -> bindStock(R.string.stock_low, R.drawable.product_ic_stock_low, R.attr.colorWarning)
            Availability.OUT_OF_STOCK -> bindStock(R.string.stock_out, R.drawable.ic_block, com.google.android.material.R.attr.colorError)
        }

        renderDescription(product.description, state.descriptionExpanded)

        // Rating: stars to the nearest half + the number to one decimal, read as one phrase.
        val rating = Catalog.rating(product.rating)
        val ratingText = NumberFormat.getNumberInstance(Locales.withLatinDigits(context.appLocale)).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(rating)
        val halves = (rating * 2).roundToInt()
        binding.ratingStars.children.forEachIndexed { index, star ->
            (star as ImageView).setImageResource(
                when {
                    halves >= (index + 1) * 2 -> R.drawable.product_ic_star
                    halves == index * 2 + 1 -> R.drawable.product_ic_star_half
                    else -> R.drawable.product_ic_star_outline
                },
            )
        }
        binding.ratingValue.text = ratingText
        binding.ratingRow.contentDescription = getString(R.string.rating_label, ratingText)
    }

    private fun renderBottomBar(state: ProductDetailUiState) {
        val existing = state.existing
        binding.onYourList.isVisible = existing != null
        if (existing != null) binding.onYourList.text = getString(R.string.on_your_list, existing.quantity)

        binding.quantity.text = state.quantity.toString()
        binding.quantity.contentDescription = getString(R.string.quantity_a11y, state.quantity)
        binding.decreaseButton.isEnabled = state.canDecrease
        binding.increaseButton.isEnabled = state.canIncrease

        binding.addButton.text = when {
            state.outOfStock -> getString(R.string.stock_out)
            existing != null -> getString(R.string.update_list)
            else -> getString(R.string.add_to_list_price, state.listName, requireContext().formatMoney(state.totalCents))
        }
        binding.addButton.isEnabled = state.canAdd
        binding.addButton.icon = if (state.saving) savingIcon else null
    }

    // ---------------------------------------------------------------- Description

    /** 4 lines; when the text is longer, it ends in "… More" (or "Less" when open) and a tap anywhere toggles it. */
    private fun renderDescription(text: String, expanded: Boolean) {
        val view = binding.description
        view.isVisible = text.isNotBlank()
        if (text.isBlank()) return
        descriptionSource = text to expanded
        if (description == null && view.text.isEmpty()) {
            // Until the width is known: plain text cut at 4 lines.
            view.maxLines = DESCRIPTION_LINES
            view.ellipsize = TextUtils.TruncateAt.END
            view.text = text
        }
        if (view.isLaidOut) updateDescription()
    }

    private fun updateDescription() {
        val b = _binding ?: return
        val (text, expanded) = descriptionSource ?: return
        val view = b.description
        val width = view.width - view.totalPaddingLeft - view.totalPaddingRight
        if (width <= 0) return
        val current = description
        if (current != null && current.text == text && current.expanded == expanded && current.width == width) return

        val overflows = lineCount(text, width) > DESCRIPTION_LINES
        val toggle = when {
            !overflows -> null
            expanded -> getString(R.string.action_less)
            else -> getString(R.string.action_more)
        }
        description = DescriptionText(text, expanded, width, toggle)
        view.maxLines = Int.MAX_VALUE
        view.text = when {
            toggle == null -> text
            expanded -> withToggle(text.trimEnd(), toggle)
            else -> collapsed(text, toggle, width)
        }
        view.isClickable = toggle != null
        view.isFocusable = toggle != null
    }

    /** The longest word-boundary cut of [text] that still fits in 4 lines with "… More" after it. */
    private fun collapsed(text: String, toggle: String, width: Int): CharSequence {
        var end = buildLayout(text, width).getLineEnd(DESCRIPTION_LINES - 1)
        while (end > 0) {
            val candidate = withToggle(text.substring(0, end).trimEnd() + ELLIPSIS, toggle)
            if (lineCount(candidate, width) <= DESCRIPTION_LINES) return candidate
            val space = text.lastIndexOf(' ', end - 2)
            end = if (space > 0) space else end - 1
        }
        return withToggle(ELLIPSIS, toggle)
    }

    private fun withToggle(body: CharSequence, toggle: String): CharSequence {
        val color = MaterialColors.getColor(binding.description, androidx.appcompat.R.attr.colorPrimary)
        return SpannableStringBuilder(body).append(' ').apply {
            val start = length
            append(toggle)
            setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TypefaceSpan(MEDIUM_FONT), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun lineCount(text: CharSequence, width: Int): Int = buildLayout(text, width).lineCount

    private fun buildLayout(text: CharSequence, width: Int): StaticLayout {
        val view = binding.description
        return StaticLayout.Builder.obtain(text, 0, text.length, view.paint, width)
            .setBreakStrategy(view.breakStrategy)
            .setHyphenationFrequency(view.hyphenationFrequency)
            .setJustificationMode(view.justificationMode)
            .setIncludePad(view.includeFontPadding)
            .setLineSpacing(view.lineSpacingExtra, view.lineSpacingMultiplier)
            .setTextDirection(TextDirectionHeuristics.LTR)
            .build()
    }

    /** TalkBack announces the tap as "More" / "Less", and only when the description has one. */
    private inner class DescriptionAccessibility : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            val toggle = description?.toggle
            if (toggle != null) info.addAction(AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_CLICK, toggle))
        }
    }

    // ---------------------------------------------------------------- Helpers

    private fun bindStock(@StringRes text: Int, @DrawableRes icon: Int, @AttrRes colorAttr: Int) {
        val color = MaterialColors.getColor(binding.stock, colorAttr)
        binding.stock.setText(text)
        binding.stock.setTextColor(color)
        binding.stock.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        TextViewCompat.setCompoundDrawableTintList(binding.stock, ColorStateList.valueOf(color))
    }

    private fun onEvent(event: ProductDetailEvent) {
        when (event) {
            is ProductDetailEvent.Added -> messageCenter.post(
                UserMessage(getString(R.string.product_added, event.title, event.listName), undo = event.undo),
            )
            is ProductDetailEvent.Updated -> messageCenter.post(
                UserMessage(getString(R.string.product_updated, event.title), undo = event.undo),
            )
        }
        navigator.navigateBack()
    }

    override fun onDestroyView() {
        imageUrl = null
        savingIcon = null
        description = null
        descriptionSource = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_LIST_ID = "listId"
        const val ARG_PRODUCT_ID = "productId"
        private const val DESCRIPTION_LINES = 4
        private const val DISABLED_ALPHA = 0.38f
        private const val FALLBACK_STATUS_BAR_DP = 24
        private const val BACK_BUTTON_GAP_DP = 8
        private const val ELLIPSIS = "…"
        private const val MEDIUM_FONT = "sans-serif-medium"

        fun args(listId: Long, productId: Int): Bundle = bundleOf(ARG_LIST_ID to listId, ARG_PRODUCT_ID to productId)
    }
}
