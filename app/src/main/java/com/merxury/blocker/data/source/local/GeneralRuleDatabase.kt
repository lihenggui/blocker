package com.merxury.blocker.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.merxury.blocker.data.source.GeneralRule

@Database(entities = [GeneralRule::class], version = 1)
@TypeConverters(ListConverters::class)
abstract class GeneralRuleDatabase : RoomDatabase() {
    abstract fun generalRuleDao(): GeneralRuleDao
}