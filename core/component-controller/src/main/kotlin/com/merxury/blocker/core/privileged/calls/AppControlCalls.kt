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

package com.merxury.blocker.core.privileged.calls

import android.content.ComponentName
import android.content.IIntentReceiver
import android.content.IIntentSender
import android.content.Intent
import android.content.IntentSender
import android.content.pm.ApplicationInfo
import android.content.pm.IPackageDataObserver
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.VersionedPackage
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import com.merxury.blocker.core.extension.MATCH_ALL_DIRECT_BOOT_COMPONENTS
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.component.userOfUid
import com.merxury.blocker.core.privileged.PrivilegedAction
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.UserIdParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** How long a call waits for the system to report an operation it finishes later. */
internal const val SYSTEM_REPLY_TIMEOUT_MILLIS = 30_000L

/**
 * Clears the data of [packageName] for [user], as "Clear storage" in system settings and
 * `pm clear` do: the system stops the app, removes its data, jobs, alarms, notifications and
 * URI grants, and announces `ACTION_PACKAGE_DATA_CLEARED`. The shell may do this. PackageManager
 * freezes the package for all users while it clears, which stops the app in every user (Android 9
 * and 14 source); only [user]'s data is removed.
 *
 * Blocks until the system reports the outcome, at most [timeoutMillis].
 *
 * @throws NameNotFoundException when [packageName] is not installed for [user]
 * @throws SecurityException when the identity may not, or the package's data is protected
 * @throws IllegalStateException when the system reports a failure
 * @throws TimeoutException when the system does not report in time
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class ClearAppData(
    val packageName: String,
    val user: UserId,
    val timeoutMillis: Long = SYSTEM_REPLY_TIMEOUT_MILLIS,
) : PrivilegedAction {
    override val retryable: Boolean get() = false

    override fun run(system: SystemServices) {
        requireInstalled(system, packageName, user)
        val reply = SystemReply<Boolean>("Clearing the data of $packageName for user ${user.value}")
        val observer = DataObserver(reply)
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            system.activityManager.clearApplicationUserData(packageName, false, observer, user.value)
        } else {
            system.activityManager.clearApplicationUserData(packageName, observer, user.value)
        }
        // The system answers false, and reports a failure, for a package it does not know.
        if (!accepted) throw NameNotFoundException("$packageName is not installed for user ${user.value}")
        check(reply.await(timeoutMillis)) { "The system could not clear the data of $packageName for user ${user.value}" }
    }
}

/**
 * Clears the cache and code cache of [packageName] for [user], internal and external, and only
 * for that user. Blocks until the system reports the outcome, at most [timeoutMillis].
 *
 * Up to Android 8.1 the system needs `DELETE_CACHE_FILES`, which root and the shell have. From
 * Android 9 it needs `INTERNAL_DELETE_CACHE_FILES`, which root has and the shell never has; a
 * caller with only `DELETE_CACHE_FILES`, as the shell, is ignored without an answer, which shows
 * here as a [TimeoutException].
 *
 * @throws NameNotFoundException when [packageName] is not installed for [user]
 * @throws SecurityException when the identity has neither permission
 * @throws TimeoutException when the system does not report in time
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class ClearAppCache(
    val packageName: String,
    val user: UserId,
    val timeoutMillis: Long = SYSTEM_REPLY_TIMEOUT_MILLIS,
) : PrivilegedAction {
    override val retryable: Boolean get() = false

    override fun run(system: SystemServices) {
        requireInstalled(system, packageName, user)
        val reply = SystemReply<Boolean>("Clearing the cache of $packageName for user ${user.value}")
        system.packageManager.deleteApplicationCacheFilesAsUser(packageName, user.value, DataObserver(reply))
        check(reply.await(timeoutMillis)) { "The system could not clear the cache of $packageName for user ${user.value}" }
    }
}

/**
 * Uninstalls [packageName] for [user] only, and blocks until the system reports the outcome, at
 * most [timeoutMillis]. The shell may do this.
 *
 * An app that came with the system is only removed for [user], with `DELETE_SYSTEM_APP`: its APK
 * stays on the system image and `pm install-existing` brings it back. Any other app is removed
 * for [user], and its APK is removed from the device only when no other user has the app.
 *
 * Running it again after it reached the system fails with [NameNotFoundException].
 *
 * @throws NameNotFoundException when [packageName] is not installed for [user]
 * @throws SecurityException when the identity may not, the system asks the user to confirm, or
 * the uninstall is blocked, for example by a device admin
 * @throws IllegalStateException when the system reports another failure
 * @throws TimeoutException when the system does not report in time
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class UninstallApp(
    val packageName: String,
    val user: UserId,
    val timeoutMillis: Long = SYSTEM_REPLY_TIMEOUT_MILLIS,
) : PrivilegedAction {
    override val retryable: Boolean get() = false

    override fun run(system: SystemServices) {
        val info = requireInstalled(system, packageName, user)
        val isSystem = info.applicationInfo?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: false
        val flags = if (isSystem) DELETE_SYSTEM_APP else DELETE_FOR_USER
        val reply = SystemReply<Intent>("Uninstalling $packageName for user ${user.value}")
        val receiver = intentSenderOf(StatusReceiver(reply))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val version = VersionedPackage(packageName, PackageManager.VERSION_CODE_HIGHEST)
            system.packageInstaller.uninstall(version, system.callerPackage, flags, receiver, user.value)
        } else {
            system.packageInstaller.uninstall(packageName, system.callerPackage, flags, receiver, user.value)
        }
        val status = reply.await(timeoutMillis)
        val message = status.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> Unit

            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                throw SecurityException("Uninstalling $packageName needs the user to confirm")

            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                throw SecurityException("Uninstalling $packageName is blocked: $message")

            else -> error("Cannot uninstall $packageName for user ${user.value}: $message")
        }
    }

    private companion object {
        // PackageManager.DELETE_SYSTEM_APP, hidden.
        const val DELETE_SYSTEM_APP = 0x00000004

        // Without DELETE_ALL_USERS the system removes the app for the given user only.
        const val DELETE_FOR_USER = 0
    }
}

/**
 * The names of the packages that have a process running in [user], each once. A process can
 * carry several packages that share its uid.
 *
 * @throws IllegalArgumentException when [user] does not exist
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class RunningPackages(val user: UserId) : PrivilegedCall<List<String>> {
    override fun run(system: SystemServices): List<String> {
        requireUser(system, user)
        return system.activityManager.runningAppProcesses.orEmpty()
            .filter { userOfUid(it.uid) == user.value }
            .flatMap { it.pkgList.orEmpty().asList() }
            .distinct()
    }
}

/**
 * The services of [packageName] that have been started in [user] and not stopped, as fully
 * qualified class names. A service that is only bound is left out: [StopService] cannot stop it.
 *
 * The system lists at most [MAX_SERVICES] services across all users; [Services.capped] says
 * whether that many came back, in which case some may be missing.
 *
 * @throws IllegalArgumentException when [user] does not exist
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class RunningServices(
    val packageName: String,
    val user: UserId,
) : PrivilegedCall<RunningServices.Services> {
    data class Services(val classNames: List<String>, val capped: Boolean)

    override fun run(system: SystemServices): Services {
        requireUser(system, user)
        val all = system.activityManager.getServices(MAX_SERVICES, 0).orEmpty()
        val classNames = all
            .filter { it.started && userOfUid(it.uid) == user.value && it.service?.packageName == packageName }
            .mapNotNull { it.service?.className }
            .distinct()
        return Services(classNames, capped = all.size >= MAX_SERVICES)
    }

    override fun writeResult(result: Services, parcel: Parcel) {
        parcel.writeStringList(result.classNames)
        parcel.writeInt(if (result.capped) 1 else 0)
    }

    override fun readResult(parcel: Parcel): Services = Services(parcel.createStringArrayList().orEmpty(), parcel.readInt() != 0)

    companion object {
        /** As many services as are asked for at once; the old controllers used the same. */
        const val MAX_SERVICES = 10_000
    }
}

