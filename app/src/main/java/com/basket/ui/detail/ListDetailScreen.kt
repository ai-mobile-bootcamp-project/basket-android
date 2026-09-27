package com.basket.ui.detail

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.basket.R
import com.basket.domain.Category
import com.basket.domain.ListItem
import com.basket.domain.ListTotals
import com.basket.domain.Section
import com.basket.domain.ShoppingGroups
import com.basket.domain.Sorting
import com.basket.ui.common.displayName
import com.basket.ui.common.formatMoney
import com.basket.ui.common.label
import com.basket.ui.lists.ListNameDialog
import com.basket.ui.theme.BasketMoneyStyles
import com.basket.ui.theme.basketColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val UNDO_TIMEOUT_MS = 5_000L

@Composable
fun ListDetailRoute(onBack: () -> Unit, onAddItem: () -> Unit, onEditItem: (Long) -> Unit, onBrowse: () -> Unit) {
    val viewModel: ListDetailViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModel.events.collect { event ->
                    when (event) {
                        is ListDetailEvent.ItemRemoved -> launch {
                            val text = context.getString(R.string.item_removed, event.name)
                            if (snackbarHostState.showWithUndo(text, undoLabel)) viewModel.undoRemove()
                        }
                        is ListDetailEvent.ShareText -> context.sharePlainText(event.text)
                    }
                }
            }
            launch {
                viewModel.messages.collect { message ->
                    launch {
                        if (message.undo != null) {
                            if (snackbarHostState.showWithUndo(message.text, undoLabel)) viewModel.undo(message)
                        } else {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(message.text, duration = SnackbarDuration.Short)
                        }
                    }
                }
            }
        }
    }

    ListDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAddItem = onAddItem,
        onEditItem = onEditItem,
        onBrowse = onBrowse,
        onTickedChange = viewModel::setTicked,
        onSwipeRemove = viewModel::removeAt,
        onRemoveItem = { item -> viewModel.removeItem(item.id, context.getString(R.string.item_removed, item.name)) },
        onShare = { viewModel.share(ResourceShareLabels(context.resources)) { cents -> context.formatMoney(cents) } },
        onRename = viewModel::rename,
        onClearBasket = viewModel::clearBasket,
        onKeepRest = viewModel::keepRest,
    )
}

