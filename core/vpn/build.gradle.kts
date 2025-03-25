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
    alias(libs.plugins.blocker.android.hilt)
    id("kotlin-parcelize")
}

android {
    namespace = "com.merxury.blocker.core.vpn"
}

dependencies {
    api(libs.timber)
    implementation(projects.core.common)
    implementation(projects.core.data)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
