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

package com.merxury.blocker.core.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.model.system.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FIXTURE = "com.merxury.blocker.core.system.fixture"

/**
 * Reads the components this test APK declares in its own manifest through the real
 * PackageManager, to pin the platform behaviour the reader relies on.
 */
class LocalSystemPackageReaderDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().context
    private val platform = LocalPlatformPackages(context)
    private val reader = PlatformSystemPackageReader(platform, Dispatchers.IO)
    private val ownUser = runBlocking { platform.users() }.single().id
    private val self = AppKey(context.packageName, ownUser)
    private val disabledService = ComponentId(self.packageName, "$FIXTURE.DisabledByResourceService")

    @After
    fun resetSetting() {
        setOwnSetting(disabledService, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    }

    @Test
    fun givenFixtureManifest_whenReadDeclarations_thenAliasAndResourceValuesResolved() = runTest {
        val declarations = assertNotNull(reader.declarations(self).getOrNull())
        val fixtures = declarations.filter { it.id.className.startsWith(FIXTURE) }.toSet()

        assertEquals(
            setOf(
                fixture("TargetActivity", ComponentType.ACTIVITY),
                fixture("Alias", ComponentType.ACTIVITY, exported = true, enabledInManifest = false),
                fixture("DisabledByResourceService", ComponentType.SERVICE, enabledInManifest = false),
                fixture("EnabledReceiver", ComponentType.RECEIVER, exported = true),
                fixture("FixtureProvider", ComponentType.PROVIDER, enabledInManifest = false),
            ),
            fixtures,
        )
    }

    @Test
    fun givenComponentEnabledByOverride_whenRead_thenSettingChangesAndManifestValueDoesNot() = runTest {
        assertEquals(PmSetting.DEFAULT, readSetting(disabledService))

        setOwnSetting(disabledService, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        assertEquals(PmSetting.ENABLED, readSetting(disabledService))
        val declaration = reader.declarations(self).getOrNull()!!.single { it.id == disabledService }
        assertEquals(false, declaration.enabledInManifest)
    }

    @Test
    fun givenComponentNotInManifest_whenReadPmSettings_thenNoOverrideReported() = runTest {
        // Android 14 answers DEFAULT for a name the app does not declare; older releases reject it.
        val setting = readSetting(ComponentId(self.packageName, "$FIXTURE.Missing"))

        assertTrue(setting == PmSetting.DEFAULT || setting == PmSetting.UNKNOWN, "was $setting")
    }

    @Test
    fun whenReadAppsOfOwnUser_thenThisPackageListedWithItsApk() = runTest {
        val app = reader.apps(ownUser).getOrNull()!!.single { it.key == self }

        assertTrue(app.apkPath!!.endsWith(".apk"))
        assertEquals(app, reader.app(self).getOrNull())
    }

    @Test
    fun givenAnotherUser_whenReadApps_thenUserNotReachable() = runTest {
        val other = UserId(ownUser.value + 1)

        assertEquals(ReadResult.Failure(ReadFailure.UserNotReachable(other)), reader.apps(other))
    }

    @Test
    fun givenMissingPackage_whenReadDeclarations_thenPackageNotFound() = runTest {
        val missing = AppKey("com.merxury.blocker.not.installed", ownUser)

        assertEquals(ReadResult.Failure(ReadFailure.PackageNotFound(missing)), reader.declarations(missing))
    }

    private suspend fun readSetting(id: ComponentId): PmSetting? = reader.pmSettings(self, listOf(id)).getOrNull()?.get(id)

    private fun setOwnSetting(id: ComponentId, state: Int) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(id.packageName, id.className),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun fixture(
        simpleName: String,
        type: ComponentType,
        exported: Boolean = false,
        enabledInManifest: Boolean = true,
    ) = ComponentDeclaration(ComponentId(self.packageName, "$FIXTURE.$simpleName"), type, exported, enabledInManifest)
}
