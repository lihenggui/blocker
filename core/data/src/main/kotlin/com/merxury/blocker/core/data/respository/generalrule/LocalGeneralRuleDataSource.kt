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

package com.merxury.blocker.core.data.respository.generalrule

import android.content.res.AssetManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.network.model.NetworkGeneralRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

private const val RULES_FOLDER = "rules"
private const val RULE_NAME = "general.json"

internal class LocalGeneralRuleDataSource @Inject constructor(
    private val json: Json,
    private val userDataRepository: UserDataRepository,
    private val assetManager: AssetManager,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : GeneralRuleDataSource {

    override fun getGeneralRules(): Flow<List<GeneralRule>> = flow {
        val language = userDataRepository.getLibDisplayLanguage()
        val ruleFile = getRuleFile(language)
        ruleFile?.use { inputStream ->
            val serializedRule = readContentFromStream(inputStream)
            emit(serializedRule)
            return@flow
        }

        // Fallback to assets
        val assetRuleFile = getRuleFileFromAssets(language)
        assetRuleFile?.use { inputStream ->
            val serializedRule = readContentFromStream(inputStream)
            emit(serializedRule)
            return@flow
        }
        Timber.e("No general rule file found")
        emit(emptyList())
    }
        .flowOn(ioDispatcher)

    @OptIn(ExperimentalSerializationApi::class)
    private fun readContentFromStream(inputStream: InputStream) = if (VERSION.SDK_INT <= VERSION_CODES.M) {
        // https://github.com/Kotlin/kotlinx.serialization/issues/2457
        // Use decodeFromString instead of decodeFromStream to avoid the issue
        val text = inputStream.bufferedReader().use { it.readText() }
        json.decodeFromString<List<NetworkGeneralRule>>(text)
            .map { it.asExternalModel() }
    } else {
        json.decodeFromStream<List<NetworkGeneralRule>>(inputStream)
            .map { it.asExternalModel() }
    }

    private suspend fun getRuleFileFromAssets(language: String): InputStream? = withContext(ioDispatcher) {
        Timber.d("Get rule file from assets, language: $language")
        try {
            return@withContext assetManager.open("$ruleBaseFolder/$RULES_FOLDER/$language/$RULE_NAME")
        } catch (e: IOException) {
            Timber.e(e, "Failed to get rule file from assets")
            return@withContext null
        }
    }

    private suspend fun getRuleFile(language: String): InputStream? = withContext(ioDispatcher) {
        val ruleFile = filesDir.resolve(ruleBaseFolder)
            .resolve(RULES_FOLDER)
            .resolve(language)
            .resolve(RULE_NAME)
        if (ruleFile.exists()) {
            return@withContext ruleFile.inputStream()
        }
        // TODO should be removed in future
        val lowercaseFolder = filesDir.resolve(ruleBaseFolder)
            .resolve(RULES_FOLDER)
            .resolve(language.lowercase())
            .resolve(RULE_NAME)
        if (lowercaseFolder.exists()) {
            Timber.i("Fallback to lowercase-language folder")
            return@withContext lowercaseFolder.inputStream()
        }
        val oldRuleFile = filesDir.resolve(ruleBaseFolder)
            .resolve("zh-cn")
            .resolve(RULE_NAME)
        if (oldRuleFile.exists()) {
            Timber.i("Fallback to old version of rules without RULES_FOLDER")
            return@withContext oldRuleFile.inputStream()
        }
        Timber.e("General rule file not found, language: $language")
        return@withContext null
    }
}
