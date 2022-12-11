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

package com.merxury.blocker.ui.home.advsearch.local

import com.merxury.blocker.core.database.app.InstalledAppEntity
import com.merxury.blocker.data.Event

sealed class LocalSearchState {
    object NotStarted : LocalSearchState()
    data class Loading(val app: InstalledAppEntity?) : LocalSearchState()
    data class Error(val exception: Event<Throwable>) : LocalSearchState()
    data class Finished(val count: Int) : LocalSearchState()
    object Searching : LocalSearchState()
}
