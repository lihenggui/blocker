/*
 * Copyright 2024 Blocker
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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.merxury.blocker.BlockerFlavor
import com.merxury.blocker.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class AndroidApplicationFirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            dependencies {
                val bom = libs.findLibrary("firebase-bom").get()
                "marketImplementation"(platform(bom))
                "marketImplementation"(libs.findLibrary("firebase.analytics").get())
                "marketImplementation"(libs.findLibrary("firebase.crashlytics").get())
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                pluginManager.apply("com.google.gms.google-services")
                pluginManager.apply("com.google.firebase.crashlytics")
            }
            extensions.configure<ApplicationExtension> {
                buildTypes.configureEach {
                    configure<CrashlyticsExtension> {
                        mappingFileUploadEnabled = this@configureEach.name.contains("release")
                    }
                }
                tasks.configureEach {
                    val isFossTask = name.contains(BlockerFlavor.foss.name, ignoreCase = true)
                    if (isFossTask) {
                        val disableKeywords = listOf("google", "crashlytics", "upload", "gms")
                        if (disableKeywords.any { name.contains(it, ignoreCase = true) }) {
                            logger.debug("Disabling task: $name")
                            enabled = false
                        }
                    }
                }
            }
            tasks.withType<Task>().configureEach {
                if (name == "uploadCrashlyticsMappingFileMarketRelease") {
                    mustRunAfter(tasks.named("updateMarketReleaseBadging"))
                }
            }
        }
    }
}
