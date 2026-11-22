// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.debloater

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class DebloatableComponentDaoTest {
    private lateinit var db: DebloaterDatabase
    private lateinit var dao: DebloatableComponentDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DebloaterDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.DebloatableComponentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun component(
        packageName: String,
        componentName: String = "$packageName.Main",
    ) = DebloatableComponentEntity(
        packageName = packageName,
        componentName = componentName,
        simpleName = "Main",
        displayName = "Main",
        ifwBlocked = false,
        pmBlocked = false,
        type = ACTIVITY,
        exported = false,
    )

    @Test
    fun givenPackagesDifferingAtUnderscore_whenGetByPackageName_thenOnlyExactPackageIsReturned() = runTest {
        val underscore = component("com.a_b")
        val other = component("com.axb")
        dao.upsertAll(listOf(underscore, other))

        assertThat(dao.getByPackageName("com.a_b").first()).containsExactly(underscore)
        assertThat(dao.getByPackageName("com.axb").first()).containsExactly(other)
    }

    @Test
    fun givenPackagesDifferingAtUnderscore_whenDeleteByPackageName_thenOtherPackageIsKept() = runTest {
        val underscore = component("com.a_b")
        val other = component("com.axb")
        dao.upsertAll(listOf(underscore, other))

        val deleted = dao.deleteByPackageName("com.a_b")

        assertThat(deleted).isEqualTo(1)
        assertThat(dao.getAll().first()).containsExactly(other)
    }

    @Test
    fun givenPackagesDifferingAtUnderscore_whenGetByPackageNameAndComponentName_thenOnlyExactRowIsReturned() = runTest {
        val underscore = component("com.a_b", "com.shared.Main")
        val other = component("com.axb", "com.shared.Main")
        dao.upsertAll(listOf(underscore, other))

        assertThat(dao.getByPackageNameAndComponentName("com.a_b", "com.shared.Main").first())
            .isEqualTo(underscore)
        assertThat(dao.getByPackageNameAndComponentName("com.axb", "com.shared.Main").first())
            .isEqualTo(other)
    }

    @Test
    fun givenComponentNamesDifferingAtUnderscore_whenGetByPackageNameAndComponentName_thenWildcardDoesNotMatch() = runTest {
        dao.upsertAll(listOf(component("com.example", "com.example.Axb")))

        assertThat(dao.getByPackageNameAndComponentName("com.example", "com.example.A_b").first())
            .isNull()
    }

    @Test
    fun givenPackageNamesDifferingInCase_whenQueryingByPackageName_thenMatchingIsCaseSensitive() = runTest {
        val lower = component("com.example.app")
        val upper = component("com.example.App")
        dao.upsertAll(listOf(lower, upper))

        assertThat(dao.getByPackageName("com.example.app").first()).containsExactly(lower)
        assertThat(dao.getByPackageNameAndComponentName("com.example.app", upper.componentName).first())
            .isNull()

        assertThat(dao.deleteByPackageName("com.example.APP")).isEqualTo(0)
        assertThat(dao.deleteByPackageName("com.example.app")).isEqualTo(1)
        assertThat(dao.getAll().first()).containsExactly(upper)
    }
}
