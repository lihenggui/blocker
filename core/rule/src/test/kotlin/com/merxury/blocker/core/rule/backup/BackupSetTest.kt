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

package com.merxury.blocker.core.rule.backup

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.testing.storage.FakeBackupStorage
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BackupSetTest {

    @Test
    fun whenLayingOutSets_thenTheOwnersIsTheFolderAndOthersAreUserSubFolders() {
        assertThat(BackupSet.OWNER.fileOf(APP)).isEqualTo("$APP.json")
        assertThat(BackupSet(UserId(10)).fileOf(APP)).isEqualTo("user-10/$APP.json")
    }

    @Test
    fun givenFolderNames_whenParsing_thenOnlyCanonicalUserFoldersOfOtherUsersAreSets() {
        assertThat(BackupSet.fromFolderName("user-10")).isEqualTo(BackupSet(UserId(10)))
        assertThat(BackupSet.fromFolderName("user-999")).isEqualTo(BackupSet(UserId(999)))
        listOf("user-0", "user-010", "user-", "user-x", "user--1", "ifw", "user-99999999999").forEach {
            assertThat(BackupSet.fromFolderName(it)).isNull()
        }
    }

    @Test
    fun givenAFolderWithSets_whenListing_thenSetsWithBackupFilesAreReturnedOwnerFirst() = runTest {
        val storage = FakeBackupStorage()
        storage.putFile("$APP.json", "{}")
        storage.putFile("ifw/$APP.xml", "<rules />")
        storage.putFile("user-11/$APP.json", "{}")
        storage.putFile("user-10/$APP.json", "{}")
        storage.putFile("user-12/notes.txt", "")

        assertThat(listBackupSets(storage)).containsExactly(BackupSet.OWNER, BackupSet(UserId(10)), BackupSet(UserId(11))).inOrder()
    }

    @Test
    fun givenOnlyAProfileSet_whenListing_thenTheOwnerSetIsNotListed() = runTest {
        val storage = FakeBackupStorage()
        storage.putFile("user-10/$APP.json", "{}")

        assertThat(listBackupSets(storage)).containsExactly(BackupSet(UserId(10)))
    }

    @Test
    fun givenASetForTheTarget_whenLookingForAlternatives_thenNoneAreOffered() = runTest {
        val storage = FakeBackupStorage().apply {
            putFile("$APP.json", "{}")
            putFile("user-10/$APP.json", "{}")
        }

        assertThat(storage.alternativeBackupSets(UserId(10))).isEmpty()
    }

    @Test
    fun givenOnlyOtherSets_whenLookingForAlternatives_thenTheyAreOfferedOwnerFirst() = runTest {
        val storage = FakeBackupStorage().apply {
            putFile("$APP.json", "{}")
            putFile("user-11/$APP.json", "{}")
            putFile("user-10/notes.txt", "")
        }

        assertThat(storage.alternativeBackupSets(UserId(10))).containsExactly(BackupSet.OWNER, BackupSet(UserId(11))).inOrder()
    }
}
