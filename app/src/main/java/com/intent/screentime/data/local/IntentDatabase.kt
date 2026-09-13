package com.intent.screentime.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.intent.screentime.data.local.dao.AppSessionDao
import com.intent.screentime.data.local.dao.CategoryDao
import com.intent.screentime.data.local.dao.DailyAppUsageDao
import com.intent.screentime.data.local.dao.DailySummaryDao
import com.intent.screentime.data.local.dao.FocusSessionDao
import com.intent.screentime.data.local.dao.IntentLogDao
import com.intent.screentime.data.local.dao.StreakDayDao
import com.intent.screentime.data.local.dao.TargetDao
import com.intent.screentime.data.local.dao.UsageEventDao
import com.intent.screentime.data.local.entity.AppCategoryEntity
import com.intent.screentime.data.local.entity.AppSessionEntity
import com.intent.screentime.data.local.entity.CategoryEntity
import com.intent.screentime.data.local.entity.DailyAppUsageEntity
import com.intent.screentime.data.local.entity.DailySummaryEntity
import com.intent.screentime.data.local.entity.FocusSessionEntity
import com.intent.screentime.data.local.entity.IntentLogEntity
import com.intent.screentime.data.local.entity.StreakDayEntity
import com.intent.screentime.data.local.entity.TargetEntity
import com.intent.screentime.data.local.entity.UsageEventEntity

@Database(
    entities = [
        UsageEventEntity::class,
        AppSessionEntity::class,
        DailyAppUsageEntity::class,
        DailySummaryEntity::class,
        CategoryEntity::class,
        AppCategoryEntity::class,
        TargetEntity::class,
        FocusSessionEntity::class,
        StreakDayEntity::class,
        IntentLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IntentDatabase : RoomDatabase() {
    abstract fun usageEventDao(): UsageEventDao
    abstract fun appSessionDao(): AppSessionDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun targetDao(): TargetDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun streakDayDao(): StreakDayDao
    abstract fun intentLogDao(): IntentLogDao

    companion object {
        private const val NAME = "intent.db"

        /**
         * Adds the intent log. A real migration rather than a destructive fallback:
         * every other table here is history the user cannot get back from anywhere else.
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `intent_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`timestampMs` INTEGER NOT NULL, " +
                        "`intentLabel` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_intent_log_timestampMs` " +
                        "ON `intent_log` (`timestampMs`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_intent_log_packageName` " +
                        "ON `intent_log` (`packageName`)",
                )
            }
        }

        fun build(context: Context): IntentDatabase =
            Room.databaseBuilder(context.applicationContext, IntentDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        DefaultCategories.SEED_SQL.forEach(db::execSQL)
                    }
                })
                .build()
    }
}
