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

/** One app waiting to be sorted, with the usage evidence that justifies sorting it. */
data class UnsortedAppRow(
    val packageName: String,
    val totalMs: Long,
    val sessionCount: Int,
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

    /**
     * The triage queue: every app still sitting in [categoryId], ranked by how much it has
     * been used since [fromDay], and floored at [minTotalMs].
     *
     * A LEFT JOIN, so an app that was classified but never opened still appears — it is
     * exactly the kind of app a queue should clear out — and it sorts last because its
     * usage is zero.
     *
     * The floor matters more than it looks. Measured against a real 30-day window this
     * table holds around 90 apps, and half of them are system components — permission
     * controllers, intent resolvers, screenshot handlers — that accumulated seconds of
     * use. Without a floor the queue is 57 cards long, which recreates the very chore
     * triage exists to remove. Anything below the floor contributes nothing measurable to
     * the producing/consuming split, so sorting it would be busywork.
     */
    @Query(
        "SELECT ac.packageName AS packageName, " +
            "COALESCE(SUM(u.totalMs), 0) AS totalMs, " +
            "COALESCE(SUM(u.sessionCount), 0) AS sessionCount " +
            "FROM app_category ac LEFT JOIN daily_app_usage u " +
            "ON u.packageName = ac.packageName AND u.dayEpochDay >= :fromDay " +
            "WHERE ac.categoryId = :categoryId " +
            "GROUP BY ac.packageName HAVING COALESCE(SUM(u.totalMs), 0) >= :minTotalMs " +
            "ORDER BY totalMs DESC, ac.packageName ASC",
    )
    suspend fun unsortedWithUsage(
        categoryId: String,
        fromDay: Long,
        minTotalMs: Long,
    ): List<UnsortedAppRow>

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
