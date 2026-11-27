// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeReceiverResolverTest {
    @Test fun givenPackageAndMediaFilters_whenResolving_thenReturnsOnlyReceiversForEachAction() {
        val context = RuntimeEnvironment.getApplication()
        val packages = shadowOf(context.packageManager)
        val target = "target.app"
        fun register(name: String, action: String, scheme: String?) {
            val component = ComponentName(target, "$target.$name")
            packages.addReceiverIfNotPresent(component)
            packages.addIntentFilterForReceiver(
                component,
                IntentFilter(action).apply {
                    if (scheme != null) addDataScheme(scheme)
                },
            )
        }
        register("PackageReceiver", Intent.ACTION_PACKAGE_ADDED, "package")
        register("MediaReceiver", Intent.ACTION_MEDIA_MOUNTED, "file")
        register("RemovalReceiver", Intent.ACTION_PACKAGE_REMOVED, "package")
        register("PlainReceiver", Intent.ACTION_PACKAGE_ADDED, null)
        register("WrongScheme", Intent.ACTION_PACKAGE_ADDED, "file")
        val resolver = PackageManagerWakeReceiverResolver(context)

        assertThat(resolver.resolve(target, Intent.ACTION_PACKAGE_ADDED).map { it.id.className })
            .containsExactly("$target.PackageReceiver", "$target.PlainReceiver")
        assertThat(resolver.resolve(target, Intent.ACTION_MEDIA_MOUNTED).map { it.id.className })
            .containsExactly("$target.MediaReceiver")
    }
}
