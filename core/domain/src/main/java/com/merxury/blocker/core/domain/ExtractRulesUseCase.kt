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

package com.merxury.blocker.core.domain

import android.content.res.AssetManager
import com.merxury.blocker.core.data.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.model.InitializeState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val FOLDER_NAME = "blocker_general_rules"

class ExtractRulesUseCase @Inject constructor(
    private val asserManager: AssetManager,
    @FilesDir private val filesDir: File,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<InitializeState> = flow {
        val files = asserManager.list(FOLDER_NAME)
        if (files.isNullOrEmpty()) {
            throw IllegalArgumentException("No files found in $FOLDER_NAME")
        }
        files.forEach {
            Timber.v("Extracting $it to ${filesDir.absolutePath}")
            val inputStream = asserManager.open("$FOLDER_NAME/$it")
            val file = File(filesDir, it)
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            emit(InitializeState.Initializing(it))
        }
        Timber.v("Done extracting rules from assets")
        emit(InitializeState.Done)
    }
        .catch {
            Timber.e(it, "Failed to extract rules from assets")
            emit(InitializeState.Done)
        }
        .flowOn(ioDispatcher)
}