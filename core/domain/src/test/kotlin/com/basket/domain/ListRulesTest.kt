package com.basket.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Totals, aisle grouping and the share text of a list. */
class ListRulesTest {

    private val categories = Samples.defaultCategories
    private val weeklyShop = Samples.weeklyShop
    private val usd: (Long) -> String = { Money.format(it, Locale.US) }

    private fun names(items: List<ListItem>) = items.map { it.name }

    // ------------------------------------------------------------------ Totals

    @Test
    fun `weekly shop totals`() {
        val totals = Totals.of(weeklyShop)
        assertEquals(4584L, totals.totalCents)
        assertEquals(916L, totals.inBasketCents)
        assertEquals(3668L, totals.totalCents - totals.inBasketCents)
        assertEquals(1, totals.withoutPriceCount)
        assertEquals(10, totals.itemCount)
        assertEquals(3, totals.tickedCount)
        assertFalse(totals.isDone)
        assertFalse(totals.isEmpty)
    }

    @Test
    fun `bbq saturday totals`() {
        val totals = Totals.of(Samples.bbqSaturday)
        assertEquals(10291L, totals.totalCents)
        assertEquals(0L, totals.inBasketCents)
        assertEquals(0, totals.tickedCount)
        assertEquals(0, totals.withoutPriceCount)
        assertEquals(6, totals.itemCount)
    }

    @Test
    fun `empty list totals are zero`() {
        val totals = Totals.of(emptyList())
        assertEquals(ListTotals(0, 0, 0L, 0L, 0), totals)
        assertTrue(totals.isEmpty)
        assertFalse(totals.isDone)
    }

    @Test
    fun `all ticked list is done`() {
        val totals = Totals.of(weeklyShop.map { it.copy(ticked = true) })
        assertTrue(totals.isDone)
        assertEquals(totals.totalCents, totals.inBasketCents)
        assertEquals(4584L, totals.inBasketCents)
    }

    @Test
    fun `progress fraction`() {
        assertEquals(0.3f, Totals.progress(3, 10), 0f)
        assertEquals(0f, Totals.progress(0, 0), 0f)
        assertEquals(1f, Totals.progress(10, 10), 0f)
        assertEquals(0f, Totals.progress(0, 10), 0f)
    }

    // ------------------------------------------------------------------ Default categories

    @Test
    fun `default categories order`() {
        assertEquals((1L..9L).toList(), categories.map { it.id })
        assertEquals((0..8).toList(), categories.map { it.position })
        assertEquals(
            listOf(
                CategoryKey.FRUIT_VEG, CategoryKey.BAKERY, CategoryKey.DAIRY_EGGS, CategoryKey.MEAT_FISH,
                CategoryKey.PANTRY, CategoryKey.FROZEN, CategoryKey.DRINKS, CategoryKey.HOUSEHOLD_PETS, CategoryKey.OTHER,
            ),
            Grouping.aisleOrder(categories.shuffled(kotlin.random.Random(3))).map { it.key },
        )
        assertTrue(categories.last().isOther)
        assertEquals(1, categories.count { it.isOther })
    }

    // ------------------------------------------------------------------ Grouping

    @Test
    fun `ticked items move down to in basket`() {
        val groups = Grouping.forShopping(weeklyShop, categories, moveTickedDown = true)
        assertEquals(
            listOf(CategoryKey.FRUIT_VEG, CategoryKey.BAKERY, CategoryKey.DAIRY_EGGS, CategoryKey.PANTRY, CategoryKey.DRINKS),
            groups.toBuy.map { it.category.key },
        )
        assertEquals(listOf("Apple", "Cucumber", "Strawberry"), names(groups.toBuy[0].items))
        assertEquals(listOf("Sourdough bread"), names(groups.toBuy[1].items))
        assertEquals(listOf("Milk"), names(groups.toBuy[2].items))
        assertEquals(listOf("Rice"), names(groups.toBuy[3].items))
        assertEquals(listOf("Nescafe Coffee"), names(groups.toBuy[4].items))
        assertEquals(7, groups.toBuy.sumOf { it.items.size })
        assertEquals(listOf("Eggs", "Potatoes", "Tissue Paper Box"), names(groups.inBasket))
    }

    @Test
    fun `ticked items stay in their aisle when not moved down`() {
        val groups = Grouping.forShopping(weeklyShop, categories, moveTickedDown = false)
        assertTrue(groups.inBasket.isEmpty())
        assertEquals(
            listOf(
                CategoryKey.FRUIT_VEG, CategoryKey.BAKERY, CategoryKey.DAIRY_EGGS, CategoryKey.PANTRY,
                CategoryKey.DRINKS, CategoryKey.HOUSEHOLD_PETS,
            ),
            groups.toBuy.map { it.category.key },
        )
        assertEquals(listOf("Apple", "Cucumber", "Potatoes", "Strawberry"), names(groups.toBuy[0].items))
        assertEquals(listOf("Eggs", "Milk"), names(groups.toBuy[2].items))
        assertEquals(listOf("Tissue Paper Box"), names(groups.toBuy.last().items))
        assertEquals(10, groups.toBuy.sumOf { it.items.size })
    }

