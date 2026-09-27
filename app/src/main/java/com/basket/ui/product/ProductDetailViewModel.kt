package com.basket.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.basket.data.CategoriesRepository
import com.basket.data.ListsRepository
import com.basket.data.catalog.CatalogProduct
import com.basket.data.catalog.CatalogRepository
import com.basket.domain.Availability
import com.basket.domain.Duplicates
import com.basket.domain.ListItem
import com.basket.domain.Quantity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

enum class ProductStatus { LOADING, READY, OFFLINE, SERVER_ERROR }

data class ProductDetailUiState(
    val status: ProductStatus = ProductStatus.LOADING,
    val product: CatalogProduct? = null,
    val listName: String = "",
    /** The row of this product already on the list, if any. */
    val existing: ListItem? = null,
    val quantity: Int = Quantity.MIN,
    val saving: Boolean = false,
    val descriptionExpanded: Boolean = false,
) {
    val outOfStock: Boolean get() = product?.availability == Availability.OUT_OF_STOCK
    val canDecrease: Boolean get() = !outOfStock && Quantity.canDecrease(quantity)
    val canIncrease: Boolean get() = !outOfStock && Quantity.canIncrease(quantity)
    val canAdd: Boolean get() = product != null && !outOfStock && !saving

    /** What the button adds: the price you pay × the chosen quantity. */
    val totalCents: Long get() = (product?.priceYouPayCents ?: 0L) * quantity
}

sealed interface ProductDetailEvent {
    data class Added(val title: String, val listName: String, val undo: suspend () -> Unit) : ProductDetailEvent
    data class Updated(val title: String, val undo: suspend () -> Unit) : ProductDetailEvent
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val listsRepository: ListsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val listId: Long = checkNotNull(savedStateHandle[ProductDetailFragment.ARG_LIST_ID])
    private val productId: Int = checkNotNull(savedStateHandle[ProductDetailFragment.ARG_PRODUCT_ID])

    private val product = MutableStateFlow(catalogRepository.product(productId))
    private val status = MutableStateFlow(if (product.value != null) ProductStatus.READY else ProductStatus.LOADING)
    private val saving = MutableStateFlow(false)

    /** 0 until the user steps; the stepper then starts at the quantity already on the list, or 1. */
    private val chosenQuantity = savedStateHandle.getStateFlow(KEY_QUANTITY, 0)
    private val expanded = savedStateHandle.getStateFlow(KEY_EXPANDED, false)

    private val _events = Channel<ProductDetailEvent>(Channel.BUFFERED)
    val events: Flow<ProductDetailEvent> = _events.receiveAsFlow()

    private val content = combine(
        status,
        product,
        listsRepository.observeItems(listId),
        listsRepository.observeList(listId).map { it?.name.orEmpty() },
    ) { status, product, items, listName ->
        ProductDetailUiState(
            status = status,
            product = product,
            listName = listName,
            existing = product?.let { Duplicates.find(items, it.title, it.id) },
        )
    }

    val uiState: StateFlow<ProductDetailUiState> = combine(content, chosenQuantity, saving, expanded) { content, chosen, isSaving, isExpanded ->
        content.copy(
            quantity = if (chosen in Quantity.MIN..Quantity.MAX) chosen else content.existing?.quantity ?: Quantity.MIN,
            saving = isSaving,
            descriptionExpanded = isExpanded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductDetailUiState())

    init {
        if (product.value == null) load()
    }

    /** After process death the catalog is empty, so the product is downloaded again. */
    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            status.value = ProductStatus.LOADING
            status.value = try {
                catalogRepository.refresh()
                val found = catalogRepository.product(productId)
                product.value = found
                if (found != null) ProductStatus.READY else ProductStatus.SERVER_ERROR
            } catch (e: IOException) {
                ProductStatus.OFFLINE
            } catch (e: HttpException) {
                ProductStatus.SERVER_ERROR
            } catch (e: SerializationException) {
                ProductStatus.SERVER_ERROR
            }
        }
    }

    fun step(delta: Int) {
        savedStateHandle[KEY_QUANTITY] = Quantity.step(uiState.value.quantity, delta)
    }

    fun toggleDescription() {
        savedStateHandle[KEY_EXPANDED] = !expanded.value
    }

    /** Add, or Update when the product is already on the list. Acts once, however fast the user taps. */
    fun addToList() {
        val state = uiState.value
        val product = state.product ?: return
        if (saving.value || state.outOfStock) return
        saving.value = true
        val repository = listsRepository
        viewModelScope.launch {
            try {
                val existing = state.existing
                if (existing != null) {
                    repository.setQuantity(existing, state.quantity)
                    _events.send(ProductDetailEvent.Updated(product.title) { repository.setQuantity(existing, existing.quantity) })
                } else {
                    val category = categoriesRepository.forKey(product.categoryKey) ?: categoriesRepository.other()
                    val item = ListItem(
                        id = 0,
                        listId = listId,
                        name = product.title,
                        quantity = state.quantity,
                        unitPriceCents = product.priceYouPayCents,
                        categoryId = category.id,
                        catalogProductId = product.id,
                    )
                    val added = item.copy(id = repository.addItem(item))
                    _events.send(ProductDetailEvent.Added(product.title, state.listName) { repository.deleteItem(added) })
                }
            } catch (e: Exception) {
                saving.value = false
                throw e
            }
        }
    }

    private companion object {
        const val KEY_QUANTITY = "quantity"
        const val KEY_EXPANDED = "description_expanded"
    }
}
