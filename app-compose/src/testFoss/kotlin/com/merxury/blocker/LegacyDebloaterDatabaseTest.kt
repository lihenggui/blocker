/*
 * Copyright 2026 Blocker
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

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LegacyDebloaterDatabaseTest {
    @Test
    fun givenLegacyDatabase_whenStartupCleanupRunsTwice_thenDeleteOnlyTheLegacyDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = "share_target"
        val current = "blocker"
        context.openOrCreateDatabase(legacy, Context.MODE_PRIVATE, null).close()
        context.openOrCreateDatabase(current, Context.MODE_PRIVATE, null).close()

        removeLegacyDebloaterDatabase(context)
        removeLegacyDebloaterDatabase(context)

        assertThat(context.getDatabasePath(legacy).exists()).isFalse()
        assertThat(context.getDatabasePath(current).exists()).isTrue()
    }
}
