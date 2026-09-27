package com.basket.ui.browse

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.basket.R
import com.basket.ui.theme.basketColors

private const val SKELETON_CARDS = 6

/** First load: grey product cards while the catalog downloads, no spinner. */
@Composable
fun SkeletonGrid(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.browse_loading)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.semantics { contentDescription = loading },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(SKELETON_CARDS) {
            SkeletonCard(Modifier.alpha(pulse))
        }
    }
}

@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).background(block, MaterialTheme.shapes.extraSmall))
                    Box(Modifier.fillMaxWidth(0.5f).height(20.dp).background(block, MaterialTheme.shapes.extraSmall))
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(48.dp).background(block, CircleShape))
            }
        }
    }
}

/** "Can't load products" when there is no saved copy to show. */
@Composable
fun LoadErrorState(error: LoadError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, message) = when (error) {
        LoadError.Offline -> Icons.Rounded.CloudOff to R.string.error_offline_message
        LoadError.Server -> Icons.Rounded.ErrorOutline to R.string.error_server_message
    }
    MessageState(
        icon = icon,
        title = stringResource(R.string.error_load_title),
        message = stringResource(message),
        modifier = modifier,
    ) {
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

/** Nothing matches the search and the chip. */
@Composable
fun NoResultsState(query: String, categoryLabel: String?, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val title = if (query.isNotBlank()) {
        stringResource(R.string.no_results, query.trim())
    } else {
        stringResource(R.string.browse_empty_category, categoryLabel.orEmpty())
    }
    MessageState(icon = Icons.Rounded.SearchOff, title = title, message = null, modifier = modifier) {
        OutlinedButton(onClick = onClear) {
            Text(stringResource(if (query.isNotBlank()) R.string.clear_search else R.string.browse_show_all))
        }
    }
}

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    // Scrollable so pull to refresh works here too, and nothing is cut off at large font sizes.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        action()
    }
}

/** "You're offline · showing products saved on 25 Sep" above the grid, with Retry. */
@Composable
fun SavedCopyBanner(savedOn: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.basketColors.warningContainer,
        contentColor = MaterialTheme.basketColors.onWarningContainer,
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.offline_banner, savedOn),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.basketColors.onWarningContainer),
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
