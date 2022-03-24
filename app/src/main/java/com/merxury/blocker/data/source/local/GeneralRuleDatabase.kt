package com.merxury.blocker.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.merxury.blocker.data.source.GeneralRule

@Database(entities = [GeneralRule::class], version = 1)
@TypeConverters(ListConverters::class)
abstract class GeneralRuleDatabase : RoomDatabase() {
    abstract fun generalRuleDao(): GeneralRuleDao

    companion object {
        @Volatile
        private var instance: GeneralRuleDatabase? = null

        fun getInstance(context: Context): GeneralRuleDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): GeneralRuleDatabase {
            return Room.databaseBuilder(context, GeneralRuleDatabase::class.java, "general_rule")
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}