package com.basket.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class BasketRulesTest {

    private val spanish = Locale("es")

    // ------------------------------------------------------------------ Money

    @Test
    fun `catalog price converts to whole cents`() {
        assertEquals(199L, Money.cents(1.99))
        assertEquals(1299L, Money.cents(12.99))
        assertEquals(99L, Money.cents(0.99))
        assertEquals(500L, Money.cents(5.0))
    }

    @Test
    fun `price you pay applies the discount and rounds halves up`() {
        assertEquals(174L, Money.priceYouPayCents(1.99, 12.62))
        assertEquals(266L, Money.priceYouPayCents(2.99, 11.05))
        assertEquals(862L, Money.priceYouPayCents(9.99, 13.7))
        assertEquals(149L, Money.priceYouPayCents(1.49, 0.16))
    }

    @Test
    fun `price you pay without discount is the catalog price`() {
        assertEquals(199L, Money.priceYouPayCents(1.99, 0.0))
    }

    @Test
    fun `discount badge rounds to whole percent`() {
        assertEquals(13, Money.discountBadgePercent(12.62))
        assertEquals(14, Money.discountBadgePercent(13.7))
        assertEquals(1, Money.discountBadgePercent(1.0))
    }

    @Test
    fun `discount badge is hidden when it rounds to zero`() {
        assertNull(Money.discountBadgePercent(0.16))
        assertNull(Money.discountBadgePercent(0.0))
    }

    @Test
    fun `line total multiplies unit price by quantity`() {
        assertEquals(1044L, Money.lineTotalCents(174, 6))
        assertEquals(174L, Money.lineTotalCents(174, 1))
    }

    @Test
    fun `line total is null without a price`() {
        assertNull(Money.lineTotalCents(null, 6))
    }

    @Test
    fun `money formats US dollars in US English`() {
        assertEquals("$1.74", Money.format(174, Locale.US))
        assertEquals("$1,234.56", Money.format(123456, Locale.US))
        assertEquals("$0.00", Money.format(0, Locale.US))
    }

    @Test
    fun `money formats US dollars in Spanish`() {
        val text = normalizeSpaces(Money.format(174, Locale("es", "ES")))
        assertTrue(text, text.contains("1,74"))
        assertTrue(text, text.contains("US$"))
        assertEquals("1,74 US$", text)
    }

    @Test
    fun `money uses Western digits in Arabic`() {
        val text = Money.format(174, Locale("ar"))
        assertTrue(text, text.none { it in '٠'..'٩' })
        assertTrue(text, text.contains("74"))
    }

    // ------------------------------------------------------------------ Quantity

    @Test
    fun `quantity clamps to 1 through 99`() {
        assertEquals(1, Quantity.clamp(0))
        assertEquals(1, Quantity.clamp(-5))
        assertEquals(99, Quantity.clamp(100))
        assertEquals(42, Quantity.clamp(42))
    }

    @Test
    fun `stepper stops at 1 and 99`() {
        assertEquals(1, Quantity.step(1, -1))
        assertEquals(99, Quantity.step(99, 1))
        assertEquals(3, Quantity.step(2, 1))
        assertEquals(1, Quantity.step(2, -1))
    }

    @Test
    fun `stepper buttons are disabled at the limits`() {
        assertFalse(Quantity.canDecrease(1))
        assertTrue(Quantity.canDecrease(2))
        assertFalse(Quantity.canIncrease(99))
        assertTrue(Quantity.canIncrease(98))
    }

    @Test
    fun `duplicate quantities add up to at most 99`() {
        assertEquals(7, Quantity.add(6, 1))
        assertEquals(99, Quantity.add(98, 5))
    }

    @Test
    fun `typed quantity must be a whole number from 1 to 99`() {
        assertNull(Quantity.parse("0"))
        assertNull(Quantity.parse("-1"))
        assertEquals(42, Quantity.parse("42"))
        assertEquals(1, Quantity.parse("1"))
        assertEquals(99, Quantity.parse("99"))
        assertNull(Quantity.parse("100"))
        assertNull(Quantity.parse(""))
        assertNull(Quantity.parse("abc"))
        assertNull(Quantity.parse("1.5"))
    }

    // ------------------------------------------------------------------ Price input

    @Test
    fun `price field reads the English decimal point`() {
        assertEquals(PriceInput.Valid(199), PriceInputs.parse("1.99", Locale.ENGLISH))
    }

    @Test
    fun `price field reads the Spanish decimal comma`() {
        assertEquals(PriceInput.Valid(199), PriceInputs.parse("1,99", spanish))
    }

    @Test
    fun `price field rejects a comma in English`() {
        assertEquals(PriceInput.Invalid, PriceInputs.parse("1,99", Locale.ENGLISH))
    }

    @Test
    fun `blank price field means no price`() {
        assertSame(PriceInput.Empty, PriceInputs.parse("", Locale.ENGLISH))
        assertSame(PriceInput.Empty, PriceInputs.parse("  ", Locale.ENGLISH))
    }

    @Test
    fun `price field accepts 0_01 to 9999_99`() {
        assertEquals(PriceInput.Valid(1), PriceInputs.parse("0.01", Locale.ENGLISH))
        assertEquals(PriceInput.Valid(999_999), PriceInputs.parse("9999.99", Locale.ENGLISH))
        assertEquals(PriceInput.Valid(500), PriceInputs.parse("5", Locale.ENGLISH))
        assertEquals(PriceInput.Valid(150), PriceInputs.parse("1.5", Locale.ENGLISH))
        assertEquals(PriceInput.Valid(199), PriceInputs.parse(" 1.99 ", Locale.ENGLISH))
    }

    @Test
    fun `price field rejects out of range and malformed values`() {
        assertEquals(PriceInput.Invalid, PriceInputs.parse("0", Locale.ENGLISH))
        assertEquals(PriceInput.Invalid, PriceInputs.parse("0.00", Locale.ENGLISH))
        assertEquals(PriceInput.Invalid, PriceInputs.parse("10000", Locale.ENGLISH))
        assertEquals(PriceInput.Invalid, PriceInputs.parse("12345", Locale.ENGLISH))
        assertEquals(PriceInput.Invalid, PriceInputs.parse("1.999", Locale.ENGLISH))
        assertEquals(PriceInput.Invalid, PriceInputs.parse("-1", Locale.ENGLISH))
        assertEquals(PriceInput.Invalid, PriceInputs.parse("abc", Locale.ENGLISH))
    }

    @Test
    fun `price field text uses the language decimal separator`() {
        assertEquals("1.74", PriceInputs.toFieldText(174, Locale.ENGLISH))
        assertEquals("1,74", PriceInputs.toFieldText(174, spanish))
        assertEquals("0.05", PriceInputs.toFieldText(5, Locale.ENGLISH))
        assertEquals("9999.99", PriceInputs.toFieldText(999_999, Locale.ENGLISH))
    }

    @Test
    fun `price field text parses back to the same cents`() {
        listOf(1L, 99L, 174L, 1174L, 999_999L).forEach { cents ->
            assertEquals(PriceInput.Valid(cents), PriceInputs.parse(PriceInputs.toFieldText(cents, Locale.ENGLISH), Locale.ENGLISH))
            assertEquals(PriceInput.Valid(cents), PriceInputs.parse(PriceInputs.toFieldText(cents, spanish), spanish))
        }
    }

    // ------------------------------------------------------------------ Names

    @Test
    fun `names are trimmed and must not be blank`() {
        assertEquals("Milk", Names.clean("  Milk  "))
        assertFalse(Names.isValid("   "))
        assertFalse(Names.isValid(""))
        assertTrue(Names.isValid(" a "))
    }

    @Test
    fun `same name ignores case and spaces`() {
        assertTrue(Names.sameName(" milk ", "Milk"))
        assertTrue(Names.sameName("Green  Bell Pepper", "green bell pepper"))
        assertFalse(Names.sameName("Milk", "Oat milk"))
    }

    @Test
    fun `name length limits`() {
        assertEquals(40, Names.LIST_MAX)
        assertEquals(60, Names.ITEM_MAX)
        assertEquals(80, Names.NOTE_MAX)
        assertEquals(30, Names.CATEGORY_MAX)
    }

    // ------------------------------------------------------------------ Sorting

    @Test
    fun `sorting ignores case`() {
        val sorted = listOf("banana", "Apple", "Cherry", "apple").sortedWith(Sorting.byName { it })
        assertEquals(setOf("Apple", "apple"), sorted.take(2).toSet())
        assertEquals(listOf("banana", "Cherry"), sorted.drop(2))
    }

    @Test
    fun `sorting ignores accents`() {
        val sorted = listOf("Fig", "Éclair", "Donut").sortedWith(Sorting.byName { it })
        assertEquals(listOf("Donut", "Éclair", "Fig"), sorted)
        assertEquals("eclair", Sorting.key("Éclair"))
    }

    @Test
    fun `items with the same name keep a stable order by id`() {
        val a = ListItem(id = 2, listId = 1, name = "milk", quantity = 1, unitPriceCents = null, categoryId = 3)
        val b = ListItem(id = 1, listId = 1, name = "Milk", quantity = 1, unitPriceCents = null, categoryId = 3)
        assertEquals(listOf(b, a), listOf(a, b).sortedWith(Sorting.itemsByName))
    }

    @Test
    fun `lists show the most recently changed first`() {
        val older = ShoppingList(1, "Weekly shop", updatedAt = 1_000)
        val newest = ShoppingList(2, "BBQ Saturday", updatedAt = 3_000)
        val middle = ShoppingList(3, "Camping trip", updatedAt = 2_000)
        assertEquals(listOf(newest, middle, older), Sorting.lists(listOf(older, newest, middle)))
    }

    // ------------------------------------------------------------------ Duplicates

    @Test
    fun `duplicate is found by catalog product`() {
        val found = Duplicates.find(Samples.weeklyShop, "Something else", catalogProductId = 16)
        assertEquals("Apple", found?.name)
    }

    @Test
    fun `duplicate is found by name ignoring case and outer spaces`() {
        val found = Duplicates.find(Samples.weeklyShop, " milk ")
        assertEquals("Milk", found?.name)
    }

    @Test
    fun `different name is not a duplicate`() {
        assertNull(Duplicates.find(Samples.weeklyShop, "Oat milk"))
        assertNull(Duplicates.find(Samples.weeklyShop, "Butter", catalogProductId = 999))
    }

    @Test
    fun `duplicate from catalog adds to the existing quantity`() {
        val existing = Duplicates.find(Samples.weeklyShop, "Apple", catalogProductId = 16)!!
        assertEquals(7, Quantity.add(existing.quantity, 1))
    }

    // ------------------------------------------------------------------ Dates

    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    private val sampleMillis: Long =
        LocalDateTime.of(2026, 9, 25, 9, 14).atZone(madrid).toInstant().toEpochMilli()

    @Test
    fun `short date and time in English`() {
        assertEquals("25 Sep, 09:14", Dates.shortDateTime(sampleMillis, madrid, Locale.ENGLISH))
    }

    @Test
    fun `short date and time in Spanish`() {
        val text = normalizeSpaces(Dates.shortDateTime(sampleMillis, madrid, spanish))
        assertTrue(text, text.startsWith("25 sept"))
        assertTrue(text, text.endsWith(", 09:14"))
        assertFalse(text, text.contains("."))
    }

    @Test
    fun `short date and time uses Western digits in Arabic`() {
        val text = Dates.shortDateTime(sampleMillis, madrid, Locale("ar"))
        assertTrue(text, text.none { it in '٠'..'٩' })
        assertTrue(text, text.contains("09:14"))
    }

    @Test
    fun `short date for the offline banner`() {
        assertEquals("25 Sep", Dates.shortDate(sampleMillis, madrid, Locale.ENGLISH))
        assertTrue(normalizeSpaces(Dates.shortDate(sampleMillis, madrid, spanish)).startsWith("25 sept"))
    }

    private fun normalizeSpaces(text: String): String = text.replace(' ', ' ').replace(' ', ' ')
}
