/*
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
    id("blocker.android.library")
    id("blocker.android.library.jacoco")
    kotlin("kapt")
}

android {
    namespace = "com.merxury.blocker.core.domain"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.componentController)
    implementation(projects.core.data)
    implementation(projects.core.ifwApi)
    implementation(projects.core.model)

    testImplementation(projects.core.testing)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.hilt.android)
    implementation(libs.timber)
    kapt(libs.hilt.compiler)
}