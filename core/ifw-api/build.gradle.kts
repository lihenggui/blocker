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
    id("kotlinx-serialization")
    alias(libs.plugins.blocker.android.hilt)
}

android {
    namespace = "com.merxury.blocker.core.ifw"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")
    }
}

dependencies {
    implementation(projects.core.common)
    testImplementation(projects.core.testing)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libsu.core)
    implementation(libs.libsu.io)
    implementation(libs.xmlutil.core.android)
    implementation(libs.xmlutil.serialization.android)
}