/*
 * Copyright 2023 Blocker
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
import com.android.build.gradle.AppPlugin
import com.merxury.blocker.BlockerFlavor
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationFirebaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            plugins.withType(AppPlugin::class.java) {
                val extension =
                    extensions.getByName("androidComponents") as ApplicationAndroidComponentsExtension
                extension.beforeVariants {
                    if (it.flavorName?.contains(BlockerFlavor.market.name) == true) {
                        pluginManager.apply("com.google.gms.google-services")
                        pluginManager.apply("com.google.firebase.crashlytics")
                    }
                }
            }
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                val bom = libs.findLibrary("firebase-bom").get()
                add("marketImplementation", platform(bom))
                add("marketImplementation", libs.findLibrary("firebase-analytics").get())
                add("marketImplementation", libs.findLibrary("firebase-crashlytics").get())
            }
        }
    }
}
