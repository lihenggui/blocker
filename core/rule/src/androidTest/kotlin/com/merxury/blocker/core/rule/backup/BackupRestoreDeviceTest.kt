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

package com.merxury.blocker.core.rule.backup

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.PmBlockRecordSeeder
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.model.system.getOrNull
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.system.SystemPackageReader
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TAG = "BackupRestoreDeviceTest"
private const val APP_B = "blk.synth.b"
private const val APP_C = "blk.synth.c"
private const val OWN_RULE_FILE = "bk7b-own-rules"

// The package of core:component-controller's device tests, which ships an activity disabled.
private const val DISABLED_TARGET = "com.merxury.blocker.core.controller.test"

// A rule a user wrote by hand: block the receiver only for senders outside the system, and an
// allow rule for a service.
private val HAND_WRITTEN_RULES = """
    <rules>
      <broadcast block="true" log="true">
        <component-filter name="$APP_B/com.google.android.gms.measurement.AppMeasurementReceiver" />
        <not>
          <sender type="system" />
        </not>
      </broadcast>
      <service block="false" log="false">
        <component-filter name="$APP_B/com.google.android.gms.ads.AdService" />
      </service>
    </rules>
""".trimIndent()

/**
 * Runs backup, restore, IFW backup, MAT import and PackageManager reset with the app's bindings
 * against the real system, for the owner and a work profile. The device is prepared from outside:
 * root granted to this package; `blk.synth.b` installed for the owner and the profile;
 * `blk.synth.c` for the owner and the profile, so taking it away from the owner keeps the
 * package; and core:component-controller's device test APK for the owner, whose activity the
 * manifest ships disabled. Backups go to a folder in this package's files directory through
 * [FileBackupStorage], since the Storage Access Framework picker cannot be driven from a test.
 *
 * Arguments `backupRestore=root` and `profileUser=<id>`; without them every test is skipped, except
 * that the reset case of Blocker's own blocks needs only `backupRestore=root`. Run
 * one test per instrumentation (`-e class BackupRestoreDeviceTest#<test>`): each injection builds
 * the app's graph again, and a second one in the same process would open the DataStore files
 * again.
 */
