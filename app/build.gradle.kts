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

import com.android.build.api.dsl.ManagedVirtualDevice
import com.merxury.blocker.BlockerBuildType

// TODO: Remove once https://youtrack.jetbrains.com/issue/KTIJ-19369 is fixed
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("blocker.android.application")
    id("blocker.android.application.compose")
    id("blocker.android.application.jacoco")
    id("blocker.android.hilt")
    id("blocker.firebase")
    id("jacoco")
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.merxury.blocker"
    defaultConfig {
        applicationId = "com.merxury.blocker"
        versionCode = 1270
        versionName = "1.2.70" // X.Y.Z; X = Major, Y = minor, Z = Patch level

        // Custom test runner to set up Hilt dependency graph
        testInstrumentationRunner = "com.merxury.blocker.core.testing.BlockerTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    buildTypes {
        val debug by getting {
            applicationIdSuffix = BlockerBuildType.DEBUG.applicationIdSuffix
        }
        val release by getting {
            isMinifyEnabled = true
            applicationIdSuffix = BlockerBuildType.RELEASE.applicationIdSuffix
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // To publish on the Play store a private signing key is required, but to allow anyone
            // who clones the code to sign and run the release variant, use the debug signing key.
            // TODO: Abstract the signing configuration to a separate file to avoid hardcoding this.
            signingConfig = signingConfigs.getByName("debug")
        }
        val benchmark by creating {
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
    packagingOptions {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        // TODO: Convert it as a convention plugin once Flamingo goes out (https://github.com/android/nowinandroid/issues/523)
        managedDevices {
            devices {
                maybeCreate<ManagedVirtualDevice>("pixel4api30").apply {
                    device = "Pixel 4"
                    apiLevel = 30
                    // ATDs currently support only API level 30.
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // TODO database, rule, datetime, network should be removed
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:rule"))
    implementation(libs.kotlinx.datetime)
    implementation(project(":core:component-controller"))
    implementation(project(":core:ifw-api"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))

    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:data-test"))
    androidTestImplementation(project(":core:network"))
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.accompanist.testharness)
    androidTestImplementation(kotlin("test"))
    debugImplementation(libs.androidx.compose.ui.testManifest)
    debugImplementation(project(":ui-test-hilt-manifest"))

    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.compose.runtime.tracing)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window.manager)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.coil.kt)
    implementation(libs.coil.kt.svg)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.lottie)
    implementation(libs.appiconloader)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.tracing.ktx)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.work.ktx)
    implementation(libs.hilt.ext.work)
    implementation(libs.freereflection)
    kapt(libs.hilt.ext.compiler)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlin.serialization)
    implementation(libs.gson)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.xlog)
    implementation(libs.libsu.core)
    implementation(libs.libsu.io)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.apache.commons.csv)
}

// androidx.test is forcing JUnit, 4.12. This forces it to use 4.13
configurations.configureEach {
    resolutionStrategy {
        force(libs.junit4)
        // Temporary workaround for https://issuetracker.google.com/174733673
        force("org.objenesis:objenesis:2.6")
    }
}
