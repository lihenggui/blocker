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

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.merxury.blocker.core.controllers.shizuku.DefaultShizukuApi
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult
import com.merxury.blocker.core.controllers.shizuku.ShizukuApi
import com.merxury.blocker.core.controllers.shizuku.ShizukuInitializer
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.shizuku.ShizukuSystemServices
import com.merxury.blocker.core.privileged.shizuku.ShizukuTransport
import com.merxury.blocker.core.privileged.shizuku.ShizukuUserService
import com.merxury.blocker.core.privileged.shizuku.ShizukuUserServiceConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"

// Shizuku sends its binder to this process when the process starts, or when a server starts later.
private val SHIZUKU_BINDER_WAIT = 2.minutes

/**
 * A [ShizukuTransport] connected as [expected], for the device tests that go through Shizuku.
 *
 * A Shizuku server reads which uids it allows from their [SHIZUKU_PERMISSION] runtime permission
 * once, when it starts, and later learns only the answers given in its own dialog. A `pm grant`
 * while the server runs is not seen, and neither is the grant of a reinstalled test package, which
 * gets a new uid. Connecting would then ask for the permission and wait for a dialog nobody
 * answers, so this fails at once and names the fix: grant this package, then start Shizuku.
 */
internal fun connectedShizukuTransport(context: Context, expected: PrivilegedIdentity): ShizukuTransport {
    val api = DefaultShizukuApi()
    val initializer = ShizukuInitializer(api)
    awaitShizukuBinder(initializer)
    // The binder arrives with the server's answer for this uid, so this reads no stale state.
    if (runBlocking { initializer.registerShizukuIfGranted() } !is RegisterShizukuResult.Granted) {
        val granted = context.checkSelfPermission(SHIZUKU_PERMISSION) == PackageManager.PERMISSION_GRANTED
        fail(
            "The Shizuku server does not allow ${context.packageName} (uid ${Process.myUid()}, $SHIZUKU_PERMISSION granted: $granted). " +
                "It reads grants only when it starts: `pm grant ${context.packageName} $SHIZUKU_PERMISSION`, then start or restart " +
                "Shizuku, and keep this package installed between runs.",
        )
    }
    val transport = shizukuTransport(context, api)
    assertEquals(ChannelState.Ready(expected), runBlocking { transport.connect() })
    return transport
}

/** Waits for the binder Shizuku sends to this process, failing the test if none arrives. */
internal fun awaitShizukuBinder(initializer: ShizukuInitializer = ShizukuInitializer(DefaultShizukuApi())) {
    runBlocking { withTimeout(SHIZUKU_BINDER_WAIT) { initializer.awaitBinder() } }
}

/** A [ShizukuTransport] over [api] that nothing has connected yet. */
internal fun shizukuTransport(context: Context, api: ShizukuApi = DefaultShizukuApi()): ShizukuTransport = ShizukuTransport(
    ShizukuInitializer(api),
    api,
    ShizukuSystemServices(context),
    ShizukuUserService(ShizukuUserServiceConnector(context), attachTimeout = 15.seconds),
    Dispatchers.IO,
)
