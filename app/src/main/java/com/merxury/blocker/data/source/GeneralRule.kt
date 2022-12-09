/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.data.source

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.merxury.blocker.core.database.converter.ListConverter

@Keep
@Entity(tableName = "general_rules")
@TypeConverters(ListConverter::class)
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
