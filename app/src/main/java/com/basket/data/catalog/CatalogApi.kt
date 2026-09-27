package com.basket.data.catalog

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** DummyJSON products API (https://dummyjson.com/docs/products). */
interface CatalogApi {

    @GET("products")
    suspend fun products(): ProductsResponse

    @GET("products/search")
    suspend fun search(@Query("q") query: String): ProductsResponse
}

@Serializable
data class ProductsResponse(
    val products: List<ProductDto>,
    val total: Int = 0,
    val skip: Int = 0,
    val limit: Int = 0,
)

@Serializable
data class ProductDto(
    val id: Int,
    val title: String,
    val description: String = "",
    val category: String = "",
    val price: Double,
    val discountPercentage: Double = 0.0,
    val rating: Double = 0.0,
    val stock: Int = 0,
    val tags: List<String> = emptyList(),
    val availabilityStatus: String? = null,
    val minimumOrderQuantity: Int = 1,
    val thumbnail: String? = null,
    val images: List<String> = emptyList(),
)

fun ProductDto.toProduct() = CatalogProduct(
    id = id,
    title = title,
    description = description,
    price = price,
    discountPercentage = discountPercentage,
    rating = rating,
    availabilityStatus = availabilityStatus,
    tags = tags,
    thumbnailUrl = thumbnail,
    imageUrl = images.firstOrNull() ?: thumbnail,
    minimumOrderQuantity = minimumOrderQuantity,
)
