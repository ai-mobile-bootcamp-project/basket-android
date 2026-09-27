package com.basket.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.basket.R
import com.basket.domain.ListItem
import com.basket.domain.Money
import com.basket.ui.common.formatMoney
import com.basket.ui.theme.BasketMoneyStyles

private const val TICKED_ALPHA = 0.6f

/**
 * One row of List detail: checkbox, name, note, "6 × $1.74", line total and the catalog icon.
 * Tapping the row edits the item; TalkBack gets the whole row as one sentence plus Edit and Remove actions.
 */
@Composable
fun ItemRow(
    item: ListItem,
    onTickedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineTotal = Money.lineTotalCents(item.unitPriceCents, item.quantity)
    val description = itemDescription(item, lineTotal)
    val editLabel = stringResource(R.string.action_edit)
    val removeLabel = stringResource(R.string.action_remove)
    val emphasis = if (item.ticked) TICKED_ALPHA else 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White)
            .clickable(onClickLabel = editLabel, onClick = onClick)
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(editLabel) { onClick(); true },
                    CustomAccessibilityAction(removeLabel) { onRemove(); true },
                )
            }
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.ticked,
            onCheckedChange = onTickedChange,
            modifier = Modifier.semantics { contentDescription = item.name },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 12.dp)
                .alpha(emphasis)
                .clearAndSetSemantics { },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.ticked) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.catalogProductId != null) {
                    Icon(
                        Icons.Rounded.Storefront,
                        contentDescription = stringResource(R.string.cd_from_catalog),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            val quantityPrice = quantityPriceText(item)
            if (!item.note.isNullOrBlank() || quantityPrice != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.note?.takeIf { it.isNotBlank() }?.let { note ->
                        Text(
                            note.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (quantityPrice != null) {
                        Text(
                            quantityPrice,
                            style = BasketMoneyStyles.Small,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Text(
            lineTotal?.let { dollars(it) } ?: stringResource(R.string.price_missing),
            style = BasketMoneyStyles.Body,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .alpha(emphasis)
                .clearAndSetSemantics { },
        )
    }
}

@Composable
private fun quantityPriceText(item: ListItem): String? {
    val price = item.unitPriceCents
    return when {
        price == null && item.quantity > 1 -> stringResource(R.string.item_quantity_only, item.quantity)
        price == null -> null
        item.quantity > 1 -> stringResource(R.string.item_quantity_price, item.quantity, dollars(price))
        else -> dollars(price)
    }
}

private fun dollars(cents: Long): String = "$" + String.format("%.2f", cents / 100.0)

/** "Apple, 6 at $1.74, $10.44, not in basket". */
@Composable
private fun itemDescription(item: ListItem, lineTotal: Long?): String {
    val price = item.unitPriceCents
    return if (price != null && lineTotal != null) {
        stringResource(
            if (item.ticked) R.string.item_a11y_in_basket else R.string.item_a11y_not_in_basket,
            item.name,
            item.quantity,
            formatMoney(price),
            formatMoney(lineTotal),
        )
    } else {
        stringResource(
            if (item.ticked) R.string.item_a11y_no_price_in_basket else R.string.item_a11y_no_price_not_in_basket,
            item.name,
            item.quantity,
        )
    }
}

/** Swipe towards the start to remove: red background with a trash icon. */
@Composable
fun SwipeToRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val currentOnRemove by rememberUpdatedState(onRemove)

    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            currentOnRemove()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
    }
}
