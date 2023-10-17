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
import androidx.hilt.work.HiltWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.merxury.blocker.core.data.di.FilesDir
import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.rule.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.time.measureTime

@HiltWorker
class CopyRulesToStorageWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val assetManager: AssetManager,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleNotificationWorker(context, params) {
    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val files = assetManager.list(ruleBaseFolder)
        if (files.isNullOrEmpty()) {
            throw IllegalArgumentException("No files found in $ruleBaseFolder")
        }
        val workingFolder = File(filesDir, ruleBaseFolder)
        if (!workingFolder.exists()) {
            Timber.i("Create ${workingFolder.absolutePath}")
            if (!workingFolder.mkdirs()) {
                Timber.e("Cannot create folder: ${workingFolder.absolutePath}")
                return@withContext Result.failure()
            }
        }
        val copyTimeCost = measureTime {
            assetManager.copyAssetFolder(ruleBaseFolder, workingFolder.absolutePath)
        }
        Timber.i("Used $copyTimeCost to copy rules from assets")
        return@withContext Result.success()
    }

    override fun getNotificationTitle(): Int = R.string.core_rule_copying_rules_to_internal_storage

    companion object {
        const val WORK_NAME = "CopyRuleToInternalStorage"
        fun copyWork() = OneTimeWorkRequestBuilder<CopyRulesToStorageWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_NAME)
            .build()
    }
}

private fun AssetManager.copyAssetFolder(srcName: String, dstName: String): Boolean {
    return try {
        var result: Boolean
        val fileList = this.list(srcName) ?: return false
        if (fileList.isEmpty()) {
            result = copyAssetFile(srcName, dstName)
        } else {
            val file = File(dstName)
            result = file.mkdirs()
            for (filename in fileList) {
                result = result and copyAssetFolder(
                    srcName + separator.toString() + filename,
                    dstName + separator.toString() + filename,
                )
            }
        }
        result
    } catch (e: IOException) {
        Timber.e(e, "Cannot copy folder from $srcName to $dstName")
        false
    }
}

private fun AssetManager.copyAssetFile(srcName: String, dstName: String): Boolean {
    return try {
        val inStream = this.open(srcName)
        val outFile = File(dstName)
        val out: OutputStream = FileOutputStream(outFile)
        val buffer = ByteArray(1024)
        var read: Int
        while (inStream.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
        inStream.close()
        out.close()
        true
    } catch (e: IOException) {
        Timber.e(e, "Cannot copy files from $srcName to $dstName")
        false
    }
}
