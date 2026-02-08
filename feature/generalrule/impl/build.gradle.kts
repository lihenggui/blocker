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
    alias(libs.plugins.blocker.android.feature.impl)
    alias(libs.plugins.blocker.android.library.compose)
    alias(libs.plugins.blocker.android.library.jacoco)
    alias(libs.plugins.roborazzi)
}
android {
    namespace = "com.merxury.blocker.feature.generalrule.impl"
    testOptions.unitTests.isIncludeAndroidResources = true
}
dependencies {
    implementation(projects.feature.generalrule.api)
    implementation(projects.feature.ruledetail.api)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)

    testImplementation(projects.core.testing)
    testImplementation(projects.core.screenshotTesting)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
}
