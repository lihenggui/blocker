/*
 * Copyright 2025 Blocker
 * Copyright 2022 The Android Open Source Project
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
plugins {
    alias(libs.plugins.blocker.android.library)
    alias(libs.plugins.blocker.android.library.jacoco)
    id("kotlinx-serialization")
    alias(libs.plugins.blocker.android.hilt)
}

android {
    namespace = "com.merxury.blocker.core.data"
    testOptions.unitTests.isIncludeAndroidResources = true
    // The device tests run the app's Hilt graph.
    defaultConfig.testInstrumentationRunner = "com.merxury.blocker.core.testing.BlockerTestRunner"
}
dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.analytics)
    implementation(projects.core.componentController)
    implementation(projects.core.ifwApi)
    implementation(projects.core.system)
    implementation(libs.smali.dexlib)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(projects.core.testing)
    testImplementation(projects.core.datastoreTest)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hiddenapibypass)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
