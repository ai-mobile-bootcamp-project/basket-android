package com.basket.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/*
 * Basket product rules — the single implementation of every number, order and message rule in the design brief
 * ("Product rules"). Pure Kotlin + java.* so it runs on the JVM; see BasketRulesTest.kt for the examples.
 */

// ------------------------------------------------------------------ Locale

object Locales {
    /** Western digits (0–9) in every language, including Arabic. */
    fun withLatinDigits(locale: Locale): Locale =
        if (locale.language == "ar") Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build() else locale
}

// ------------------------------------------------------------------ Money

object Money {
    private val USD: Currency = Currency.getInstance("USD")

    /** Catalog price in dollars → whole cents, halves up (1.99 → 199). */
    fun cents(price: Double): Long =
        BigDecimal(price.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

    /**
     * Price you pay: catalog price × (1 − discount ÷ 100), rounded to the nearest cent, halves up.
     * Apple 1.99 − 12.62% = 174 · Eggs 2.99 − 11.05% = 266 · Chicken Meat 9.99 − 13.7% = 862.
     */
    fun priceYouPayCents(price: Double, discountPercentage: Double): Long {
        val factor = BigDecimal(100).subtract(BigDecimal(discountPercentage.toString())).divide(BigDecimal(100))
        return BigDecimal(price.toString()).multiply(factor)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    /** Discount badge: whole percent, halves up; null when it rounds to 0 (12.62 → 13, 0.16 → null). */
    fun discountBadgePercent(discountPercentage: Double): Int? {
        val rounded = BigDecimal(discountPercentage.toString()).setScale(0, RoundingMode.HALF_UP).toInt()
        return rounded.takeIf { it > 0 }
    }

    /** Line total = unit price × quantity; null when the item has no price. */
    fun lineTotalCents(unitPriceCents: Long?, quantity: Int): Long? = unitPriceCents?.let { it * quantity }

    /** US dollars in the language's format, Western digits: "$1,234.56" · "1234,56 US$". */
    fun format(cents: Long, locale: Locale): String {
        val format = NumberFormat.getCurrencyInstance(Locales.withLatinDigits(locale))
        format.currency = USD
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return format.format(BigDecimal.valueOf(cents, 2))
    }
}

// ------------------------------------------------------------------ Quantity

object Quantity {
    const val MIN = 1
    const val MAX = 99

    fun clamp(quantity: Int): Int = quantity.coerceIn(MIN, MAX)

    /** Stepper: − and + stop at 1 and 99. */
    fun step(quantity: Int, delta: Int): Int = clamp(quantity + delta)

    fun canDecrease(quantity: Int): Boolean = quantity > MIN

    fun canIncrease(quantity: Int): Boolean = quantity < MAX

    /** Duplicates: quantities add up to at most 99. */
    fun add(current: Int, added: Int): Int = clamp(current + added)

    /** Typed quantity: a whole number 1–99, otherwise null. */
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN..MAX }
}

// ------------------------------------------------------------------ Price input

object PriceInputs {
    const val MIN_CENTS = 1L
    const val MAX_CENTS = 999_999L

    fun decimalSeparator(locale: Locale): Char = DecimalFormatSymbols.getInstance(Locales.withLatinDigits(locale)).decimalSeparator

    /**
     * Reads the Unit price field with the decimal separator of the app language: English "1.99" and Spanish "1,99"
     * both mean 199 cents. Up to 2 decimals, 0.01–9,999.99. Blank means "no price".
     */
    fun parse(text: String, locale: Locale): PriceInput {
        val input = text.trim()
        if (input.isEmpty()) return PriceInput.Empty
        val separator = Regex.escape(decimalSeparator(locale).toString())
        val match = Regex("^(\\d{1,7})(?:$separator(\\d{0,2}))?$").matchEntire(input) ?: return PriceInput.Invalid
        val whole = match.groupValues[1].toLong()
        val fraction = match.groupValues[2].padEnd(2, '0').toLong()
        val cents = whole * 100 + fraction
        return if (cents in MIN_CENTS..MAX_CENTS) PriceInput.Valid(cents) else PriceInput.Invalid
    }

    /** Cents → the text the Unit price field shows when editing: "1.74" · "1,74". */
    fun toFieldText(cents: Long, locale: Locale): String =
        "${cents / 100}${decimalSeparator(locale)}${(cents % 100).toString().padStart(2, '0')}"
}

// ------------------------------------------------------------------ Names

object Names {
    const val LIST_MAX = 40
    const val ITEM_MAX = 60
    const val NOTE_MAX = 80
    const val CATEGORY_MAX = 30

