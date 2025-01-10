/*
 * Copyright 2022 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import com.merxury.blocker.configureAndroidLicensesTasks
import com.merxury.blocker.configureBadgingTasks
import com.merxury.blocker.configureGradleManagedDevices
import com.merxury.blocker.configureKotlinAndroid
import com.merxury.blocker.configureLicensee
import com.merxury.blocker.configurePrintApksTask
import com.merxury.blocker.configureRenameBuildOutputTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
                apply("blocker.android.lint")
                apply("com.dropbox.dependency-guard")
            }
            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 35
                @Suppress("UnstableApiUsage")
                testOptions.animationsDisabled = true
                configureGradleManagedDevices(this)
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                configurePrintApksTask(this)
                configureRenameBuildOutputTask(this)
                configureBadgingTasks(extensions.getByType<BaseExtension>(), this)
                configureLicensee()
                configureAndroidLicensesTasks()
            }
            extensions.configure<JavaPluginExtension> {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
            }
        }
    }
}
