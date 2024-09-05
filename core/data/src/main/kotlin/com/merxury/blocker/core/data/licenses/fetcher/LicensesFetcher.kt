// Copyright 2023, Christopher Banes and the Tivi project contributors
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.licenses.fetcher

import com.merxury.blocker.core.model.licenses.LicenseItem

interface LicensesFetcher {
    suspend fun fetch(): List<LicenseItem>
}
