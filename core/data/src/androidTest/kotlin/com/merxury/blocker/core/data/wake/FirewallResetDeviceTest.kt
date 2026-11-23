// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.core.ifw.IfwStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val TAG = "FirewallResetDeviceTest"

/**
 * Runs the reset [ResetIfwWorker][com.merxury.blocker.core.rule.work.ResetIfwWorker] uses, with the
 * app's bindings, against the real rule directory. The device is set up and checked from outside:
 * the rule files are placed in `/data/system/ifw` before the run and listed after it.
 *
 * Argument `firewallReset=<case>`: `root` (su allows this package; the controller type is root),
 * `shizuku-root` (Shizuku runs as root and allows this package, su does not) or `shizuku-shell`
 * (Shizuku runs as the shell). Without it every test is skipped.
 */
@HiltAndroidTest
class FirewallResetDeviceTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var repository: FirewallResetRepository

    @Inject
    lateinit var userData: UserDataRepository

    @Inject
    lateinit var store: IfwStore

    private val case: String? = InstrumentationRegistry.getArguments().getString("firewallReset")

    @Before
    fun allowHiddenApis() {
        // The Shizuku transport calls hidden interfaces in this process, as the app does.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
    }

    /**
     * Skips unless the case is one of [cases], then injects. Only the test that runs injects: each
     * injection builds a new graph, and a second one would open the same DataStore files again.
     */
    private fun runCase(vararg cases: String) {
        assumeTrue(case in cases)
        hilt.inject()
    }

    @Test
    fun givenDamagedAndValidRuleFiles_whenResetAll_thenBothDeleted() = runBlocking<Unit> {
        runCase("root", "shizuku-root")
        userData.setControllerType(if (case == "root") ControllerType.PM else ControllerType.SHIZUKU)
        val reported = mutableListOf<String>()

        val removed = repository.reset(null) { pkg, index, total -> reported += "$pkg $index/$total" }

        Log.i(TAG, "$case: removed $removed, reported $reported")
        assertEquals(2, removed)
        assertEquals(IfwResult.Success(emptyList()), store.packagesWithRuleFiles())
    }

    @Test
    fun givenShellIdentity_whenResetAll_thenNoAccessAndNothingDeleted() = runBlocking<Unit> {
        runCase("shizuku-shell")
        userData.setControllerType(ControllerType.SHIZUKU)

        val removed = repository.reset(null) { pkg, _, _ -> error("Asked to delete $pkg as the shell") }

        Log.i(TAG, "$case: removed $removed")
        assertNull(removed)
    }
}
