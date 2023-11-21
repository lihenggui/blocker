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

package com.merxury.blocker.core.data.respository.generalrule

import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.network.model.NetworkGeneralRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val RULES_FOLDER = "rules"
private const val LANGUAGE = "zh-cn"
private const val RULE_NAME = "general.json"

class LocalGeneralRuleDataSource @Inject constructor(
    private val json: Json,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : GeneralRuleDataSource {
    @OptIn(ExperimentalSerializationApi::class)
    override fun getGeneralRules(): Flow<List<GeneralRule>> = flow {
        val ruleFile = getRuleFile()
        ruleFile.inputStream().use { inputStream ->
            val serializedRule = json.decodeFromStream<List<NetworkGeneralRule>>(inputStream)
                .map { it.asExternalModel() }
            emit(serializedRule)
        }
    }
        .flowOn(ioDispatcher)

    private fun getRuleFile(): File {
        val ruleFile = filesDir.resolve(ruleBaseFolder)
            .resolve(RULES_FOLDER)
            .resolve(LANGUAGE)
            .resolve(RULE_NAME)
        if (ruleFile.exists()) {
            return ruleFile
        }
        Timber.i("Fallback to old version of rules")
        // TODO should be removed in future
        val oldRuleFile = filesDir.resolve(ruleBaseFolder)
            .resolve(LANGUAGE)
            .resolve(RULE_NAME)
        if (oldRuleFile.exists()) {
            return oldRuleFile
        }
        throw IllegalStateException("Cannot find general rule in files folder.")
    }
}
