// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string
import org.junit.Test
import com.merxury.blocker.core.ui.R.string as coreString

class WakeAppNoticesTest {
    @Test
    fun givenPartialBlock_whenMappingSnackbar_thenReportsBothCounts() {
        val result = blockFailureText(WakeBlockFailure.NeedsRoot, 1, 2)
        assertThat(result).isEqualTo(
            WakeText.Resource(
                string.feature_wake_api_list_pair,
                WakeText.Count(plurals.feature_wake_api_blocked_count, 1),
                WakeText.Count(plurals.feature_wake_api_not_blocked_count, 2),
            ),
        )
    }

    @Test
    fun givenTypedFailures_whenMappingSnackbar_thenUsesTheRelevantReason() {
        val cases = mapOf(
            WakeBlockFailure.NoAccess to coreString.core_ui_reason_no_access,
            WakeBlockFailure.NeedsRoot to coreString.core_ui_reason_shizuku_shell,
            WakeBlockFailure.SenderFilterUnsupported(listOf("peer")) to string.feature_wake_api_sender_unsupported,
            BackgroundUsageFailure.NOT_CHANGEABLE.wakeFailure() to string.feature_wake_api_background_not_changeable,
        )
        cases.forEach { (failure, id) ->
            assertThat(blockFailureText(failure, 0, 1)).isEqualTo(WakeText.Resource(id))
        }
    }

    @Test
    fun givenGenericFailure_whenMappingSnackbar_thenUsesSpecificComponentOrCount() {
        val failure = WakeBlockFailure.SystemError(null)
        assertThat(blockFailureText(failure, 0, 1, "example.Service")).isEqualTo(
            WakeText.Resource(coreString.core_ui_block_component_failed, "Service"),
        )
        assertThat(blockFailureText(failure, 0, 3)).isEqualTo(WakeText.Count(plurals.feature_wake_api_not_blocked_count, 3))
    }
}
