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

plugins {
    alias(libs.plugins.blocker.android.library)
    alias(libs.plugins.blocker.android.library.jacoco)
    alias(libs.plugins.blocker.android.hilt)
    id("kotlinx-serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.merxury.blocker.core.rule"
    testOptions.unitTests.isIncludeAndroidResources = true
    // The device tests run the app's Hilt graph.
    defaultConfig.testInstrumentationRunner = "com.merxury.blocker.core.testing.BlockerTestRunner"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.componentController)
    implementation(projects.core.data)
    implementation(projects.core.ifwApi)

    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.ext.work)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xmlutil.core)
    implementation(libs.xmlutil.serialization.jvm)

    ksp(libs.hilt.compiler)
    ksp(libs.hilt.ext.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)

    androidTestImplementation(projects.core.system)
    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hiddenapibypass)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlin.test.junit)
    kspAndroidTest(libs.hilt.compiler)
}
