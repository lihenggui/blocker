package com.merxury.blocker.data.source

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.merxury.blocker.data.source.local.ListConverters

@Keep
@Entity(tableName = "general_rules")
@TypeConverters(ListConverters::class)
data class GeneralRule(
    @PrimaryKey val id: Int,
    var name: String? = null,
    var iconUrl: String? = null,
    var company: String? = null,
    var searchKeyword: List<String> = listOf(),
    var useRegexSearch: Boolean? = null,
    var description: String? = null,
    var safeToBlock: Boolean? = null,
    var sideEffect: String? = null,
    var contributors: List<String> = listOf()
)