/*
 * Copyright 2022 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.database.generalrule

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.merxury.blocker.core.database.util.ListConverter
import com.merxury.blocker.core.model.data.GeneralRule

@Entity(tableName = "general_rules")
@TypeConverters(ListConverter::class)
data class GeneralRuleEntity(
    @PrimaryKey val id: Int,
    val name: String? = null,
    val iconUrl: String? = null,
    val company: String? = null,
    val searchKeyword: List<String> = listOf(),
    val useRegexSearch: Boolean? = null,
    val description: String? = null,
    val safeToBlock: Boolean? = null,
    val sideEffect: String? = null,
    val contributors: List<String> = listOf(),
)

fun GeneralRuleEntity.asExternalModel() = GeneralRule(
    id = id,
    name = name,
    iconUrl = iconUrl,
    company = company,
    searchKeyword = searchKeyword,
    useRegexSearch = useRegexSearch,
    description = description,
    safeToBlock = safeToBlock,
    sideEffect = sideEffect,
    contributors = contributors,
)

fun GeneralRule.fromExternalModel() = GeneralRuleEntity(
    id = id,
    name = name,
    iconUrl = iconUrl,
    company = company,
    searchKeyword = searchKeyword,
    useRegexSearch = useRegexSearch,
    description = description,
    safeToBlock = safeToBlock,
    sideEffect = sideEffect,
    contributors = contributors,
)