/**
 * Starts [component], a service declared by its package, in [user], as `am startservice` does.
 *
 * @throws NameNotFoundException when the package is not installed for [user] or does not declare
 * the service
 * @throws SecurityException when the service is not exported to the identity or needs a
 * permission it lacks, or the system does not let the app start services in the background
 * @throws IllegalStateException when the system could not start it for another reason, for
 * example because the service is disabled
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class StartService(
    val component: ComponentName,
    val user: UserId,
) : PrivilegedAction {
    override val retryable: Boolean get() = false

    override fun run(system: SystemServices) {
        requireService(system, component, user)
        val intent = Intent().setComponent(component)
        val am = system.activityManager
        val started = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                am.startService(null, intent, null, false, system.callerPackage, null, user.value)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                am.startService(null, intent, null, false, system.callerPackage, user.value)

            else -> am.startService(null, intent, null, system.callerPackage, user.value)
        }
        val name = component.flattenToShortString()
        // ActiveServices codes refusals in the package name and puts the reason in the class name.
        when (started?.packageName) {
            null -> error("The system did not start $name for user ${user.value}; it may be disabled")
            "!" -> throw SecurityException("Not allowed to start $name: requires ${started.className}")
            "?" -> throw SecurityException("Not allowed to start $name: ${started.className}")
            "!!" -> error("Cannot start $name: ${started.className}")
        }
    }
}

/**
 * Stops [component], a service declared by its package, in [user], as `am stopservice` does. A
 * service that is not running counts as stopped. A service still bound by a client keeps
 * running until it is unbound.
 *
 * @throws NameNotFoundException when the package is not installed for [user] or does not declare
 * the service
 * @throws SecurityException when the service is not exported to the identity or needs a
 * permission it lacks
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class StopService(
    val component: ComponentName,
    val user: UserId,
) : PrivilegedAction {
    override fun run(system: SystemServices) {
        requireService(system, component, user)
        val intent = Intent().setComponent(component)
        // 1 stopped, 0 not running; -1 the service is not accessible to the caller.
        val result = system.activityManager.stopService(null, intent, null, user.value)
        if (result < 0) {
            throw SecurityException("Not allowed to stop ${component.flattenToShortString()}: not exported or needs a permission")
        }
    }
}

/**
 * One answer from a system callback, which arrives on a binder thread, for the thread that made
 * the request to wait for. Needs no looper, so it works the same in the root process and in this
 * app's process. Both transports run each call on an IO thread of its own, so a call waiting here
 * holds up no other call.
 */
