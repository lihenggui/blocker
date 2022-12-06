package com.merxury.blocker.data.instantinfo

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.merxury.blocker.data.source.local.ListConverters

@Database(entities = [InstantComponentInfo::class], version = 1, exportSchema = true)
@TypeConverters(ListConverters::class)
abstract class InstantComponentInfoDatabase : RoomDatabase() {
    abstract fun instantComponentInfoDao(): InstantComponentInfoDao

    companion object {
        @Volatile
        private var instance: InstantComponentInfoDatabase? = null

        fun getInstance(context: Context): InstantComponentInfoDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): InstantComponentInfoDatabase {
            return Room.databaseBuilder(
                context,
                InstantComponentInfoDatabase::class.java,
                "instant_component_info"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