    /** Names are trimmed and need at least one non-space character. */
    fun clean(name: String): String = name.trim()

    fun isValid(name: String): Boolean = name.isNotBlank()

    /** Same name ignoring case and spaces: " milk " and "Milk" match. */
    fun sameName(a: String, b: String): Boolean = matchKey(a) == matchKey(b)

    private fun matchKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}

// ------------------------------------------------------------------ Sorting

object Sorting {
    private val marks = Regex("\\p{Mn}+")

    /** Sort key ignoring case and accents: "Éclair" sorts with "eclair". */
    fun key(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replace(marks, "").lowercase(Locale.ROOT)

    /** Name A→Z, ignoring case and accents. */
    fun <T> byName(name: (T) -> String): Comparator<T> = compareBy { key(name(it)) }

    val itemsByName: Comparator<ListItem> = byName<ListItem> { it.name }.thenBy { it.id }

    /** Lists: most recently changed first. */
    fun lists(lists: List<ShoppingList>): List<ShoppingList> = lists.sortedByDescending { it.updatedAt }
}

// ------------------------------------------------------------------ Totals and progress

object Totals {
    fun of(items: List<ListItem>): ListTotals = ListTotals(
        itemCount = items.size,
        tickedCount = items.count { it.ticked },
        totalCents = items.sumOf { Money.lineTotalCents(it.unitPriceCents, it.quantity) ?: 0L },
        inBasketCents = items.filter { it.ticked }.sumOf { Money.lineTotalCents(it.unitPriceCents, it.quantity) ?: 0L },
        withoutPriceCount = items.count { it.unitPriceCents == null },
    )

    /** Progress bar fraction 0–1: 3 of 10 = 0.3; an empty list has no progress. */
    fun progress(done: Int, total: Int): Float = if (total <= 0) 0f else done.coerceIn(0, total).toFloat() / total
}

// ------------------------------------------------------------------ Grouping

object Grouping {
    /** Categories in aisle order; "Other" is always last. */
    fun aisleOrder(categories: List<Category>): List<Category> =
        categories.sortedWith(compareBy<Category> { it.isOther }.thenBy { it.position })

    /**
     * To buy: one section per category in the aisle order from Categories, empty categories hidden, items A→Z.
     * With [moveTickedDown] ticked items go to In basket (A→Z); otherwise they stay in their aisle.
     * Items whose category no longer exists are shown under Other.
     */
    fun forShopping(items: List<ListItem>, categories: List<Category>, moveTickedDown: Boolean): ShoppingGroups {
        val ordered = aisleOrder(categories)
        val other = ordered.firstOrNull { it.isOther }
        val known = ordered.map { it.id }.toSet()
        val toBuyItems = if (moveTickedDown) items.filterNot { it.ticked } else items
        val byCategory = toBuyItems.groupBy { item ->
            if (item.categoryId in known || other == null) item.categoryId else other.id
        }
        val sections = ordered.mapNotNull { category ->
            byCategory[category.id]?.takeIf { it.isNotEmpty() }?.let { Section(category, it.sortedWith(Sorting.itemsByName)) }
        }
        val inBasket = if (moveTickedDown) items.filter { it.ticked }.sortedWith(Sorting.itemsByName) else emptyList()
        return ShoppingGroups(sections, inBasket)
    }

    /** Items in the order the user walks the store: aisle position, then name. */
    fun walkingOrder(items: List<ListItem>, categories: List<Category>): List<ListItem> {
        val ordered = aisleOrder(categories)
        val rank = ordered.withIndex().associate { (index, category) -> category.id to index }
        val otherRank = ordered.indexOfFirst { it.isOther }.takeIf { it >= 0 } ?: Int.MAX_VALUE
        return items.sortedWith(compareBy<ListItem> { rank[it.categoryId] ?: otherRank }.then(Sorting.itemsByName))
    }
}

// ------------------------------------------------------------------ Duplicates

object Duplicates {
    /** Same catalog product, or same name ignoring case and outer spaces → the existing row. */
    fun find(items: List<ListItem>, name: String, catalogProductId: Int? = null): ListItem? =
        (catalogProductId?.let { id -> items.firstOrNull { it.catalogProductId == id } })
            ?: items.firstOrNull { Names.sameName(it.name, name) }
}

// ------------------------------------------------------------------ Catalog

object Catalog {
    /** DummyJSON `availabilityStatus` has exactly these values. Anything else counts as in stock. */
    fun availability(status: String?): Availability = when (status) {
        "In Stock" -> Availability.IN_STOCK
        "Low Stock" -> Availability.LOW_STOCK
        "Out of Stock" -> Availability.OUT_OF_STOCK
        else -> Availability.IN_STOCK
    }

