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

package com.merxury.blocker.feature.sharefilter

import com.merxury.blocker.core.domain.model.MatchedHeaderData

/**
 * Represents a group of share target activities matched for a specific app
 *
 * @param header The header information for the matched app
 * @param shareTargets The list of share target UI items belonging to this app
 */
data class MatchedShareTarget(
    val header: MatchedHeaderData,
    val shareTargets: List<ShareTargetUiItem>,
)
