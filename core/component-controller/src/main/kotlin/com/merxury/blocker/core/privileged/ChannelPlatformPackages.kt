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
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.system.LocalPlatformPackages
import com.merxury.blocker.core.system.PlatformPackages
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Reads packages through the app's own PackageManager without waiting for privileged access. */
suspend fun <T> withLocalPackageReads(block: suspend () -> T): T = withContext(LocalPackageReads) { block() }

private object LocalPackageReads : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<LocalPackageReads>
}

/**
 * The [PlatformPackages] the app reads through: [privileged] while the privileged channel is
 * [ChannelState.Ready], which reaches every user, and [local] otherwise, which reaches only the
 * user Blocker runs in. Each request first waits for [PrivilegedExecutor.checkedState], so a read
 * right after a cold start waits for the first attempt to acquire the channel without a prompt
 * instead of reading only Blocker's own user; once that attempt ends unavailable, reads go local
 * at once. Privileged reads follow the quiet policy, including when reopening an idle root session.
 * Each request is routed on its own, so a channel that becomes ready is used from the next one.
 */
internal class ChannelPlatformPackages(
    private val channel: PrivilegedExecutor,
    private val privileged: PlatformPackages,
    private val local: PlatformPackages,
) : PlatformPackages {

    @Inject constructor(
        channel: PrivilegedExecutor,
        privileged: PrivilegedPlatformPackages,
        local: LocalPlatformPackages,
    ) : this(channel, privileged as PlatformPackages, local as PlatformPackages)

    private suspend fun active(): PlatformPackages = when {
        currentCoroutineContext()[LocalPackageReads.Key] != null -> local
        channel.checkedState() is ChannelState.Ready -> privileged
        else -> local
    }

    override suspend fun users(): List<DeviceUser> = active().users()

    override suspend fun installedPackages(user: UserId): List<PackageInfo> = active().installedPackages(user)

    override suspend fun packageInfo(packageName: String, flags: Int, user: UserId): PackageInfo = active().packageInfo(packageName, flags, user)

    override fun archiveInfo(apkPath: String, flags: Int): PackageInfo? = local.archiveInfo(apkPath, flags)

    override suspend fun applicationEnabledSettings(packageNames: List<String>, user: UserId): List<Int?> = active().applicationEnabledSettings(packageNames, user)

    override suspend fun componentEnabledSettings(packageName: String, classNames: List<String>, user: UserId): List<Int?> = active().componentEnabledSettings(packageName, classNames, user)

    override fun label(application: ApplicationInfo): String = local.label(application)
}
