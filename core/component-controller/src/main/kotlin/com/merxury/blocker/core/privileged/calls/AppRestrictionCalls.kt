// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.calls

import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_DEFAULT
import android.app.AppOpsManager.MODE_IGNORED
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import com.merxury.blocker.core.appcontrol.AppRestrictionReading
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.system.DeepRestrictionSnapshot
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.UserIdParceler
import com.merxury.blocker.core.privileged.wake.runBounded
import com.merxury.core.ifw.isWakePackageName
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException

@Parcelize
internal data class AppRestrictionState(
    val standbyBucket: Int?,
    val runInBackgroundMode: Int,
    val exactAlarmMode: Int?,
    val changeable: Boolean,
    val bucketSupported: Boolean,
    val exactAlarmSupported: Boolean,
    val firstInstallTime: Long,
) : Parcelable {
    fun snapshot() = DeepRestrictionSnapshot(standbyBucket, runInBackgroundMode, exactAlarmMode)

    fun toReading() = AppRestrictionReading(
        setting = DeepBackgroundRestriction(
            restrictedBucket = standbyBucket == restrictedBucket,
            runInBackgroundIgnored = runInBackgroundMode == MODE_IGNORED,
            exactAlarmIgnored = exactAlarmMode == MODE_IGNORED,
            changeable = changeable,
            bucketSupported = bucketSupported,
            exactAlarmSupported = exactAlarmSupported,
        ),
        snapshot = snapshot(),
        firstInstallTime = firstInstallTime,
    )
}

private object RestrictionSnapshotParceler : Parceler<DeepRestrictionSnapshot> {
    override fun create(parcel: Parcel) = DeepRestrictionSnapshot(
        parcel.readInt().takeUnless { it == -1 },
        parcel.readInt(),
        parcel.readInt().takeUnless { it == -1 },
    )

    override fun DeepRestrictionSnapshot.write(parcel: Parcel, flags: Int) {
        parcel.writeInt(standbyBucket ?: -1)
        parcel.writeInt(runInBackgroundMode)
        parcel.writeInt(exactAlarmMode ?: -1)
    }
}

@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class ReadDeepBackgroundRestriction(val packageName: String, val user: UserId) : PrivilegedCall<AppRestrictionState> {
    override val needsPrivilegedProcess: Boolean get() = standbyBucketSupported
    override val needsSystemFiles: Boolean get() = false

    override fun run(system: SystemServices): AppRestrictionState = run(system, ::runStandbyCommand)

    internal fun run(system: SystemServices, command: (List<String>) -> String): AppRestrictionState {
        val info = restrictionPackage(system, packageName, user)
        return readRestriction(system, packageName, user, info, command)
    }
}

@Parcelize
@TypeParceler<UserId, UserIdParceler>
@TypeParceler<DeepRestrictionSnapshot, RestrictionSnapshotParceler>
internal data class SetDeepBackgroundRestricted(
    val packageName: String,
    val user: UserId,
    val restricted: Boolean,
    val target: DeepRestrictionSnapshot? = null,
    val expected: DeepRestrictionSnapshot? = null,
    val firstInstallTime: Long? = null,
) : PrivilegedCall<AppRestrictionState> {
    override val needsPrivilegedProcess: Boolean get() = standbyBucketSupported
    override val needsSystemFiles: Boolean get() = false
    override val retryable: Boolean get() = false

    override fun run(system: SystemServices): AppRestrictionState = run(system, ::runStandbyCommand)

    internal fun run(system: SystemServices, command: (List<String>) -> String): AppRestrictionState {
        val info = restrictionPackage(system, packageName, user)
        val previous = readRestriction(system, packageName, user, info, command)
        check(firstInstallTime == null || firstInstallTime == previous.firstInstallTime) { "Installation changed for $packageName" }
        if (!previous.changeable) return previous
        check(expected == null || expected == previous.snapshot()) { "Background restrictions changed for $packageName" }
        val desired = target ?: DeepRestrictionSnapshot(
            if (standbyBucketSupported) if (restricted) restrictedBucket else ACTIVE_BUCKET else null,
            if (restricted) MODE_IGNORED else RestrictionOp.RUN_IN_BACKGROUND.restoredMode,
            if (exactAlarmSupported) if (restricted) MODE_IGNORED else RestrictionOp.EXACT_ALARM.restoredMode else null,
        )
        writeRestriction(system, checkNotNull(info.applicationInfo).uid, previous, desired, command)
        return previous.copy(
            standbyBucket = desired.standbyBucket ?: previous.standbyBucket,
            runInBackgroundMode = desired.runInBackgroundMode,
            exactAlarmMode = desired.exactAlarmMode ?: previous.exactAlarmMode,
        )
    }

    private fun writeRestriction(
        system: SystemServices,
        uid: Int,
        previous: AppRestrictionState,
        desired: DeepRestrictionSnapshot,
        command: (List<String>) -> String,
    ) {
        val appOps = system.appOps
        val written = mutableListOf<Pair<RestrictionOp, Int>>()
        var bucketWritten = false
        try {
            fun writeOp(op: RestrictionOp, before: Int, mode: Int) {
                if (before == mode) return
                written += op to before
                appOps.setMode(op.code, uid, packageName, mode)
                check(appOps.checkOperation(op.code, uid, packageName) == mode) {
                    "Cannot change $op for $packageName in user ${user.value}"
                }
            }
            writeOp(RestrictionOp.RUN_IN_BACKGROUND, previous.runInBackgroundMode, desired.runInBackgroundMode)
            val exactAlarm = desired.exactAlarmMode
            if (previous.exactAlarmMode != null && exactAlarm != null) {
                writeOp(RestrictionOp.EXACT_ALARM, previous.exactAlarmMode, exactAlarm)
            }
            val bucket = desired.standbyBucket
            if (previous.standbyBucket != null && bucket != null && bucket != previous.standbyBucket) {
                bucketWritten = true
                writeStandbyBucket(bucket, command)
            }
        } catch (e: Exception) {
            for ((op, mode) in written.asReversed()) {
                try {
                    appOps.setMode(op.code, uid, packageName, mode)
                    check(appOps.checkOperation(op.code, uid, packageName) == mode)
                } catch (rollback: Exception) {
                    Timber.w(rollback, "Cannot restore %s for %s in user %s", op, packageName, user.value)
                }
            }
            if (bucketWritten && previous.standbyBucket != null) {
                try {
                    writeStandbyBucket(previous.standbyBucket, command)
                } catch (rollback: Exception) {
                    Timber.w(rollback, "Cannot restore standby bucket for %s in user %s", packageName, user.value)
                }
            }
            throw e
        }
    }

    private fun writeStandbyBucket(bucket: Int, command: (List<String>) -> String) {
        val written = standbyCommand("set-standby-bucket", packageName, user, command, listOf(bucket.toString()))
        check(written != null && readStandbyBucket(packageName, user, command) == bucket) {
            "Cannot change the standby bucket for $packageName in user ${user.value}"
        }
    }
}

