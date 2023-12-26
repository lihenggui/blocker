/*
 * Copyright 2023 Blocker
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
    alias(libs.plugins.blocker.android.feature)
    alias(libs.plugins.blocker.android.library.compose)
    alias(libs.plugins.blocker.android.library.jacoco)
    id("kotlin-parcelize")
}
android {
    namespace = "com.merxury.blocker.feature.appdetail"
}
dependencies {
    implementation(projects.core.componentController)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.rule)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.work.ktx)
    implementation(libs.kotlinx.datetime)

    testImplementation(projects.core.testing)

    androidTestImplementation(projects.core.testing)
}