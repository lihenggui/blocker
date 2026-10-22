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

package com.merxury.blocker.sync.workers

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.datastore.UserPreferences
import com.merxury.blocker.core.datastore.test.InMemoryDataStore
import com.merxury.blocker.core.git.GitClient
import com.merxury.blocker.core.git.RepositoryInfo
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class SyncWorkerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val preferences = BlockerPreferencesDataSource(
        InMemoryDataStore(UserPreferences.getDefaultInstance()),
    )
    private val userDataRepository: UserDataRepository = mock()
    private val gitClient: GitClient = mock()
    private val gitClientFactory: GitClient.Factory = mock()
    private lateinit var projectFolder: File
    private lateinit var worker: SyncWorker

    @Before
    fun setUp() = runTest {
        val filesDir = temporaryFolder.newFolder("files")
        projectFolder = filesDir.resolve(GITHUB.projectName)
        preferences.updateChangeListVersion { copy(ruleCommitId = "old-head") }
        whenever(userDataRepository.userData).thenReturn(preferences.userData)
        whenever(gitClientFactory.create(any(), eq(filesDir))).thenReturn(gitClient)
        whenever(gitClient.cloneRepository()).thenReturn(true)
        whenever(gitClient.setRemote(any(), any())).thenReturn(true)
        whenever(gitClient.resetToRemote(any(), any())).thenReturn(true)
        whenever(gitClient.getHeadCommitId()).thenReturn("new-head")
        worker = SyncWorker(
            appContext = mock(),
            workerParams = mock(),
            userDataRepository = userDataRepository,
            filesDir = filesDir,
            blockerPreferences = preferences,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            analyticsHelper = mock(),
            syncSubscriber = mock(),
            appDebugChecker = mock(),
            gitClientFactory = gitClientFactory,
        )
    }

    @Test
    fun givenMissingCheckout_whenSyncRule_thenClonesAndStoresHead() = runTest {
        assertThat(worker.syncRule()).isTrue()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("new-head")
        verify(gitClient).cloneRepository()
        verify(gitClient, never()).resetToRemote(any(), any())
    }

    @Test
    fun givenFolderWithoutGit_whenSyncRule_thenDeletesFolderBeforeCloning() = runTest {
        projectFolder.mkdirs()
        projectFolder.resolve("old-rule.json").writeText("Old asset rule")
        whenever(gitClient.cloneRepository()).thenAnswer {
            assertThat(projectFolder.exists()).isFalse()
            true
        }

        assertThat(worker.syncRule()).isTrue()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("new-head")
    }

    @Test
    fun givenCloneReturnsFalse_whenSyncRule_thenCommitIdUnchanged() = runTest {
        whenever(gitClient.cloneRepository()).thenReturn(false)

        assertThat(worker.syncRule()).isFalse()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
        verify(gitClient, never()).getHeadCommitId()
    }

    @Test
    fun givenCloneThrows_whenSyncRule_thenCommitIdUnchanged() = runTest {
        whenever(gitClient.cloneRepository()).thenAnswer { throw IOException("Clone failed") }

        assertThat(worker.syncRule()).isFalse()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
        verify(gitClient, times(1)).cloneRepository()
    }

    @Test
    fun givenResetReturnsFalse_whenSyncRule_thenCommitIdUnchanged() = runTest {
        projectFolder.resolve(".git").mkdirs()
        whenever(gitClient.resetToRemote(GITHUB.name, "main")).thenReturn(false)

        assertThat(worker.syncRule()).isFalse()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
        verify(gitClient, never()).cloneRepository()
        verify(gitClient, never()).getHeadCommitId()
    }

    @Test
    fun givenSetRemoteReturnsFalse_whenSyncRule_thenCommitIdUnchanged() = runTest {
        projectFolder.resolve(".git").mkdirs()
        whenever(gitClient.setRemote(GITHUB.url, GITHUB.name)).thenReturn(false)

        assertThat(worker.syncRule()).isFalse()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
        verify(gitClient, never()).resetToRemote(any(), any())
        verify(gitClient, never()).cloneRepository()
        verify(gitClient, never()).getHeadCommitId()
    }

    @Test
    fun givenResetSucceeds_whenSyncRule_thenStoresHeadAfterSettingRemote() = runTest {
        projectFolder.resolve(".git").mkdirs()

        assertThat(worker.syncRule()).isTrue()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("new-head")
        inOrder(gitClient) {
            verify(gitClient).setRemote(GITHUB.url, GITHUB.name)
            verify(gitClient).resetToRemote(GITHUB.name, "main")
            verify(gitClient).getHeadCommitId()
        }
        verify(gitClient, never()).cloneRepository()
    }

    @Test
    fun givenResetThrows_whenSyncRule_thenDeletesCheckoutAndClonesOnce() = runTest {
        projectFolder.resolve(".git").mkdirs()
        projectFolder.resolve("stale-rule.json").writeText("Stale rule")
        whenever(gitClient.resetToRemote(GITHUB.name, "main")).thenAnswer { throw IOException("Corrupt repository") }
        whenever(gitClient.cloneRepository()).thenAnswer {
            assertThat(projectFolder.exists()).isFalse()
            true
        }

        assertThat(worker.syncRule()).isTrue()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("new-head")
        verify(gitClient, times(1)).cloneRepository()
    }

    @Test
    fun givenResetThrowsAndCloneFails_whenSyncRule_thenCommitIdUnchanged() = runTest {
        projectFolder.resolve(".git").mkdirs()
        whenever(gitClient.resetToRemote(GITHUB.name, "main")).thenAnswer { throw IOException("Corrupt repository") }
        whenever(gitClient.cloneRepository()).thenReturn(false)

        assertThat(worker.syncRule()).isFalse()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
        verify(gitClient, times(1)).cloneRepository()
        verify(gitClient, never()).getHeadCommitId()
    }

    @Test
    fun givenResetThrowsAndCloneThrows_whenSyncRule_thenCommitIdUnchanged() = runTest {
        projectFolder.resolve(".git").mkdirs()
        whenever(gitClient.resetToRemote(GITHUB.name, "main")).thenAnswer { throw IOException("Corrupt repository") }
        whenever(gitClient.cloneRepository()).thenAnswer { throw IOException("Clone failed") }

        assertThat(worker.syncRule()).isFalse()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
        verify(gitClient, times(1)).cloneRepository()
    }

    @Test
    fun givenNoHeadAfterClone_whenSyncRule_thenCommitIdUnchanged() = runTest {
        whenever(gitClient.getHeadCommitId()).thenReturn(null)

        assertThat(worker.syncRule()).isFalse()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
    }

    @Test
    fun givenNoHeadAfterReset_whenSyncRule_thenCommitIdUnchanged() = runTest {
        projectFolder.resolve(".git").mkdirs()
        whenever(gitClient.getHeadCommitId()).thenReturn(null)

        assertThat(worker.syncRule()).isFalse()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
    }

    @Test
    fun givenProviderSwitched_whenSyncRule_thenSetsNewRemoteAndStoresItsHead() = runTest {
        projectFolder.resolve(".git").mkdirs()
        whenever(gitClient.getHeadCommitId()).thenReturn("github-head", "gitlab-head")
        assertThat(worker.syncRule()).isTrue()
        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("github-head")
        preferences.setRuleServerProvider(GITLAB)

        assertThat(worker.syncRule()).isTrue()

        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("gitlab-head")
        verify(gitClientFactory).create(
            RepositoryInfo(GITLAB.name, GITLAB.url, GITLAB.projectName, "main"),
            requireNotNull(projectFolder.parentFile),
        )
        inOrder(gitClient) {
            verify(gitClient).setRemote(GITHUB.url, GITHUB.name)
            verify(gitClient).resetToRemote(GITHUB.name, "main")
            verify(gitClient).getHeadCommitId()
            verify(gitClient).setRemote(GITLAB.url, GITLAB.name)
            verify(gitClient).resetToRemote(GITLAB.name, "main")
            verify(gitClient).getHeadCommitId()
        }
        verify(gitClient, never()).cloneRepository()
        verify(gitClient, never()).getTrackingRemote()
    }

    @Test
    fun givenStoredHeadUnchanged_whenSyncRule_thenStillResetsCheckout() = runTest {
        projectFolder.resolve(".git").mkdirs()
        whenever(gitClient.getHeadCommitId()).thenReturn("old-head")

        assertThat(worker.syncRule()).isTrue()

        verify(gitClient).resetToRemote(GITHUB.name, "main")
        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
    }

    @Test
    fun givenSyncCancelled_whenSyncRule_thenKeepsCheckoutAndCommitId() = runTest {
        projectFolder.resolve(".git").mkdirs()
        whenever(gitClient.resetToRemote(GITHUB.name, "main")).thenThrow(CancellationException("Stopped"))

        try {
            worker.syncRule()
            fail("Expected cancellation to propagate")
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("Stopped")
        }

        assertThat(projectFolder.resolve(".git").exists()).isTrue()
        assertThat(preferences.getChangeListVersions().ruleCommitId).isEqualTo("old-head")
        verify(gitClient, never()).cloneRepository()
    }
}
