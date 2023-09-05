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

package com.merxury.blocker.core.rule.work

import android.content.Context
import android.content.res.AssetManager
import androidx.work.WorkerParameters
import com.merxury.blocker.core.data.di.FilesDir
import com.merxury.blocker.core.rule.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

private const val FOLDER_NAME = "blocker_general_rules"
class CopyRulesToStorageWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val asserManager: AssetManager,
    @FilesDir private val filesDir: File,
) : RuleNotificationWorker(context, params) {
    override suspend fun doWork(): Result {
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
        }
        Timber.v("Done extracting rules from assets")
        return Result.success()
    }

    override fun getNotificationTitle(): Int = R.string.core_rule_copying_rules_to_internal_storage
}
