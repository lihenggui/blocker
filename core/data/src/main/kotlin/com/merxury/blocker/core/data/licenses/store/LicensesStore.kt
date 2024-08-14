// Copyright 2023, Google LLC, Christopher Banes and the Tivi project contributors
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.licenses.store

import com.merxury.blocker.core.model.licenses.LicenseItem

interface LicensesStore {
    suspend fun getOpenSourceItemList(): List<LicenseItem>
}
