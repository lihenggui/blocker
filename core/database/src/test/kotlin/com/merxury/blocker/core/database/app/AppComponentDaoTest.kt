// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.SERVICE
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
class AppComponentDaoTest {
    private lateinit var db: InstalledAppDatabase
    private lateinit var dao: AppComponentDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            InstalledAppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.appComponentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun component(
        packageName: String,
        componentName: String = "$packageName.Main",
        type: ComponentType = ACTIVITY,
    ) = AppComponentEntity(
        packageName = packageName,
        componentName = componentName,
        ifwBlocked = false,
        pmBlocked = false,
        type = type,
        exported = false,
    )

    @Test
    fun givenPackagesDifferingAtUnderscore_whenGetByPackageName_thenOnlyExactPackageIsReturned() = runTest {
        val underscore = component("com.a_b")
        val other = component("com.axb")
        dao.upsertComponentList(listOf(underscore, other))

        assertThat(dao.getByPackageName("com.a_b").first()).containsExactly(underscore)
        assertThat(dao.getByPackageName("com.axb").first()).containsExactly(other)
    }

    @Test
    fun givenPackagesDifferingAtUnderscore_whenDeleteByPackageName_thenOtherPackageIsKept() = runTest {
        val underscore = component("com.a_b")
        val other = component("com.axb")
        dao.upsertComponentList(listOf(underscore, other))

        val deleted = dao.deleteByPackageName("com.a_b")

        assertThat(deleted).isEqualTo(1)
        assertThat(dao.getByPackageName("com.a_b").first()).isEmpty()
        assertThat(dao.getByPackageName("com.axb").first()).containsExactly(other)
    }

    @Test
    fun givenPackagesDifferingAtUnderscore_whenGetByPackageNameAndComponentName_thenOnlyExactRowIsReturned() = runTest {
        val underscore = component("com.a_b", "com.shared.Main")
        val other = component("com.axb", "com.shared.Main")
        dao.upsertComponentList(listOf(underscore, other))

        assertThat(dao.getByPackageNameAndComponentName("com.a_b", "com.shared.Main").first())
            .isEqualTo(underscore)
        assertThat(dao.getByPackageNameAndComponentName("com.axb", "com.shared.Main").first())
            .isEqualTo(other)
    }

    @Test
    fun givenComponentNamesDifferingAtUnderscore_whenGetByPackageNameAndComponentName_thenWildcardDoesNotMatch() = runTest {
        dao.upsertComponentList(listOf(component("com.example", "com.example.Axb")))

        assertThat(dao.getByPackageNameAndComponentName("com.example", "com.example.A_b").first())
            .isNull()
    }

    @Test
    fun givenPackagesDifferingAtUnderscore_whenGetByPackageNameAndType_thenOnlyExactPackageIsReturned() = runTest {
        val underscore = component("com.a_b", "com.a_b.Main", ACTIVITY)
        val underscoreService = component("com.a_b", "com.a_b.Svc", SERVICE)
        val other = component("com.axb", "com.axb.Main", ACTIVITY)
        dao.upsertComponentList(listOf(underscore, underscoreService, other))

        assertThat(dao.getByPackageNameAndType("com.a_b", ACTIVITY).first()).containsExactly(underscore)
        assertThat(dao.getByPackageNameAndType("com.axb", ACTIVITY).first()).containsExactly(other)
    }

    @Test
    fun givenPackageNamesDifferingInCase_whenQueryingByPackageName_thenMatchingIsCaseSensitive() = runTest {
        val lower = component("com.example.app")
        val upper = component("com.example.App")
        dao.upsertComponentList(listOf(lower, upper))

        assertThat(dao.getByPackageName("com.example.app").first()).containsExactly(lower)
        assertThat(dao.getByPackageNameAndType("com.example.App", ACTIVITY).first()).containsExactly(upper)
        assertThat(dao.getByPackageNameAndComponentName("com.example.app", upper.componentName).first())
            .isNull()

        assertThat(dao.deleteByPackageName("com.example.APP")).isEqualTo(0)
        assertThat(dao.deleteByPackageName("com.example.app")).isEqualTo(1)
        assertThat(dao.getByPackageName("com.example.App").first()).containsExactly(upper)
    }

    @Test
    fun givenComponentNamesDifferingAtUnderscoreOrCase_whenGetByName_thenOnlyExactNameMatches() = runTest {
        dao.upsertComponentList(listOf(component("com.example", "Axb")))

        assertThat(dao.getByName("A_b").first()).isNull()
        assertThat(dao.getByName("axb").first()).isNull()
        assertThat(dao.getByName("Axb").first()).isNotNull()
    }
}
