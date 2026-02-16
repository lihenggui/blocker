/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor
import org.gradle.kotlin.dsl.invoke

@Suppress("EnumEntryName")
enum class FlavorDimension {
    contentType
}

@Suppress("EnumEntryName")
enum class BlockerFlavor(val dimension: FlavorDimension, val applicationIdSuffix: String? = null) {
    foss(FlavorDimension.contentType),
    market(FlavorDimension.contentType),
}

fun configureFlavors(
    commonExtension: CommonExtension,
    flavorConfigurationBlock: ProductFlavor.(flavor: BlockerFlavor) -> Unit = {},
) {
    commonExtension.apply {
        FlavorDimension.entries.forEach { flavorDimension ->
            flavorDimensions += flavorDimension.name
        }
        productFlavors {
            BlockerFlavor.entries.forEach { blockerFlavor ->
                register(blockerFlavor.name) {
                    dimension = blockerFlavor.dimension.name
                    flavorConfigurationBlock(this, blockerFlavor)
                    if (commonExtension is ApplicationExtension && this is ApplicationProductFlavor) {
                        if (blockerFlavor.applicationIdSuffix != null) {
                            this.applicationIdSuffix = blockerFlavor.applicationIdSuffix
                        }
                    }
                }
            }
        }
    }
}
