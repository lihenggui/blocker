/*
 * Copyright 2023 The Android Open Source Project
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

package com.merxury.blocker.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of `AnalyticsHelper` which logs events to a Firebase backend.
 */
class FirebaseAnalyticsHelper @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsHelper {

    override fun logEvent(event: AnalyticsEvent) {
        Timber.v("Received analytics event: $event")
        firebaseAnalytics.logEvent(event.type, event.extras.toBundle())
    }

    private fun List<AnalyticsEvent.Param>.toBundle(): Bundle {
        val bundle = Bundle()
        for (extra in this) {
            // Truncate parameter keys and values according to firebase maximum length values.
            bundle.putString(extra.key.take(40), extra.value.take(100))
        }
        return bundle
    }
}
