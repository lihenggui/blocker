// Copyright 2023, Christopher Banes and the Tivi project contributors
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker

import app.cash.licensee.LicenseeExtension
import app.cash.licensee.UnusedAction
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import java.util.Locale

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
    }
}

fun Project.configureAndroidLicensesTasks() {
    androidComponents {
        onVariants { variant ->
            val capitalizedVariantName = variant.name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault(),
                ) else it.toString()
            }

            val copyArtifactsTask = tasks.register<AssetCopyTask>(
                "copy${capitalizedVariantName}LicenseeOutputToAndroidAssets",
            ) {
                inputFile.set(
                    layout.buildDirectory
                        .file("reports/licensee/android$capitalizedVariantName/artifacts.json"),
                )
                outputFilename.set("licenses.json")

                dependsOn("licenseeAndroid$capitalizedVariantName")
            }

            variant.sources.assets
                ?.addGeneratedSourceDirectory(copyArtifactsTask, AssetCopyTask::outputDirectory)
        }
    }
}

private fun Project.androidComponents(action: ApplicationAndroidComponentsExtension.() -> Unit) =
    extensions.configure(action)