    private val tagCategories: Map<String, CategoryKey> = mapOf(
        "fruits" to CategoryKey.FRUIT_VEG,
        "vegetables" to CategoryKey.FRUIT_VEG,
        "dairy" to CategoryKey.DAIRY_EGGS,
        "meat" to CategoryKey.MEAT_FISH,
        "seafood" to CategoryKey.MEAT_FISH,
        "cooking essentials" to CategoryKey.PANTRY,
        "condiments" to CategoryKey.PANTRY,
        "grains" to CategoryKey.PANTRY,
        "health supplements" to CategoryKey.PANTRY,
        "desserts" to CategoryKey.FROZEN,
        "beverages" to CategoryKey.DRINKS,
        "coffee" to CategoryKey.DRINKS,
        "household essentials" to CategoryKey.HOUSEHOLD_PETS,
        "pet supplies" to CategoryKey.HOUSEHOLD_PETS,
    )

    /** First matching tag wins; anything else → Other. */
    fun categoryFor(tags: List<String>): CategoryKey =
        tags.firstNotNullOfOrNull { tagCategories[it.trim().lowercase(Locale.ROOT)] } ?: CategoryKey.OTHER

    /** Search on the phone: the title contains the query, ignoring case and accents. */
    fun matches(title: String, query: String): Boolean = query.isBlank() || Sorting.key(title).contains(Sorting.key(query))

    /** Rating to one decimal, halves up: 4.19 → 4.2. */
    fun rating(rating: Double): Double = BigDecimal(rating.toString()).setScale(1, RoundingMode.HALF_UP).toDouble()
}

// ------------------------------------------------------------------ Dates

object Dates {
    /** Short date and 24-hour time in the app language: "25 Sep, 09:14" · "25 sept, 09:14". */
    fun shortDateTime(epochMillis: Long, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofPattern("d MMM, HH:mm", Locales.withLatinDigits(locale))
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
            .replace(".", "")

    /** Short date in the app language: "25 Sep" · "25 sept". */
    fun shortDate(epochMillis: Long, zone: ZoneId, locale: Locale): String =
        DateTimeFormatter.ofPattern("d MMM", Locales.withLatinDigits(locale))
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
            .replace(".", "")
}

// ------------------------------------------------------------------ Share

/** Localised pieces of the share text; the app implements this with string resources. */
interface ShareLabels {
    /** "To buy (7)" */
    fun toBuy(count: Int): String

    /** "In basket (3)" */
    fun inBasket(count: Int): String

    /** "Total $45.84 (1 item without price)" — [withoutPrice] may be 0. */
    fun total(formattedTotal: String, withoutPrice: Int): String
}

object Share {
    /**
     * Plain text for the Android share sheet (brief, "Share format"): items in the order the user walks the
     * store, "× n" only above 1, the note in brackets, the line total when there is a price.
     */
    fun text(
        listName: String,
        items: List<ListItem>,
        categories: List<Category>,
        labels: ShareLabels,
        money: (Long) -> String,
    ): String {
        val ordered = Grouping.walkingOrder(items, categories)
        val toBuy = ordered.filterNot { it.ticked }
        val inBasket = ordered.filter { it.ticked }
        val totals = Totals.of(items)
        return buildString {
            appendLine(listName)
            if (toBuy.isNotEmpty()) {
                appendLine(labels.toBuy(toBuy.size))
                toBuy.forEach { appendLine("• " + line(it, money)) }
            }
            if (inBasket.isNotEmpty()) {
                appendLine(labels.inBasket(inBasket.size))
                inBasket.forEach { appendLine("✓ " + line(it, money)) }
            }
            append(labels.total(money(totals.totalCents), totals.withoutPriceCount))
        }
    }

    private fun line(item: ListItem, money: (Long) -> String): String = buildString {
        append(item.name)
        item.note?.takeIf { it.isNotBlank() }?.let { append(" (").append(it.trim()).append(")") }
        if (item.quantity > 1) append(" × ").append(item.quantity)
        Money.lineTotalCents(item.unitPriceCents, item.quantity)?.let { append(" — ").append(money(it)) }
    }
}
