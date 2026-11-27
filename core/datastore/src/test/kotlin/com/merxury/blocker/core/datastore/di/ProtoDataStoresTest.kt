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

package com.merxury.blocker.core.datastore.di

import com.merxury.blocker.core.datastore.AppProperties
import com.merxury.blocker.core.datastore.AppPropertiesSerializer
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.datastore.UserPreferences
import com.merxury.blocker.core.datastore.UserPreferencesSerializer
import com.merxury.blocker.core.datastore.appProperties
import com.merxury.blocker.core.datastore.copy
import com.merxury.blocker.core.datastore.userPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProtoDataStoresTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testScope = TestScope(UnconfinedTestDispatcher())

    // A lone 0 byte is an invalid protobuf tag, the same input the serializer tests use.
    private val garbage = byteArrayOf(0)

    @Test
    fun givenCorruptUserPreferencesFile_whenRead_thenReturnsDefaultWithStatisticsDisabled() = testScope.runTest {
        val file = tempFolder.newFile("user_preferences.pb").apply { writeBytes(garbage) }
        val dataStore = createUserPreferencesDataStore(
            serializer = UserPreferencesSerializer(),
            scope = backgroundScope,
            produceFile = { file },
        )

        // Statistics are off after a reset because the user's choice is unknown.
        assertEquals(userPreferences { disableStatistics = true }, dataStore.data.first())
    }

    @Test
    fun givenCorruptUserPreferencesFile_whenRead_thenStatisticsDisabledAndOtherFieldsDefault() = testScope.runTest {
        val file = tempFolder.newFile("user_preferences.pb").apply { writeBytes(garbage) }
        val dataStore = createUserPreferencesDataStore(
            serializer = UserPreferencesSerializer(),
            scope = backgroundScope,
            produceFile = { file },
        )

        val actual = dataStore.data.first()

        assertTrue(actual.disableStatistics)
        assertEquals(
            UserPreferences.getDefaultInstance(),
            actual.copy { disableStatistics = false },
        )
        assertFalse(BlockerPreferencesDataSource(dataStore).userData.first().enableStatistics)
    }

    @Test
    fun givenNoUserPreferencesFile_whenRead_thenStatisticsStayAtDefault() = testScope.runTest {
        val file = File(tempFolder.root, "user_preferences.pb")
        val dataStore = createUserPreferencesDataStore(
            serializer = UserPreferencesSerializer(),
            scope = backgroundScope,
            produceFile = { file },
        )

        assertEquals(UserPreferences.getDefaultInstance(), dataStore.data.first())
        assertTrue(BlockerPreferencesDataSource(dataStore).userData.first().enableStatistics)
    }

    @Test
    fun givenCorruptUserPreferencesFile_whenWrittenAfterRecovery_thenRoundTrips() = testScope.runTest {
        val file = tempFolder.newFile("user_preferences.pb").apply { writeBytes(garbage) }
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val dataStore = createUserPreferencesDataStore(
            serializer = UserPreferencesSerializer(),
            scope = firstScope,
            produceFile = { file },
        )
        dataStore.data.first()

        dataStore.updateData { it.copy { appDisplayLanguage = "en" } }

        assertEquals("en", dataStore.data.first().appDisplayLanguage)
        // Only one DataStore may be active per file, so close the first one.
        firstScope.cancel()
        val reopened = createUserPreferencesDataStore(
            serializer = UserPreferencesSerializer(),
            scope = backgroundScope,
            produceFile = { file },
        )
        assertEquals(
            userPreferences {
                appDisplayLanguage = "en"
                disableStatistics = true
            },
            reopened.data.first(),
        )
    }

    @Test
    fun givenCorruptAppPropertiesFile_whenRead_thenReturnsDefaultValue() = testScope.runTest {
        val file = tempFolder.newFile("app_properties.pb").apply { writeBytes(garbage) }
        val dataStore = createAppPropertiesDataStore(
            serializer = AppPropertiesSerializer(),
            scope = backgroundScope,
            produceFile = { file },
        )

        assertEquals(AppProperties.getDefaultInstance(), dataStore.data.first())
    }

    @Test
    fun givenCorruptAppPropertiesFile_whenWrittenAfterRecovery_thenRoundTrips() = testScope.runTest {
        val file = tempFolder.newFile("app_properties.pb").apply { writeBytes(garbage) }
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val dataStore = createAppPropertiesDataStore(
            serializer = AppPropertiesSerializer(),
            scope = firstScope,
            produceFile = { file },
        )
        dataStore.data.first()

        dataStore.updateData { it.copy { pmBlockRecordsSeeded = true } }

        assertTrue(dataStore.data.first().pmBlockRecordsSeeded)
        // Only one DataStore may be active per file, so close the first one.
        firstScope.cancel()
        val reopened = createAppPropertiesDataStore(
            serializer = AppPropertiesSerializer(),
            scope = backgroundScope,
            produceFile = { file },
        )
        assertEquals(appProperties { pmBlockRecordsSeeded = true }, reopened.data.first())
    }

    @Test
    fun givenObsoleteCacheFields_whenOpeningTheStore_thenTheyAreRemovedFromDisk() = testScope.runTest {
        val current = appProperties {
            rootGrantRemembered = true
            pmBlockRecordsSeeded = true
            lastCatalogSyncedEpochMs = 1_795_335_233_000L
        }
        val old = current.copy {
            componentDatabaseInitialized = true
            generalRuleDatabaseInitialized = true
            lastOpenedAppListHash = "obsolete"
        }
        val file = tempFolder.newFile("app_properties.pb").apply { writeBytes(old.toByteArray()) }

        val store = createAppPropertiesDataStore(AppPropertiesSerializer(), backgroundScope) { file }

        assertEquals(current, store.data.first())
        assertEquals(current, AppProperties.parseFrom(file.readBytes()))
    }

    @Test
    fun givenCorruptFiles_whenRead_thenCorruptionIsLoggedWithStoreName() = testScope.runTest {
        val logged = mutableListOf<String>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                logged += message
            }
        }
        Timber.plant(tree)
        try {
            val userFile = tempFolder.newFile("user_preferences.pb").apply { writeBytes(garbage) }
            val appFile = tempFolder.newFile("app_properties.pb").apply { writeBytes(garbage) }
            createUserPreferencesDataStore(UserPreferencesSerializer(), backgroundScope) { userFile }
                .data.first()
            createAppPropertiesDataStore(AppPropertiesSerializer(), backgroundScope) { appFile }
                .data.first()
        } finally {
            Timber.uproot(tree)
        }

        assertTrue(logged.any { "user preferences" in it }, "Logged: $logged")
        assertTrue(logged.any { "app properties" in it }, "Logged: $logged")
    }
}
