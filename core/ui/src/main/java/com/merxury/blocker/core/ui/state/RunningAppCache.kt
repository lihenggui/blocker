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

package com.merxury.blocker.core.ui.state

import com.merxury.blocker.core.extension.exec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

object RunningAppCache {
    private val runningAppList = mutableSetOf<String>()

    suspend fun refresh(dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        try {
            val commandResult = "ps -A -o NAME".exec(dispatcher)
            if (!commandResult.isSuccess) {
                Timber.e("Failed to get running app list: ${commandResult.err}")
                return
            }
            runningAppList.clear()
            runningAppList.addAll(commandResult.out)
        } catch (e: Exception) {
            Timber.w("Failed to refresh running app list")
        }
    }

    fun isRunning(packageName: String): Boolean {
        return runningAppList.contains(packageName)
    }

    suspend fun update(packageName: String, dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        try {
            val result = "pidof $packageName".exec(dispatcher)
            if (result.isSuccess && result.out.isNotEmpty()) {
                runningAppList.add(packageName)
            } else {
                runningAppList.remove(packageName)
            }
            Timber.d(
                "Updated running status: $packageName" +
                    "result is ${result.out.isNotEmpty()}",
            )
        } catch (e: Exception) {
            Timber.w("Failed to update running app: $packageName")
        }
    }
}
