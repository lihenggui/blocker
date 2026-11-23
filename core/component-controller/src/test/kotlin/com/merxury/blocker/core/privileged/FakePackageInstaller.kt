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

package com.merxury.blocker.core.privileged

import android.content.IIntentSender
import android.content.Intent
import android.content.IntentSender
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerCallback
import android.content.pm.IPackageInstallerSession
import android.content.pm.PackageInstaller
import android.content.pm.ParceledListSlice
import android.content.pm.VersionedPackage
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder

/**
 * A package installer that answers an uninstall through its status receiver, as the system's
 * delete observer adapter does.
 */
internal class FakePackageInstaller : IPackageInstaller {
    /** The `PackageInstaller.STATUS_*` to report, or null to never answer. */
    var status: Int? = PackageInstaller.STATUS_SUCCESS
    var statusMessage: String? = null

    /** Each uninstall: the overload by its first argument type, the package, flags and user. */
    val uninstalls = mutableListOf<String>()

    override fun uninstall(packageName: String, callerPackageName: String, flags: Int, statusReceiver: IntentSender, userId: Int) {
        uninstalls += "String $packageName $flags $userId"
        answer(statusReceiver, packageName)
    }

    override fun uninstall(versionedPackage: VersionedPackage, callerPackageName: String, flags: Int, statusReceiver: IntentSender, userId: Int) {
        uninstalls += "VersionedPackage ${versionedPackage.packageName} $flags $userId"
        answer(statusReceiver, versionedPackage.packageName)
    }

    private fun answer(receiver: IntentSender, packageName: String) {
        val status = status ?: return
        val intent = Intent()
            .putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
            .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, statusMessage)
        // IntentSender.sendIntent goes through the activity manager from Android 12; call the
        // binder behind it directly, with the overload the release has.
        val target = IntentSender::class.java.getMethod("getTarget").invoke(receiver) as IIntentSender
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            target.send(0, intent, null, null, null, null, null)
        } else {
            target.send(0, intent, null, null, null, null)
        }
    }

    override fun createSession(params: PackageInstaller.SessionParams, installerPackageName: String, userId: Int): Int = unused()
    override fun updateSessionAppIcon(sessionId: Int, appIcon: Bitmap) = unused()
    override fun updateSessionAppLabel(sessionId: Int, appLabel: String) = unused()
    override fun abandonSession(sessionId: Int) = unused()
    override fun openSession(sessionId: Int): IPackageInstallerSession = unused()
    override fun getSessionInfo(sessionId: Int): PackageInstaller.SessionInfo = unused()
    override fun getAllSessions(userId: Int): ParceledListSlice<PackageInstaller.SessionInfo> = unused()
    override fun getMySessions(installerPackageName: String, userId: Int): ParceledListSlice<PackageInstaller.SessionInfo> = unused()
    override fun getStagedSessions(): ParceledListSlice<PackageInstaller.SessionInfo> = unused()
    override fun registerCallback(callback: IPackageInstallerCallback, userId: Int) = unused()
    override fun unregisterCallback(callback: IPackageInstallerCallback) = unused()
    override fun installExistingPackage(
        packageName: String,
        installFlags: Int,
        installReason: Int,
        statusReceiver: IntentSender,
        userId: Int,
        whiteListedPermissions: List<String>,
    ) = unused()

    override fun setPermissionsResult(sessionId: Int, accepted: Boolean) = unused()
    override fun asBinder(): IBinder = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("Not used by privileged calls")
}
