package com.basket.data.catalog

import com.basket.data.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The product catalog. Keeps the last download so Browse can show it at once while it refreshes, and so
 * Product detail and the item form can look products up without another network call.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: CatalogApi,
    private val clock: AppClock,
) {
    private val _products = MutableStateFlow<List<CatalogProduct>?>(null)

    /** Last downloaded catalog, or null before the first successful download. */
    val products: StateFlow<List<CatalogProduct>?> = _products.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long?>(null)

    /** Epoch millis of the last successful download. */
    val lastUpdated: StateFlow<Long?> = _lastUpdated.asStateFlow()

    private val known = ConcurrentHashMap<Int, CatalogProduct>()

    /**
     * Downloads the catalog and keeps it. Throws [java.io.IOException] when there is no connection or the request
     * times out, [retrofit2.HttpException] for an HTTP error and a serialization exception for an unreadable reply.
     */
    suspend fun refresh(): List<CatalogProduct> {
        val products = api.products().products.map { it.toProduct() }
        products.forEach { known[it.id] = it }
        _products.value = products
        _lastUpdated.value = clock.now()
        return products
    }

    /** Products whose title matches [query]. */
    suspend fun search(query: String): List<CatalogProduct> {
        val products = api.search(query).products.map { it.toProduct() }
        products.forEach { known[it.id] = it }
        return products
    }

    /** A product from the last download or search, if any. */
    fun product(id: Int): CatalogProduct? = known[id]
}
