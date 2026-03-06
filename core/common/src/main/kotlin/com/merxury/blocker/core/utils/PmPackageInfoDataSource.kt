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

package com.merxury.blocker.core.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import com.merxury.blocker.core.Application
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [PackageInfoDataSource] that delegates
 * to [ApplicationUtil] for backwards compatibility during migration.
 */
@Singleton
class PmPackageInfoDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pm: PackageManager,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : PackageInfoDataSource {

    override suspend fun getApplicationList(): List<Application> = ApplicationUtil.getApplicationList(context, ioDispatcher)

    override suspend fun getThirdPartyApplicationList(): List<Application> = ApplicationUtil.getThirdPartyApplicationList(context, ioDispatcher)

    override suspend fun getSystemApplicationList(): List<Application> = ApplicationUtil.getSystemApplicationList(context, ioDispatcher)

    override suspend fun getApplicationInfo(packageName: String): Application? = ApplicationUtil.getApplicationInfo(context, packageName)

    override suspend fun getActivityList(packageName: String): List<ActivityInfo> = ApplicationUtil.getActivityList(pm, packageName, ioDispatcher)

    override suspend fun getReceiverList(packageName: String): List<ActivityInfo> = ApplicationUtil.getReceiverList(pm, packageName, ioDispatcher)

    override suspend fun getServiceList(packageName: String): List<ServiceInfo> = ApplicationUtil.getServiceList(pm, packageName, ioDispatcher)

    override suspend fun getProviderList(packageName: String): List<ProviderInfo> = ApplicationUtil.getProviderList(pm, packageName, ioDispatcher)

    override suspend fun getApplicationComponents(packageName: String): PackageInfo = ApplicationUtil.getApplicationComponents(pm, packageName, ioDispatcher)

    override suspend fun getApplicationComponents(packageName: String, flags: Int): PackageInfo? = ApplicationUtil.getApplicationComponents(pm, packageName, flags, ioDispatcher)

    override fun checkComponentIsEnabled(componentName: ComponentName): Boolean = ApplicationUtil.checkComponentIsEnabled(pm, componentName)

    override fun isAppInstalled(packageName: String?): Boolean = ApplicationUtil.isAppInstalled(pm, packageName)

    override fun isSystemApp(packageName: String?): Boolean = ApplicationUtil.isSystemApp(pm, packageName)

    override fun isDebugMode(): Boolean = ApplicationUtil.isDebugMode(context)

    override suspend fun isRunning(packageName: String): Boolean = ApplicationUtil.isRunning(packageName, ioDispatcher)
}
