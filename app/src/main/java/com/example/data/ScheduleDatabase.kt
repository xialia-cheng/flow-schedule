package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class ScheduleConverters {
    @TypeConverter
    fun fromRepeatRule(rule: RepeatRule): String = rule.name

    @TypeConverter
    fun toRepeatRule(value: String): RepeatRule =
        runCatching { RepeatRule.valueOf(value) }.getOrDefault(RepeatRule.NONE)

    @TypeConverter
    fun fromCategory(category: ScheduleCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): ScheduleCategory =
        runCatching { ScheduleCategory.valueOf(value) }.getOrDefault(ScheduleCategory.WORK)

    @TypeConverter
    fun fromWeekType(weekType: WeekType): String = weekType.name

    @TypeConverter
    fun toWeekType(value: String): WeekType =
        runCatching { WeekType.valueOf(value) }.getOrDefault(WeekType.ALL)
}

@Database(
    entities = [
        ScheduleEntity::class,
        TimetableEntity::class,
        CourseEntity::class,
        SectionTimeEntity::class,
        CourseOccurrenceStateEntity::class,
        StudyTaskEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(ScheduleConverters::class)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile
        private var INSTANCE: ScheduleDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN customColorHex TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN customWeeksCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE schedules ADD COLUMN customTagId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE schedules ADD COLUMN customTagName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE schedules ADD COLUMN customTagColorHex TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): ScheduleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScheduleDatabase::class.java,
                    "flow_schedule_db",
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
