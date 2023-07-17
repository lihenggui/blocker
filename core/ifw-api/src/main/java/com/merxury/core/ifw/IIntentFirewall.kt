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

package com.merxury.core.ifw

interface IIntentFirewall {
    /**
     * Save the rules to IFW folder
     */
    suspend fun save(packageName: String, rule: Rules)

    /**
     * Add single rule for a component
     * @return true if this method executed successfully, the component will be blocked
     */
    suspend fun add(packageName: String, componentName: String): Boolean

    /**
     * Remove single rule for a component
     * @return true if this method executed successfully, the component will be unblocked
     */
    suspend fun remove(packageName: String, componentName: String): Boolean

    /**
     * @return false if the component is blocked
     */
    suspend fun getComponentEnableState(packageName: String, componentName: String): Boolean

    /**
     * Remove the IFW rules for specific package
     */
    suspend fun clear(packageName: String)
}
