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

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.model.system.getOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private const val PKG = "com.example"
private val OWNER = UserId(0)
private val WORK = UserId(10)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PlatformSystemPackageReaderTest {

    private val scope = TestScope()
    private val platform = FakePlatformPackages()
    private val reader = PlatformSystemPackageReader(platform, StandardTestDispatcher(scope.testScheduler))

    @Test
    fun whenReadUsers_thenPlatformUsersReturned() = scope.runTest {
        assertThat(reader.users().getOrNull()).isEqualTo(platform.users)
    }

    @Test
    fun givenPlatformThrows_whenReadUsers_thenChannelUnavailable() = scope.runTest {
        platform.dead = true

        assertThat(reader.users()).isEqualTo(ReadResult.Failure(ReadFailure.ChannelUnavailable))
    }

    @Test
    fun givenInstalledApp_whenReadApps_thenEveryFieldMapped() = scope.runTest {
        platform.install(
            OWNER,
            packageInfo(PKG) {
                versionName = "1.2"
                longVersionCode = 12
                firstInstallTime = 1_000
                lastUpdateTime = 2_000
                applicationInfo = ApplicationInfo().apply {
                    packageName = PKG
                    flags = ApplicationInfo.FLAG_SYSTEM
                    sourceDir = "/data/app/example/base.apk"
                    dataDir = "/data/user/0/$PKG"
                    minSdkVersion = 24
                    targetSdkVersion = 34
                    uid = 10_123
                    nonLocalizedLabel = "Example"
                    enabled = false
                }
            },
        )
        platform.appSettings[AppKey(PKG, OWNER)] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER

        val app = reader.apps(OWNER).getOrNull()!!.single()

        assertThat(app.key).isEqualTo(AppKey(PKG, OWNER))
        assertThat(app.uid).isEqualTo(10_123)
        assertThat(app.label).isEqualTo("Example")
        assertThat(app.versionName).isEqualTo("1.2")
        assertThat(app.versionCode).isEqualTo(12)
        assertThat(app.isSystem).isTrue()
        assertThat(app.enabled).isFalse()
        assertThat(app.enabledSetting).isEqualTo(AppEnabledSetting.DISABLED_USER)
        assertThat(app.apkPath).isEqualTo("/data/app/example/base.apk")
        assertThat(app.dataDir).isEqualTo("/data/user/0/$PKG")
        assertThat(app.firstInstallTime).isEqualTo(Instant.fromEpochMilliseconds(1_000))
        assertThat(app.lastUpdateTime).isEqualTo(Instant.fromEpochMilliseconds(2_000))
        assertThat(app.minSdk).isEqualTo(24)
        assertThat(app.targetSdk).isEqualTo(34)
    }

    @Test
    fun givenWorkPackages_whenListingRevisions_thenReadVersionAndTimeWithoutPerAppSettings() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG) { longVersionCode = 1 })
        platform.install(
            WORK,
            packageInfo(PKG) {
                longVersionCode = 12
                lastUpdateTime = 2_000
            },
        )

        val revisions = reader.packageRevisions(WORK).getOrNull()

        assertThat(revisions).containsExactly(PKG, PackageRevision(12, Instant.fromEpochMilliseconds(2_000)))
        assertThat(platform.appSettingRequests).isEmpty()
    }

    @Test
    fun givenAppInTwoUsers_whenReadApps_thenEachUserHasItsOwnSetting() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))
        platform.install(WORK, packageInfo(PKG))
        platform.appSettings[AppKey(PKG, WORK)] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        assertThat(reader.apps(OWNER).getOrNull()!!.single().enabledSetting).isEqualTo(AppEnabledSetting.DEFAULT)
        assertThat(reader.apps(WORK).getOrNull()!!.single().enabledSetting).isEqualTo(AppEnabledSetting.DISABLED)
    }

    @Test
    fun givenPackageWithoutApplicationInfo_whenReadApps_thenPackageNameIsLabel() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG) { applicationInfo = null })

        val app = reader.apps(OWNER).getOrNull()!!.single()

        assertThat(app.label).isEqualTo(PKG)
        assertThat(app.apkPath).isNull()
        assertThat(app.dataDir).isNull()
        assertThat(app.uid).isNull()
        assertThat(app.isSystem).isFalse()
        assertThat(app.enabled).isTrue()
    }

    @Test
    fun givenManifestDisabledAppWithoutOverride_whenReadApps_thenDisabledWithDefaultSetting() = scope.runTest {
        platform.install(
            OWNER,
            packageInfo(PKG) { applicationInfo!!.enabled = false },
        )

        val app = reader.apps(OWNER).getOrNull()!!.single()

        assertThat(app.enabled).isFalse()
        assertThat(app.enabledSetting).isEqualTo(AppEnabledSetting.DEFAULT)
    }

    @Test
    fun givenAppRemovedWhileListing_whenReadApps_thenSettingUnknown() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))
        platform.unreadableApps += PKG

        assertThat(reader.apps(OWNER).getOrNull()!!.single().enabledSetting).isEqualTo(AppEnabledSetting.UNKNOWN)
    }

    @Test
    fun givenManyApps_whenReadApps_thenOneBatchRequestForTheirSettings() = scope.runTest {
        val names = (1..230).map { "com.example$it" }
        names.forEach { platform.install(OWNER, packageInfo(it)) }
        platform.appSettings[AppKey(names.last(), OWNER)] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val apps = reader.apps(OWNER).getOrNull()!!

        assertThat(platform.appSettingRequests).containsExactly(names)
        assertThat(apps.last().enabledSetting).isEqualTo(AppEnabledSetting.DISABLED)
    }

    @Test
    fun whenReadOneApp_thenBatchOfOne() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))

        reader.app(AppKey(PKG, OWNER))

        assertThat(platform.appSettingRequests).containsExactly(listOf(PKG))
    }

    @Test
    fun givenReadInFlight_whenCancelled_thenPlatformCallCancelledAndNotTakenForAFailure() = scope.runTest {
        platform.install(OWNER, fullPackage())
        platform.hang = true

        val read = launch { reader.declarations(AppKey(PKG, OWNER)) }
        runCurrent()
        read.cancelAndJoin()

        assertThat(platform.cancelledCalls).isEqualTo(1)
        assertThat(platform.flagsRequested).isEmpty()
    }

    @Test
    fun givenUnreachableUser_whenRead_thenUserNotReachable() = scope.runTest {
        platform.unreachable += WORK
        val failure = ReadResult.Failure(ReadFailure.UserNotReachable(WORK))
        val app = AppKey(PKG, WORK)

        assertThat(reader.apps(WORK)).isEqualTo(failure)
        assertThat(reader.app(app)).isEqualTo(failure)
        assertThat(reader.declarations(app)).isEqualTo(failure)
        assertThat(reader.pmSettings(app, listOf(ComponentId(PKG, "$PKG.Main")))).isEqualTo(failure)
    }

    @Test
    fun givenMissingPackage_whenRead_thenPackageNotFound() = scope.runTest {
        val app = AppKey(PKG, OWNER)
        val failure = ReadResult.Failure(ReadFailure.PackageNotFound(app))

        assertThat(reader.app(app)).isEqualTo(failure)
        assertThat(reader.declarations(app)).isEqualTo(failure)
        assertThat(reader.pmSettings(app, listOf(ComponentId(PKG, "$PKG.Main")))).isEqualTo(failure)
    }

    @Test
    fun givenAppInOtherUserOnly_whenReadApp_thenPackageNotFoundForThisUser() = scope.runTest {
        platform.install(WORK, packageInfo(PKG))

        assertThat(reader.app(AppKey(PKG, WORK)).getOrNull()).isNotNull()
        assertThat(reader.app(AppKey(PKG, OWNER)))
            .isEqualTo(ReadResult.Failure(ReadFailure.PackageNotFound(AppKey(PKG, OWNER))))
    }

    @Test
    fun givenAllComponentTypes_whenReadDeclarations_thenTypeExportedAndManifestDefaultMapped() = scope.runTest {
        platform.install(OWNER, fullPackage())

        val declarations = reader.declarations(AppKey(PKG, OWNER)).getOrNull()

        assertThat(declarations).containsExactly(
            declaration("Main", ComponentType.ACTIVITY, exported = true),
            declaration("Alias", ComponentType.ACTIVITY, enabledInManifest = false),
            declaration("Sync", ComponentType.SERVICE),
            declaration("Boot", ComponentType.RECEIVER, exported = true, enabledInManifest = false),
            declaration("Data", ComponentType.PROVIDER),
        )
        assertThat(platform.flagsRequested.single() and PackageManager.MATCH_DISABLED_COMPONENTS).isNotEqualTo(0)
    }

    @Test
    fun givenAppWithoutComponents_whenReadDeclarations_thenEmptySuccess() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))

        assertThat(reader.declarations(AppKey(PKG, OWNER))).isEqualTo(ReadResult.Success(emptyList<Any>()))
    }

    @Test
    fun givenAnswerTooLargeForOneCall_whenReadDeclarations_thenReadByType() = scope.runTest {
        platform.install(OWNER, fullPackage())
        platform.maxTypesPerCall = 1

        val declarations = reader.declarations(AppKey(PKG, OWNER)).getOrNull()

        assertThat(declarations).hasSize(5)
        assertThat(platform.archivePathsParsed).isEmpty()
    }

    @Test
    fun givenAnswerTooLargeEvenByType_whenReadDeclarations_thenApkParsed() = scope.runTest {
        platform.install(OWNER, fullPackage())
        platform.maxTypesPerCall = 0
        platform.archives["/data/app/example/base.apk"] = fullPackage()

        val declarations = reader.declarations(AppKey(PKG, OWNER)).getOrNull()

        assertThat(declarations).hasSize(5)
        assertThat(platform.archivePathsParsed).containsExactly("/data/app/example/base.apk")
    }

    @Test
    fun givenApkCannotBeParsed_whenReadDeclarations_thenManifestUnreadable() = scope.runTest {
        platform.install(OWNER, fullPackage())
        platform.maxTypesPerCall = 0

        assertThat(reader.declarations(AppKey(PKG, OWNER)))
            .isEqualTo(ReadResult.Failure(ReadFailure.ManifestUnreadable(PKG)))
    }

    @Test
    fun givenSystemDead_whenReadDeclarations_thenChannelUnavailable() = scope.runTest {
        platform.install(OWNER, fullPackage())
        platform.dead = true

        assertThat(reader.declarations(AppKey(PKG, OWNER)))
            .isEqualTo(ReadResult.Failure(ReadFailure.ChannelUnavailable))
    }

    @Test
    fun givenComponentWithUnusableName_whenReadDeclarations_thenSkipped() = scope.runTest {
        platform.install(
            OWNER,
            packageInfo(PKG) {
                services = arrayOf(component<ServiceInfo>("$PKG.Sync"), component<ServiceInfo>(""))
            },
        )

        assertThat(reader.declarations(AppKey(PKG, OWNER)).getOrNull())
            .containsExactly(declaration("Sync", ComponentType.SERVICE))
    }

    @Test
    fun givenStoredSettings_whenReadPmSettings_thenEachValueMapped() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))
        val default = ComponentId(PKG, "$PKG.Default")
        val enabled = ComponentId(PKG, "$PKG.Enabled")
        val disabled = ComponentId(PKG, "$PKG.Disabled")
        val odd = ComponentId(PKG, "$PKG.Odd")
        platform.componentSettings[enabled to OWNER] = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        platform.componentSettings[disabled to OWNER] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        platform.componentSettings[odd to OWNER] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER

        val settings = reader.pmSettings(AppKey(PKG, OWNER), listOf(default, enabled, disabled, odd)).getOrNull()

        assertThat(settings).containsExactly(
            default,
            PmSetting.DEFAULT,
            enabled,
            PmSetting.ENABLED,
            disabled,
            PmSetting.DISABLED,
            odd,
            PmSetting.UNKNOWN,
        )
    }

    @Test
    fun givenSettingDiffersPerUser_whenReadPmSettings_thenOnlyRequestedUserRead() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))
        platform.install(WORK, packageInfo(PKG))
        val id = ComponentId(PKG, "$PKG.Main")
        platform.componentSettings[id to WORK] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        assertThat(reader.pmSettings(AppKey(PKG, OWNER), listOf(id)).getOrNull()).containsExactly(id, PmSetting.DEFAULT)
        assertThat(reader.pmSettings(AppKey(PKG, WORK), listOf(id)).getOrNull()).containsExactly(id, PmSetting.DISABLED)
    }

    @Test
    fun givenUnreadableComponent_whenReadPmSettings_thenUnknownForThatComponentOnly() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))
        val known = ComponentId(PKG, "$PKG.Main")
        val gone = ComponentId(PKG, "$PKG.Gone")
        platform.unreadableComponents += gone

        val settings = reader.pmSettings(AppKey(PKG, OWNER), listOf(known, gone)).getOrNull()

        assertThat(settings).containsExactly(known, PmSetting.DEFAULT, gone, PmSetting.UNKNOWN)
    }

    @Test
    fun givenManyComponents_whenReadPmSettings_thenOneBatchRequestForThePackage() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))
        val ids = (1..500).map { ComponentId(PKG, "$PKG.C$it") }
        platform.componentSettings[ids.last() to OWNER] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val settings = reader.pmSettings(AppKey(PKG, OWNER), ids).getOrNull()!!

        assertThat(platform.componentSettingRequests).containsExactly(ids.map { it.className })
        assertThat(settings).hasSize(500)
        assertThat(settings[ids.last()]).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenComponentOfAnotherPackage_whenReadPmSettings_thenUnknownAndNotRequested() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))
        val own = ComponentId(PKG, "$PKG.Main")
        val foreign = ComponentId("com.other", "com.other.Main")

        val settings = reader.pmSettings(AppKey(PKG, OWNER), listOf(own, foreign)).getOrNull()

        assertThat(settings).containsExactly(own, PmSetting.DEFAULT, foreign, PmSetting.UNKNOWN)
        assertThat(platform.componentSettingRequests).containsExactly(listOf(own.className))
    }

    @Test
    fun givenPlatformAnswersWrongCount_whenReadPmSettings_thenChannelUnavailable() = scope.runTest {
        platform.install(OWNER, packageInfo(PKG))
        platform.dropLastSetting = true

        assertThat(reader.pmSettings(AppKey(PKG, OWNER), listOf(ComponentId(PKG, "$PKG.Main"))))
            .isEqualTo(ReadResult.Failure(ReadFailure.ChannelUnavailable))
    }

    private fun declaration(
        simpleName: String,
        type: ComponentType,
        exported: Boolean = false,
        enabledInManifest: Boolean = true,
    ) = ComponentDeclaration(ComponentId(PKG, "$PKG.$simpleName"), type, exported, enabledInManifest)

    private fun fullPackage() = packageInfo(PKG) {
        activities = arrayOf(
            component<ActivityInfo>("$PKG.Main", exported = true),
            component<ActivityInfo>("$PKG.Alias", enabled = false),
        )
        services = arrayOf(component<ServiceInfo>("$PKG.Sync"))
        receivers = arrayOf(component<ActivityInfo>("$PKG.Boot", exported = true, enabled = false))
        providers = arrayOf(component<ProviderInfo>("$PKG.Data"))
    }
}

