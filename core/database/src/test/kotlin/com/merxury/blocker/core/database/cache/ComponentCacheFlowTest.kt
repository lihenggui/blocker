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

package com.merxury.blocker.core.database.cache

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room re-runs a query after any write to a table it reads. These tests write something unrelated,
 * wait until a flow that the write does affect has emitted, then write something related: the
 * next item must be the related one. Room may merge invalidations that arrive close together, so
 * a pass shows unrelated writes do not emit in the common case, not under every interleaving.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ComponentCacheFlowTest : ComponentCacheTestBase() {

    @Test
    fun givenObservedComponents_whenStateChanges_thenEachRelevantWriteEmitsAndOtherUsersWritesDoNot() = runTest {
        install("com.example")
        dao.declare("com.example", declaration("com.example"))

        turbineScope {
            val owner = dao.observeComponents("com.example", OWNER).testIn(backgroundScope)
            val work = dao.observeComponents("com.example", WORK).testIn(backgroundScope)
            assertThat(owner.awaitItem().single().pmSetting).isNull()
            work.awaitItem()

            dao.upsertPmSettings("com.example", WORK, mapOf("com.example.Main" to PmSetting.DISABLED))
            assertThat(work.awaitItem().single().pmSetting).isEqualTo(PmSetting.DISABLED)

            dao.upsertPmSettings("com.example", OWNER, mapOf("com.example.Main" to PmSetting.ENABLED))
            assertThat(owner.awaitItem().single().pmSetting).isEqualTo(PmSetting.ENABLED)

            dao.upsertIfwBlocks("com.example", mapOf("com.example.Main" to IfwBlock.BLOCKED))
            assertThat(owner.awaitItem().single().ifwBlock).isEqualTo(IfwBlock.BLOCKED)
            assertThat(work.awaitItem().single().ifwBlock).isEqualTo(IfwBlock.BLOCKED)

            dao.declare("com.example", declaration("com.example"), declaration("com.example", "com.example.Second"))
            assertThat(owner.awaitItem()).hasSize(2)
            assertThat(work.awaitItem()).hasSize(2)

            owner.cancelAndIgnoreRemainingEvents()
            work.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenObservedComponents_whenTheAppIsRemovedForThatUser_thenTheCascadeEmitsUnknown() = runTest {
        install("com.example")
        dao.declare("com.example", declaration("com.example"))
        dao.upsertPmSettings("com.example", OWNER, mapOf("com.example.Main" to PmSetting.DISABLED))

        dao.observeComponents("com.example", OWNER).test {
            assertThat(awaitItem().single().pmSetting).isEqualTo(PmSetting.DISABLED)

            dao.deleteApp("com.example", OWNER)

            assertThat(awaitItem().single().pmSetting).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenObservedApps_whenAnotherUsersAppsChange_thenOnlyThatUsersFlowEmits() = runTest {
        install("com.example")

        turbineScope {
            val owner = dao.observeApps(OWNER).testIn(backgroundScope)
            val work = dao.observeApps(WORK).testIn(backgroundScope)
            owner.awaitItem()
            work.awaitItem()

            dao.replaceApps(WORK, emptyList())
            assertThat(work.awaitItem()).isEmpty()

            dao.replaceApps(OWNER, listOf(app("com.example", OWNER, label = "Renamed")))
            assertThat(owner.awaitItem().single().label).isEqualTo("Renamed")

            owner.cancelAndIgnoreRemainingEvents()
            work.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenObservedUsers_whenReplacingWithTheSameUsers_thenNothingIsEmitted() = runTest {
        install()

        turbineScope {
            val users = dao.observeUsers().testIn(backgroundScope)
            val apps = dao.observeApps(OWNER).testIn(backgroundScope)
            assertThat(users.awaitItem()).hasSize(2)
            apps.awaitItem()

            dao.replaceUsers(listOf(user(OWNER), user(WORK)))
            dao.upsertApp(app("com.example", OWNER))
            apps.awaitItem()

            dao.replaceUsers(listOf(user(OWNER)))
            assertThat(users.awaitItem().map { it.userId }).containsExactly(OWNER)

            users.cancelAndIgnoreRemainingEvents()
            apps.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenObservedApp_whenTheSameAppIsWrittenAgain_thenOnlyTheRealChangeEmits() = runTest {
        install("com.example")

        dao.observeApp("com.example", OWNER).test {
            assertThat(awaitItem()).isEqualTo(app("com.example", OWNER))

            dao.upsertApp(app("com.example", OWNER))
            dao.upsertApp(app("com.example", OWNER, lastUpdateTime = 9_000))

            assertThat(awaitItem()?.lastUpdateTime?.toEpochMilliseconds()).isEqualTo(9_000)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
