/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.ui.applist

import com.merxury.blocker.core.model.data.AppServiceStatus

/** The service line of an [AppListItem]: how many of an app's services run, are blocked, exist. */
data class ServiceCounts(
    val running: Int,
    val blocked: Int,
    val total: Int,
)

internal fun AppServiceStatus.toServiceCounts() = ServiceCounts(running = running, blocked = blocked, total = total)
