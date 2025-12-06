/*
 * Copyright 2025 Blocker
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

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.merxury.blocker.configureRecoveryFlash
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin that enables recovery flash support for the application.
 *
 * When this plugin is applied, the generated APK can be flashed in recovery mode
 * to remove IFW rules that may cause boot issues.
 */
class FlashableApkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.application") {
                val extension = extensions.getByType<ApplicationAndroidComponentsExtension>()
                configureRecoveryFlash(extension)
            }
        }
    }
}
