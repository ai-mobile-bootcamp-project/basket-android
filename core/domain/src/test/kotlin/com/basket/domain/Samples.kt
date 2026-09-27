package com.basket.domain

/** Sample data from the design brief, shared by the rule tests. */
object Samples {

    val defaultCategories: List<Category> = CategoryKey.entries.mapIndexed { index, key ->
        Category(id = (index + 1).toLong(), key = key, name = null, position = index)
    }

    fun categoryId(key: CategoryKey): Long = defaultCategories.first { it.key == key }.id

    private fun item(
        id: Long,
        listId: Long,
        name: String,
        key: CategoryKey,
        quantity: Int,
        unitPriceCents: Long?,
        ticked: Boolean = false,
        note: String? = null,
        catalogProductId: Int? = null,
    ) = ListItem(
        id = id,
        listId = listId,
        name = name,
        quantity = quantity,
        unitPriceCents = unitPriceCents,
        categoryId = categoryId(key),
        note = note,
        ticked = ticked,
        catalogProductId = catalogProductId,
    )

    val weeklyShop: List<ListItem> = listOf(
        item(1, 1, "Apple", CategoryKey.FRUIT_VEG, 6, 174, catalogProductId = 16),
        item(2, 1, "Cucumber", CategoryKey.FRUIT_VEG, 2, 149, catalogProductId = 21),
        item(3, 1, "Strawberry", CategoryKey.FRUIT_VEG, 1, 395, catalogProductId = 40),
        item(4, 1, "Potatoes", CategoryKey.FRUIT_VEG, 2, 217, ticked = true, catalogProductId = 35),
        item(5, 1, "Sourdough bread", CategoryKey.BAKERY, 1, null, note = "sliced"),
        item(6, 1, "Eggs", CategoryKey.DAIRY_EGGS, 1, 266, ticked = true, catalogProductId = 23),
        item(7, 1, "Milk", CategoryKey.DAIRY_EGGS, 2, 301, catalogProductId = 32),
        item(8, 1, "Rice", CategoryKey.PANTRY, 1, 543, catalogProductId = 38),
        item(9, 1, "Nescafe Coffee", CategoryKey.DRINKS, 1, 786, catalogProductId = 34),
        item(10, 1, "Tissue Paper Box", CategoryKey.HOUSEHOLD_PETS, 1, 216, ticked = true, catalogProductId = 41),
    )

    val bbqSaturday: List<ListItem> = listOf(
        item(11, 2, "Beef Steak", CategoryKey.MEAT_FISH, 4, 1174, catalogProductId = 17),
        item(12, 2, "Chicken Meat", CategoryKey.MEAT_FISH, 2, 862, catalogProductId = 19),
        item(13, 2, "Red Onions", CategoryKey.FRUIT_VEG, 3, 179, catalogProductId = 37),
        item(14, 2, "Green Bell Pepper", CategoryKey.FRUIT_VEG, 4, 129, catalogProductId = 25),
        item(15, 2, "Soft Drinks", CategoryKey.DRINKS, 12, 164, catalogProductId = 39),
        item(16, 2, "Charcoal", CategoryKey.OTHER, 1, 850),
    )

    /** English share labels, as the app's string resources produce them. */
    val englishShareLabels: ShareLabels = object : ShareLabels {
        override fun toBuy(count: Int): String = "To buy ($count)"

        override fun inBasket(count: Int): String = "In basket ($count)"

        override fun total(formattedTotal: String, withoutPrice: Int): String = when (withoutPrice) {
            0 -> "Total $formattedTotal"
            1 -> "Total $formattedTotal (1 item without price)"
            else -> "Total $formattedTotal ($withoutPrice items without price)"
        }
    }
}
