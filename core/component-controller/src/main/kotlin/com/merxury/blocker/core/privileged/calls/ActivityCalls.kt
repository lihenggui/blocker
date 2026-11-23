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
import android.content.Intent
import android.os.Build
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.UserIdParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

private const val SHELL_UID = 2000
private const val SHELL_PACKAGE = "com.android.shell"

/**
 * Starts the activity [component] for [user] in a new task, as `am start -n` does, and returns the
 * activity manager's start result; [startOutcome] reads it. Root and the system uid may start any
 * activity, the shell only an exported one: the system refuses it any other with a
 * [SecurityException], which is [CallFailure.NotPermitted].
 *
 * Android 7 to 10 have `startActivityAsUser`; Android 11 and later `startActivityAsUserWithFeature`,
 * with no feature. From Android 12 the system checks that the calling package belongs to the
 * calling uid, except for root and the system uid, so the shell passes its own package.
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class StartActivity(
    val component: ComponentName,
    val user: UserId,
) : PrivilegedCall<Int> {
    override fun run(system: SystemServices): Int {
        val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val caller = if (system.callerUid == SHELL_UID) SHELL_PACKAGE else system.callerPackage
        val am = system.activityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            am.startActivityAsUserWithFeature(null, caller, null, intent, null, null, null, 0, 0, null, null, user.value)
        } else {
            am.startActivityAsUser(null, caller, intent, null, null, null, 0, 0, null, null, user.value)
        }
    }
}

/**
 * What an activity manager start result [code] means. 0 to 3 started the activity or brought its
 * task forward. A refused permission is [CallFailure.NotPermitted]; an intent that resolves to no
 * activity, a class that does not exist and a component that is not an activity are
 * [CallFailure.TargetNotFound]. Every other code is [CallFailure.SystemError] with the code.
 *
 * A start the Intent Firewall blocks cannot be told apart: every release from Android 7 to 16
 * answers it with START_SUCCESS. From 8.1 the activity manager aborts it internally with
 * START_ABORTED, 102, and turns that into START_SUCCESS before answering.
 */
internal fun startOutcome(code: Int, sdk: Int = Build.VERSION.SDK_INT): CallResult<Unit> {
    // ActivityManager's START_ constants: the value up to Android 7.1, and the offset from -100 after.
    fun fatal(legacy: Int, offset: Int) = if (sdk >= Build.VERSION_CODES.O) -100 + offset else legacy
    val permissionDenied = fatal(legacy = -4, offset = 6)
    val notFound = setOf(
        fatal(legacy = -1, offset = 9), // START_INTENT_NOT_RESOLVED
        fatal(legacy = -2, offset = 8), // START_CLASS_NOT_FOUND
        fatal(legacy = -5, offset = 5), // START_NOT_ACTIVITY
    )
    return when (code) {
        // START_SUCCESS, _RETURN_INTENT_TO_CALLER, _TASK_TO_FRONT and _DELIVERED_TO_TOP.
        in 0..3 -> CallResult.Success(Unit)

        permissionDenied -> CallResult.Failure(CallFailure.NotPermitted("Activity start refused ($code)"))

        in notFound -> CallResult.Failure(CallFailure.TargetNotFound("No such activity ($code)"))

        else -> CallResult.Failure(CallFailure.SystemError("Activity start failed with result $code"))
    }
}
