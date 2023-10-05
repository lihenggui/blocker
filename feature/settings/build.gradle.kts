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
    alias(libs.plugins.blocker.android.feature)
    alias(libs.plugins.blocker.android.library.compose)
    alias(libs.plugins.blocker.android.library.jacoco)
}
android {
    namespace = "com.merxury.blocker.feature.settings"
}
dependencies {
    implementation(projects.core.datastore)
    implementation(projects.core.rule)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.work.ktx)
    implementation(libs.google.oss.licenses) {
        exclude(group = "androidx.appcompat")
    }
}
