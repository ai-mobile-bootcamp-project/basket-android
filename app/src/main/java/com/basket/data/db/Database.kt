package com.basket.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Epoch millis of the last change to the list or one of its items. */
    val updatedAt: Long,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** CategoryKey name for the default aisles, null for categories the user added. */
    val key: String?,
    /** Custom name; null means "the default name in the app language". */
    val name: String?,
    val position: Int,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(entity = ListEntity::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("listId"), Index("categoryId")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val name: String,
    val quantity: Int,
    /** Unit price in cents; null when the item has no price. */
    val priceCents: Long?,
    val categoryId: Long,
    val note: String?,
    val ticked: Boolean,
    /** DummyJSON product id for items added from Browse products. */
    val catalogProductId: Int?,
)

/** Saved copy of the DummyJSON groceries catalog for offline use. */
@Entity(tableName = "catalog_products")
data class CatalogProductEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val description: String,
    val price: Double,
    val discountPercentage: Double,
    val rating: Double,
    val availabilityStatus: String?,
    /** Tags joined with "|". */
    val tags: String,
    val thumbnail: String?,
    val image: String?,
    /** Epoch millis of the download this row came from. */
    val savedAt: Long,
)

/** Number of items per category, across all lists. */
data class CategoryCount(val categoryId: Long, val count: Int)

@Dao
interface ListDao {
    @Query("SELECT * FROM lists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists WHERE id = :id")
    fun observe(id: Long): Flow<ListEntity?>

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun get(id: Long): ListEntity?

    @Insert
    suspend fun insert(list: ListEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lists: List<ListEntity>)

    @Query("UPDATE lists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE lists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM lists")
    suspend fun deleteAll()
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE listId = :listId")
    fun observeForList(listId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE listId = :listId")
    suspend fun getForList(listId: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): ItemEntity?

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("UPDATE items SET ticked = :ticked WHERE id = :id")
    suspend fun setTicked(id: Long, ticked: Boolean)

    @Query("UPDATE items SET quantity = :quantity WHERE id = :id")
    suspend fun setQuantity(id: Long, quantity: Int)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM items WHERE listId = :listId AND ticked = 1")
    suspend fun deleteTicked(listId: Long)

    @Query("DELETE FROM items WHERE listId = :listId")
    suspend fun deleteForList(listId: Long)

    @Query("SELECT categoryId, COUNT(*) AS count FROM items GROUP BY categoryId")
    fun observeCountsByCategory(): Flow<List<CategoryCount>>

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY position")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_products ORDER BY title")
    fun observeAll(): Flow<List<CatalogProductEntity>>

    @Query("SELECT * FROM catalog_products ORDER BY title")
    suspend fun getAll(): List<CatalogProductEntity>

    @Upsert
    suspend fun upsertAll(products: List<CatalogProductEntity>)

    @Query("DELETE FROM catalog_products")
    suspend fun deleteAll()
}

@Database(
    entities = [ListEntity::class, ItemEntity::class, CategoryEntity::class, CatalogProductEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BasketDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun catalogDao(): CatalogDao
}
