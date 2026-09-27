package com.basket.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules applied to the saved DummyJSON groceries response (27 products). */
class CatalogTest {

    private data class Product(
        val id: Int,
        val title: String,
        val price: Double,
        val discountPercentage: Double,
        val tags: List<String>,
        val availabilityStatus: String,
    )

    private data class Expected(
        val title: String,
        val category: CategoryKey,
        val badge: Int?,
        val payCents: Long,
        val availability: Availability = Availability.IN_STOCK,
    )

    private val root: JsonObject by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream("/groceries.json")) { "groceries.json missing" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        Json.parseToJsonElement(text).jsonObject
    }

    private val products: List<Product> by lazy {
        root.getValue("products").jsonArray.map { element ->
            val o = element.jsonObject
            Product(
                id = o.getValue("id").jsonPrimitive.int,
                title = o.getValue("title").jsonPrimitive.content,
                price = o.getValue("price").jsonPrimitive.double,
                discountPercentage = o.getValue("discountPercentage").jsonPrimitive.double,
                tags = o.getValue("tags").jsonArray.map { it.jsonPrimitive.content },
                availabilityStatus = o.getValue("availabilityStatus").jsonPrimitive.content,
            )
        }
    }

    private val expected = listOf(
        Expected("Apple", CategoryKey.FRUIT_VEG, 13, 174),
        Expected("Beef Steak", CategoryKey.MEAT_FISH, 10, 1174),
        Expected("Cat Food", CategoryKey.HOUSEHOLD_PETS, 10, 813),
        Expected("Chicken Meat", CategoryKey.MEAT_FISH, 14, 862),
        Expected("Cooking Oil", CategoryKey.PANTRY, 9, 452),
        Expected("Cucumber", CategoryKey.FRUIT_VEG, null, 149),
        Expected("Dog Food", CategoryKey.HOUSEHOLD_PETS, 10, 986),
        Expected("Eggs", CategoryKey.DAIRY_EGGS, 11, 266),
        Expected("Fish Steak", CategoryKey.MEAT_FISH, 4, 1436),
        Expected("Green Bell Pepper", CategoryKey.FRUIT_VEG, null, 129),
        Expected("Green Chili Pepper", CategoryKey.FRUIT_VEG, 1, 98, Availability.LOW_STOCK),
        Expected("Honey Jar", CategoryKey.PANTRY, 14, 598),
        Expected("Ice Cream", CategoryKey.FROZEN, 9, 501),
        Expected("Juice", CategoryKey.DRINKS, 12, 351),
        Expected("Kiwi", CategoryKey.FRUIT_VEG, 15, 211),
        Expected("Lemon", CategoryKey.FRUIT_VEG, 10, 71),
        Expected("Milk", CategoryKey.DAIRY_EGGS, 14, 301),
        Expected("Mulberry", CategoryKey.FRUIT_VEG, 13, 435),
        Expected("Nescafe Coffee", CategoryKey.DRINKS, 2, 786),
        Expected("Potatoes", CategoryKey.FRUIT_VEG, 5, 217),
        Expected("Protein Powder", CategoryKey.PANTRY, 8, 1847),
        Expected("Red Onions", CategoryKey.FRUIT_VEG, 10, 179),
        Expected("Rice", CategoryKey.PANTRY, 9, 543),
        Expected("Soft Drinks", CategoryKey.DRINKS, 17, 164),
        Expected("Strawberry", CategoryKey.FRUIT_VEG, 1, 395),
        Expected("Tissue Paper Box", CategoryKey.HOUSEHOLD_PETS, 13, 216),
        Expected("Water", CategoryKey.DRINKS, 15, 84),
    )

    private fun product(title: String): Product = products.single { it.title == title }

    @Test
    fun `response has 27 products`() {
        assertEquals(27, products.size)
        assertEquals(27, root.getValue("total").jsonPrimitive.int)
        assertEquals(expected.map { it.title }.toSet(), products.map { it.title }.toSet())
    }

    @Test
    fun `price you pay for every product`() {
        expected.forEach { e ->
            val p = product(e.title)
            assertEquals(e.title, e.payCents, Money.priceYouPayCents(p.price, p.discountPercentage))
        }
    }

    @Test
    fun `discount badge for every product`() {
        expected.forEach { e ->
            assertEquals(e.title, e.badge, Money.discountBadgePercent(product(e.title).discountPercentage))
        }
    }

    @Test
    fun `category for every product`() {
        expected.forEach { e ->
            assertEquals(e.title, e.category, Catalog.categoryFor(product(e.title).tags))
        }
    }

    @Test
    fun `availability for every product`() {
        expected.forEach { e ->
            assertEquals(e.title, e.availability, Catalog.availability(product(e.title).availabilityStatus))
        }
        assertEquals(listOf("Green Chili Pepper"), products.filter { Catalog.availability(it.availabilityStatus) == Availability.LOW_STOCK }.map { it.title })
    }

    @Test
    fun `price you pay formats in dollars`() {
        assertEquals("$1.74", Money.format(Money.priceYouPayCents(1.99, 12.62), java.util.Locale.US))
        assertEquals("$0.98", Money.format(Money.priceYouPayCents(0.99, 1.0), java.util.Locale.US))
        assertEquals("$18.47", Money.format(Money.priceYouPayCents(19.99, 7.59), java.util.Locale.US))
    }

    @Test
    fun `catalog price of Apple is 199 cents`() {
        assertEquals(199L, Money.cents(product("Apple").price))
    }

    @Test
    fun `products sort A to Z from Apple to Water`() {
        val sorted = products.shuffled(kotlin.random.Random(7)).sortedWith(Sorting.byName<Product> { it.title })
        assertEquals("Apple", sorted.first().title)
        assertEquals("Water", sorted.last().title)
        assertEquals(expected.map { it.title }, sorted.map { it.title })
    }

    @Test
    fun `availability maps the exact API strings`() {
        assertEquals(Availability.IN_STOCK, Catalog.availability("In Stock"))
        assertEquals(Availability.LOW_STOCK, Catalog.availability("Low Stock"))
        assertEquals(Availability.OUT_OF_STOCK, Catalog.availability("Out of Stock"))
    }

    @Test
    fun `unknown availability counts as in stock`() {
        assertEquals(Availability.IN_STOCK, Catalog.availability("Low stock"))
        assertEquals(Availability.IN_STOCK, Catalog.availability("out of stock"))
        assertEquals(Availability.IN_STOCK, Catalog.availability(""))
        assertEquals(Availability.IN_STOCK, Catalog.availability(null))
    }

    @Test
    fun `category tags map to aisles`() {
        assertEquals(CategoryKey.HOUSEHOLD_PETS, Catalog.categoryFor(listOf("pet supplies", "cat food")))
        assertEquals(CategoryKey.DRINKS, Catalog.categoryFor(listOf("beverages", "coffee")))
        assertEquals(CategoryKey.FRUIT_VEG, Catalog.categoryFor(listOf("Vegetables ")))
        assertEquals(CategoryKey.OTHER, Catalog.categoryFor(listOf("charcoal")))
        assertEquals(CategoryKey.OTHER, Catalog.categoryFor(emptyList()))
    }

    @Test
    fun `rating rounds to one decimal`() {
        assertEquals(4.2, Catalog.rating(4.19), 0.0)
        assertEquals(4.0, Catalog.rating(4.0), 0.0)
        assertEquals(3.5, Catalog.rating(3.45), 0.0)
    }

    @Test
    fun `search matches title ignoring case and accents`() {
        assertTrue(Catalog.matches("Nescafe Coffee", "NESCAF"))
        assertTrue(Catalog.matches("Nescafe Coffee", "nescafé"))
        assertTrue(Catalog.matches("Apple", "  "))
        assertEquals(false, Catalog.matches("Apple", "milk"))
    }

    @Test
    fun `search finds the peppers`() {
        val found = products.filter { Catalog.matches(it.title, "pepper") }.map { it.title }.sorted()
        assertEquals(listOf("Green Bell Pepper", "Green Chili Pepper"), found)
    }
}
