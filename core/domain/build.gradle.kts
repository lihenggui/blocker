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
    alias(libs.plugins.blocker.android.library)
    alias(libs.plugins.blocker.android.library.jacoco)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.merxury.blocker.core.domain"
}

dependencies {
    api(projects.core.data)
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.componentController)
    implementation(projects.core.ifwApi)
    implementation(projects.core.rule)

    testImplementation(projects.core.testing)
    implementation(libs.androidx.work.ktx)
    implementation(libs.kotlinx.datetime)
    implementation(libs.hilt.android)
    implementation(libs.timber)
    implementation(libs.javax.inject)

    ksp(libs.hilt.compiler)

    testImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.work.testing)
}