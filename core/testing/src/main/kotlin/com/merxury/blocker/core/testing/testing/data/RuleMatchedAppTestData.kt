/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.testing.testing.data

import androidx.compose.runtime.mutableStateListOf
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem

val matchedItemTestData = MatchedItem(
    header = MatchedHeaderData(
        title = "Blocker",
        uniqueId = "com.merxury.blocker",
    ),
    componentList = receiverTestData,
)

val ruleMatchedAppListTestData = mutableStateListOf(
    matchedItemTestData,
)
