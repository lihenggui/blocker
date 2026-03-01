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

package com.merxury.core.ifw

import android.content.pm.PackageManager
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class PmComponentTypeResolver @Inject constructor(
    private val pm: PackageManager,
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) : ComponentTypeResolver {

    override suspend fun getComponentType(
        packageName: String,
        componentName: String,
    ): ComponentType = withContext(cpuDispatcher) {
        val receivers = ApplicationUtil.getReceiverList(pm, packageName, dispatcher)
        for (receiver in receivers) {
            if (componentName == receiver.name) {
                return@withContext RECEIVER
            }
        }
        val services = ApplicationUtil.getServiceList(pm, packageName, dispatcher)
        for (service in services) {
            if (componentName == service.name) {
                return@withContext SERVICE
            }
        }
        val activities = ApplicationUtil.getActivityList(pm, packageName, dispatcher)
        for (activity in activities) {
            if (componentName == activity.name) {
                return@withContext ACTIVITY
            }
        }
        val providers = ApplicationUtil.getProviderList(pm, packageName, dispatcher)
        for (provider in providers) {
            if (componentName == provider.name) {
                return@withContext PROVIDER
            }
        }
        Timber.e("Cannot find component type for $packageName/$componentName")
        return@withContext PROVIDER
    }
}
