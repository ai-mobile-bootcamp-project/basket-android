package com.basket.domain

/** A shopping list. [updatedAt] is epoch millis; the Lists screen shows the most recently changed first. */
data class ShoppingList(
    val id: Long,
    val name: String,
    val updatedAt: Long,
)

/** One row on a shopping list. Money is always kept in whole cents. */
data class ListItem(
    val id: Long,
    val listId: Long,
    val name: String,
    val quantity: Int,
    /** Unit price in cents, or null when the user did not enter one. */
    val unitPriceCents: Long?,
    val categoryId: Long,
    val note: String? = null,
    val ticked: Boolean = false,
    /** DummyJSON product id when the item was added from Browse products. */
    val catalogProductId: Int? = null,
)

/** The default aisles. User-created categories have no key. */
enum class CategoryKey {
    FRUIT_VEG,
    BAKERY,
    DAIRY_EGGS,
    MEAT_FISH,
    PANTRY,
    FROZEN,
    DRINKS,
    HOUSEHOLD_PETS,
    OTHER,
}

/**
 * An aisle on the Categories screen. Default categories carry a [key]; their [name] is null until the user
 * renames them, so the UI can show the name in the app language.
 */
data class Category(
    val id: Long,
    val key: CategoryKey?,
    val name: String?,
    val position: Int,
) {
    val isOther: Boolean get() = key == CategoryKey.OTHER
}

/** Stock state of a catalog product, mapped from DummyJSON's `availabilityStatus`. */
enum class Availability { IN_STOCK, LOW_STOCK, OUT_OF_STOCK }

/** Totals of one list (rule "Totals" and "Counts"). */
data class ListTotals(
    val itemCount: Int,
    val tickedCount: Int,
    /** Sum of the line totals of items with a price. */
    val totalCents: Long,
    /** The same for ticked items only. */
    val inBasketCents: Long,
    /** Items without a price add nothing to the totals and are counted here. */
    val withoutPriceCount: Int,
) {
    val isEmpty: Boolean get() = itemCount == 0
    val isDone: Boolean get() = itemCount > 0 && tickedCount == itemCount
}

/** One aisle section of the To buy part of List detail. */
data class Section(
    val category: Category,
    val items: List<ListItem>,
)

/** List detail content: aisles in the Categories order, then the In basket part. */
data class ShoppingGroups(
    val toBuy: List<Section>,
    val inBasket: List<ListItem>,
)

/** Result of reading the Unit price field. */
sealed interface PriceInput {
    data object Empty : PriceInput
    data class Valid(val cents: Long) : PriceInput
    data object Invalid : PriceInput
}