internal class SystemReply<T : Any>(private val operation: String) {
    private val answer = ArrayBlockingQueue<T>(1)

    /** Keeps the first answer; the system may call back more than once. */
    fun deliver(value: T) {
        answer.offer(value)
    }

    /** @throws TimeoutException when no answer arrives within [timeoutMillis] */
    fun await(timeoutMillis: Long): T = answer.poll(timeoutMillis, TimeUnit.MILLISECONDS)
        ?: throw TimeoutException("$operation timed out after $timeoutMillis ms")
}

/** Answers clear data and clear cache. */
internal class DataObserver(private val reply: SystemReply<Boolean>) : IPackageDataObserver.Stub() {
    override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
        reply.deliver(succeeded)
    }
}

/** Receives the status intent that the package installer sends for an uninstall. */
internal class StatusReceiver(private val reply: SystemReply<Intent>) : IIntentSender.Stub() {
    override fun send(
        code: Int,
        intent: Intent?,
        resolvedType: String?,
        finishedReceiver: IIntentReceiver?,
        requiredPermission: String?,
        options: Bundle?,
    ) {
        reply.deliver(intent ?: Intent())
    }

    override fun send(
        code: Int,
        intent: Intent?,
        resolvedType: String?,
        whitelistToken: IBinder?,
        finishedReceiver: IIntentReceiver?,
        requiredPermission: String?,
        options: Bundle?,
    ) {
        reply.deliver(intent ?: Intent())
    }
}

/**
 * An [IntentSender] that delivers to [target] in this process. Reading one from a parcel is the
 * public way to build it; the constructor that takes the binder is hidden.
 */
internal fun intentSenderOf(target: IIntentSender): IntentSender {
    val parcel = Parcel.obtain()
    try {
        parcel.writeStrongBinder(target.asBinder())
        parcel.setDataPosition(0)
        return checkNotNull(IntentSender.readIntentSenderOrNullFromParcel(parcel)) { "No IntentSender read back" }
    } finally {
        parcel.recycle()
    }
}

private fun requireInstalled(system: SystemServices, packageName: String, user: UserId) = packageInfo(system, packageName, 0, user)
    ?: throw NameNotFoundException("$packageName is not installed for user ${user.value}")

private fun requireService(system: SystemServices, component: ComponentName, user: UserId) {
    val flags = PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS or MATCH_ALL_DIRECT_BOOT_COMPONENTS
    val info = packageInfo(system, component.packageName, flags, user)
        ?: throw NameNotFoundException("${component.packageName} is not installed for user ${user.value}")
    if (info.services.orEmpty().none { it.name == component.className }) {
        throw NameNotFoundException("${component.packageName} does not declare the service ${component.className}")
    }
}
