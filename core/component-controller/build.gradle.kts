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

plugins {
    alias(libs.plugins.blocker.android.library)
    alias(libs.plugins.blocker.android.library.jacoco)
    alias(libs.plugins.blocker.android.hilt)
    alias(libs.plugins.rikka.refine)
    id("kotlinx-serialization")
}

android {
    namespace = "com.merxury.blocker.core.controller"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    compileOnly(projects.core.hiddenApi)
    
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.ifwApi)

    implementation(libs.rikka.refine.runtime)
    implementation(libs.hiddenapibypass)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libsu.core)
    implementation(libs.libsu.io)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(projects.core.testing)
}