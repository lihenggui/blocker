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
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupRulesTest {
    @Test
    fun givenLegacyBackupRules_whenExcludingDeviceState_thenUserPreferencesRemainEligible() {
        assertDeviceStateExcluded(R.xml.wake_backup_exclusions, "full-backup-content")
    }

    @Test
    fun givenCurrentBackupRules_whenExcludingDeviceState_thenCloudAndDeviceTransferKeepUserPreferencesEligible() {
        assertDeviceStateExcluded(R.xml.wake_data_extraction, "cloud-backup", "device-transfer")
    }

    private fun assertDeviceStateExcluded(resource: Int, vararg sections: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exclusions = sections.associateWith { mutableSetOf<Pair<String, String>>() }
        context.resources.getXml(resource).use { xml ->
            var section: String? = null
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG) {
                    if (xml.name in sections) section = xml.name
                    assertThat(xml.name).isNotEqualTo("include")
                    if (xml.name == "exclude") {
                        val domain = xml.getAttributeValue(null, "domain")
                        val path = xml.getAttributeValue(null, "path")
                        exclusions.getValue(checkNotNull(section)).add(domain to path)
                    }
                } else if (xml.eventType == XmlPullParser.END_TAG && xml.name == section) {
                    section = null
                }
            }
        }
        for (entries in exclusions.values) {
            assertThat(entries.filter { it.first == "file" || it.first == "root" }).containsExactly(
                "file" to "datastore/app_properties.pb",
                "file" to "datastore/app_properties.pb.tmp",
            )
            assertThat(entries).containsAtLeast(
                "database" to "installed_app",
                "database" to "installed_app-shm",
                "database" to "installed_app-wal",
                "database" to "installed_app-journal",
            )
        }
    }
}
