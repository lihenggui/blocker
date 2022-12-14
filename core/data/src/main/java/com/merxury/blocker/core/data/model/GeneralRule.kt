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

package com.merxury.blocker.core.data.model

import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import com.merxury.blocker.core.network.model.NetworkGeneralRule

fun NetworkGeneralRule.asEntity() = GeneralRuleEntity(
    id = id,
    name = name,
    iconUrl = iconUrl,
    company = company,
    searchKeyword = searchKeyword,
    useRegexSearch = useRegexSearch,
    description = description,
    safeToBlock = safeToBlock,
    sideEffect = sideEffect,
    contributors = contributors
)