    @Test
    fun `sections follow category position not name`() {
        val reordered = categories.map {
            when (it.key) {
                CategoryKey.DRINKS -> it.copy(position = -1)
                CategoryKey.FRUIT_VEG -> it.copy(position = 4)
                CategoryKey.PANTRY -> it.copy(position = 0)
                else -> it
            }
        }
        val groups = Grouping.forShopping(weeklyShop, reordered, moveTickedDown = true)
        assertEquals(
            listOf(CategoryKey.DRINKS, CategoryKey.PANTRY, CategoryKey.BAKERY, CategoryKey.DAIRY_EGGS, CategoryKey.FRUIT_VEG),
            groups.toBuy.map { it.category.key },
        )
    }

    @Test
    fun `renamed categories keep their position`() {
        val renamed = categories.map { if (it.key == CategoryKey.FRUIT_VEG) it.copy(name = "Zucchini corner") else it }
        val groups = Grouping.forShopping(weeklyShop, renamed, moveTickedDown = true)
        assertEquals(CategoryKey.FRUIT_VEG, groups.toBuy.first().category.key)
        assertEquals("Zucchini corner", groups.toBuy.first().category.name)
    }

    @Test
    fun `empty categories are hidden`() {
        val groups = Grouping.forShopping(Samples.bbqSaturday, categories, moveTickedDown = true)
        assertEquals(
            listOf(CategoryKey.FRUIT_VEG, CategoryKey.MEAT_FISH, CategoryKey.DRINKS, CategoryKey.OTHER),
            groups.toBuy.map { it.category.key },
        )
        assertTrue(groups.toBuy.all { it.items.isNotEmpty() })
    }

    @Test
    fun `other is always last`() {
        val otherFirst = categories.map { if (it.isOther) it.copy(position = -10) else it }
        assertTrue(Grouping.aisleOrder(otherFirst).last().isOther)
        val groups = Grouping.forShopping(Samples.bbqSaturday, otherFirst, moveTickedDown = true)
        assertEquals(CategoryKey.OTHER, groups.toBuy.last().category.key)
        assertEquals(listOf("Charcoal"), names(groups.toBuy.last().items))
    }

    @Test
    fun `items with an unknown category go to other`() {
        val orphan = ListItem(id = 99, listId = 1, name = "Batteries", quantity = 1, unitPriceCents = 499, categoryId = 1234)
        val groups = Grouping.forShopping(weeklyShop + orphan, categories, moveTickedDown = true)
        val last = groups.toBuy.last()
        assertEquals(CategoryKey.OTHER, last.category.key)
        assertEquals(listOf("Batteries"), names(last.items))
    }

    @Test
    fun `walking order follows aisles then names`() {
        assertEquals(
            listOf(
                "Apple", "Cucumber", "Potatoes", "Strawberry", "Sourdough bread", "Eggs", "Milk", "Rice",
                "Nescafe Coffee", "Tissue Paper Box",
            ),
            names(Grouping.walkingOrder(weeklyShop, categories)),
        )
    }

    // ------------------------------------------------------------------ Share

    @Test
    fun `share text of the weekly shop`() {
        val expected = listOf(
            "Weekly shop",
            "To buy (7)",
            "• Apple × 6 — $10.44",
            "• Cucumber × 2 — $2.98",
            "• Strawberry — $3.95",
            "• Sourdough bread (sliced)",
            "• Milk × 2 — $6.02",
            "• Rice — $5.43",
            "• Nescafe Coffee — $7.86",
            "In basket (3)",
            "✓ Potatoes × 2 — $4.34",
            "✓ Eggs — $2.66",
            "✓ Tissue Paper Box — $2.16",
            "Total $45.84 (1 item without price)",
        ).joinToString("\n")
        assertEquals(expected, Share.text("Weekly shop", weeklyShop, categories, Samples.englishShareLabels, usd))
    }

    @Test
    fun `share text of an empty list`() {
        assertEquals(
            "Camping trip\nTotal $0.00",
            Share.text("Camping trip", emptyList(), categories, Samples.englishShareLabels, usd),
        )
    }

    @Test
    fun `share text with nothing ticked has no in basket part`() {
        val text = Share.text("BBQ Saturday", Samples.bbqSaturday, categories, Samples.englishShareLabels, usd)
        val lines = text.split("\n")
        assertEquals("BBQ Saturday", lines.first())
        assertEquals("To buy (6)", lines[1])
        assertFalse(text.contains("In basket"))
        assertEquals("• Charcoal — $8.50", lines[lines.size - 2])
        assertEquals("Total $102.91", lines.last())
        assertFalse(text.endsWith("\n"))
    }

    @Test
    fun `share labels count items without price`() {
        assertEquals("Total $1.00 (2 items without price)", Samples.englishShareLabels.total("$1.00", 2))
        assertNull(Money.lineTotalCents(weeklyShop.single { it.name == "Sourdough bread" }.unitPriceCents, 1))
    }
}
