package com.local.dailyautomation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppConfigEntity::class,
        ModuleConfigEntity::class,
        CycleEntity::class,
        RunEntity::class,
        ModuleRunEntity::class,
        FailureScreenshotEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AssistantDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    abstract fun moduleConfigDao(): ModuleConfigDao
    abstract fun cycleDao(): CycleDao
    abstract fun runDao(): RunDao
    abstract fun moduleRunDao(): ModuleRunDao
    abstract fun failureScreenshotDao(): FailureScreenshotDao

    companion object {
        @Volatile private var instance: AssistantDatabase? = null

        fun get(context: Context): AssistantDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AssistantDatabase::class.java,
                "daily-automation.db",
            ).build().also { instance = it }
        }
    }
}
