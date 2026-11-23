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
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.system.PlatformPackages
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelPlatformPackagesTest {

    private val channel = FixedChannel()
    private val privileged = NamedPackages("privileged")
    private val local = NamedPackages("local")
    private val packages = ChannelPlatformPackages(channel, privileged, local)

    @Test
    fun givenEachUnavailableReason_whenRead_thenLocal() {
        UnavailableReason.entries.forEach { reason ->
            channel.state.value = ChannelState.Unavailable(reason)

            assertThat(packages.users().single().name).isEqualTo("local")
        }
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenChannelReady_whenRead_thenPrivileged() {
        channel.state.value = ChannelState.Ready(PrivilegedIdentity.SHELL)

        assertThat(packages.users().single().name).isEqualTo("privileged")
        assertThat(packages.installedPackages(UserId(10)).single().packageName).isEqualTo("privileged")
        assertThat(packages.packageInfo("p", 0, UserId(10)).packageName).isEqualTo("privileged")
        assertThat(packages.applicationEnabledSetting("p", UserId(10))).isEqualTo(privileged.answer)
        assertThat(packages.componentEnabledSettings("p", listOf("c"), UserId(10))).containsExactly(privileged.answer)
    }

    @Test
    fun givenChannelBecomesReadyThenLost_whenRead_thenEachRequestRoutedOnItsOwn() {
        assertThat(packages.users().single().name).isEqualTo("local")

        channel.state.value = ChannelState.Ready(PrivilegedIdentity.ROOT)
        assertThat(packages.users().single().name).isEqualTo("privileged")

        channel.state.value = ChannelState.Unavailable(UnavailableReason.FAILED)
        assertThat(packages.users().single().name).isEqualTo("local")
    }

    @Test
    fun givenChannelReady_whenLabelOrArchive_thenStillLocal() {
        channel.state.value = ChannelState.Ready(PrivilegedIdentity.ROOT)

        assertThat(packages.label(ApplicationInfo())).isEqualTo("local")
        assertThat(packages.archiveInfo("/data/app/base.apk", 0)?.packageName).isEqualTo("local")
    }
}

private class FixedChannel : PrivilegedChannel {
    override val state = MutableStateFlow<ChannelState>(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
    var connectCount = 0

    override suspend fun connect(): ChannelState {
        connectCount++
        return state.value
    }
}

/** Answers every request with its [name], so a test can tell which one was asked. */
private class NamedPackages(private val name: String) : PlatformPackages {
    val answer = name.length

    private fun info() = PackageInfo().apply { packageName = name }

    override fun users() = listOf(DeviceUser(UserId(0), name, UserKind.OWNER, parent = null, running = true))
    override fun installedPackages(user: UserId) = listOf(info())
    override fun packageInfo(packageName: String, flags: Int, user: UserId) = info()
    override fun archiveInfo(apkPath: String, flags: Int) = info()
    override fun applicationEnabledSetting(packageName: String, user: UserId) = answer
    override fun componentEnabledSettings(packageName: String, classNames: List<String>, user: UserId) = classNames.map { answer }
    override fun label(application: ApplicationInfo) = name
}