private fun packageInfo(name: String, configure: PackageInfo.() -> Unit = {}) = PackageInfo().apply {
    packageName = name
    applicationInfo = ApplicationInfo().apply {
        packageName = name
        sourceDir = "/data/app/example/base.apk"
        nonLocalizedLabel = name
    }
    configure()
}

private inline fun <reified T : ComponentInfo> component(
    name: String,
    exported: Boolean = false,
    enabled: Boolean = true,
): T = T::class.java.getDeclaredConstructor().newInstance().apply {
    this.packageName = PKG
    this.name = name
    this.exported = exported
    this.enabled = enabled
}

/**
 * Packages per user, with switches for the ways the real PackageManager fails.
 *
 * @property maxTypesPerCall a request for more component types than this throws the bare
 * [RuntimeException] PackageManager uses for an answer that is too large
 */
private class FakePlatformPackages : PlatformPackages {
    val users = listOf(
        DeviceUser(OWNER, "Owner", UserKind.OWNER, parent = null, running = true),
        DeviceUser(WORK, "Work", UserKind.WORK_PROFILE, parent = OWNER, running = false),
    )
    val unreachable = mutableSetOf<UserId>()
    val appSettings = mutableMapOf<AppKey, Int>()
    val componentSettings = mutableMapOf<Pair<ComponentId, UserId>, Int>()
    val unreadableComponents = mutableSetOf<ComponentId>()
    val componentSettingRequests = mutableListOf<List<String>>()
    var dropLastSetting = false
    val archives = mutableMapOf<String, PackageInfo>()
    val archivePathsParsed = mutableListOf<String>()
    val flagsRequested = mutableListOf<Int>()
    val unreadableApps = mutableSetOf<String>()

