package com.merxury.blocker.data.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [InstalledApp::class, AppComponent::class] , version = 1)
@TypeConverters(Converters::class)
abstract class InstalledAppDatabase: RoomDatabase() {
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun appComponentDao(): AppComponentDao

    companion object {
        @Volatile
        private var instance: InstalledAppDatabase? = null

        fun getInstance(context: Context): InstalledAppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): InstalledAppDatabase {
            return Room.databaseBuilder(context, InstalledAppDatabase::class.java, "installed_app")
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}