package com.basket.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.basket.R
import com.basket.domain.Category
import com.basket.domain.Grouping
import com.basket.domain.ListItem
import com.basket.domain.Totals
import com.basket.ui.common.formatMoney
import kotlinx.coroutines.launch

/** Finish shopping: "8 of 10 bought · $34.39 spent", what was not bought, and the two choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishShoppingSheet(
    items: List<ListItem>,
    categories: List<Category>,
    onKeepRest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val totals = remember(items) { Totals.of(items) }
    val notBought = remember(items, categories) {
        Grouping.walkingOrder(items.filterNot { it.ticked }, categories).joinToString(", ") { it.name }
    }

    var closing by remember { mutableStateOf(false) }

    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) then()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.menu_finish_shopping), style = MaterialTheme.typography.titleLarge)
            Text(
                pluralStringResource(
                    R.plurals.finish_summary,
                    totals.itemCount,
                    totals.tickedCount,
                    totals.itemCount,
                    formatMoney(totals.inBasketCents),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (notBought.isNotEmpty()) {
                Text(
                    stringResource(R.string.finish_not_bought, notBought),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { close { onKeepRest(); onDismiss() } },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.finish_keep_rest))
            }
            OutlinedButton(
                onClick = { close(onDismiss) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.finish_keep_all))
            }
        }
    }
}
