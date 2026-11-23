/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.privileged

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.privileged.calls.ApplicationEnabledSettings
import com.merxury.blocker.core.privileged.calls.ComponentEnabledSettings
import com.merxury.blocker.core.privileged.calls.GetPackageInfo
import com.merxury.blocker.core.privileged.calls.InstalledPackages
import com.merxury.blocker.core.privileged.calls.ListUsers
import com.merxury.blocker.core.system.LocalPlatformPackages
import com.merxury.blocker.core.system.PlatformPackages
import com.merxury.blocker.core.system.UserNotReachableException
import javax.inject.Inject

/**
 * [PlatformPackages] through the privileged channel, which reaches every user. It only uses a
 * channel that is already ready and never connects, so reading never shows a permission prompt.
 *
 * A failed call becomes what [PlatformPackages] callers understand: a package the user does not
 * have is [NameNotFoundException], a user the identity may not read or that does not exist is
 * [UserNotReachableException], and an unavailable channel or a system error is a
 * [RuntimeException]. Cancelling a read cancels the call in flight. Labels and APK parsing run in
 * this process: the APK is the same for every user.
 */
internal class PrivilegedPlatformPackages(
    private val executor: PrivilegedExecutor,
    private val local: PlatformPackages,
) : PlatformPackages {

    @Inject constructor(
        executor: ActivePrivilegedChannel,
        local: LocalPlatformPackages,
    ) : this(executor as PrivilegedExecutor, local as PlatformPackages)

    override suspend fun users(): List<DeviceUser> = call(ListUsers, user = null, packageName = null)

    override suspend fun installedPackages(user: UserId): List<PackageInfo> = call(InstalledPackages(user), user, packageName = null)

    override suspend fun packageInfo(packageName: String, flags: Int, user: UserId): PackageInfo = call(GetPackageInfo(packageName, flags, user), user, packageName)

    override fun archiveInfo(apkPath: String, flags: Int): PackageInfo? = local.archiveInfo(apkPath, flags)

    /** Splits a long list over several calls, because one request to the root process is bounded. */
    override suspend fun applicationEnabledSettings(packageNames: List<String>, user: UserId): List<Int?> = packageNames.chunked(NAMES_PER_CALL)
        .flatMap { chunk -> call(ApplicationEnabledSettings(chunk, user), user, packageName = null) }

    /** Splits a long list over several calls, because one request to the root process is bounded. */
    override suspend fun componentEnabledSettings(packageName: String, classNames: List<String>, user: UserId): List<Int?> = classNames.chunked(NAMES_PER_CALL)
        .ifEmpty { listOf(emptyList()) }
        .flatMap { chunk -> call(ComponentEnabledSettings(packageName, chunk, user), user, packageName) }

    override fun label(application: ApplicationInfo): String = local.label(application)

    private suspend fun <R> call(call: PrivilegedCall<R>, user: UserId?, packageName: String?): R = when (val result = executor.executeIfReady(call)) {
        is CallResult.Success -> result.value
        is CallResult.Failure -> throw result.reason.toException(call, user, packageName)
    }

    private fun CallFailure.toException(call: PrivilegedCall<*>, user: UserId?, packageName: String?): Exception = when {
        this is CallFailure.TargetNotFound && packageName != null -> NameNotFoundException(packageName)

        this is CallFailure.TargetNotFound && user != null -> UserNotReachableException(user)

        this is CallFailure.NotPermitted && user != null -> UserNotReachableException(user)

        // The reader reports a RuntimeException as an unavailable channel.
        else -> IllegalStateException("$call failed: $this")
    }

    private companion object {
        // Package and class names are about a hundred bytes each, well inside what one root call carries.
        const val NAMES_PER_CALL = 500
    }
}
