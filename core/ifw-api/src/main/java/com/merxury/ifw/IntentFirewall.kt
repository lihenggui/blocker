/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.ifw

import com.merxury.ifw.entity.ComponentType

interface IntentFirewall {
    @Throws(Exception::class)
    suspend fun save()

    @Throws(Exception::class)
    suspend fun load(): IntentFirewall
    suspend fun add(packageName: String, componentName: String, type: ComponentType?): Boolean
    suspend fun remove(packageName: String, componentName: String, type: ComponentType?): Boolean

    /**
     * @return false if the component is blocked
     */
    suspend fun getComponentEnableState(packageName: String, componentName: String): Boolean

    @Throws(Exception::class)
    suspend fun clear()
    val packageName: String
}
