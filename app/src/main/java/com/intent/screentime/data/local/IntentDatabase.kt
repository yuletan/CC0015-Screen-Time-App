package com.intent.screentime.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.intent.screentime.data.intent.Reasons
import com.intent.screentime.data.local.dao.AppSessionDao
import com.intent.screentime.data.local.dao.CategoryDao
import com.intent.screentime.data.local.dao.DailyAppUsageDao
import com.intent.screentime.data.local.dao.DailySummaryDao
import com.intent.screentime.data.local.dao.DayNoteDao
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
import com.intent.screentime.data.local.entity.DayNoteEntity
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
        DayNoteEntity::class,
    ],
    version = 3,
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
    abstract fun dayNoteDao(): DayNoteDao

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

        /**
         * Adds the reason ledger to the intent log, and the day note table.
         *
         * The backfill is the important part: without it every row written before this
         * version would have a null `reasonKey` and be indistinguishable from a prompt the
         * user deliberately let close.
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `intent_log` ADD COLUMN `reasonKey` TEXT")
                db.execSQL(
                    "ALTER TABLE `intent_log` ADD COLUMN `skipped` INTEGER NOT NULL DEFAULT 0",
                )

                for (option in Reasons.OPTIONS) {
                    db.execSQL(
                        "UPDATE `intent_log` SET `reasonKey` = ? WHERE `intentLabel` = ?",
                        arrayOf(option.key, option.label),
                    )
                }

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_note` (" +
                        "`dayEpochDay` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`reflection` TEXT, " +
                        "`updatedAtMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`dayEpochDay`))",
                )
            }
        }

        fun build(context: Context): IntentDatabase =
            Room.databaseBuilder(context.applicationContext, IntentDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        DefaultCategories.SEED_SQL.forEach(db::execSQL)
                    }
                })
                .build()
    }
}
