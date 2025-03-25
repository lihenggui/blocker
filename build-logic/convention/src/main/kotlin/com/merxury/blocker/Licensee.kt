// Copyright 2023, Christopher Banes and the Tivi project contributors
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker

import app.cash.licensee.LicenseeExtension
import app.cash.licensee.UnusedAction
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configureLicensee() {
    with(pluginManager) {
        apply("app.cash.licensee")
    }

    configure<LicenseeExtension> {
        allow("Apache-2.0")
        allow("EPL-2.0")
        allow("MIT")
        allow("BSD-2-Clause")
        allow("BSD-3-Clause")
        allowUrl("http://opensource.org/licenses/BSD-2-Clause")
        allowUrl("https://opensource.org/licenses/MIT")
        allowUrl("https://developer.android.com/studio/terms.html")
        allowUrl("https://github.com/jordond/materialkolor/blob/master/LICENSE") // MIT
        allowUrl("https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE") // MIT
        ignoreDependencies("com.github.jeziellago", "Markwon") // MIT
        ignoreDependencies("com.github.topjohnwu.libsu") // Apache-2.0
        unusedAction(UnusedAction.IGNORE)
        bundleAndroidAsset.set(true)
    }
}
