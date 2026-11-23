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

package com.merxury.blocker.core.data.device

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val PACKAGES = listOf("com.example.a", "com.example.b", "com.example.c")

/** [ComponentStatusRepository.refreshAll] and [ComponentStatusRepository.disabledByPackageManager]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class RefreshAllTest : DeviceRepositoryTestBase() {

    private fun installAll(packages: List<String> = PACKAGES) = packages.forEach { system.install(installedApp(it), declaration("Main", packageName = it)) }

    @Test
    fun givenThreeApps_whenRefreshAll_thenProgressForEachAndFinished() = runTest {
        installAll()

        val progress = components(backgroundScope).refreshAll(OWNER).toList()

        assertThat(progress.first()).isEqualTo(RefreshProgress.Refreshing(done = 0, total = 3, current = null))
        val steps = progress.filterIsInstance<RefreshProgress.Refreshing>().drop(1)
        assertThat(steps.map { it.done }).containsExactly(1, 2, 3).inOrder()
        assertThat(steps.map { it.current }).containsExactlyElementsIn(PACKAGES)
        assertThat(progress.last()).isEqualTo(RefreshProgress.Finished(total = 3, failed = 0, appListFailure = null))
        assertThat(components(backgroundScope).allComponents(OWNER).first()).hasSize(3)
    }

    @Test
    fun givenOneAppFailing_whenRefreshAll_thenOthersStoredAndFailureCounted() = runTest {
        installAll()
        system.declarationFailures["com.example.b"] = ReadFailure.ManifestUnreadable("com.example.b")
        val repository = components(backgroundScope)

        val progress = repository.refreshAll(OWNER).toList()

        assertThat(progress.last()).isEqualTo(RefreshProgress.Finished(total = 3, failed = 1, appListFailure = null))
        assertThat(repository.allComponents(OWNER).first().map { it.declaration.id.packageName }).containsExactly("com.example.a", "com.example.c").inOrder()
    }

    @Test
    fun givenAppListUnreadable_whenRefreshAll_thenOnlyFinishedWithTheFailure() = runTest {
        installAll()
        system.appsFailures[OWNER] = ReadFailure.ChannelUnavailable

        val progress = components(backgroundScope).refreshAll(OWNER).toList()

        assertThat(progress).containsExactly(RefreshProgress.Finished(total = 0, failed = 0, appListFailure = ReadFailure.ChannelUnavailable))
        assertThat(system.reads("declarations")).isEqualTo(0)
    }

    @Test
    fun givenManyApps_whenCollectorCancelsEarly_thenRemainingAppsNotRead() = runTest {
        val packages = (1..40).map { "com.example.app$it" }
        installAll(packages)

        val progress = components(backgroundScope).refreshAll(OWNER).take(2).toList()

        assertThat((progress.last() as RefreshProgress.Refreshing).done).isEqualTo(1)
        assertThat(system.reads("declarations")).isLessThan(packages.size)
    }

    @Test
    fun givenStaleCache_whenDisabledByPackageManager_thenAnswerFromTheSystemAndCacheUpdated() = runTest {
        installAll()
        val repository = components(backgroundScope)
        val a = declaration("Main", packageName = "com.example.a")
        val c = declaration("Main", packageName = "com.example.c")
        system.put(OWNER, a, PmSetting.DISABLED)
        repository.refreshAll(OWNER).toList()
        system.put(OWNER, a, PmSetting.DEFAULT)
        system.put(OWNER, c, PmSetting.DISABLED)
        system.put(WORK, declaration("Main", packageName = "com.example.b"), PmSetting.DISABLED)

        val disabled = repository.disabledByPackageManager(OWNER)

        assertThat(disabled).isEqualTo(ReadResult.Success(listOf(ComponentKey(c.id, OWNER))))
        assertThat(repository.component(ComponentKey(a.id, OWNER)).first()).isEqualTo(status(a, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
        assertThat(repository.component(ComponentKey(c.id, OWNER)).first()).isEqualTo(status(c, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenOneAppsSettingsUnreadable_whenDisabledByPackageManager_thenFailure() = runTest {
        installAll()
        system.pmFailures["com.example.b"] = ReadFailure.ChannelUnavailable

        val disabled = components(backgroundScope).disabledByPackageManager(OWNER)

        assertThat(disabled).isEqualTo(ReadResult.Failure(ReadFailure.ChannelUnavailable))
    }

    @Test
    fun givenRulesUnreadable_whenDisabledByPackageManager_thenSettingsStillAnswered() = runTest {
        installAll()
        val a = declaration("Main", packageName = "com.example.a")
        system.put(OWNER, a, PmSetting.DISABLED)
        files.noAccess = true

        val disabled = components(backgroundScope).disabledByPackageManager(OWNER)

        assertThat(disabled).isEqualTo(ReadResult.Success(listOf(ComponentKey(a.id, OWNER))))
    }

    @Test
    fun givenAppRemovedDuringTheRead_whenDisabledByPackageManager_thenSkipped() = runTest {
        installAll()
        system.pmFailures["com.example.b"] = ReadFailure.PackageNotFound(AppKey("com.example.b", OWNER))

        val disabled = components(backgroundScope).disabledByPackageManager(OWNER)

        assertThat(disabled).isEqualTo(ReadResult.Success(emptyList<ComponentKey>()))
    }
}
