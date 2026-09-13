package com.intent.screentime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.intent.screentime.data.local.entity.AppCategoryEntity
import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.data.local.entity.CategoryKind
import kotlinx.coroutines.flow.Flow

/** Flat row for the package-to-category join. */
data class AppCategoryRow(
    val packageName: String,
    val categoryId: String,
    val name: String,
    val kind: CategoryKind,
    val colorHex: String,
)

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    /** Package to category, joined so the UI can chip every app row in one read. */
    @Query(
        "SELECT ac.packageName AS packageName, c.id AS categoryId, c.name AS name, " +
            "c.kind AS kind, c.colorHex AS colorHex " +
            "FROM app_category ac JOIN category c ON c.id = ac.categoryId",
    )
    suspend fun appCategoryRows(): List<AppCategoryRow>

    @Query("SELECT * FROM category ORDER BY name ASC")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM category ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("UPDATE category SET name = :name, kind = :kind, colorHex = :colorHex WHERE id = :id")
    suspend fun updateCategory(id: String, name: String, kind: CategoryKind, colorHex: String)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteCategory(id: String)

    /** Used when a custom category is removed: its apps fall back to Uncategorised. */
    @Query("UPDATE app_category SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun reassignAppCategories(fromId: String, toId: String)

    @Query("SELECT COUNT(*) FROM app_category WHERE categoryId = :categoryId")
    suspend fun appCountForCategory(categoryId: String): Int

    @Upsert
    suspend fun upsertAppCategories(rows: List<AppCategoryEntity>)

    @Query("SELECT * FROM app_category")
    suspend fun allAppCategories(): List<AppCategoryEntity>

    @Query("SELECT * FROM app_category")
    fun observeAppCategories(): Flow<List<AppCategoryEntity>>

    @Query("SELECT * FROM app_category WHERE packageName = :packageName")
    suspend fun forPackage(packageName: String): AppCategoryEntity?

    /** User choices are marked so automatic re-classification never clobbers them. */
    @Query("UPDATE app_category SET categoryId = :categoryId, isUserOverride = 1 WHERE packageName = :packageName")
    suspend fun setUserCategory(packageName: String, categoryId: String)

    @Query("INSERT OR IGNORE INTO app_category (packageName, categoryId, isUserOverride) VALUES (:packageName, :categoryId, 0)")
    suspend fun insertAppCategoryIfAbsent(packageName: String, categoryId: String)
}
