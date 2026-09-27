package com.basket.ui.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.basket.data.CategoriesRepository
import com.basket.data.ListsRepository
import com.basket.data.MessageCenter
import com.basket.data.UserMessage
import com.basket.data.catalog.CatalogProduct
import com.basket.data.catalog.CatalogRepository
import com.basket.domain.CategoryKey
import com.basket.domain.ListItem
import com.basket.domain.Quantity
import com.basket.domain.Sorting
import com.basket.ui.navigation.BrowseDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import javax.inject.Inject

/** Why the catalog could not be downloaded. */
enum class LoadError { Offline, Server }

data class BrowseUiState(
    /** Name of the list products are added to ("Weekly shop"). */
    val listName: String = "",
    val category: CategoryKey? = null,
    /** Products to show, filtered and sorted A→Z; null while there is nothing to show yet. */
    val products: List<CatalogProduct>? = null,
    /** A search is waiting for its results. */
    val searching: Boolean = false,
    /** The pull-to-refresh indicator is showing. */
    val refreshing: Boolean = false,
    val error: LoadError? = null,
    /** Epoch millis of the saved copy of the catalog. */
    val savedAt: Long? = null,
    /** Items of the open list that came from the catalog, by product id. */
    val onList: Map<Int, ListItem> = emptyMap(),
) {
    /** A refresh failed while products are still on screen. */
    val showSavedCopyBanner: Boolean get() = error != null && products != null && savedAt != null
}

private data class SearchResult(val query: String, val products: List<CatalogProduct>)

private data class Visible(val category: CategoryKey?, val products: List<CatalogProduct>?, val searching: Boolean)

private data class Status(val refreshing: Boolean, val error: LoadError?, val savedAt: Long?)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
    private val listsRepository: ListsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val messageCenter: MessageCenter,
) : ViewModel() {

    private val listId: Long = savedStateHandle.toRoute<BrowseDestination>().listId

    /** Text of the search field. */
    var query by mutableStateOf(savedStateHandle.get<String>(KEY_QUERY).orEmpty())
        private set

    private val queryFlow = MutableStateFlow(query)
    private val categoryFlow: Flow<CategoryKey?> = savedStateHandle.getStateFlow<String?>(KEY_CATEGORY, null)
        .map { name -> name?.let { runCatching { CategoryKey.valueOf(it) }.getOrNull() } }

    private val searchResult = MutableStateFlow<SearchResult?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<LoadError?>(null)

    private var refreshJob: Job? = null
    private var searchJob: Job? = null

    /** Snackbar messages for this screen, including the ones Product detail posts before coming back. */
    val messages: Flow<UserMessage> = messageCenter.messages

    private val visible: Flow<Visible> = combine(
        catalogRepository.products,
        searchResult,
        queryFlow,
        categoryFlow,
    ) { catalog, search, query, category ->
        val trimmed = query.trim()
        val source = if (trimmed.isEmpty()) catalog else search?.products ?: catalog
        Visible(
            category = category,
            products = source
                ?.filter { category == null || it.categoryKey == category }
                ?.sortedWith(Sorting.byName { it.title }),
            searching = trimmed.isNotEmpty() && search?.query != trimmed,
        )
    }

    private val status: Flow<Status> = combine(refreshing, error, catalogRepository.lastUpdated) { refreshing, error, savedAt ->
        Status(refreshing, error, savedAt)
    }

    private val onList: Flow<Map<Int, ListItem>> = listsRepository.observeItems(listId).map { items ->
        buildMap {
            items.sortedBy { it.id }.forEach { item ->
                val productId = item.catalogProductId ?: return@forEach
                if (productId !in this) put(productId, item)
            }
        }
    }

    private val listName: Flow<String> = listsRepository.observeList(listId).map { it?.name.orEmpty() }

    val uiState: StateFlow<BrowseUiState> = combine(visible, status, onList, listName) { visible, status, onList, listName ->
        BrowseUiState(
            listName = listName,
            category = visible.category,
            products = visible.products,
            searching = visible.searching,
            refreshing = status.refreshing,
            error = status.error,
            savedAt = status.savedAt,
            onList = onList,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BrowseUiState(
            products = catalogRepository.products.value?.sortedWith(Sorting.byName { it.title }),
            savedAt = catalogRepository.lastUpdated.value,
        ),
    )

    init {
        // The saved copy shows at once; the download runs quietly behind it.
        refresh(showIndicator = false)
        if (query.isNotBlank()) search(query.trim(), debounce = false)
    }

    fun onQueryChange(value: String) {
        query = value
        savedStateHandle[KEY_QUERY] = value
        queryFlow.value = value
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            searchResult.value = null
        } else if (searchResult.value?.query != trimmed) {
            search(trimmed, debounce = true)
        }
    }

    fun onCategorySelected(category: CategoryKey?) {
        savedStateHandle[KEY_CATEGORY] = category?.name
    }

    /** "Clear search" on the no-results state: empties the search and goes back to All. */
    fun clearFilters() {
        onQueryChange("")
        onCategorySelected(null)
    }

    /** Pull to refresh, Retry on the banner and on the error state. */
    fun retry() {
        refresh(showIndicator = products() != null)
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) search(trimmed, debounce = false)
    }

    private fun products(): List<CatalogProduct>? = uiState.value.products

    private fun refresh(showIndicator: Boolean) {
        if (refreshJob?.isActive == true) {
            if (showIndicator) refreshing.value = true
            return
        }
        if (catalogRepository.products.value == null) error.value = null
        refreshJob = viewModelScope.launch {
            refreshing.value = showIndicator
            try {
                catalogRepository.refresh()
                error.value = null
            } catch (e: HttpException) {
                error.value = LoadError.Server
            } catch (e: SerializationException) {
                error.value = LoadError.Server
            } finally {
                refreshing.value = false
            }
        }
    }

    private fun search(query: String, debounce: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            try {
                searchResult.value = SearchResult(query, catalogRepository.search(query))
            } catch (e: HttpException) {
                error.value = LoadError.Server
            } catch (e: SerializationException) {
                error.value = LoadError.Server
            }
        }
    }

    /** + on a product card: adds the product to the open list and offers Undo. */
    fun add(product: CatalogProduct, message: String) {
        viewModelScope.launch {
            val category = categoriesRepository.forKey(product.categoryKey) ?: categoriesRepository.other()
            val priceCents = (product.price * (1 - product.discountPercentage / 100) * 100).toInt().toLong()
            val id = listsRepository.addItem(
                ListItem(
                    id = 0,
                    listId = listId,
                    name = product.title,
                    quantity = product.minimumOrderQuantity,
                    unitPriceCents = priceCents,
                    categoryId = category.id,
                    catalogProductId = product.id,
                ),
            )
            messageCenter.post(
                UserMessage(message) {
                    listsRepository.getItem(id)?.let { listsRepository.deleteItem(it) }
                },
            )
        }
    }

    /** − on the compact stepper: lowers the quantity, or removes the item at 1 with Undo. */
    fun decrease(item: ListItem, removedMessage: String) {
        viewModelScope.launch {
            if (Quantity.canDecrease(item.quantity)) {
                listsRepository.setQuantity(item, Quantity.step(item.quantity, -1))
            } else {
                listsRepository.deleteItem(item)
                messageCenter.post(UserMessage(removedMessage) { listsRepository.restoreItem(item) })
            }
        }
    }

    fun undo(message: UserMessage) {
        messageCenter.undo(message)
    }

    private companion object {
        const val KEY_QUERY = "browse_query"
        const val KEY_CATEGORY = "browse_category"
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
