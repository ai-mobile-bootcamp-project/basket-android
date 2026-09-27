package com.basket.ui.product

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
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

        binding.backButton.setOnClickListener { navigator.navigateBack() }
        binding.retryButton.setOnClickListener { viewModel.retry() }
        binding.moreButton.setOnClickListener { viewModel.toggleDescription() }
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
        if (product == null) return

        renderProduct(product, state)
        renderBottomBar(product, state)
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
        binding.category.setText(product.categoryKey.labelRes)

        // Price block: the price you pay, and the original struck through only when there is a visible discount.
        binding.price.text = context.formatMoney(product.priceYouPayCents)
        val badge = product.discountBadgePercent
        binding.originalPrice.isVisible = badge != null
        binding.discountBadge.isVisible = badge != null
        if (badge != null) {
            binding.originalPrice.text = context.formatMoney(product.originalPriceCents)
            binding.discountBadge.text = getString(R.string.save_percent, badge)
        }

        when (product.availability) {
            Availability.IN_STOCK -> bindStock(R.string.stock_in, R.drawable.ic_check_circle, R.attr.colorSuccess)
            Availability.LOW_STOCK -> bindStock(R.string.stock_low, R.drawable.ic_warning, R.attr.colorWarning)
            Availability.OUT_OF_STOCK -> bindStock(R.string.stock_out, R.drawable.ic_block, com.google.android.material.R.attr.colorError)
        }

        // Description: 4 lines, "More" only when it does not fit.
        if (binding.description.text.toString() != product.description) binding.description.text = product.description
        binding.description.isVisible = product.description.isNotBlank()
        binding.description.maxLines = if (state.descriptionExpanded) Int.MAX_VALUE else DESCRIPTION_LINES
        binding.moreButton.setText(if (state.descriptionExpanded) R.string.action_less else R.string.action_more)
        binding.description.doOnPreDraw { updateMoreButton() }

        // Rating: stars + the number to one decimal, read as one phrase.
        val rating = Catalog.rating(product.rating)
        val ratingText = NumberFormat.getNumberInstance(Locales.withLatinDigits(context.appLocale)).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(rating)
        binding.ratingBar.rating = rating.toFloat()
        binding.ratingValue.text = ratingText
        binding.ratingRow.contentDescription = getString(R.string.rating_label, ratingText)
    }

    private fun renderBottomBar(product: CatalogProduct, state: ProductDetailUiState) {
        val existing = state.existing
        binding.onYourList.isVisible = existing != null
        if (existing != null) binding.onYourList.text = getString(R.string.on_your_list, existing.quantity)

        binding.quantity.text = state.quantity.toString()
        binding.quantity.contentDescription = getString(R.string.quantity_a11y, state.quantity)
        binding.decreaseButton.setEnabledWithAlpha(state.canDecrease)
        binding.increaseButton.setEnabledWithAlpha(state.canIncrease)

        binding.addButton.text = when {
            state.outOfStock -> getString(R.string.stock_out)
            existing != null -> getString(R.string.update_list)
            else -> getString(R.string.add_to_list_price, state.listName, requireContext().formatMoney(state.totalCents))
        }
        binding.addButton.isEnabled = state.canAdd
        binding.addButton.icon = if (state.saving) savingIcon else null
    }

    private fun updateMoreButton() {
        val b = _binding ?: return
        val expanded = viewModel.uiState.value.descriptionExpanded
        val layout = b.description.layout
        val overflows = layout != null && layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0
        b.moreButton.isVisible = b.description.isVisible && (expanded || overflows)
    }

    private fun bindStock(@StringRes text: Int, @DrawableRes icon: Int, @AttrRes colorAttr: Int) {
        val color = MaterialColors.getColor(binding.stock, colorAttr)
        binding.stock.setText(text)
        binding.stock.setTextColor(color)
        binding.stock.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        TextViewCompat.setCompoundDrawableTintList(binding.stock, ColorStateList.valueOf(color))
    }

    private fun View.setEnabledWithAlpha(enabled: Boolean) {
        isEnabled = enabled
        alpha = if (enabled) 1f else DISABLED_ALPHA
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
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_LIST_ID = "listId"
        const val ARG_PRODUCT_ID = "productId"
        private const val DESCRIPTION_LINES = 4
        private const val DISABLED_ALPHA = 0.38f

        fun args(listId: Long, productId: Int): Bundle = bundleOf(ARG_LIST_ID to listId, ARG_PRODUCT_ID to productId)
    }
}
