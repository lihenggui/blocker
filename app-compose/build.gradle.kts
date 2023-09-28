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

import com.merxury.blocker.BlockerBuildType

plugins {
    id("blocker.android.application")
    id("blocker.android.application.compose")
    id("blocker.android.application.flavors")
    id("blocker.android.application.jacoco")
    id("blocker.android.hilt")
    id("blocker.android.application.firebase")
    id("jacoco")
    id("kotlin-parcelize")
    id("com.google.android.gms.oss-licenses-plugin")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.merxury.blocker"
    defaultConfig {
        applicationId = "com.merxury.blocker"
        val gitCommitCount = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim()
        versionCode = gitCommitCount.toIntOrNull() ?: 1
        versionName = "2.0.$gitCommitCount" // X.Y.Z; X = Major, Y = minor, Z = version code

        // Custom test runner to set up Hilt dependency graph
        testInstrumentationRunner = "com.merxury.blocker.core.testing.BlockerTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    androidResources {
        generateLocaleConfig = true
    }
    buildTypes {
        debug {
            applicationIdSuffix = BlockerBuildType.DEBUG.applicationIdSuffix
        }
        val release by getting {
            isMinifyEnabled = true
            applicationIdSuffix = BlockerBuildType.RELEASE.applicationIdSuffix
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // To publish on the Play store a private signing key is required, but to allow anyone
            // who clones the code to sign and run the release variant, use the debug signing key.
            signingConfig = if (project.hasProperty("releaseStoreFile")) {
                signingConfigs.create("release") {
                    storeFile = File(project.properties["releaseStoreFile"] as String)
                    storePassword = project.properties["releaseStorePassword"] as String
                    keyAlias = project.properties["releaseKeyAlias"] as String
                    keyPassword = project.properties["releaseKeyPassword"] as String
                }
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("benchmark") {
            // Enable all the optimizations from release build through initWith(release).
            initWith(release)
            matchingFallbacks.add("release")
            // Debug key signing is available to everyone.
            signingConfig = signingConfigs.getByName("debug")
            // Only use benchmark proguard rules
            proguardFiles("benchmark-rules.pro")
            isMinifyEnabled = true
            applicationIdSuffix = BlockerBuildType.BENCHMARK.applicationIdSuffix
        }
    }
    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(projects.feature.appdetail)
    implementation(projects.feature.applist)
    implementation(projects.feature.generalrule)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)
    implementation(projects.feature.ruledetail)
    implementation(projects.feature.sort)

    implementation(projects.core.analytics)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.provider)
    implementation(projects.core.rule)
    implementation(projects.core.ui)

    implementation(projects.sync.work)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(projects.core.datastoreTest)
    androidTestImplementation(projects.core.dataTest)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.accompanist.testharness)
    androidTestImplementation(kotlin("test"))
    debugImplementation(libs.androidx.compose.ui.testManifest)
    debugImplementation(projects.uiTestHiltManifest)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.compose.runtime.tracing)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window.manager)
    implementation(libs.androidx.work.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.accompanist.navigation.material)
    implementation(libs.coil.kt)
    implementation(libs.coil.kt.svg)
    implementation(libs.hilt.ext.work)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.datetime)
    implementation(libs.libsu.core)
    implementation(libs.timber)

    // Core functions
    testImplementation(projects.core.testing)
    testImplementation(projects.core.datastoreTest)
    testImplementation(projects.core.dataTest)
    testImplementation(projects.core.network)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.accompanist.testharness)
    testImplementation(kotlin("test"))
    implementation(libs.androidx.work.testing)
    kspTest(libs.hilt.compiler)
}
