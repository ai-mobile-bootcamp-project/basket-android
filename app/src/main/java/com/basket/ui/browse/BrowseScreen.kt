package com.basket.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.basket.R
import com.basket.data.catalog.CatalogProduct
import com.basket.domain.CategoryKey
import com.basket.domain.Dates
import com.basket.domain.ListItem
import com.basket.ui.common.appLocale
import com.basket.ui.common.emoji
import com.basket.ui.common.labelRes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/** Snackbars with Undo stay for 5 seconds (rule "Deleting"). */
private const val UNDO_TIMEOUT_MS = 5_000L

/** The chips after "All", in aisle order. Bakery and Other have no catalog products. */
private val chipCategories = listOf(
    CategoryKey.FRUIT_VEG,
    CategoryKey.DAIRY_EGGS,
    CategoryKey.MEAT_FISH,
    CategoryKey.PANTRY,
    CategoryKey.FROZEN,
    CategoryKey.DRINKS,
    CategoryKey.HOUSEHOLD_PETS,
)

@Composable
fun BrowseRoute(onBack: () -> Unit, onOpenProduct: (Int) -> Unit) {
    val viewModel: BrowseViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val undoLabel = stringResource(R.string.action_undo)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.messages.collect { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                launch {
                    val result = withTimeoutOrNull(UNDO_TIMEOUT_MS) {
                        snackbarHostState.showSnackbar(
                            message = message.text,
                            actionLabel = if (message.undo != null) undoLabel else null,
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(message)
                }
            }
        }
    }

    BrowseScreen(
        state = state,
        query = viewModel.query,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onCategorySelected = viewModel::onCategorySelected,
        onClearFilters = viewModel::clearFilters,
        onRetry = viewModel::retry,
        onOpenProduct = onOpenProduct,
        onAdd = { product ->
            viewModel.add(product, resources.getString(R.string.product_added, product.title, state.listName))
        },
        onDecrease = { item ->
            viewModel.decrease(item, resources.getString(R.string.item_removed, item.name))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    query: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (CategoryKey?) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onOpenProduct: (Int) -> Unit,
    onAdd: (CatalogProduct) -> Unit,
    onDecrease: (ListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { BrowseTopBar(listName = state.listName, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            SearchField(query = query, onQueryChange = onQueryChange)
            CategoryChips(selected = state.category, onSelect = onCategorySelected)
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                BrowseContent(
                    state = state,
                    query = query,
                    gridState = gridState,
                    onClearFilters = onClearFilters,
                    onRetry = onRetry,
                    onOpenProduct = onOpenProduct,
                    onAdd = onAdd,
                    onDecrease = onDecrease,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseTopBar(listName: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.browse_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (listName.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.browse_subtitle, listName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        },
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val fieldColor = MaterialTheme.colorScheme.surfaceContainerHigh
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fieldColor,
            unfocusedContainerColor = fieldColor,
            disabledContainerColor = fieldColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun CategoryChips(selected: CategoryKey?, onSelect: (CategoryKey?) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            CategoryChip(
                label = stringResource(R.string.chip_all),
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        items(chipCategories, key = { it.name }) { key ->
            CategoryChip(
                label = stringResource(key.labelRes),
                emoji = key.emoji,
                selected = selected == key,
                onClick = { onSelect(key) },
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit, emoji: String? = null) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = when {
            selected -> {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            }
            emoji != null -> {
                { Text(text = emoji, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clearAndSetSemantics {}) }
            }
            else -> null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun BrowseContent(
    state: BrowseUiState,
    query: String,
    gridState: LazyGridState,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onOpenProduct: (Int) -> Unit,
    onAdd: (CatalogProduct) -> Unit,
    onDecrease: (ListItem) -> Unit,
) {
    val products = state.products
    val error = state.error
    when {
        products == null && error != null -> LoadErrorState(error = error, onRetry = onRetry)
        products == null -> SkeletonGrid(Modifier.fillMaxSize())
        else -> Column(Modifier.fillMaxSize()) {
            val savedAt = state.savedAt
            if (state.showSavedCopyBanner && savedAt != null) {
                SavedCopyBanner(
                    savedOn = Dates.shortDate(savedAt, ZoneId.systemDefault(), appLocale()),
                    onRetry = onRetry,
                )
            }
            if (products.isEmpty() && !state.searching) {
                NoResultsState(
                    query = query,
                    categoryLabel = state.category?.let { stringResource(it.labelRes) },
                    onClear = onClearFilters,
                )
            } else {
                ProductGrid(
                    products = products,
                    onList = state.onList,
                    gridState = gridState,
                    onOpenProduct = onOpenProduct,
                    onAdd = onAdd,
                    onDecrease = onDecrease,
                )
            }
        }
    }
}

@Composable
private fun ProductGrid(
    products: List<CatalogProduct>,
    onList: Map<Int, ListItem>,
    gridState: LazyGridState,
    onOpenProduct: (Int) -> Unit,
    onAdd: (CatalogProduct) -> Unit,
    onDecrease: (ListItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "count", span = { GridItemSpan(maxLineSpan) }, contentType = "count") {
            Text(
                text = "${products.size} products",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        items(products, key = { it.id }, contentType = { "product" }) { product ->
            ProductCard(
                product = product,
                itemOnList = onList[product.id],
                onOpen = { onOpenProduct(product.id) },
                onAdd = { onAdd(product) },
                onDecrease = onDecrease,
            )
        }
    }
}
