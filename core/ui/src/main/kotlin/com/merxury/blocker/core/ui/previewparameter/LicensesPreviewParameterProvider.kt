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

package com.merxury.blocker.core.ui.previewparameter

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.model.data.LicenseGroup
import com.merxury.blocker.core.model.licenses.LicenseItem
import com.merxury.blocker.core.model.licenses.Scm
import com.merxury.blocker.core.model.licenses.SpdxLicense
import com.merxury.blocker.core.ui.previewparameter.LicensesPreviewParameterData.licensesList

class LicensesPreviewParameterProvider : PreviewParameterProvider<List<LicenseGroup>> {
    override val values: Sequence<List<LicenseGroup>> = sequenceOf(licensesList)
}

object LicensesPreviewParameterData {
    val licensesList = listOf(
        LicenseGroup(
            id = "androidx.activity",
            artifacts = listOf(
                LicenseItem(
                    artifactId = "activity-compose",
                    name = "Activity Compose",
                    version = "1.9.0",
                    groupId = "groupId1",
                    scm = Scm(
                        url = "url1",
                    ),
                    spdxLicenses = listOf(
                        SpdxLicense(
                            name = "Apache License 2.0",
                            identifier = "identifier1",
                            url = "url1",
                        ),
                    ),
                ),
                LicenseItem(
                    artifactId = "activity-Ktx",
                    name = "Activity Kotlin Extensions",
                    version = "1.9.0",
                    groupId = "groupId1",
                    scm = Scm(
                        url = "url1",
                    ),
                    spdxLicenses = listOf(
                        SpdxLicense(
                            name = "Apache License 2.0",
                            identifier = "identifier1",
                            url = "url1",
                        ),
                    ),
                ),
            ),
        ),
        LicenseGroup(
            id = "androidx.annotation",
            artifacts = listOf(
                LicenseItem(
                    artifactId = "annotation",
                    name = "Annotation",
                    version = "1.8.0",
                    groupId = "groupId1",
                    scm = Scm(
                        url = "url1",
                    ),
                    spdxLicenses = listOf(
                        SpdxLicense(
                            name = "Apache License 2.0",
                            identifier = "identifier1",
                            url = "url1",
                        ),
                    ),
                ),
            ),
        ),
    )
}
