// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.device

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.utils.ManifestParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

interface AppManifestReader {
    /** Reads the installed app's base manifest and wake components from its split APKs, or returns null when unavailable. */
    suspend fun read(app: AppKey): AndroidManifest?
}

internal class LocalAppManifestReader @Inject constructor(
    private val apps: InstalledAppRepository,
    @ApplicationContext private val context: Context,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : AppManifestReader {
    override suspend fun read(app: AppKey): AndroidManifest? = read(app) { ManifestParser.parseManifest(it, ioDispatcher) }

    internal suspend fun read(app: AppKey, parse: suspend (File) -> Result<AndroidManifest>): AndroidManifest? = withContext(ioDispatcher) {
        val apkPath = apps.app(app).first()?.apkPath
        if (apkPath.isNullOrBlank()) {
            Timber.w("Cannot read the manifest of %s: no base APK path", app)
            return@withContext null
        }
        val manifest = parseForApp(app, File(apkPath), parse) ?: return@withContext null
        val splits = splitApkPaths(app, apkPath).mapNotNull { path ->
            parseForApp(app, File(path), parse)?.application
        }
        manifest.copy(
            application = manifest.application.copy(
                receivers = manifest.application.receivers + splits.flatMap { it.receivers },
                services = manifest.application.services + splits.flatMap { it.services },
            ),
        )
    }

    private fun splitApkPaths(app: AppKey, apkPath: String): List<String> {
        if (app.userId != UserId.SYSTEM) return emptyList()
        val info = try {
            context.packageManager.getApplicationInfo(app.packageName, 0)
        } catch (e: NameNotFoundException) {
            Timber.w(e, "Cannot look up split APKs for %s", app)
            return emptyList()
        } catch (e: SecurityException) {
            Timber.w(e, "Cannot look up split APKs for %s", app)
            return emptyList()
        }
        // A package update can replace the APK directory before the installed-app cache catches up.
        return if (info.sourceDir == apkPath) info.splitSourceDirs.orEmpty().toList() else emptyList()
    }

    private suspend fun parseForApp(
        app: AppKey,
        apk: File,
        parse: suspend (File) -> Result<AndroidManifest>,
    ): AndroidManifest? {
        val manifest = parse(apk).getOrElse {
            if (it is CancellationException) throw it
            Timber.w(it, "Cannot read %s for %s", apk.name, app)
            return null
        }
        if (manifest.packageName != app.packageName) {
            Timber.w("Cannot read %s for %s: APK belongs to %s", apk.name, app, manifest.packageName)
            return null
        }
        return manifest
    }
}
