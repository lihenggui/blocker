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

package com.merxury.blocker.core.controllers.root.api

import android.os.Parcelable
import be.mygod.librootkotlinx.RootCommand
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.root.RootCommandExecutor
import com.merxury.blocker.core.utils.RootAvailabilityChecker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RootApiClient @Inject constructor(
    private val rootChecker: RootAvailabilityChecker,
    private val rootCommandExecutor: RootCommandExecutor,
) {
    suspend fun ensureAvailable() {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
    }

    suspend fun <T : Parcelable?> execute(command: RootCommand<T>): T = rootCommandExecutor.execute(command)
}
