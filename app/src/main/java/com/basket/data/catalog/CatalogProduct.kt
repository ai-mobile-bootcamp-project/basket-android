package com.basket.data.catalog

import com.basket.domain.Availability
import com.basket.domain.Catalog
import com.basket.domain.CategoryKey
import com.basket.domain.Money

/** A product from the DummyJSON groceries catalog. Titles and descriptions are English and never translated. */
data class CatalogProduct(
    val id: Int,
    val title: String,
    val description: String,
    /** Original price in dollars, as sent by the API. */
    val price: Double,
    val discountPercentage: Double,
    val rating: Double,
    /** Raw API value: "In Stock", "Low Stock" or "Out of Stock". */
    val availabilityStatus: String?,
    val tags: List<String>,
    val thumbnailUrl: String?,
    val imageUrl: String?,
    val minimumOrderQuantity: Int,
) {
    /** Price you pay, in cents (rule "Price you pay"). */
    val priceYouPayCents: Long get() = Money.priceYouPayCents(price, discountPercentage)

    /** Original price in cents, shown struck through. */
    val originalPriceCents: Long get() = Money.cents(price)

    /** "−13%" badge value, or null when the discount rounds to 0. */
    val discountBadgePercent: Int? get() = Money.discountBadgePercent(discountPercentage)

    val availability: Availability get() = Catalog.availability(availabilityStatus)

    val categoryKey: CategoryKey get() = Catalog.categoryFor(tags)
}
