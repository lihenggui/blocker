// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.Context
import be.mygod.librootkotlinx.ParcelableBoolean
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.root.RootCommandExecutor
import com.merxury.core.ifw.ResetWakeRules
import com.merxury.core.ifw.WakeResetTransport
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class AuthorizedWakeRuleWriterTest {
    @Test fun givenDirectRoot_whenResettingEitherScope_thenTheAuthorizedRootSessionIsReused() = runTest {
        val reader = mock<WakeSystemReader>()
        val root = mock<RootCommandExecutor>()
        val context = mock<Context>()
        whenever(context.packageName).thenReturn("blocker.test")
        val writer = AuthorizedWakeRuleWriter(reader, root, context)

        for (target in listOf(null, "target.pkg")) {
            val command = ResetWakeRules("blocker.test", target)
            whenever(root.execute(command)).thenReturn(ParcelableBoolean(true))

            assertThat(writer.reset(target, WakeResetTransport.DIRECT_ROOT)).isTrue()

            verify(root).execute(command)
        }
        verifyNoInteractions(reader)
    }
}
