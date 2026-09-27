package com.basket.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object ListsDestination
@Serializable data class ListDetailDestination(val listId: Long)

/** Add / edit item. [itemId] 0 means "add a new item". */
@Serializable data class ItemFormDestination(val listId: Long, val itemId: Long = 0L)
@Serializable data class BrowseDestination(val listId: Long)
@Serializable data class ProductDetailDestination(val listId: Long, val productId: Int)
@Serializable data object SettingsDestination
@Serializable data object CategoriesDestination

/** Navigation entry points for the XML (Fragment) screens hosted inside the Compose NavHost. */
interface BasketNavigator {
    fun navigateBack()
    fun openListDetail(listId: Long)
    fun openCategories()
    fun backToLists()
}
