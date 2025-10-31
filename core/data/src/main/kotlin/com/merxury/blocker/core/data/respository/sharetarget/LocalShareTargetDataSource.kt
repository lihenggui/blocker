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

package com.merxury.blocker.core.data.respository.sharetarget

import android.content.Intent
import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.getSimpleName
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

internal class LocalShareTargetDataSource @Inject constructor(
    private val pm: PackageManager,
    @IfwControl private val ifwController: IController,
    @RootApiControl private val pmController: IController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ShareTargetDataSource {

    override fun getShareTargetActivities(): Flow<List<ComponentInfo>> = flow {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
        }

        val sendMultipleIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
        }

        val sendResolveInfoList = pm.queryIntentActivities(sendIntent, PackageManager.MATCH_ALL)
        val sendMultipleResolveInfoList = pm.queryIntentActivities(sendMultipleIntent, PackageManager.MATCH_ALL)

        val allResolveInfoList = (sendResolveInfoList + sendMultipleResolveInfoList)
            .distinctBy { it.activityInfo.packageName + it.activityInfo.name }

        val componentList = allResolveInfoList.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName
            val componentName = activityInfo.name

            ComponentInfo(
                name = componentName,
                simpleName = activityInfo.getSimpleName(),
                packageName = packageName,
                type = ComponentType.ACTIVITY,
                exported = activityInfo.exported,
                pmBlocked = !pmController.checkComponentEnableState(packageName, componentName),
                ifwBlocked = !ifwController.checkComponentEnableState(packageName, componentName),
            )
        }

        emit(componentList)
    }.flowOn(ioDispatcher)
}
