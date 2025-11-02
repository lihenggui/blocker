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

import android.content.Context
import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.database.sharetarget.ShareTargetActivityEntity
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.getInstalledPackagesCompat
import com.merxury.blocker.core.utils.ActivityIntentFilterInfo
import com.merxury.blocker.core.utils.ApkParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import javax.inject.Inject

internal class LocalShareTargetDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pm: PackageManager,
    @IfwControl private val ifwController: IController,
    @RootApiControl private val pmController: IController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ShareTargetDataSource {

    override fun getShareTargetActivities(): Flow<List<ShareTargetActivityEntity>> = flow {
        val installedPackages = pm.getInstalledPackagesCompat(0)
        val entityList = mutableListOf<ShareTargetActivityEntity>()

        for (packageInfo in installedPackages) {
            val packageName = packageInfo.packageName
            val sourceDir = packageInfo.applicationInfo?.sourceDir ?: continue
            val apkFile = File(sourceDir)

            try {
                val activities = ApkParser.getActivitiesWithIntentFilters(apkFile, ioDispatcher)

                for (activity in activities) {
                    val componentName = activity.name
                    val simpleName = componentName.substringAfterLast('.')
                    val displayName = resolveActivityLabel(activity)

                    val entity = ShareTargetActivityEntity(
                        packageName = packageName,
                        componentName = componentName,
                        simpleName = simpleName,
                        displayName = displayName,
                        ifwBlocked = !ifwController.checkComponentEnableState(packageName, componentName),
                        pmBlocked = !pmController.checkComponentEnableState(packageName, componentName),
                        exported = activity.exported,
                    )
                    entityList.add(entity)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse activities for package: $packageName")
            }
        }

        emit(entityList)
    }.flowOn(ioDispatcher)

    /**
     * Resolves the display label for an activity.
     *
     * Uses PackageManager to load the activity's label. Falls back to the simple name
     * if resolution fails.
     *
     * @param activity the activity information
     * @return the resolved display name or simple name
     */
    private fun resolveActivityLabel(activity: ActivityIntentFilterInfo): String {
        return try {
            val componentName = android.content.ComponentName(activity.packageName, activity.name)
            val activityInfo = pm.getActivityInfo(componentName, 0)
            activityInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve label for ${activity.name}")
            activity.name.substringAfterLast('.')
        }
    }
}
