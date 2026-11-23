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
import javax.inject.Inject

/**
 * The [PlatformPackages] the app reads through: [privileged] while the privileged channel is
 * [ChannelState.Ready], which reaches every user, and [local] otherwise, which reaches only the
 * user Blocker runs in. Reading never connects the channel, so it never shows a permission prompt.
 * Each request is routed on its own, so a channel that becomes ready is used from the next one.
 */
internal class ChannelPlatformPackages(
    private val channel: PrivilegedChannel,
    private val privileged: PlatformPackages,
    private val local: PlatformPackages,
) : PlatformPackages {

    @Inject constructor(
        channel: PrivilegedChannel,
        privileged: PrivilegedPlatformPackages,
        local: LocalPlatformPackages,
    ) : this(channel, privileged as PlatformPackages, local as PlatformPackages)

    private val active: PlatformPackages
        get() = if (channel.state.value is ChannelState.Ready) privileged else local

    override fun users(): List<DeviceUser> = active.users()

    override fun installedPackages(user: UserId): List<PackageInfo> = active.installedPackages(user)

    override fun packageInfo(packageName: String, flags: Int, user: UserId): PackageInfo = active.packageInfo(packageName, flags, user)

    override fun archiveInfo(apkPath: String, flags: Int): PackageInfo? = local.archiveInfo(apkPath, flags)

    override fun applicationEnabledSetting(packageName: String, user: UserId): Int = active.applicationEnabledSetting(packageName, user)

    override fun componentEnabledSettings(packageName: String, classNames: List<String>, user: UserId): List<Int?> = active.componentEnabledSettings(packageName, classNames, user)

    override fun label(application: ApplicationInfo): String = local.label(application)
}