@HiltAndroidTest
class BackupRestoreDeviceTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var seeder: PmBlockRecordSeeder

    @Inject lateinit var exporter: BlockerRuleExporter

    @Inject lateinit var importer: BlockerRuleImporter

    @Inject lateinit var ifwExporter: IfwRuleExporter

    @Inject lateinit var ifwImporter: IfwRuleImporter

    @Inject lateinit var mat: MatRuleImporter

    @Inject lateinit var components: ComponentStatusRepository

    @Inject lateinit var channel: PrivilegedChannel

    @Inject lateinit var userData: UserDataRepository

    @Inject lateinit var store: IfwStore

    @Inject lateinit var reader: SystemPackageReader

    @Inject lateinit var reset: PackageManagerReset

    private val arguments = InstrumentationRegistry.getArguments()
    private val profile = arguments.getString("profileUser")?.toIntOrNull()?.let(::UserId)
    private var injected = false
    private val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "bk7b")
    private val storage = FileBackupStorage(folder)

    @Before
    fun allowHiddenApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
    }

    private fun runCase(needsProfile: Boolean = true) = runBlocking {
        assumeTrue(arguments.getString("backupRestore") == "root" && (profile != null || !needsProfile))
        hilt.inject()
        injected = true
        userData.setControllerType(ControllerType.PM)
        folder.deleteRecursively()
    }

    @After
    fun restore() = runBlocking {
        // Skipped tests never injected; there is nothing to undo then.
        if (!injected) return@runBlocking
        // The reset case takes APP_C away from the owner, keeping its settings; bring it back first
        // so the unblock below clears them.
        su("pm install-existing --user 0 $APP_C")
        for (key in listOfNotNull(AppKey(APP_B, UserId.SYSTEM), profile?.let { AppKey(APP_B, it) }, AppKey(APP_C, UserId.SYSTEM))) {
            val declarations = declared(key).map { it.declaration }
            if (declarations.isNotEmpty()) components.control(ControlRequest(key.userId, declarations, ControlGoal.UNBLOCK, ControlLayer.entries.toSet())).toList()
        }
        store.clear(APP_B)
        store.clear(APP_C)
        // ENABLE leaves a setting UNBLOCK keeps.
        su("pm default-state --user 0 $DISABLED_TARGET/com.merxury.blocker.core.control.ControlFixtureDisabledActivity")
        folder.deleteRecursively()
    }

    @Test
    fun givenOwnerAndProfileState_whenExportedChangedAndRestored_thenEachUserGetsItsOwnState() = runBlocking<Unit> {
        runCase()
        val user = profile!!
        val owner = UserId.SYSTEM
        val service = declaration(owner, "com.google.android.gms.measurement.AppMeasurementService")
        val provider = declaration(owner, "io.sentry.android.core.SentryInitProvider")
        val receiver = declaration(owner, "com.google.android.gms.measurement.AppMeasurementReceiver")
        val activity = declaration(owner, "com.facebook.ads.AudienceNetworkActivity")
        control(owner, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), service, provider)
        val legacyBlock = ControlRequest(owner, listOf(receiver), ControlGoal.BLOCK, setOf(ControlLayer.INTENT_FIREWALL))
        components.restore(legacyBlock).toList()
        assertTrue(ruleFileHas(receiver))
        declared(AppKey(APP_B, user))
        control(user, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), activity)

        val ownerExport = exporter.export(storage, owner)
        val profileExport = exporter.export(storage, user)
        Log.i(TAG, "owner export $ownerExport; profile export $profileExport; files ${storage.allPaths()}")
        assertTrue(File(folder, "$APP_B.json").isFile)
        assertTrue(File(folder, "user-${user.value}/$APP_B.json").isFile)
        assertEquals(listOf(BackupSet.OWNER, BackupSet(user)), listBackupSets(storage))
        Log.i(TAG, "owner file: ${File(folder, "$APP_B.json").readText()}")

        val all = declared(AppKey(APP_B, owner)).map { it.declaration }.toTypedArray()
        control(owner, ControlGoal.UNBLOCK, ControlLayer.entries.toSet(), *all)
        control(user, ControlGoal.UNBLOCK, ControlLayer.entries.toSet(), *all)
        assertEquals(PmSetting.DEFAULT, pm(owner, service))
        assertFalse(ruleFileHas(receiver))

        val ownerImport = importer.import(storage, BackupSet.OWNER, owner)
        Log.i(TAG, "owner import $ownerImport")
        assertEquals(PmSetting.DISABLED, pm(owner, service))
        assertEquals(PmSetting.DISABLED, pm(owner, provider))
        assertTrue(ruleFileHas(receiver))
        assertTrue(pm(owner, activity) != PmSetting.DISABLED)
        // The profile is untouched by the owner's restore.
        assertTrue(pm(user, activity) != PmSetting.DISABLED)
        assertTrue(pm(user, service) != PmSetting.DISABLED)

        val profileImport = importer.import(storage, BackupSet(user), user)
        Log.i(TAG, "profile import $profileImport")
        assertEquals(PmSetting.DISABLED, pm(user, activity))
        assertTrue(pm(user, service) != PmSetting.DISABLED)
        assertTrue(pm(owner, activity) != PmSetting.DISABLED)
        assertEquals(PmSetting.DISABLED, pm(owner, service))
    }

    @Test
    fun givenHandWrittenConditionalRule_whenIfwExportedAndImported_thenRulesAreRestoredWhole() = runBlocking<Unit> {
        runCase()
        val handWritten = IfwXmlDeserializer().deserialize(HAND_WRITTEN_RULES)
        assertTrue(store.update(APP_B) { handWritten } is IfwResult.Success)
        val ownFile = store.restoreRuleFile(OWN_RULE_FILE, HAND_WRITTEN_RULES)
        Log.i(TAG, "own file written: $ownFile")
        val stored = (store.ruleFileText(APP_B) as IfwResult.Success).value

        val export = ifwExporter.export(storage)
        Log.i(TAG, "IFW export $export; files ${storage.allPaths()}")
        assertEquals(stored, File(folder, "ifw/$APP_B.xml").readText())
        assertEquals(HAND_WRITTEN_RULES, File(folder, "ifw/$OWN_RULE_FILE.xml").readText())

        store.clear(APP_B)
        val restored = ifwImporter.import(storage)
        Log.i(TAG, "IFW import $restored; $APP_B now: ${(store.ruleFileText(APP_B) as IfwResult.Success).value}")
        assertEquals(handWritten, (store.rules(APP_B) as IfwResult.Success).value)
        // The user's own file is still there, so it is not overwritten.
        assertEquals(1, (restored as RuleRunResult.Completed).summary.skipped[SkipReason.FILE_NOT_RESTORED])

        val again = ifwImporter.import(storage)
        Log.i(TAG, "IFW import again $again")
        assertEquals(0, (again as RuleRunResult.Completed).summary.applied)
        assertEquals(handWritten, (store.rules(APP_B) as IfwResult.Success).value)
    }

    @Test
    fun givenMatLines_whenImported_thenDeclaredComponentsAreBlockedWithTheChosenController() = runBlocking<Unit> {
        runCase()
        val owner = UserId.SYSTEM
        val service = declaration(owner, "com.google.android.gms.ads.AdService")

        val result = mat.import(
            listOf("$APP_B/com.google.android.gms.ads.AdService", " $APP_B/com.google.android.gms.ads.AdService", "$APP_B/.Missing", "not.installed/.Main"),
            owner,
        )

        Log.i(TAG, "MAT import $result")
        assertEquals(PmSetting.DISABLED, pm(owner, service))
        val summary = (result as RuleRunResult.Completed).summary
        assertEquals(1, summary.applied)
        assertEquals(mapOf(SkipReason.MALFORMED_LINE to 1, SkipReason.NOT_DECLARED to 1, SkipReason.APP_NOT_INSTALLED to 1), summary.skipped)
    }

    @Test
    fun givenTwoPackagesAndOneTheSystemRefuses_whenResettingPm_thenTheOtherIsRestored() = runBlocking<Unit> {
        runCase()
        val owner = UserId.SYSTEM
        val inB = declaration(owner, "com.google.android.gms.measurement.AppMeasurementService")
        val inC = declaration(owner, "com.google.android.gms.measurement.AppMeasurementService", APP_C)
        control(owner, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), inB)
        control(owner, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), inC)

        // APP_C goes away for the owner after the settings were read, so the system refuses it.
        val result = PackageManagerReset(BeforeReset(components) { declarations -> if (declarations.any { it.id.packageName == APP_C }) su("pm uninstall -k --user 0 $APP_C") }, channel, seeder).reset(owner)

        Log.i(TAG, "PM reset $result")
        assertEquals(PmSetting.DEFAULT, pm(owner, inB))
        val summary = (result as RuleRunResult.Completed).summary
        assertEquals(1, summary.applied)
        assertEquals(1, summary.failedCount)
    }

    @Test
    fun givenManifestDisabledComponent_whenRestoredResetAndEnabled_thenDefaultTwiceThenEnabled() = runBlocking<Unit> {
        runCase()
        val owner = UserId.SYSTEM
        val shippedOff = declaration(owner, "com.merxury.blocker.core.control.ControlFixtureDisabledActivity", DISABLED_TARGET)
        assertFalse(shippedOff.enabledInManifest)
        assertEquals(PmSetting.DEFAULT, pm(owner, shippedOff))
        val exported = exporter.export(storage, owner, packageName = DISABLED_TARGET)
        Log.i(TAG, "export $exported")

        control(owner, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), shippedOff)
        val restored = importer.import(storage, BackupSet.OWNER, owner, packageName = DISABLED_TARGET)
        Log.i(TAG, "restore $restored")
        // The backup said "not disabled", which a restore clears to DEFAULT, never ENABLED.
        assertEquals(PmSetting.DEFAULT, pm(owner, shippedOff))

        control(owner, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), shippedOff)
        val reset = reset.reset(owner)
        Log.i(TAG, "PM reset $reset")
        assertEquals(PmSetting.DEFAULT, pm(owner, shippedOff))

        control(owner, ControlGoal.ENABLE, setOf(ControlLayer.PACKAGE_MANAGER), shippedOff)
        assertEquals(PmSetting.ENABLED, pm(owner, shippedOff))
    }

    @Test
    fun givenTwoBlockedByBlockerAndOneByTheAppItself_whenResettingPm_thenOnlyBlockersChangeOnTheWholeDevice() = runBlocking<Unit> {
        runCase(needsProfile = false)
        val owner = UserId.SYSTEM
        val service = declaration(owner, "com.google.android.gms.measurement.AppMeasurementService")
        val ads = declaration(owner, "com.google.android.gms.ads.AdService")
        val selfOff = declaration(owner, "com.facebook.ads.AudienceNetworkActivity")
        control(owner, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), service, ads)
        // Disabled the way an app or the system does it, which Blocker must not take for its own.
        su("pm disable --user 0 ${selfOff.id.flatten()}")
        val before = disabledOnDevice()
        Log.i(TAG, "disabled before the reset: ${before.size} components")

        val result = reset.reset(owner)

        val after = disabledOnDevice()
        Log.i(TAG, "PM reset $result; cleared ${before - after}; newly disabled ${after - before}")
        assertEquals(setOf("0 ${service.id.flatten()}", "0 ${ads.id.flatten()}"), before - after)
        assertEquals(emptySet(), after - before)
        assertEquals(PmSetting.DEFAULT, pm(owner, service))
        assertEquals(PmSetting.DEFAULT, pm(owner, ads))
        assertEquals(PmSetting.DISABLED, pm(owner, selfOff))
        assertEquals(2, (result as RuleRunResult.Completed).summary.applied)
    }

    /** Every component disabled for any user, as `<user> <package>/<class>`, from the system's own dump. */
    private fun disabledOnDevice(): Set<String> {
        val disabled = mutableSetOf<String>()
        var packageName: String? = null
        var user: String? = null
        var listIndent = -1
        for (line in suOutput("dumpsys package packages").lines()) {
            val text = line.trimStart()
            val indent = line.length - text.length
            if (listIndent >= 0) {
                if (indent > listIndent && text.isNotEmpty() && !text.endsWith(":")) {
                    disabled += "$user $packageName/$text"
                    continue
                }
                listIndent = -1
            }
            when {
                text.startsWith("Package [") -> packageName = text.substringAfter('[').substringBefore(']')
                text.startsWith("User ") && text.contains(':') -> user = text.removePrefix("User ").substringBefore(':')
                text == "disabledComponents:" -> listIndent = indent
            }
        }
        return disabled
    }

    private suspend fun declared(app: AppKey): List<ComponentStatus> {
        components.refresh(app)
        return components.components(app).first()
    }

    private suspend fun declaration(user: UserId, className: String, packageName: String = APP_B): ComponentDeclaration = assertNotNull(declared(AppKey(packageName, user)).firstOrNull { it.declaration.id.className == className }, "$className is not declared").declaration

    private suspend fun control(user: UserId, goal: ControlGoal, layers: Set<ControlLayer>, vararg declarations: ComponentDeclaration) {
        val events = components.control(ControlRequest(user, declarations.toList(), goal, layers)).toList()
        Log.i(TAG, "$goal $layers for user ${user.value}: ${events.last()}")
        assertEquals(ControlEvent.Finished(declarations.size, 0, 0), events.last())
    }

    private suspend fun pm(user: UserId, declaration: ComponentDeclaration): PmSetting? {
        val read = reader.pmSettings(AppKey(declaration.id.packageName, user), listOf(declaration.id))
        return read.getOrNull()?.get(declaration.id).also { Log.i(TAG, "PM of ${declaration.id.simpleName} for user ${user.value}: $it") }
    }

    private suspend fun ruleFileHas(declaration: ComponentDeclaration): Boolean {
        val rules = (store.rules(declaration.id.packageName) as IfwResult.Success<IfwRules>).value
        return rules.componentFiltersFor(IfwComponentType.BROADCAST).contains(declaration.id.flatten())
    }
}

/** Runs [beforeReset] before a PackageManager reset reaches the system. */
private class BeforeReset(
    private val delegate: ComponentStatusRepository,
    private val beforeReset: (List<ComponentDeclaration>) -> Unit,
) : ComponentStatusRepository by delegate {
    override fun resetPmBlocks(user: UserId, components: List<ComponentDeclaration>): Flow<ControlEvent> {
        beforeReset(components)
        return delegate.resetPmBlocks(user, components)
    }
}

/** Runs [command] through su, which this test package is granted, and returns what it printed. */
private fun suOutput(command: String): String {
    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output
}

/** Runs [command] through su and logs it. */
private fun su(command: String) {
    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
    val output = process.inputStream.bufferedReader().readText()
    Log.i(TAG, "su $command: exit ${process.waitFor()} $output")
}
