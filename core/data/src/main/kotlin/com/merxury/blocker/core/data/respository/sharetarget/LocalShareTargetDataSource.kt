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
     * Resolves the display label for an activity from its manifest label string.
     *
     * Handles resource references (e.g., "@string/app_name") by loading the app's
     * resources and resolving the string. Falls back to the application label if
     * resolution fails.
     *
     * @param activity the activity information containing the raw label
     * @return the resolved display name
     */
    private fun resolveActivityLabel(activity: ActivityIntentFilterInfo): String {
        val label = activity.label

        if (label.isNullOrEmpty()) {
            return getApplicationLabel(activity.packageName)
        }

        if (!label.startsWith("@")) {
            return label
        }

        return try {
            val resources = pm.getResourcesForApplication(activity.packageName)
            val resourceId = label.substring(1).toIntOrNull()

            if (resourceId != null) {
                resources.getString(resourceId)
            } else {
                val (type, name) = parseResourceReference(label)
                val id = resources.getIdentifier(name, type, activity.packageName)
                if (id != 0) {
                    resources.getString(id)
                } else {
                    getApplicationLabel(activity.packageName)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve label for ${activity.name}")
            getApplicationLabel(activity.packageName)
        }
    }

    /**
     * Parses a resource reference string (e.g., "@string/app_name") into type and name.
     *
     * @param reference the resource reference string
     * @return a pair of resource type and resource name
     */
    private fun parseResourceReference(reference: String): Pair<String, String> {
        val withoutAt = reference.removePrefix("@")
        val parts = withoutAt.split("/", limit = 2)
        return if (parts.size == 2) {
            Pair(parts[0], parts[1])
        } else {
            Pair("string", withoutAt)
        }
    }

    /**
     * Gets the application label as a fallback when activity label cannot be resolved.
     *
     * @param packageName the package name
     * @return the application label
     */
    private fun getApplicationLabel(packageName: String): String {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            Timber.w(e, "Failed to get application label for $packageName")
            packageName
        }
    }
}
