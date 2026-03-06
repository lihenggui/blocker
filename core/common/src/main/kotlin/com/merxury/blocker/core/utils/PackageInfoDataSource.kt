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
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import com.merxury.blocker.core.Application

/**
 * Abstraction over Android [android.content.pm.PackageManager] queries,
 * enabling dependency injection and testability.
 */
interface PackageInfoDataSource {
    suspend fun getApplicationList(): List<Application>
    suspend fun getThirdPartyApplicationList(): List<Application>
    suspend fun getSystemApplicationList(): List<Application>
    suspend fun getApplicationInfo(packageName: String): Application?
    suspend fun getActivityList(packageName: String): List<ActivityInfo>
    suspend fun getReceiverList(packageName: String): List<ActivityInfo>
    suspend fun getServiceList(packageName: String): List<ServiceInfo>
    suspend fun getProviderList(packageName: String): List<ProviderInfo>
    suspend fun getApplicationComponents(packageName: String): PackageInfo
    suspend fun getApplicationComponents(packageName: String, flags: Int): PackageInfo?
    fun checkComponentIsEnabled(componentName: ComponentName): Boolean
    fun isAppInstalled(packageName: String?): Boolean
    fun isSystemApp(packageName: String?): Boolean
    fun isDebugMode(): Boolean
    suspend fun isRunning(packageName: String): Boolean
}
