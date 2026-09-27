package com.basket.ui.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.basket.R
import com.basket.data.catalog.CatalogProduct
import com.basket.domain.ListItem
import com.basket.domain.Quantity
import com.basket.ui.theme.BasketMoneyStyles
import com.basket.ui.theme.basketColors

private const val OUT_OF_STOCK_ALPHA = 0.5f

@Composable
fun ProductCard(
    product: CatalogProduct,
    itemOnList: ListItem?,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onDecrease: (ListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lowStock = product.availabilityStatus == "Low stock"
    val outOfStock = product.availabilityStatus == "Out of stock"

    Card(
        onClick = onOpen,
        modifier = modifier.height(336.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.fillMaxSize().alpha(if (outOfStock) OUT_OF_STOCK_ALPHA else 1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                ProductThumbnail(url = product.thumbnailUrl, modifier = Modifier.fillMaxSize())
                product.discountBadgePercent?.let { percent ->
                    DiscountBadge(percent, Modifier.align(Alignment.TopStart).padding(8.dp))
                }
                when {
                    outOfStock -> StockBadge(
                        text = stringResource(R.string.stock_out),
                        icon = Icons.Rounded.Block,
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    )
                    lowStock -> StockBadge(
                        text = stringResource(R.string.stock_low),
                        icon = Icons.Rounded.Warning,
                        containerColor = MaterialTheme.basketColors.warning,
                        contentColor = MaterialTheme.basketColors.onWarning,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            ) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                PricePair(product)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (itemOnList != null) {
                        CompactStepper(
                            quantity = itemOnList.quantity,
                            onDecrease = { onDecrease(itemOnList) },
                            onIncrease = onAdd,
                            increaseEnabled = !outOfStock && Quantity.canIncrease(itemOnList.quantity),
                        )
                    } else {
                        FilledTonalIconButton(onClick = onAdd, enabled = !outOfStock) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PricePair(product: CatalogProduct) {
    Row(
        modifier = Modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "$" + String.format("%.2f", product.priceYouPayCents / 100.0),
            style = BasketMoneyStyles.Title,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        if (product.discountBadgePercent != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$" + String.format("%.2f", product.originalPriceCents / 100.0),
                style = BasketMoneyStyles.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** − quantity + on a product card. The number is the quantity already on the list. */
@Composable
fun CompactStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    increaseEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrease) {
                Icon(Icons.Rounded.Remove, contentDescription = null)
            }
            Text(
                text = quantity.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 24.dp),
            )
            IconButton(onClick = onIncrease, enabled = increaseEnabled) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    }
}

/** Product image with the basket placeholder while it loads, when it fails and when there is none. */
@Composable
fun ProductThumbnail(url: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
    )
    val loaded = painter.state is AsyncImagePainter.State.Success
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (!loaded) {
            Icon(
                painter = painterResource(R.drawable.ic_basket),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun DiscountBadge(percent: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = stringResource(R.string.discount_badge, percent),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StockBadge(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
