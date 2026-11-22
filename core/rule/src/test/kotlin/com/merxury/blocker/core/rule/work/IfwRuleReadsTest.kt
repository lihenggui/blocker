// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import com.merxury.blocker.core.testing.controller.FakeIntentFirewall
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IfwRuleReadsTest {
    private val packageName = "com.example"
    private val blockedReceiver = "com.example.BootReceiver"
    private val intentFirewall = FakeIntentFirewall()

    @Test
    fun givenUnreadableRuleFile_whenChangesWereNotApplied_thenTheyWereRefused() = runTest {
        intentFirewall.saveRules(packageName, blockedReceiverRules())
        intentFirewall.unreadablePackages += packageName

        assertTrue(intentFirewall.hasUnreadableRules(packageName))
    }

    @Test
    fun givenReadableRuleFile_whenChangesWereNotApplied_thenTheyWereLegitimatelySkipped() = runTest {
        intentFirewall.saveRules(packageName, blockedReceiverRules())

        assertFalse(intentFirewall.hasUnreadableRules(packageName))
    }

    @Test
    fun givenNoRuleFile_whenChangesWereNotApplied_thenTheyWereLegitimatelySkipped() = runTest {
        assertFalse(intentFirewall.hasUnreadableRules(packageName))
    }

    @Test
    fun givenUnreadableRuleFile_whenReadingStatesForBackup_thenThrowsInsteadOfGuessing() = runTest {
        intentFirewall.saveRules(packageName, blockedReceiverRules())
        intentFirewall.unreadablePackages += packageName
        // The display read guesses "not blocked" for the same file.
        assertTrue(intentFirewall.getComponentEnableState(packageName, blockedReceiver))

        assertFailsWith<IOException> { intentFirewall.readIfwEnabledStates(packageName) }
    }

    @Test
    fun givenBlockedComponent_whenReadingStatesForBackup_thenOnlyThatComponentIsDisabled() = runTest {
        intentFirewall.saveRules(packageName, blockedReceiverRules())

        val isEnabled = intentFirewall.readIfwEnabledStates(packageName)

        assertFalse(isEnabled(packageName, blockedReceiver))
        assertTrue(isEnabled(packageName, "com.example.OtherReceiver"))
    }

    private fun blockedReceiverRules() = IfwRules(
        listOf(
            IfwRule(
                componentType = IfwComponentType.BROADCAST,
                filters = listOf(IfwFilter.ComponentFilter("$packageName/$blockedReceiver")),
            ),
        ),
    )
}
