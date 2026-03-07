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

package com.merxury.blocker.core.testing.controller

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import com.merxury.blocker.core.Application
import com.merxury.blocker.core.utils.PackageInfoDataSource

class FakePackageInfoDataSource : PackageInfoDataSource {
    var applicationList: List<Application> = emptyList()
    var thirdPartyApplicationList: List<Application> = emptyList()
    var systemApplicationList: List<Application> = emptyList()
    var activities: Map<String, List<ActivityInfo>> = emptyMap()
    var receivers: Map<String, List<ActivityInfo>> = emptyMap()
    var services: Map<String, List<ServiceInfo>> = emptyMap()
    var providers: Map<String, List<ProviderInfo>> = emptyMap()
    var components: Map<String, PackageInfo> = emptyMap()
    var enabledComponents: Set<ComponentName> = emptySet()
    var installedPackages: Set<String> = emptySet()
    var systemPackages: Set<String> = emptySet()
    var runningPackages: Set<String> = emptySet()

    override suspend fun getApplicationList(): List<Application> = applicationList
    override suspend fun getThirdPartyApplicationList(): List<Application> = thirdPartyApplicationList
    override suspend fun getSystemApplicationList(): List<Application> = systemApplicationList
    override suspend fun getApplicationInfo(packageName: String): Application? = applicationList.find { it.packageName == packageName }

    override suspend fun getActivityList(packageName: String): List<ActivityInfo> = activities[packageName].orEmpty()

    override suspend fun getReceiverList(packageName: String): List<ActivityInfo> = receivers[packageName].orEmpty()

    override suspend fun getServiceList(packageName: String): List<ServiceInfo> = services[packageName].orEmpty()

    override suspend fun getProviderList(packageName: String): List<ProviderInfo> = providers[packageName].orEmpty()

    override suspend fun getApplicationComponents(packageName: String): PackageInfo = components[packageName] ?: PackageInfo()

    override suspend fun getApplicationComponents(packageName: String, flags: Int): PackageInfo? = components[packageName]

    override fun checkComponentIsEnabled(componentName: ComponentName): Boolean = componentName in enabledComponents

    override fun isAppInstalled(packageName: String?): Boolean = packageName in installedPackages

    override fun isSystemApp(packageName: String?): Boolean = packageName in systemPackages

    override suspend fun isRunning(packageName: String): Boolean = packageName in runningPackages
}
