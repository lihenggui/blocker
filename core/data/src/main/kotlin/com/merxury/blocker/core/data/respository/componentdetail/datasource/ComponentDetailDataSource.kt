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

package com.merxury.blocker.core.data.respository.componentdetail.datasource

import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.flow.Flow
import java.util.Locale

val LIB_SUPPORTED_LANGUAGE = listOf(
    Locale.ENGLISH.toLanguageTag(),
    Locale.SIMPLIFIED_CHINESE.toLanguageTag(),
)

interface ComponentDetailDataSource {

    fun getByPackageName(packageName: String): Flow<List<ComponentDetail>>

    fun getByComponentName(name: String): Flow<ComponentDetail?>

    fun saveComponentData(component: ComponentDetail): Flow<Boolean>

    suspend fun getLibDisplayLanguage(displayLanguageInSettings: String): String {
        if (displayLanguageInSettings.isNotBlank()) {
            return displayLanguageInSettings
        }
        // Empty means follow the system language first
        // If no matching found, fallback to English
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country
        val systemLanguage = if (country.isNotBlank()) {
            "$language-$country"
        } else {
            language
        }
        var splittedLanguage = systemLanguage.split("-")
        while (splittedLanguage.isNotEmpty()) {
            val languageTag = splittedLanguage.joinToString("-")
            val matchingLanguage = LIB_SUPPORTED_LANGUAGE.find { it == languageTag }
            if (matchingLanguage != null) {
                return matchingLanguage
            } else {
                splittedLanguage = splittedLanguage.dropLast(1)
            }
        }
        return Locale.ENGLISH.toLanguageTag()
    }
}
