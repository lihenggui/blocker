// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("ktlint:standard:filename")

package com.merxury.blocker.core.model.system

/** Deep background restrictions beyond Android Settings' Allow background usage toggle. */
data class DeepBackgroundRestriction(
    val restrictedBucket: Boolean,
    val runInBackgroundIgnored: Boolean,
    val exactAlarmIgnored: Boolean,
    val changeable: Boolean,
    val bucketSupported: Boolean = true,
    val exactAlarmSupported: Boolean = true,
) {
    val active: Boolean
        get() = runInBackgroundIgnored && (!bucketSupported || restrictedBucket) && (!exactAlarmSupported || exactAlarmIgnored)

    val anyActive: Boolean
        get() = (bucketSupported && restrictedBucket) || runInBackgroundIgnored || (exactAlarmSupported && exactAlarmIgnored)
}

data class DeepRestrictionSnapshot(
    val standbyBucket: Int?,
    val runInBackgroundMode: Int,
    val exactAlarmMode: Int?,
) {
    /** Restores only the fields still holding the value written by Wake. */
    fun restoreMatching(written: DeepRestrictionSnapshot, baseline: DeepRestrictionSnapshot) = DeepRestrictionSnapshot(
        standbyBucket = if (standbyBucket == written.standbyBucket) baseline.standbyBucket else standbyBucket,
        runInBackgroundMode = if (runInBackgroundMode == written.runInBackgroundMode) baseline.runInBackgroundMode else runInBackgroundMode,
        exactAlarmMode = if (exactAlarmMode == written.exactAlarmMode) baseline.exactAlarmMode else exactAlarmMode,
    )
}
