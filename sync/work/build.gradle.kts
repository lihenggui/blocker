/*
 * Copyright 2024 Blocker
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
    defaultConfig {
        testInstrumentationRunner = "com.merxury.blocker.core.testing.BlockerTestRunner"
    }
    namespace = "com.merxury.blocker.sync"
}
dependencies {
    ksp(libs.hilt.ext.compiler)

    implementation(libs.androidx.tracing.ktx)
    implementation(libs.androidx.work.ktx)
    implementation(libs.hilt.ext.work)
    implementation(projects.core.analytics)
    implementation(projects.core.data)
    implementation(projects.core.git)
    implementation(projects.core.rule)

    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(projects.core.testing)
}