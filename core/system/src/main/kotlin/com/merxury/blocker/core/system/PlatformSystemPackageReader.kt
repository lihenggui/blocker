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

import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.getVersionCode
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Instant

/** [SystemPackageReader] over [PlatformPackages]; it reaches the users [platform] reaches. */
class PlatformSystemPackageReader @Inject constructor(
    private val platform: PlatformPackages,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : SystemPackageReader {

    override suspend fun users(): ReadResult<List<DeviceUser>> = read("users") {
        ReadResult.Success(platform.users())
    }

    override suspend fun apps(user: UserId): ReadResult<List<InstalledApp>> = read("apps of user ${user.value}") {
        val packages = platform.installedPackages(user)
        val settings = platform.applicationEnabledSettings(packages.map { it.packageName }, user)
        check(settings.size == packages.size) { "${settings.size} settings for ${packages.size} apps of user ${user.value}" }
        ReadResult.Success(
            packages.zip(settings) { info, setting ->
                coroutineContext.ensureActive()
                info.toInstalledApp(user, setting)
            },
        )
    }

    override suspend fun packageRevisions(user: UserId): ReadResult<Map<String, PackageRevision>> = read("package revisions of user ${user.value}") {
        ReadResult.Success(
            platform.installedPackages(user).associate { info ->
                coroutineContext.ensureActive()
                info.packageName to PackageRevision(info.getVersionCode(), Instant.fromEpochMilliseconds(info.lastUpdateTime))
            },
        )
    }

    override suspend fun app(key: AppKey): ReadResult<InstalledApp> = read("app $key") {
        try {
            val info = platform.packageInfo(key.packageName, 0, key.userId)
            val setting = platform.applicationEnabledSettings(listOf(info.packageName), key.userId).singleOrNull()
            ReadResult.Success(info.toInstalledApp(key.userId, setting))
        } catch (e: NameNotFoundException) {
            ReadResult.Failure(ReadFailure.PackageNotFound(key))
        }
    }

    override suspend fun declarations(app: AppKey): ReadResult<List<ComponentDeclaration>> = read("components of $app") {
        try {
            val info = componentsInOneCall(app) ?: componentsByType(app) ?: componentsFromApk(app)
            if (info == null) {
                ReadResult.Failure(ReadFailure.ManifestUnreadable(app.packageName))
            } else {
                ReadResult.Success(info.declarations(app.packageName))
            }
        } catch (e: NameNotFoundException) {
            ReadResult.Failure(ReadFailure.PackageNotFound(app))
        }
    }

    override suspend fun pmSettings(
        app: AppKey,
        components: Collection<ComponentId>,
    ): ReadResult<Map<ComponentId, PmSetting>> = read("component settings of $app") {
        val (own, foreign) = components.distinct().partition { it.packageName == app.packageName }
        if (foreign.isNotEmpty()) Timber.w("Not components of $app: $foreign")
        try {
            val settings = platform.componentEnabledSettings(app.packageName, own.map { it.className }, app.userId)
            check(settings.size == own.size) { "${settings.size} settings for ${own.size} components of $app" }
            ReadResult.Success(
                own.zip(settings) { id, setting -> id to setting.toPmSetting() }.toMap() +
                    foreign.associateWith { PmSetting.UNKNOWN },
            )
        } catch (e: NameNotFoundException) {
            ReadResult.Failure(ReadFailure.PackageNotFound(app))
        }
    }

    private suspend fun <T> read(what: String, block: suspend () -> ReadResult<T>): ReadResult<T> = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserNotReachableException) {
            ReadResult.Failure(ReadFailure.UserNotReachable(e.user))
        } catch (e: RuntimeException) {
            // PackageManager reports a dead or overloaded system as a bare RuntimeException.
            Timber.e(e, "The system did not answer when reading $what")
            ReadResult.Failure(ReadFailure.ChannelUnavailable)
        }
    }

    /**
     * The usual path. PackageManager has already resolved resource references such as
     * `android:enabled="@bool/..."` and lists `activity-alias` entries among the activities.
     * Returns null when the answer does not fit in one binder transaction, which happens for
     * apps with thousands of components.
     */
    private suspend fun componentsInOneCall(app: AppKey): PackageInfo? = try {
        platform.packageInfo(app.packageName, ALL_COMPONENTS or MATCH_DISABLED, app.userId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Timber.w(e, "Cannot read the components of $app in one call")
        null
    }

    /** One call per component type, each a smaller transaction. Null when one still fails. */
    private suspend fun componentsByType(app: AppKey): PackageInfo? = try {
        val merged = PackageInfo()
        merged.packageName = app.packageName
        merged.activities = platform.packageInfo(app.packageName, GET_ACTIVITIES, app.userId).activities
        merged.services = platform.packageInfo(app.packageName, GET_SERVICES, app.userId).services
        merged.receivers = platform.packageInfo(app.packageName, GET_RECEIVERS, app.userId).receivers
        merged.providers = platform.packageInfo(app.packageName, GET_PROVIDERS, app.userId).providers
        merged
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        Timber.w(e, "Cannot read the components of $app by type")
        null
    }

    /**
     * Parses the base APK in this process, which has no transaction limit. If the system does not
     * answer here either, the [RuntimeException] reaches [read].
     */
    private suspend fun componentsFromApk(app: AppKey): PackageInfo? {
        val apkPath = platform.packageInfo(app.packageName, 0, app.userId).applicationInfo?.sourceDir
        if (apkPath == null) {
            Timber.e("No APK path for $app")
            return null
        }
        val parsed = platform.archiveInfo(apkPath, ALL_COMPONENTS or MATCH_DISABLED)
        if (parsed == null) Timber.e("Cannot parse $apkPath")
        return parsed
    }

    private fun Int?.toPmSetting(): PmSetting = when (this) {
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> PmSetting.DEFAULT

        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> PmSetting.ENABLED

        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> PmSetting.DISABLED

        // Unreadable, or one of the app-only states PackageManager refuses to store for a component.
        else -> PmSetting.UNKNOWN
    }

    private fun PackageInfo.toInstalledApp(user: UserId, enabledSetting: Int?): InstalledApp {
        val application: ApplicationInfo? = applicationInfo
        return InstalledApp(
            key = AppKey(packageName, user),
            uid = application?.uid,
            label = application?.let(platform::label) ?: packageName,
            versionName = versionName.orEmpty(),
            versionCode = getVersionCode(),
            isSystem = application != null && application.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            // PackageManager fills this for the user it answers for: the manifest's value while the
            // user's setting is DEFAULT, otherwise what the setting says.
            enabled = application?.enabled ?: true,
            enabledSetting = appEnabledSettingOf(enabledSetting),
            apkPath = application?.sourceDir,
            // PackageManager fills this for the user it answers for.
            dataDir = application?.dataDir,
            firstInstallTime = Instant.fromEpochMilliseconds(firstInstallTime),
            lastUpdateTime = Instant.fromEpochMilliseconds(lastUpdateTime),
            minSdk = application?.minSdkVersion ?: 0,
            targetSdk = application?.targetSdkVersion ?: 0,
        )
    }

    private fun PackageInfo.declarations(packageName: String): List<ComponentDeclaration> = activities.declarations(packageName, ComponentType.ACTIVITY) +
        services.declarations(packageName, ComponentType.SERVICE) +
        receivers.declarations(packageName, ComponentType.RECEIVER) +
        providers.declarations(packageName, ComponentType.PROVIDER)

    private fun Array<out ComponentInfo>?.declarations(
        packageName: String,
        type: ComponentType,
    ): List<ComponentDeclaration> = orEmpty().mapNotNull { info ->
        // ComponentInfo.enabled is the manifest value; the per-user setting does not change it.
        val id = try {
            ComponentId(packageName, info.name)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Skipping a component of $packageName with an unusable name")
            return@mapNotNull null
        }
        ComponentDeclaration(id = id, type = type, exported = info.exported, enabledInManifest = info.enabled)
    }

    private companion object {
        const val GET_ACTIVITIES = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
        const val GET_SERVICES = PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS
        const val GET_RECEIVERS = PackageManager.GET_RECEIVERS or PackageManager.MATCH_DISABLED_COMPONENTS
        const val GET_PROVIDERS = PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS
        const val MATCH_DISABLED = PackageManager.MATCH_DISABLED_COMPONENTS
        const val ALL_COMPONENTS = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
    }
}
