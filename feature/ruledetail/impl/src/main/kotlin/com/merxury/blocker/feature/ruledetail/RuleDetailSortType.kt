/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.ruledetail

import androidx.annotation.StringRes
import com.merxury.blocker.feature.ruledetail.api.R.string as ruledetailString

enum class RuleDetailSortType(
    @StringRes val labelRes: Int,
) {
    NAME(ruledetailString.feature_ruledetail_api_sort_by_name),
    RECENTLY_INSTALLED(ruledetailString.feature_ruledetail_api_sort_by_recently_installed),
    MOST_MATCHED(ruledetailString.feature_ruledetail_api_sort_by_most_matched),
    MOST_BLOCKED(ruledetailString.feature_ruledetail_api_sort_by_most_blocked),
    MOST_ENABLED(ruledetailString.feature_ruledetail_api_sort_by_most_enabled),
}
