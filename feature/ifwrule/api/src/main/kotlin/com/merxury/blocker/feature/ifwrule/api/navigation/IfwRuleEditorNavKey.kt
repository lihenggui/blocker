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

package com.merxury.blocker.feature.ifwrule.api.navigation

import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import kotlinx.serialization.Serializable

@Serializable
data class IfwRuleEditorNavKey(
    val packageName: String,
    val componentName: String,
    val componentType: String,
) : NavKey

fun Navigator.navigateToIfwRuleEditor(
    packageName: String,
    componentName: String,
    componentType: String,
) {
    navigate(IfwRuleEditorNavKey(packageName, componentName, componentType))
}
