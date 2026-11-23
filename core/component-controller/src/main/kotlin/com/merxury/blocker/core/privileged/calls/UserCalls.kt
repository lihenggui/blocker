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

import android.content.pm.UserInfo
import android.os.Build
import android.os.Parcel
import android.os.Process
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import kotlinx.parcelize.Parcelize

/**
 * Every user on the device, leaving out users that are being created or removed. A stopped
 * user is listed with `running` false.
 */
@Parcelize
internal data object ListUsers : PrivilegedCall<List<DeviceUser>> {
    override fun run(system: SystemServices): List<DeviceUser> {
        val users = system.userManager
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            users.getUsers(true, true, true)
        } else {
            users.getUsers(true)
        }
        return infos.map { info ->
            val parent = users.getProfileParent(info.id)?.id
            DeviceUser(
                id = UserId(info.id),
                name = info.name,
                kind = kindOf(info, parent),
                parent = parent?.let(::UserId),
                running = system.activityManager.isUserRunning(info.id, 0),
            )
        }
    }

    private fun kindOf(info: UserInfo, parent: Int?): UserKind = when {
        info.id == UserId.SYSTEM.value -> UserKind.OWNER
        info.isManagedProfile -> UserKind.WORK_PROFILE
        parent != null -> UserKind.OTHER_PROFILE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && info.isProfile -> UserKind.OTHER_PROFILE
        else -> UserKind.SECONDARY
    }

    override fun writeResult(result: List<DeviceUser>, parcel: Parcel) {
        parcel.writeInt(result.size)
        result.forEach { user ->
            parcel.writeInt(user.id.value)
            parcel.writeString(user.name)
            parcel.writeInt(user.kind.ordinal)
            parcel.writeInt(user.parent?.value ?: NO_USER)
            parcel.writeInt(if (user.running) 1 else 0)
        }
    }

    override fun readResult(parcel: Parcel): List<DeviceUser> = List(parcel.readInt()) {
        DeviceUser(
            id = UserId(parcel.readInt()),
            name = parcel.readString(),
            kind = UserKind.entries[parcel.readInt()],
            parent = parcel.readInt().takeIf { it != NO_USER }?.let(::UserId),
            running = parcel.readInt() != 0,
        )
    }

    private const val NO_USER = -1
}

/**
 * The uid of the process [run] runs in. Through the root transport that is the root process;
 * Shizuku runs calls in this app's process, so it says nothing there.
 */
@Parcelize
internal data object CallerUid : PrivilegedCall<Int> {
    override fun run(system: SystemServices): Int = Process.myUid()
}
