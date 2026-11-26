// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.wake

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeBlockDaoTest {
    @Test fun givenMutualBlocks_whenUpdatingAndResetting_thenRowsAreIndependentAndOnlyFirewallRowsReset() = runTest {
        val room = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = room.blocks()
            val incoming = WakeBlockEntity("in", "first.pkg", "FIREWALL_RULE", "SERVICE", "second.pkg", "first.pkg.Wake", null, 1)
            val outgoing = incoming.copy(pathKey = "out", target = "second.pkg", sender = "first.pkg", component = "second.pkg.Wake")
            val component = incoming.copy(pathKey = "pm", method = "COMPONENT", componentType = null, sender = null)
            dao.observeAll().test {
                assertThat(awaitItem()).isEmpty()
                dao.update(listOf(incoming, outgoing, component), emptyList())
                assertThat(awaitItem()).containsExactly(incoming, outgoing, component)
                val edited = outgoing.copy(blockedAt = 2)
                dao.update(listOf(edited), listOf(incoming.pathKey))
                assertThat(awaitItem()).containsExactly(edited, component)
                dao.deleteFirewall("first.pkg")
                assertThat(dao.all()).containsExactly(edited, component)
                dao.deleteFirewall(null)
                assertThat(awaitItem()).containsExactly(component)
            }
        } finally {
            room.close()
        }
    }
}
