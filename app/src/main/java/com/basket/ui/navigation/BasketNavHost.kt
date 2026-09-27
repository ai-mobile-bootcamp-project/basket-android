package com.basket.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.fragment.compose.AndroidFragment
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.basket.ui.browse.BrowseRoute
import com.basket.ui.categories.CategoriesFragment
import com.basket.ui.detail.ListDetailRoute
import com.basket.ui.item.ItemFormFragment
import com.basket.ui.lists.ListsRoute
import com.basket.ui.product.ProductDetailFragment
import com.basket.ui.settings.SettingsFragment
import com.basket.ui.welcome.WelcomeRoute

/**
 * Roles the design handover does not define (background, surfaceContainer, surfaceVariant, inverse colours) would
 * otherwise fall back to the Material baseline palette. They are derived from the handover's neutrals, with the
 * same values as res/values(-night)/colors.xml.
 */
@Composable
private fun WithDerivedRoles(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    MaterialTheme(
        colorScheme = scheme.copy(
            background = scheme.surface,
            onBackground = scheme.onSurface,
            surfaceVariant = if (dark) Color(0xFF414941) else Color(0xFFDDE5DA),
            surfaceContainerLowest = if (dark) Color(0xFF0B0F0B) else Color(0xFFFFFFFF),
            surfaceContainer = if (dark) Color(0xFF1C211C) else Color(0xFFEAEFE7),
            surfaceBright = if (dark) Color(0xFF363A35) else scheme.surface,
            surfaceDim = if (dark) scheme.surface else Color(0xFFD6DBD3),
            inverseSurface = if (dark) Color(0xFFDFE4DC) else Color(0xFF2D322D),
            inverseOnSurface = if (dark) Color(0xFF2D322D) else Color(0xFFEDF2EA),
            inversePrimary = if (dark) Color(0xFF1E6B3F) else Color(0xFF8DD8A5),
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

@Composable
fun BasketNavHost(nav: NavHostController, startDestination: Any) = WithDerivedRoles {
    // Each destination keeps clear of the system bars and the keyboard, except Product detail, whose image runs
    // under the status bar (the fragment offsets its back button itself).
    val safe = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<WelcomeDestination> {
                Box(safe) {
                    WelcomeRoute(
                        onDone = {
                            nav.navigate(ListsDestination) {
                                popUpTo<WelcomeDestination> { inclusive = true }
                            }
                        },
                    )
                }
            }
            composable<ListsDestination> {
                Box(safe) {
                    ListsRoute(
                        onOpenList = { id -> nav.navigate(ListDetailDestination(id)) },
                        onOpenSettings = { nav.navigate(SettingsDestination) },
                    )
                }
            }
            composable<ListDetailDestination> { entry ->
                val route = entry.toRoute<ListDetailDestination>()
                Box(safe) {
                    ListDetailRoute(
                        onBack = { nav.popBackStack() },
                        onAddItem = { nav.navigate(ItemFormDestination(route.listId)) },
                        onEditItem = { itemId -> nav.navigate(ItemFormDestination(route.listId, itemId)) },
                        onBrowse = { nav.navigate(BrowseDestination(route.listId)) },
                    )
                }
            }
            composable<ItemFormDestination> { entry ->
                val route = entry.toRoute<ItemFormDestination>()
                AndroidFragment<ItemFormFragment>(
                    safe,
                    arguments = ItemFormFragment.args(route.listId, route.itemId),
                )
            }
            composable<BrowseDestination> { entry ->
                val route = entry.toRoute<BrowseDestination>()
                Box(safe) {
                    BrowseRoute(
                        onBack = { nav.popBackStack() },
                        onOpenProduct = { productId -> nav.navigate(ProductDetailDestination(route.listId, productId)) },
                    )
                }
            }
            composable<ProductDetailDestination> { entry ->
                val route = entry.toRoute<ProductDetailDestination>()
                AndroidFragment<ProductDetailFragment>(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                    arguments = ProductDetailFragment.args(route.listId, route.productId),
                )
            }
            composable<SettingsDestination> {
                AndroidFragment<SettingsFragment>(safe)
            }
            composable<CategoriesDestination> {
                AndroidFragment<CategoriesFragment>(safe)
            }
        }
    }
}
