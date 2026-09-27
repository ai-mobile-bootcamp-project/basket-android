package com.basket.data.db

import com.basket.domain.Category
import com.basket.domain.CategoryKey
import com.basket.domain.ListItem
import com.basket.domain.ShoppingList

fun ListEntity.toDomain() = ShoppingList(id = id, name = name, updatedAt = updatedAt)

fun ShoppingList.toEntity() = ListEntity(id = id, name = name, updatedAt = updatedAt)

fun ItemEntity.toDomain() = ListItem(
    id = id,
    listId = listId,
    name = name,
    quantity = quantity,
    unitPriceCents = priceCents,
    categoryId = categoryId,
    note = note,
    ticked = ticked,
    catalogProductId = catalogProductId,
)

fun ListItem.toEntity() = ItemEntity(
    id = id,
    listId = listId,
    name = name,
    quantity = quantity,
    priceCents = unitPriceCents,
    categoryId = categoryId,
    note = note?.takeIf { it.isNotBlank() },
    ticked = ticked,
    catalogProductId = catalogProductId,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    key = key?.let { runCatching { CategoryKey.valueOf(it) }.getOrNull() },
    name = name,
    position = position,
)

fun Category.toEntity() = CategoryEntity(id = id, key = key?.name, name = name, position = position)