private fun readRestriction(
    system: SystemServices,
    packageName: String,
    user: UserId,
    info: PackageInfo,
    command: (List<String>) -> String,
): AppRestrictionState {
    val uid = info.applicationInfo?.uid ?: throw NameNotFoundException("$packageName is not installed for user ${user.value}")
    val bucket = readStandbyBucket(packageName, user, command)
    if (standbyBucketSupported && bucket == null) throw IOException("Cannot read the standby bucket for $packageName in user ${user.value}")
    return AppRestrictionState(
        standbyBucket = bucket,
        runInBackgroundMode = system.appOps.checkOperation(RestrictionOp.RUN_IN_BACKGROUND.code, uid, packageName),
        exactAlarmMode = if (exactAlarmSupported) system.appOps.checkOperation(RestrictionOp.EXACT_ALARM.code, uid, packageName) else null,
        changeable = packageName !in system.deviceIdle.systemPowerWhitelist,
        bucketSupported = standbyBucketSupported,
        exactAlarmSupported = exactAlarmSupported,
        firstInstallTime = info.firstInstallTime,
    )
}

// The fallback codes are the AOSP AppOps IDs on the SDKs where these operations exist.
private enum class RestrictionOp(opName: String, fallback: Int, val restoredMode: Int) {
    RUN_IN_BACKGROUND("android:run_in_background", 63, MODE_ALLOWED),
    EXACT_ALARM("android:schedule_exact_alarm", 107, MODE_DEFAULT),
    ;

    val code: Int by lazy { resolveAppOp(opName, fallback) }
}

@Suppress("DiscouragedPrivateApi")
private fun resolveAppOp(name: String, fallback: Int): Int = try {
    val method = AppOpsManager::class.java.getMethod("strOpToOp", String::class.java)
    (method.invoke(null, name) as Int).takeIf { it >= 0 } ?: fallback
} catch (_: ReflectiveOperationException) {
    fallback
} catch (_: SecurityException) {
    fallback
}

private val standbyBucketSupported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
private val exactAlarmSupported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
private val restrictedBucket get() = if (exactAlarmSupported) 45 else 40
private const val ACTIVE_BUCKET = 10

private fun restrictionPackage(system: SystemServices, packageName: String, user: UserId): PackageInfo {
    require(isWakePackageName(packageName))
    return packageInfo(system, packageName, 0, user)
        ?: throw NameNotFoundException("$packageName is not installed for user ${user.value}")
}

private fun readStandbyBucket(packageName: String, user: UserId, command: (List<String>) -> String): Int? {
    if (!standbyBucketSupported) return null
    val output = standbyCommand("get-standby-bucket", packageName, user, command) ?: return null
    return output.trim().toIntOrNull()?.takeIf { it >= 0 }.also { bucket ->
        if (bucket == null) Timber.w("Cannot parse the standby bucket for %s in user %s", packageName, user.value)
    }
}

private fun standbyCommand(
    verb: String,
    packageName: String,
    user: UserId,
    command: (List<String>) -> String,
    arguments: List<String> = emptyList(),
): String? = try {
    command(listOf("/system/bin/am", verb, "--user", user.value.toString(), packageName) + arguments)
} catch (e: IOException) {
    Timber.w(e, "Cannot %s for %s in user %s", verb, packageName, user.value)
    null
} catch (e: SecurityException) {
    Timber.w(e, "Cannot %s for %s in user %s", verb, packageName, user.value)
    null
}

private fun runStandbyCommand(arguments: List<String>): String {
    val output = ByteArrayOutputStream()
    val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
    runBounded(process, arguments[1], input = null, output, limitMillis = 3_000)
    return output.toString(Charsets.UTF_8.name())
}