    /** Every packageInfo call waits until it is cancelled. */
    var hang = false
    var cancelledCalls = 0
    val appSettingRequests = mutableListOf<List<String>>()
    var maxTypesPerCall = 4
    var dead = false

    private val installed = mutableMapOf<UserId, MutableMap<String, PackageInfo>>()

    fun install(user: UserId, info: PackageInfo) {
        installed.getOrPut(user) { mutableMapOf() }[info.packageName] = info
    }

    override suspend fun users(): List<DeviceUser> {
        checkAlive()
        return users
    }

    override suspend fun installedPackages(user: UserId): List<PackageInfo> {
        check(user)
        return installed[user].orEmpty().values.toList()
    }

    override suspend fun packageInfo(packageName: String, flags: Int, user: UserId): PackageInfo {
        check(user)
        if (hang) {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                cancelledCalls++
                throw e
            }
        }
        val info = installed[user]?.get(packageName) ?: throw NameNotFoundException(packageName)
        val types = listOf(
            PackageManager.GET_ACTIVITIES,
            PackageManager.GET_SERVICES,
            PackageManager.GET_RECEIVERS,
            PackageManager.GET_PROVIDERS,
        ).filter { flags and it != 0 }
        if (types.isEmpty()) return info
        flagsRequested += flags
        if (types.size > maxTypesPerCall) throw RuntimeException("Package manager has died")
        return PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = info.applicationInfo
            if (PackageManager.GET_ACTIVITIES in types) activities = info.activities
            if (PackageManager.GET_SERVICES in types) services = info.services
            if (PackageManager.GET_RECEIVERS in types) receivers = info.receivers
            if (PackageManager.GET_PROVIDERS in types) providers = info.providers
        }
    }

    override fun archiveInfo(apkPath: String, flags: Int): PackageInfo? {
        archivePathsParsed += apkPath
        return archives[apkPath]
    }

    override suspend fun applicationEnabledSettings(packageNames: List<String>, user: UserId): List<Int?> {
        check(user)
        appSettingRequests.add(packageNames)
        return packageNames.map { packageName ->
            if (packageName in unreadableApps || installed[user]?.get(packageName) == null) {
                null
            } else {
                appSettings[AppKey(packageName, user)] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
        }
    }

    override suspend fun componentEnabledSettings(packageName: String, classNames: List<String>, user: UserId): List<Int?> {
        check(user)
        if (installed[user]?.get(packageName) == null) throw NameNotFoundException(packageName)
        componentSettingRequests.add(classNames)
        val settings = classNames.map { className ->
            val id = ComponentId(packageName, className)
            if (id in unreadableComponents) null else componentSettings[id to user] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        return if (dropLastSetting) settings.dropLast(1) else settings
    }

    override fun label(application: ApplicationInfo): String = application.nonLocalizedLabel.toString()

    private fun check(user: UserId) {
        checkAlive()
        if (user in unreachable) throw UserNotReachableException(user)
    }

    private fun checkAlive() {
        if (dead) throw RuntimeException("Package manager has died")
    }
}
