// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import androidx.datastore.core.DataStore
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.CodedOutputStream
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.datastore.UserPreferences
import com.merxury.blocker.core.datastore.test.InMemoryDataStore
import com.merxury.blocker.core.datastore.userPreferences
import com.merxury.blocker.core.testing.repository.TestCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class LegacyRuleCacheCleanupTest {
    @get:Rule val filesDir = TemporaryFolder()

    private val catalog = TestCatalogRepository()
    private val store = InMemoryDataStore(oldPreferences())
    private val checkout: File get() = filesDir.root.resolve("blocker-general-rules")

    @Test
    fun givenACheckoutAndAnInstalledCatalog_whenCleaningUp_thenOnlyTheCheckoutAndCursorAreRemoved() = runTest {
        val cleanup = cleanup()
        checkout.resolve(".git").mkdirs()
        checkout.resolve(".git/HEAD").writeText("old-cursor")
        val userRules = filesDir.newFolder("user-generated-rules").resolve("rule.json")
        userRules.writeText("user rule")

        assertThat(cleanup.run()).isTrue()

        assertThat(checkout.exists()).isFalse()
        assertThat(userRules.readText()).isEqualTo("user rule")
        assertThat(store.data.first()).isEqualTo(oldPreferences(withCursor = false))

        assertThat(cleanup.run()).isTrue()
        assertThat(store.data.first()).isEqualTo(oldPreferences(withCursor = false))
    }

    @Test
    fun givenACheckoutAndNoCatalog_whenCleaningUp_thenTheCheckoutAndCursorAreRetained() = runTest {
        catalog.clear()
        checkout.mkdir()

        assertThat(cleanup().run()).isTrue()

        assertThat(checkout.isDirectory).isTrue()
        assertThat(store.data.first()).isEqualTo(oldPreferences())
    }

    @Test
    fun givenAnAlreadyMissingCheckout_whenCleaningUp_thenTheStaleCursorIsStillRemoved() = runTest {
        assertThat(cleanup().run()).isTrue()
        assertThat(store.data.first()).isEqualTo(oldPreferences(withCursor = false))
    }

    @Test
    fun givenCursorClearingFails_whenCleaningUp_thenTheCheckoutIsRetainedForRetry() = runTest {
        checkout.mkdir()
        val unwritable = object : DataStore<UserPreferences> by store {
            override suspend fun updateData(transform: suspend (UserPreferences) -> UserPreferences): UserPreferences = throw IOException("Injected preferences write failure")
        }

        assertThat(cleanup(unwritable).run()).isFalse()
        assertThat(checkout.isDirectory).isTrue()
        assertThat(store.data.first()).isEqualTo(oldPreferences())
    }

    @Test
    fun givenACheckoutSymlink_whenCleaningUp_thenTheTargetIsRetained() = runTest {
        assumeFalse(System.getProperty("os.name", "").orEmpty().startsWith("Windows"))
        val userRules = filesDir.newFolder("user-generated-rules")
        val rule = userRules.resolve("rule.json").apply { writeText("user rule") }
        checkout.mkdir()
        val link = ProcessBuilder("ln", "-s", userRules.path, checkout.resolve("link").path).start()
        assertThat(link.waitFor()).isEqualTo(0)

        assertThat(cleanup().run()).isTrue()
        assertThat(checkout.exists()).isFalse()
        assertThat(rule.readText()).isEqualTo("user rule")
    }

    private fun cleanup(dataStore: DataStore<UserPreferences> = store) = LegacyRuleCacheCleanup(
        filesDir.root,
        catalog,
        BlockerPreferencesDataSource(dataStore),
        Dispatchers.IO,
    )

    private fun oldPreferences(withCursor: Boolean = true): UserPreferences {
        val output = ByteArrayOutputStream()
        userPreferences { appDisplayLanguage = "en" }.writeTo(output)
        CodedOutputStream.newInstance(output).apply {
            if (withCursor) writeString(17, "old-cursor")
            writeString(200, "unknown preference")
            flush()
        }
        return UserPreferences.parseFrom(output.toByteArray())
    }
}
