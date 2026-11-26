// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.testing.repository.device

import com.merxury.blocker.core.data.device.AppManifestReader
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.manifest.AndroidManifest

class FakeAppManifestReader : AppManifestReader {
    val manifests = mutableMapOf<AppKey, AndroidManifest>()
    val reads = mutableListOf<AppKey>()

    override suspend fun read(app: AppKey): AndroidManifest? {
        reads += app
        return manifests[app]
    }
}
