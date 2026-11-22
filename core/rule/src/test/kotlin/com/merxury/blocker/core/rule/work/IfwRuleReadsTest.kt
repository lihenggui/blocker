// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.rule.ComponentRule
import com.merxury.blocker.core.testing.controller.FakeIntentFirewall
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IfwRuleReadsTest {
    private val packageName = "com.example"
    private val blockedReceiver = "com.example.BootReceiver"
    private val intentFirewall = FakeIntentFirewall()

    // ── hasUnreadableRules ──

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

    // ── IfwRunReader ──

    @Test
    fun givenUnreadableRuleFile_whenReading_thenThrowsInsteadOfGuessing() = runTest {
        intentFirewall.saveRules(packageName, blockedReceiverRules())
        intentFirewall.unreadablePackages += packageName
        // The display read guesses "not blocked" for the same file.
        assertTrue(intentFirewall.getComponentEnableState(packageName, blockedReceiver))
        val reader = IfwRunReader(intentFirewall)

        assertFailsWith<IOException> { reader.read(packageName) }
        assertFalse(reader.rootUnavailable)
    }

    @Test
    fun givenBlockedComponent_whenReading_thenOnlyThatComponentIsDisabled() = runTest {
        intentFirewall.saveRules(packageName, blockedReceiverRules())

        val isEnabled = assertNotNull(IfwRunReader(intentFirewall).read(packageName))

        assertFalse(isEnabled(packageName, blockedReceiver))
        assertTrue(isEnabled(packageName, "com.example.OtherReceiver"))
    }

    @Test
    fun givenRootUnavailable_whenReading_thenStatesAreUnknownAndRootIsNotProbedAgain() = runTest {
        val rootless = RootlessIntentFirewall()
        val reader = IfwRunReader(rootless)

        assertNull(reader.read(packageName))
        assertNull(reader.read("com.other"))

        assertTrue(reader.rootUnavailable)
        assertEquals(1, rootless.reads)
    }

    @Test
    fun givenUnreadableRuleFileFirst_whenReadingAnotherPackage_thenItIsStillRead() = runTest {
        intentFirewall.unreadablePackages += "com.broken"
        intentFirewall.saveRules(packageName, blockedReceiverRules())
        val reader = IfwRunReader(intentFirewall)
        assertFailsWith<IOException> { reader.read("com.broken") }

        val isEnabled = assertNotNull(reader.read(packageName))

        assertFalse(isEnabled(packageName, blockedReceiver))
    }

    @Test
    fun givenReadableRuleFile_whenReadingForRestore_thenComparesWithTheRules() = runTest {
        intentFirewall.saveRules(packageName, blockedReceiverRules())

        val read = IfwRunReader(intentFirewall).readForRestore(packageName)

        assertIs<IfwRestoreRead.Rules>(read)
        assertFalse(read.isEnabled(packageName, blockedReceiver))
    }

    @Test
    fun givenUnreadableRuleFile_whenReadingForRestore_thenNoIfwChangeIsAllowed() = runTest {
        intentFirewall.unreadablePackages += packageName

        assertSame(IfwRestoreRead.Unreadable, IfwRunReader(intentFirewall).readForRestore(packageName))
    }

    @Test
    fun givenRootUnavailable_whenReadingForRestore_thenRestoreKeepsItsRootlessBehaviour() = runTest {
        assertSame(IfwRestoreRead.RootUnavailable, IfwRunReader(RootlessIntentFirewall()).readForRestore(packageName))
    }

    // ── backupEntries ──

    @Test
    fun givenKnownIfwState_whenBuildingBackupEntries_thenIfwEntryPrecedesPackageManagerEntry() {
        assertEquals(
            listOf(
                ComponentRule(packageName, blockedReceiver, false, RECEIVER, IFW),
                ComponentRule(packageName, blockedReceiver, true, RECEIVER, PM),
            ),
            backupEntries(packageName, blockedReceiver, RECEIVER, ifwEnabled = false, pmEnabled = true),
        )
    }

    @Test
    fun givenUnknownIfwState_whenBuildingBackupEntries_thenOnlyThePackageManagerEntryIsWritten() {
        assertEquals(
            listOf(ComponentRule(packageName, blockedReceiver, true, RECEIVER, PM)),
            backupEntries(packageName, blockedReceiver, RECEIVER, ifwEnabled = null, pmEnabled = true),
        )
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

/** Reports root as unavailable for every read, as IntentFirewall does without root. */
private class RootlessIntentFirewall(
    private val delegate: IIntentFirewall = FakeIntentFirewall(),
) : IIntentFirewall by delegate {
    var reads = 0

    override suspend fun getRules(packageName: String): IfwRules {
        reads++
        throw RootUnavailableException()
    }
}
