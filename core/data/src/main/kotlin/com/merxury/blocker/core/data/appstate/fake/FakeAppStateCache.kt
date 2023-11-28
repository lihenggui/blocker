/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.data.appstate.fake

import com.merxury.blocker.core.data.appstate.AppState
import com.merxury.blocker.core.data.appstate.IAppStateCache

class FakeAppStateCache : IAppStateCache {
    override fun getOrNull(packageName: String): AppState? {
        return null
    }

    override suspend fun get(packageName: String): AppState {
        return AppState(packageName = packageName)
    }
}