/** Shows [text] with Undo for 5 seconds; true when the user tapped Undo. */
private suspend fun SnackbarHostState.showWithUndo(text: String, undoLabel: String): Boolean = coroutineScope {
    currentSnackbarData?.dismiss()
    val timeout = launch {
        delay(UNDO_TIMEOUT_MS)
        currentSnackbarData?.takeIf { it.visuals.message == text }?.dismiss()
    }
    val result = showSnackbar(text, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
    timeout.cancel()
    result == SnackbarResult.ActionPerformed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    state: ListDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    onBrowse: () -> Unit,
    onTickedChange: (ListItem, Boolean) -> Unit,
    onSwipeRemove: (Int) -> Unit,
    onRemoveItem: (ListItem) -> Unit,
    onShare: () -> Unit,
    onRename: (String) -> Unit,
    onClearBasket: () -> Unit,
    onKeepRest: () -> Unit,
    modifier: Modifier = Modifier,
    moveTickedDown: Boolean = false,
) {
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showFinish by rememberSaveable { mutableStateOf(false) }
    var inBasketExpanded by rememberSaveable { mutableStateOf(true) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val groups = remember(state.items, state.categories, moveTickedDown, configuration) {
        groupItems(state.items, state.categories, resources, moveTickedDown)
    }
    val rows = remember(groups, inBasketExpanded) { detailRows(groups, inBasketExpanded) }
    val showContent = !state.loading && !state.isEmpty

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(state.listName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                    }
                    DetailOverflowMenu(
                        canClearBasket = state.totals.tickedCount > 0,
                        canFinish = !state.isEmpty,
                        onRename = { showRename = true },
                        onClearBasket = onClearBasket,
                        onFinish = { showFinish = true },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (showContent) {
                TotalsFooter(
                    totals = state.totals,
                    onBrowse = onBrowse,
                    onFinish = { showFinish = true },
                )
            }
        },
        floatingActionButton = {
            if (showContent) {
                ExtendedFloatingActionButton(
                    onClick = onAddItem,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_item)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            state.isEmpty -> EmptyListState(
                onAddItem = onAddItem,
                onBrowse = onBrowse,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, row -> row.contentType }) { index, row ->
                    when (row) {
                        is DetailRow.SectionHeader -> SectionHeader(
                            title = stringResource(R.string.section_header, row.category.label(), row.count),
                            modifier = Modifier.animateItem(),
                        )
                        is DetailRow.BasketHeader -> InBasketHeader(
                            count = row.count,
                            expanded = inBasketExpanded,
                            onToggle = { inBasketExpanded = !inBasketExpanded },
                            modifier = Modifier.animateItem(),
                        )
                        is DetailRow.Item -> SwipeToRemove(
                            onRemove = { onSwipeRemove(index) },
                            modifier = Modifier.animateItem(),
                        ) {
                            ItemRow(
                                item = row.item,
                                onTickedChange = { ticked -> onTickedChange(row.item, ticked) },
                                onClick = { onEditItem(row.item.id) },
                                onRemove = { onRemoveItem(row.item) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        ListNameDialog(
            title = stringResource(R.string.rename_list),
            confirmLabel = stringResource(R.string.action_save),
            initialName = state.listName,
            onConfirm = { name ->
                showRename = false
                onRename(name)
            },
            onDismiss = { showRename = false },
        )
    }

    if (showFinish && !state.isEmpty) {
        FinishShoppingSheet(
            items = state.items,
            categories = state.categories,
            onKeepRest = onKeepRest,
            onDismiss = { showFinish = false },
        )
    }
}

// ------------------------------------------------------------------ Grouping

private sealed interface DetailRow {
    val key: Any
    val contentType: String

    data class SectionHeader(val category: Category, val count: Int) : DetailRow {
        override val key: Any get() = "section-${category.id}"
        override val contentType: String get() = "section"
    }

    data class BasketHeader(val count: Int) : DetailRow {
        override val key: Any get() = "in-basket"
        override val contentType: String get() = "in-basket"
    }

    data class Item(val item: ListItem) : DetailRow {
        override val key: Any get() = item.id
        override val contentType: String get() = "item"
    }
}

/** To buy sections by category, items A→Z; with [moveTickedDown] the ticked items go to In basket. */
private fun groupItems(
    items: List<ListItem>,
    categories: List<Category>,
    resources: Resources,
    moveTickedDown: Boolean,
): ShoppingGroups {
    val byId = categories.associateBy { it.id }
    val other = categories.firstOrNull { it.isOther }
    val toBuy = if (moveTickedDown) items.filterNot { it.ticked } else items
    val sections = toBuy
        .groupBy { it.categoryId }
        .mapNotNull { (categoryId, sectionItems) ->
            val category = byId[categoryId] ?: other ?: return@mapNotNull null
            Section(category, sectionItems.sortedWith(Sorting.itemsByName))
        }
        .sortedWith(Sorting.byName { it.category.displayName(resources) })
    val inBasket = if (moveTickedDown) items.filter { it.ticked }.sortedWith(Sorting.itemsByName) else emptyList()
    return ShoppingGroups(sections, inBasket)
}

private fun detailRows(groups: ShoppingGroups, inBasketExpanded: Boolean): List<DetailRow> = buildList {
    groups.toBuy.forEach { section ->
        add(DetailRow.SectionHeader(section.category, section.items.size))
        section.items.forEach { add(DetailRow.Item(it)) }
    }
    if (groups.inBasket.isNotEmpty()) {
        add(DetailRow.BasketHeader(groups.inBasket.size))
        if (inBasketExpanded) groups.inBasket.forEach { add(DetailRow.Item(it)) }
    }
}

// ------------------------------------------------------------------ Parts

@Composable
private fun DetailOverflowMenu(
    canClearBasket: Boolean,
    canFinish: Boolean,
    onRename: () -> Unit,
    onClearBasket: () -> Unit,
    onFinish: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_clear_basket)) },
                enabled = canClearBasket,
                onClick = {
                    expanded = false
                    onClearBasket()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_finish_shopping)) },
                enabled = canFinish,
                onClick = {
                    expanded = false
                    onFinish()
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun InBasketHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val toggleLabel = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = toggleLabel, onClick = onToggle)
            .semantics { heading() }
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.basketColors.success,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "In basket · $count",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = toggleLabel,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TotalsFooter(
    totals: ListTotals,
    onBrowse: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Text(
                stringResource(R.string.totals_total, formatMoney(totals.totalCents)),
                style = BasketMoneyStyles.Display,
            )
            Text(
                stringResource(R.string.totals_in_basket, formatMoney(totals.inBasketCents)),
                style = BasketMoneyStyles.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totals.withoutPriceCount > 0) {
                Text(
                    pluralStringResource(R.plurals.items_without_price, totals.withoutPriceCount, totals.withoutPriceCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onBrowse,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.browse_products), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (totals.isDone) {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.menu_finish_shopping), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyListState(onAddItem: () -> Unit, onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.ShoppingBasket,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.list_empty),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddItem, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.add_item))
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onBrowse, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.browse_products))
        }
    }
}
